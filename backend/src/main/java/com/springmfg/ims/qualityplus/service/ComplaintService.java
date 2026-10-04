package com.springmfg.ims.qualityplus.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.insight.traceability.TraceabilityDto;
import com.springmfg.ims.insight.traceability.TraceabilityService;
import com.springmfg.ims.inventory.domain.FgBatch;
import com.springmfg.ims.inventory.repository.FgBatchRepository;
import com.springmfg.ims.partner.domain.Customer;
import com.springmfg.ims.partner.repository.CustomerRepository;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.product.repository.ProductRepository;
import com.springmfg.ims.qualityplus.domain.CapaRecord;
import com.springmfg.ims.qualityplus.domain.ComplaintStatus;
import com.springmfg.ims.qualityplus.domain.CustomerComplaint;
import com.springmfg.ims.qualityplus.dto.ComplaintDtos.ComplaintDto;
import com.springmfg.ims.qualityplus.dto.ComplaintDtos.ComplaintSummaryDto;
import com.springmfg.ims.qualityplus.dto.ComplaintDtos.LookupDto;
import com.springmfg.ims.qualityplus.dto.ComplaintDtos.SaveComplaintRequest;
import com.springmfg.ims.qualityplus.dto.ComplaintDtos.TransitionComplaintRequest;
import com.springmfg.ims.qualityplus.repository.CapaRecordRepository;
import com.springmfg.ims.qualityplus.repository.CustomerComplaintRepository;

/**
 * Customer complaints (QMS-009): registered against a customer and, when known, the FG batch and product, then
 * worked through OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED. Resolving needs a disposition and notes. The FG batch
 * link gives the complaint its traceability view back to raw-material batches. Returned goods are not posted to
 * stock here; the quantity returned is recorded for the complaint only.
 */
@Service
public class ComplaintService {

    private final CustomerComplaintRepository repo;
    private final CustomerRepository customerRepo;
    private final ProductRepository productRepo;
    private final FgBatchRepository fgBatchRepo;
    private final CapaRecordRepository capaRepo;
    private final TraceabilityService traceabilityService;
    private final DocumentNumberService numberService;
    private final AuditService auditService;

    public ComplaintService(CustomerComplaintRepository repo, CustomerRepository customerRepo,
                            ProductRepository productRepo, FgBatchRepository fgBatchRepo,
                            CapaRecordRepository capaRepo, TraceabilityService traceabilityService,
                            DocumentNumberService numberService, AuditService auditService) {
        this.repo = repo;
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
        this.fgBatchRepo = fgBatchRepo;
        this.capaRepo = capaRepo;
        this.traceabilityService = traceabilityService;
        this.numberService = numberService;
        this.auditService = auditService;
    }

