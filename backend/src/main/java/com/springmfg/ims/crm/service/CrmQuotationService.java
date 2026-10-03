package com.springmfg.ims.crm.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.crm.domain.CrmOpportunity;
import com.springmfg.ims.crm.domain.CrmQuotation;
import com.springmfg.ims.crm.domain.CrmQuotationItem;
import com.springmfg.ims.crm.domain.QuotationStatus;
import com.springmfg.ims.crm.dto.QuotationDto;
import com.springmfg.ims.crm.dto.QuotationDto.QuotationItemDto;
import com.springmfg.ims.crm.dto.SaveQuotationRequest;
import com.springmfg.ims.crm.repository.CrmOpportunityRepository;
import com.springmfg.ims.crm.repository.CrmQuotationItemRepository;
import com.springmfg.ims.crm.repository.CrmQuotationRepository;
import com.springmfg.ims.partner.domain.Customer;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.sales.dto.AddSalesOrderItemRequest;
import com.springmfg.ims.sales.dto.CreateSalesOrderRequest;
import com.springmfg.ims.sales.dto.SalesOrderDto;
import com.springmfg.ims.sales.service.SalesOrderService;

import jakarta.persistence.criteria.Predicate;

@Service
public class CrmQuotationService {

    private final CrmQuotationRepository repo;
    private final CrmQuotationItemRepository itemRepo;
    private final CrmOpportunityRepository oppRepo;
    private final CrmOpportunityService opportunityService;
    private final CustomerPriceService priceService;
    private final SalesOrderService salesOrderService;
    private final DocumentNumberService numberService;
    private final SystemSettingService settings;
    private final CrmLookups lookups;
    private final AuditService auditService;

    public CrmQuotationService(CrmQuotationRepository repo, CrmQuotationItemRepository itemRepo,
                               CrmOpportunityRepository oppRepo, CrmOpportunityService opportunityService,
                               CustomerPriceService priceService, SalesOrderService salesOrderService,
                               DocumentNumberService numberService,
                               SystemSettingService settings, CrmLookups lookups, AuditService auditService) {
        this.repo = repo;
        this.itemRepo = itemRepo;
        this.oppRepo = oppRepo;
        this.opportunityService = opportunityService;
        this.priceService = priceService;
        this.salesOrderService = salesOrderService;
        this.numberService = numberService;
        this.settings = settings;
        this.lookups = lookups;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<QuotationDto> search(Long customerId, String status, Long opportunityId, Pageable pageable) {
        Specification<CrmQuotation> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (customerId != null) p.add(cb.equal(root.get("customerId"), customerId));
            if (status != null && !status.isBlank()) p.add(cb.equal(root.get("status"), status));
            if (opportunityId != null) p.add(cb.equal(root.get("opportunityId"), opportunityId));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return repo.findAll(spec, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public QuotationDto findById(Long id) {
        return toDto(require(id));
    }

    @Transactional
    public QuotationDto create(SaveQuotationRequest req) {
        CrmOpportunity opp = req.opportunityId() == null ? null : requireOpportunity(req.opportunityId());
        Long customerId = resolveCustomerId(req, opp);
        Customer customer = lookups.requireCustomer(customerId);
        LocalDate today = LocalDate.now();
        LocalDate validUntil = req.validUntil() != null ? req.validUntil()
                : today.plusDays(settings.getInt("crm.quotation.default_validity_days", 30));
        if (validUntil.isBefore(today)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "validUntil must not be in the past");
        }

        CrmQuotation q = new CrmQuotation();
        q.setQuotationNumber(numberService.next("QT"));
        q.setOpportunityId(req.opportunityId());
        q.setCustomerId(customerId);
        q.setValidUntil(validUntil);
        q.setStatus(QuotationStatus.DRAFT.name());
        q.setPaymentTerms(req.paymentTerms() != null ? req.paymentTerms() : customer.getPaymentTerms());
        q.setNotes(req.notes());
        q = repo.save(q);
        q.setTotalAmount(saveItems(q.getId(), customerId, req.items()));
        q = repo.save(q);
        auditService.record("QUOTATION_CREATE", "CRM_QUOTATION", String.valueOf(q.getId()), null,
                q.getQuotationNumber() + " total " + q.getTotalAmount(), null);
        return toDto(q);
    }

    @Transactional
    public QuotationDto update(Long id, SaveQuotationRequest req) {
        CrmQuotation q = require(id);
        CrmLookups.checkVersion(req.version(), q.getVersion(), "Quotation");
        requireStatus(q, QuotationStatus.DRAFT, "Only a DRAFT quotation can be edited");
        CrmOpportunity opp = req.opportunityId() == null ? null : requireOpportunity(req.opportunityId());
        Long customerId = resolveCustomerId(req, opp);
        Customer customer = lookups.requireCustomer(customerId);
        LocalDate validUntil = req.validUntil() != null ? req.validUntil() : q.getValidUntil();
        if (validUntil.isBefore(LocalDate.now())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "validUntil must not be in the past");
        }
        BigDecimal oldTotal = q.getTotalAmount();
        q.setOpportunityId(req.opportunityId());
        q.setCustomerId(customerId);
        q.setValidUntil(validUntil);
        q.setPaymentTerms(req.paymentTerms() != null ? req.paymentTerms() : customer.getPaymentTerms());
        q.setNotes(req.notes());
        itemRepo.deleteAllByQuotationId(id);
        q.setTotalAmount(saveItems(id, customerId, req.items()));
        q = repo.save(q);
        auditService.record("QUOTATION_UPDATE", "CRM_QUOTATION", String.valueOf(id), "total " + oldTotal,
                "total " + q.getTotalAmount(), null);
        return toDto(q);
    }

    @Transactional
    public QuotationDto send(Long id) {
        CrmQuotation q = require(id);
        move(q, QuotationStatus.SENT, "QUOTATION_SENT", null);
        if (itemRepo.findByQuotationIdOrderByLineNo(id).isEmpty()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A quotation without lines cannot be sent");
        }
        if (q.getValidUntil().isBefore(LocalDate.now())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "The quotation validity date has passed; extend it before sending");
        }
        q.setSentAt(Instant.now());
        return toDto(repo.save(q));
    }

