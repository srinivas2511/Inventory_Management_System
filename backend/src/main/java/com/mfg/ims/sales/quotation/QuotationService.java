package com.mfg.ims.sales.quotation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.numbering.DocumentNumberService;

@Service
@Transactional
public class QuotationService {

    private final QuotationRepository repo;
    private final QuotationRevisionRepository revisionRepo;
    private final DocumentNumberService numberService;
    private final ObjectMapper objectMapper;

    public QuotationService(QuotationRepository repo,
                            QuotationRevisionRepository revisionRepo,
                            DocumentNumberService numberService,
                            ObjectMapper objectMapper) {
        this.repo = repo;
        this.revisionRepo = revisionRepo;
        this.numberService = numberService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<QuotationDto> list(Long companyId) {
        return repo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public QuotationDto get(Long id) {
        return toDto(find(id));
    }

    public QuotationDto create(SaveQuotationRequest req) {
        var q = new Quotation();
        q.setDocNo(numberService.next("QT"));
        q.setDocDate(req.docDate() != null ? req.docDate() : LocalDate.now());
        q.setCustomerId(req.customerId());
        q.setCurrency(req.currency() != null ? req.currency() : "INR");
        q.setValidUntil(req.validUntil());
        q.setDeliveryTerms(req.deliveryTerms());
        q.setEnquiryRef(req.enquiryRef());
        q.setRemarks(req.remarks());
        if (req.items() != null) {
            int lineNo = 1;
            for (var line : req.items()) {
                var item = new QuotationItem();
                item.setQuotation(q);
                item.setLineNo(lineNo++);
                item.setMaterialId(line.materialId());
                item.setProductId(line.productId());
                item.setDescription(line.description());
                item.setQty(line.qty());
                item.setUom(line.uom());
                item.setRate(line.rate());
                if (line.discountPct() != null) item.setDiscountPct(line.discountPct());
                item.setLeadTimeDays(line.leadTimeDays());
                item.setCustomerPartNo(line.customerPartNo());
                if (line.rate() != null) item.setLineTotal(line.qty().multiply(line.rate()));
                q.getItems().add(item);
            }
        }
        return toDto(repo.save(q));
    }

    public QuotationDto update(Long id, SaveQuotationRequest req) {
        var q = find(id);
        if (!"DRAFT".equals(q.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Only DRAFT quotations can be updated");
        }
        q.setCustomerId(req.customerId());
        if (req.docDate() != null) q.setDocDate(req.docDate());
        q.setValidUntil(req.validUntil());
        q.setDeliveryTerms(req.deliveryTerms());
        q.setEnquiryRef(req.enquiryRef());
        q.setRemarks(req.remarks());
        q.getItems().clear();
        if (req.items() != null) {
            int lineNo = 1;
            for (var line : req.items()) {
                var item = new QuotationItem();
                item.setQuotation(q);
                item.setLineNo(lineNo++);
                item.setMaterialId(line.materialId());
                item.setProductId(line.productId());
                item.setDescription(line.description());
                item.setQty(line.qty());
                item.setUom(line.uom());
                item.setRate(line.rate());
                if (line.discountPct() != null) item.setDiscountPct(line.discountPct());
                item.setLeadTimeDays(line.leadTimeDays());
                item.setCustomerPartNo(line.customerPartNo());
                if (line.rate() != null) item.setLineTotal(line.qty().multiply(line.rate()));
                q.getItems().add(item);
            }
        }
        return toDto(repo.save(q));
    }

    public QuotationDto submit(Long id) {
        var q = find(id);
        if (!"DRAFT".equals(q.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Quotation must be DRAFT to submit");
        }
        q.setStatus("SUBMITTED");
        return toDto(repo.save(q));
    }

    /**
     * Amend an APPROVED or SENT quotation: snapshots current state, resets to DRAFT with revision_no incremented.
     * Requires QUOTATION_AMEND permission.
     */
    public QuotationDto amend(Long id, String reason) {
        var q = find(id);
        if (!"APPROVED".equals(q.getStatus()) && !"SENT".equals(q.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only APPROVED or SENT quotations can be amended (current: " + q.getStatus() + ")");
        }
        // snapshot the current state before resetting to DRAFT
        var rev = new QuotationRevision();
        rev.setQuotation(q);
        rev.setRevisionNumber(q.getRevisionNo());
        rev.setRevisedAt(Instant.now());
        rev.setRevisedBy(currentUserId());
        rev.setReason(reason);
        rev.setSnapshot(toJson(toDto(q)));
        revisionRepo.save(rev);

        // advance revision counter and reset status to DRAFT
        q.setRevisionNo(q.getRevisionNo() + 1);
        q.setStatus("DRAFT");
        return toDto(repo.save(q));
    }

    /** Return revision history for a quotation (newest revision first). */
    @Transactional(readOnly = true)
    public List<QuotationRevisionDto> listRevisions(Long id) {
        find(id); // 404 if not found
        return revisionRepo.findByQuotationIdOrderByRevisionNumberDesc(id).stream()
                .map(r -> new QuotationRevisionDto(r.getId(), id, r.getRevisionNumber(),
                        r.getRevisedAt(), r.getRevisedBy(), r.getReason(), r.getSnapshot()))
                .toList();
    }

    public QuotationDto cancel(Long id, String reason) {
        var q = find(id);
        if ("CANCELLED".equals(q.getStatus()) || "CONVERTED".equals(q.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quotation cannot be cancelled in status: " + q.getStatus());
        }
        // 11.8 cascade check: block cancel if linked OC or SO exists (downstream documents)
        long linkedDocs = repo.countLinkedOrderConfirmations(id);
        if (linkedDocs > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cancel linked Order Confirmations first");
        }
        q.setStatus("CANCELLED");
        q.setLostReason(reason);
        return toDto(repo.save(q));
    }

    public QuotationDto markLost(Long id, String reason) {
        var q = find(id);
        q.setStatus("LOST");
        q.setLostReason(reason);
        return toDto(repo.save(q));
    }

    /** Mark quotation CONVERTED after creating the downstream document. */
    public void markConverted(Long id, String targetType, Long targetId) {
        var q = find(id);
        q.setStatus("CONVERTED");
        q.setConvertedToType(targetType);
        q.setConvertedToId(targetId);
        repo.save(q);
    }

    private Quotation find(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found: " + id));
    }

    private Long currentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof CachedUser user) {
                return user.id();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private QuotationDto toDto(Quotation q) {
        var items = q.getItems().stream()
                .map(i -> new QuotationDto.ItemDto(i.getId(), i.getLineNo(), i.getItemKind(),
                        i.getMaterialId(), i.getProductId(), i.getDescription(),
                        i.getQty(), i.getUom(), i.getRate(), i.getLineTotal(),
                        i.getLeadTimeDays(), i.getCustomerPartNo()))
                .toList();
        return new QuotationDto(q.getId(), q.getDocNo(), q.getDocDate(), q.getStatus(),
                q.getRevisionNo(), q.getCustomerId(), q.getCurrency(), q.getValidUntil(),
                q.getGrandTotal(), q.getRemarks(), items);
    }
}