    // ---- queries ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<ComplaintSummaryDto> search(String q, ComplaintStatus status, Pageable pageable) {
        return repo.search(q, status == null ? null : status.name(), pageable).map(c -> {
            Product p = c.getProductId() == null ? null : productRepo.findById(c.getProductId()).orElse(null);
            FgBatch b = c.getFgBatchId() == null ? null : fgBatchRepo.findById(c.getFgBatchId()).orElse(null);
            return new ComplaintSummaryDto(c.getId(), c.getComplaintNumber(), customerName(c.getCustomerId()),
                    p == null ? null : p.getProductCode(), b == null ? null : b.getBatchNumber(),
                    c.getComplaintDate(), c.getCategory(), c.getStatus(), capaNumber(c.getCapaId()));
        });
    }

    @Transactional(readOnly = true)
    public ComplaintDto get(Long id) {
        return toDto(load(id));
    }

    /** Up to 20 customers matching the text, for the complaint form's picker. */
    @Transactional(readOnly = true)
    public List<LookupDto> customerLookup(String q) {
        return customerRepo.search(q, null, PageRequest.of(0, 20, Sort.by("name"))).getContent().stream()
                .map(c -> new LookupDto(c.getId(), c.getCustomerCode(), c.getName())).toList();
    }

    /** The FG batch's trace back to raw-material batches, suppliers and purchase orders. */
    @Transactional(readOnly = true)
    public TraceabilityDto trace(Long id) {
        CustomerComplaint c = load(id);
        if (c.getFgBatchId() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Complaint " + c.getComplaintNumber() + " is not linked to an FG batch");
        }
        return traceabilityService.traceFgBatch(c.getFgBatchId());
    }

    // ---- maintenance -----------------------------------------------------------------------------------------

    @Transactional
    public ComplaintDto create(SaveComplaintRequest req) {
        CustomerComplaint c = new CustomerComplaint();
        c.setComplaintNumber(numberService.next("CMP"));
        c.setReceivedBy(QualitySecurity.requireUserId("register a complaint"));
        apply(c, req);
        c = repo.save(c);
        auditService.record("COMPLAINT_CREATE", "CustomerComplaint", String.valueOf(c.getId()), null,
                c.getComplaintNumber(), null);
        return toDto(c);
    }

    @Transactional
    public ComplaintDto update(Long id, SaveComplaintRequest req) {
        CustomerComplaint c = repo.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Complaint not found: " + id));
        if (c.getStatus() == ComplaintStatus.CLOSED) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION, "A closed complaint cannot be edited");
        }
        apply(c, req);
        c = repo.save(c);
        auditService.record("COMPLAINT_UPDATE", "CustomerComplaint", String.valueOf(id), null,
                c.getComplaintNumber(), null);
        return toDto(c);
    }

    @Transactional
    public ComplaintDto transition(Long id, TransitionComplaintRequest req) {
        CustomerComplaint c = repo.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Complaint not found: " + id));
        ComplaintStatus old = c.getStatus();
        ComplaintStatus target = old.transitionTo(req.target());
        if (target == ComplaintStatus.RESOLVED) {
            if (req.disposition() == null || req.resolutionNotes() == null || req.resolutionNotes().isBlank()) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Resolving a complaint needs a disposition and resolution notes");
            }
            c.setDisposition(req.disposition());
            c.setResolutionNotes(req.resolutionNotes().trim());
            if (req.quantityReturned() != null) {
                c.setQuantityReturned(req.quantityReturned());
            }
        }
        if (target == ComplaintStatus.CLOSED) {
            c.setClosedAt(Instant.now());
        }
        c.setStatus(target);
        c = repo.save(c);
        auditService.record("COMPLAINT_" + target.name(), "CustomerComplaint", String.valueOf(id), old.name(),
                target.name(), req.resolutionNotes());
        return toDto(c);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private void apply(CustomerComplaint c, SaveComplaintRequest req) {
        customerRepo.findById(req.customerId())
                .orElseThrow(() -> new NotFoundException("Customer not found: " + req.customerId()));
        Long productId = req.productId();
        if (req.fgBatchId() != null) {
            FgBatch batch = fgBatchRepo.findById(req.fgBatchId())
                    .orElseThrow(() -> new NotFoundException("FG batch not found: " + req.fgBatchId()));
            Long batchProduct = batch.getProduct().getId();
            if (productId != null && !productId.equals(batchProduct)) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "FG batch " + batch.getBatchNumber() + " is not a batch of the selected product");
            }
            productId = batchProduct;
        } else if (productId != null && !productRepo.existsById(productId)) {
            throw new NotFoundException("Product not found: " + productId);
        }
        c.setCustomerId(req.customerId());
        c.setProductId(productId);
        c.setFgBatchId(req.fgBatchId());
        c.setComplaintDate(req.complaintDate());
        c.setCategory(req.category());
        c.setDescription(req.description().trim());
        c.setCustomerRef(req.customerRef());
        c.setQuantityComplained(req.quantityComplained());
    }

    private CustomerComplaint load(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Complaint not found: " + id));
    }

    private String customerName(Long customerId) {
        return customerRepo.findById(customerId).map(Customer::getName).orElse(null);
    }

    private String capaNumber(Long capaId) {
        return capaId == null ? null : capaRepo.findById(capaId).map(CapaRecord::getCapaNumber).orElse(null);
    }

    private ComplaintDto toDto(CustomerComplaint c) {
        Product p = c.getProductId() == null ? null : productRepo.findById(c.getProductId()).orElse(null);
        FgBatch b = c.getFgBatchId() == null ? null : fgBatchRepo.findById(c.getFgBatchId()).orElse(null);
        return new ComplaintDto(c.getId(), c.getComplaintNumber(), c.getCustomerId(), customerName(c.getCustomerId()),
                c.getProductId(), p == null ? null : p.getProductCode(), p == null ? null : p.getName(),
                c.getFgBatchId(), b == null ? null : b.getBatchNumber(), c.getComplaintDate(), c.getReceivedBy(),
                c.getCategory(), c.getDescription(), c.getCustomerRef(), c.getQuantityComplained(),
                c.getQuantityReturned(), c.getDisposition(), c.getResolutionNotes(), c.getStatus(),
                c.getStatus().next(), c.getCapaId(), capaNumber(c.getCapaId()), c.getClosedAt(), c.getCreatedAt());
    }
}