    @Transactional
    public QuotationDto accept(Long id) {
        CrmQuotation q = require(id);
        if (QuotationStatus.SENT.name().equals(q.getStatus()) && q.getValidUntil().isBefore(LocalDate.now())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Quotation " + q.getQuotationNumber() + " expired on " + q.getValidUntil());
        }
        move(q, QuotationStatus.ACCEPTED, "QUOTATION_ACCEPTED", null);
        q.setRespondedAt(Instant.now());
        return toDto(repo.save(q));
    }

    @Transactional
    public QuotationDto reject(Long id, String reason) {
        CrmQuotation q = require(id);
        move(q, QuotationStatus.REJECTED, "QUOTATION_REJECTED", reason);
        q.setRespondedAt(Instant.now());
        q.setRejectReason(reason);
        return toDto(repo.save(q));
    }

    /**
     * Creates a DRAFT sales order from an ACCEPTED quotation (DESIGN 18.2). Order lines carry the net unit price
     * (list price less discount) so the order total equals the quotation total. Both documents are audited.
     */
    @Transactional
    public QuotationDto convertToSalesOrder(Long id) {
        CrmQuotation q = require(id);
        if (QuotationStatus.CONVERTED.name().equals(q.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Quotation " + q.getQuotationNumber() + " is already converted to a sales order");
        }
        requireStatus(q, QuotationStatus.ACCEPTED, "Only an ACCEPTED quotation can be converted to a sales order");

        Customer customer = lookups.requireCustomer(q.getCustomerId());
        List<CrmQuotationItem> items = itemRepo.findByQuotationIdOrderByLineNo(id);
        String quotationNumber = q.getQuotationNumber();
        List<AddSalesOrderItemRequest> lines = items.stream()
                .map(i -> new AddSalesOrderItemRequest(i.getProductId(), i.getQuantity(),
                        QuotationPricing.netUnitPrice(i.getUnitPrice(), i.getDiscountPct()), null,
                        quotationNumber + " L" + i.getLineNo()))
                .toList();
        SalesOrderDto order = salesOrderService.create(new CreateSalesOrderRequest(
                q.getCustomerId(), LocalDate.now(), null, customer.getAddress(), q.getPaymentTerms(),
                "Created from quotation " + q.getQuotationNumber(), lines));

        q.setStatus(QuotationStatus.CONVERTED.name());
        q.setConvertedSalesOrderId(order.id());
        q.setConvertedAt(Instant.now());
        q = repo.save(q);
        auditService.record("QUOTATION_CONVERTED", "CRM_QUOTATION", String.valueOf(id),
                QuotationStatus.ACCEPTED.name(), QuotationStatus.CONVERTED.name(), "sales order " + order.orderNumber());
        auditService.record("SO_FROM_QUOTATION", "SALES_ORDER", String.valueOf(order.id()), null,
                q.getQuotationNumber(), null);
        if (q.getOpportunityId() != null) {
            opportunityService.recordConvertedQuotation(q.getOpportunityId(), q.getId());
        }
        return toDto(q);
    }

    /** Marks DRAFT and SENT quotations past their validity date as EXPIRED; returns how many were expired. */
    @Transactional
    public int expireOverdue() {
        List<CrmQuotation> overdue = repo.findByStatusInAndValidUntilBefore(
                List.of(QuotationStatus.DRAFT.name(), QuotationStatus.SENT.name()), LocalDate.now());
        for (CrmQuotation q : overdue) {
            String old = q.getStatus();
            q.setStatus(QuotationStatus.EXPIRED.name());
            auditService.record("QUOTATION_EXPIRED", "CRM_QUOTATION", String.valueOf(q.getId()), old,
                    QuotationStatus.EXPIRED.name(), "valid until " + q.getValidUntil());
        }
        repo.saveAll(overdue);
        return overdue.size();
    }

    private Long resolveCustomerId(SaveQuotationRequest req, CrmOpportunity opp) {
        Long customerId = req.customerId() != null ? req.customerId() : opp != null ? opp.getCustomerId() : null;
        if (customerId == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "A quotation needs a customer, directly or through an opportunity that has one");
        }
        if (opp != null && opp.getCustomerId() != null && !opp.getCustomerId().equals(customerId)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "The customer does not match the opportunity's customer");
        }
        return customerId;
    }

    /** Saves the lines, defaulting each unit price from the price list, and returns the quotation total. */
    private BigDecimal saveItems(Long quotationId, Long customerId, List<SaveQuotationRequest.Item> requested) {
        BigDecimal total = BigDecimal.ZERO;
        int lineNo = 1;
        LocalDate today = LocalDate.now();
        for (SaveQuotationRequest.Item req : requested) {
            Product product = lookups.requireProduct(req.productId());
            BigDecimal unitPrice = req.unitPrice() != null ? req.unitPrice()
                    : priceService.resolve(customerId, req.productId(), req.quantity(), today)
                            .map(p -> p.getUnitPrice())
                            .orElseThrow(() -> new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                                    "No price list entry for " + product.getProductCode() + "; enter a unit price"));
            BigDecimal discount = req.discountPct() != null ? req.discountPct() : BigDecimal.ZERO;
            BigDecimal net = QuotationPricing.netUnitPrice(unitPrice, discount);
            CrmQuotationItem item = new CrmQuotationItem();
            item.setQuotationId(quotationId);
            item.setLineNo(lineNo++);
            item.setProductId(req.productId());
            item.setQuantity(req.quantity());
            item.setUnitPrice(unitPrice);
            item.setDiscountPct(discount);
            item.setLineTotal(QuotationPricing.lineTotal(req.quantity(), net));
            itemRepo.save(item);
            total = total.add(item.getLineTotal());
        }
        return total;
    }

    private void move(CrmQuotation q, QuotationStatus target, String action, String reason) {
        QuotationStatus current = QuotationStatus.valueOf(q.getStatus());
        if (!current.canMoveTo(target)) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Cannot move quotation " + q.getQuotationNumber() + " from " + current + " to " + target);
        }
        q.setStatus(target.name());
        auditService.record(action, "CRM_QUOTATION", String.valueOf(q.getId()), current.name(), target.name(), reason);
    }

    private void requireStatus(CrmQuotation q, QuotationStatus required, String message) {
        if (!required.name().equals(q.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    message + " (quotation " + q.getQuotationNumber() + " is " + q.getStatus() + ")");
        }
    }

    private CrmQuotation require(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Quotation", id));
    }

    private CrmOpportunity requireOpportunity(Long id) {
        return oppRepo.findById(id).orElseThrow(() -> new NotFoundException("Opportunity", id));
    }

    private QuotationDto toDto(CrmQuotation q) {
        List<CrmQuotationItem> items = itemRepo.findByQuotationIdOrderByLineNo(q.getId());
        Map<Long, Product> products = items.stream().map(CrmQuotationItem::getProductId).distinct()
                .collect(Collectors.toMap(pid -> pid, lookups::requireProduct));
        List<QuotationItemDto> itemDtos = items.stream().map(i -> {
            Product p = products.get(i.getProductId());
            return new QuotationItemDto(i.getId(), i.getLineNo(), i.getProductId(), p.getProductCode(), p.getName(),
                    i.getQuantity(), i.getUnitPrice(), i.getDiscountPct(),
                    QuotationPricing.netUnitPrice(i.getUnitPrice(), i.getDiscountPct()), i.getLineTotal());
        }).toList();
        String soNumber = q.getConvertedSalesOrderId() == null ? null
                : salesOrderService.findById(q.getConvertedSalesOrderId()).orderNumber();
        List<String> actions = switch (QuotationStatus.valueOf(q.getStatus())) {
            case DRAFT -> List.of("EDIT", "SEND");
            case SENT -> List.of("ACCEPT", "REJECT");
            case ACCEPTED -> List.of("CONVERT");
            default -> List.of();
        };
        return new QuotationDto(q.getId(), q.getQuotationNumber(), q.getOpportunityId(), q.getCustomerId(),
                lookups.customerName(q.getCustomerId()), q.getValidUntil(), q.getStatus(), q.getTotalAmount(),
                q.getPaymentTerms(), q.getNotes(), q.getSentAt(), q.getRespondedAt(), q.getRejectReason(),
                q.getConvertedSalesOrderId(), soNumber, q.getConvertedAt(), itemDtos, q.getVersion(), actions);
    }
}
