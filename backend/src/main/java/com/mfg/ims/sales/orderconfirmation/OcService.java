package com.mfg.ims.sales.orderconfirmation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.common.security.SegregationOfDuties;
import com.mfg.ims.engineering.bom.BomRepository;
import com.mfg.ims.production.domain.ProductionOrder;
import com.mfg.ims.production.repository.ProductionOrderRepository;
import com.mfg.ims.sales.quotation.QuotationConversionResultDto;
import com.mfg.ims.sales.quotation.QuotationService;

@Service
@Transactional
public class OcService {

    private final OcRepository repo;
    private final DocumentNumberService numberService;
    private final QuotationService quotationService;
    private final ProductionOrderRepository productionOrderRepo;
    private final BomRepository bomRepo;

    public OcService(OcRepository repo, DocumentNumberService numberService,
            QuotationService quotationService,
            ProductionOrderRepository productionOrderRepo,
            BomRepository bomRepo) {
        this.repo = repo;
        this.numberService = numberService;
        this.quotationService = quotationService;
        this.productionOrderRepo = productionOrderRepo;
        this.bomRepo = bomRepo;
    }

    @Transactional(readOnly = true)
    public List<OcDto> list(Long companyId) {
        return repo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public OcDto get(Long id) {
        return toDto(find(id));
    }

    public OcDto create(SaveOcRequest req) {
        var oc = new OrderConfirmation();
        oc.setDocNo(numberService.next("OC"));
        oc.setDocDate(req.docDate() != null ? req.docDate() : LocalDate.now());
        oc.setCustomerId(req.customerId());
        oc.setOcKind(req.ocKind() != null ? req.ocKind() : "DOMESTIC");
        oc.setQuotationId(req.quotationId());
        oc.setCustomerPoNo(req.customerPoNo());
        oc.setCustomerPoDate(req.customerPoDate());
        oc.setDeliveryDate(req.deliveryDate());
        oc.setCurrency(req.currency() != null ? req.currency() : "INR");
        oc.setIncoterm(req.incoterm());
        oc.setRemarks(req.remarks());
        applyItems(oc, req);
        var saved = repo.save(oc);
        if (req.quotationId() != null) {
            quotationService.markConverted(req.quotationId(), "OC", saved.getId());
        }
        return toDto(saved);
    }

    public OcDto update(Long id, SaveOcRequest req) {
        var oc = find(id);
        if (!"DRAFT".equals(oc.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only DRAFT order confirmations can be updated");
        }
        oc.setCustomerId(req.customerId());
        if (req.docDate() != null) oc.setDocDate(req.docDate());
        oc.setCustomerPoNo(req.customerPoNo());
        oc.setCustomerPoDate(req.customerPoDate());
        oc.setDeliveryDate(req.deliveryDate());
        oc.setIncoterm(req.incoterm());
        oc.setRemarks(req.remarks());
        oc.getItems().clear();
        applyItems(oc, req);
        return toDto(repo.save(oc));
    }

    public OcDto confirm(Long id) {
        var oc = find(id);
        if (!"DRAFT".equals(oc.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "OC must be DRAFT to confirm");
        }
        oc.setStatus("CONFIRMED");
        return toDto(repo.save(oc));
    }

    public OcDto cancel(Long id) {
        var oc = find(id);
        if ("CANCELLED".equals(oc.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OC already cancelled");
        }
        oc.setStatus("CANCELLED");
        return toDto(repo.save(oc));
    }

    /** Submit DRAFT OC for approval — records submitter for SoD. */
    public OcDto submit(Long id) {
        var oc = find(id);
        if (!"DRAFT".equals(oc.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "OC must be DRAFT to submit");
        }
        oc.setStatus("SUBMITTED");
        oc.setSubmittedBy(currentUserId());
        return toDto(repo.save(oc));
    }

    /** Approve a SUBMITTED OC — approver must differ from submitter (SoD). */
    public OcDto approve(Long id) {
        var oc = find(id);
        if (!"SUBMITTED".equals(oc.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "OC must be SUBMITTED to approve");
        }
        SegregationOfDuties.requireDifferentUser(currentUserId(), oc.getSubmittedBy(),
                "Approver must be different from the person who submitted the OC (SoD)");
        oc.setStatus("APPROVED");
        return toDto(repo.save(oc));
    }

    /**
     * Convert an approved/submitted quotation into an Order Confirmation.
     * The quotation is marked CONVERTED after successful OC creation.
     */
    public QuotationConversionResultDto convertFromQuotation(Long quotationId) {
        var qt = quotationService.get(quotationId);
        if (!"SUBMITTED".equals(qt.status()) && !"APPROVED".equals(qt.status())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Quotation must be SUBMITTED or APPROVED to convert (current: " + qt.status() + ")");
        }
        var oc = new OrderConfirmation();
        oc.setDocNo(numberService.next("OC"));
        oc.setDocDate(LocalDate.now());
        oc.setCustomerId(qt.customerId());
        oc.setCurrency(qt.currency() != null ? qt.currency() : "INR");
        oc.setQuotationId(quotationId);
        int lineNo = 1;
        for (var item : qt.items()) {
            var ocItem = new OrderConfirmationItem();
            ocItem.setOc(oc);
            ocItem.setLineNo(lineNo++);
            ocItem.setProductId(item.productId());
            ocItem.setDescription(item.description());
            ocItem.setQty(item.qty());
            ocItem.setUom(item.uom());
            ocItem.setRate(item.rate());
            if (item.rate() != null) ocItem.setLineTotal(item.qty().multiply(item.rate()));
            oc.getItems().add(ocItem);
        }
        var saved = repo.save(oc);
        quotationService.markConverted(quotationId, "OC", saved.getId());
        return new QuotationConversionResultDto(saved.getId(), saved.getDocNo());
    }

    /**
     * Create a production work order from an APPROVED or CONFIRMED OC.
     * Uses the first OC line's product and an available BOM for that product.
     */
    public WorkOrderResultDto createWorkOrder(Long ocId) {
        var oc = find(ocId);
        if (!"APPROVED".equals(oc.getStatus()) && !"CONFIRMED".equals(oc.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "OC must be APPROVED or CONFIRMED to create a work order (current: " + oc.getStatus() + ")");
        }
        var firstLine = oc.getItems().stream()
                .filter(i -> i.getProductId() != null)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "OC has no product lines — cannot create a work order"));
        var bom = bomRepo.findByProductIdAndStatusIn(firstLine.getProductId(),
                List.of("RELEASED", "APPROVED", "DRAFT"))
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No BOM found for product " + firstLine.getProductId()));
        var wo = new ProductionOrder();
        wo.setOrderNumber(numberService.next("WO"));
        wo.setProductId(firstLine.getProductId());
        wo.setBomId(bom.getId());
        wo.setPlannedQty(firstLine.getQty() != null ? firstLine.getQty() : BigDecimal.ONE);
        wo.setStartDate(oc.getDeliveryDate());
        wo.setSalesOrderId(oc.getSalesOrderId());
        wo.setOcId(ocId);
        wo.setStatus("DRAFT");
        var saved = productionOrderRepo.save(wo);
        return new WorkOrderResultDto(saved.getId(), saved.getOrderNumber());
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

    private void applyItems(OrderConfirmation oc, SaveOcRequest req) {
        if (req.items() == null) return;
        int lineNo = 1;
        for (var line : req.items()) {
            var item = new OrderConfirmationItem();
            item.setOc(oc);
            item.setLineNo(lineNo++);
            item.setMaterialId(line.materialId());
            item.setProductId(line.productId());
            item.setDescription(line.description());
            item.setQty(line.qty());
            item.setUom(line.uom());
            item.setRate(line.rate());
            item.setDeliveryDate(line.deliveryDate());
            item.setCustomerPartNo(line.customerPartNo());
            if (line.rate() != null) item.setLineTotal(line.qty().multiply(line.rate()));
            oc.getItems().add(item);
        }
    }

    private OrderConfirmation find(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Order confirmation not found: " + id));
    }

    private OcDto toDto(OrderConfirmation oc) {
        var items = oc.getItems().stream()
                .map(i -> new OcDto.ItemDto(i.getId(), i.getLineNo(), i.getItemKind(),
                        i.getMaterialId(), i.getProductId(), i.getDescription(),
                        i.getQty(), i.getUom(), i.getRate(), i.getLineTotal(),
                        i.getDeliveryDate(), i.getCustomerPartNo(), i.getQtyDispatched()))
                .toList();
        return new OcDto(oc.getId(), oc.getDocNo(), oc.getDocDate(), oc.getStatus(),
                oc.getOcKind(), oc.getCustomerId(), oc.getQuotationId(),
                oc.getCustomerPoNo(), oc.getCustomerPoDate(), oc.getDeliveryDate(),
                oc.getCurrency(), oc.getGrandTotal(), oc.getRemarks(),
                oc.getSalesOrderId(), items);
    }
}
