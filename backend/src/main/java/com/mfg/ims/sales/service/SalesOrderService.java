package com.mfg.ims.sales.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.common.security.SegregationOfDuties;
import com.mfg.ims.partner.repository.CustomerRepository;
import com.mfg.ims.product.repository.ProductRepository;
import com.mfg.ims.enterprise.scope.PlantScope;
import com.mfg.ims.sales.domain.SalesOrder;
import com.mfg.ims.sales.domain.SalesOrderItem;
import com.mfg.ims.sales.dto.AddSalesOrderItemRequest;
import com.mfg.ims.sales.dto.CreateSalesOrderRequest;
import com.mfg.ims.sales.dto.SalesOrderDto;
import com.mfg.ims.sales.repository.SalesOrderItemRepository;
import com.mfg.ims.sales.repository.SalesOrderRepository;

@Service
@Transactional
public class SalesOrderService {

    private final SalesOrderRepository orderRepo;
    private final SalesOrderItemRepository itemRepo;
    private final CustomerRepository customerRepo;
    private final ProductRepository productRepo;
    private final DocumentNumberService numberService;
    private final AuditService auditService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final ReservationService reservationService;

    private final CustomerApprovedProductService approvedProducts;

    private final com.mfg.ims.enterprise.scope.PlantScope plantScope;


    public SalesOrderService(SalesOrderRepository orderRepo,
                             SalesOrderItemRepository itemRepo,
                             CustomerRepository customerRepo,
                             ProductRepository productRepo,
                             DocumentNumberService numberService,
                             AuditService auditService,
                             org.springframework.context.ApplicationEventPublisher eventPublisher,
                             ReservationService reservationService,

            com.mfg.ims.enterprise.scope.PlantScope plantScope,
            CustomerApprovedProductService approvedProducts) {
        this.approvedProducts = approvedProducts;

        this.plantScope = plantScope;
        this.orderRepo = orderRepo;
        this.itemRepo = itemRepo;
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
        this.numberService = numberService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.reservationService = reservationService;
    }

    public SalesOrderDto create(CreateSalesOrderRequest req) {
        customerRepo.findById(req.customerId())
                .orElseThrow(() -> new NotFoundException("Customer", req.customerId()));

        SalesOrder order = new SalesOrder();
        order.setOrderNumber(numberService.next("SO"));
        order.setCustomerId(req.customerId());
        order.setOrderDate(req.orderDate() != null ? req.orderDate() : LocalDate.now());
        order.setRequiredDate(req.requiredDate());
        order.setDeliveryAddress(req.deliveryAddress());
        order.setPaymentTerms(req.paymentTerms());
        order.setNotes(req.notes());
        order.setStatus("DRAFT");
        order.setPlantId(plantScope.resolveForCreate(req.plantId()));
        order = orderRepo.save(order);

        if (req.items() != null) {
            int line = 1;
            for (var itemReq : req.items()) {
                approvedProducts.assertMayBuy(req.customerId(), itemReq.productId()); // MDM-021 / GC-2 (setting-driven)
                SalesOrderItem item = buildItem(order.getId(), line++, itemReq);
                itemRepo.save(item);
            }
        }

        auditService.record("SO_CREATED", "SALES_ORDER", String.valueOf(order.getId()), null, order.getStatus(), null);
        return findById(order.getId());
    }

    public SalesOrderDto addItem(Long orderId, AddSalesOrderItemRequest req) {
        SalesOrder order = requireOrder(orderId);
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Items can only be added to DRAFT orders");
        }
        productRepo.findById(req.productId())
                .orElseThrow(() -> new NotFoundException("Product", req.productId()));

        approvedProducts.assertMayBuy(order.getCustomerId(), req.productId()); // MDM-021 / GC-2 (setting-driven)
        int nextLine = itemRepo.maxLineNo(orderId) + 1;
        SalesOrderItem item = buildItem(orderId, nextLine, req);
        itemRepo.save(item);
        return findById(orderId);
    }

    public SalesOrderDto removeItem(Long orderId, Long itemId) {
        SalesOrder order = requireOrder(orderId);
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Items can only be removed from DRAFT orders");
        }
        itemRepo.deleteById(itemId);
        return findById(orderId);
    }

    public SalesOrderDto confirm(Long orderId) {
        SalesOrder order = requireOrder(orderId);
        requireTransition(order, "DRAFT", "ORDERED");
        List<SalesOrderItem> items = itemRepo.findBySalesOrderId(orderId);
        if (items.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Order has no items");
        }
        order.setStatus("ORDERED");
        order.setSubmittedBy(currentUserId());
        auditService.record("SO_CONFIRMED", "SALES_ORDER", String.valueOf(orderId), "DRAFT", "ORDERED", null);
        return toDto(order, items);
    }

    public SalesOrderDto approve(Long orderId) {
        SalesOrder order = requireOrder(orderId);
        requireTransition(order, "ORDERED", "APPROVED");
        // Segregation of duties (SEC-008 principle, RUNBOOK second-approver step): whoever created or submitted the
        // order cannot approve it, and holding SALES_APPROVE (the Administrator included) does not change that.
        SegregationOfDuties.requireDifferentFromAll(currentUserId(),
                "You created or submitted sales order " + order.getOrderNumber()
                        + "; a different user with SALES_APPROVE must approve it",
                order.getCreatedBy(), order.getSubmittedBy());
        order.setStatus("APPROVED");
        order.setApprovedBy(currentUserId());
        order.setApprovedAt(Instant.now());
        auditService.record("SO_APPROVED", "SALES_ORDER", String.valueOf(orderId), "ORDERED", "APPROVED", null);
        eventPublisher.publishEvent(new com.mfg.ims.sales.event.SalesOrderApprovedEvent(orderId));
        return toDto(order, itemRepo.findBySalesOrderId(orderId));
    }

    public SalesOrderDto cancel(Long orderId, String reason) {
        SalesOrder order = requireOrder(orderId);
        if ("DISPATCHED".equals(order.getStatus()) || "CANCELLED".equals(order.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Cannot cancel a " + order.getStatus() + " order");
        }
        String previousStatus = order.getStatus();
        // Reserved finished goods go back to available stock; cancelling must never leave them locked.
        reservationService.releaseAll(orderId);
        order.setStatus("CANCELLED");
        order.setCancelledBy(currentUserId());
        order.setCancelledAt(Instant.now());
        order.setCancelReason(reason);
        auditService.record("SO_CANCELLED", "SALES_ORDER", String.valueOf(orderId), previousStatus, "CANCELLED", reason);
        return toDto(order, itemRepo.findBySalesOrderId(orderId));
    }

    @Transactional(readOnly = true)
    public SalesOrderDto findById(Long orderId) {
        SalesOrder order = requireOrder(orderId);
        List<SalesOrderItem> items = itemRepo.findBySalesOrderId(orderId);
        return toDto(order, items);
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderDto> search(Long customerId, String status, LocalDate from, LocalDate to, Pageable pageable) {
        PlantScope.Filter scope = plantScope.filter();
        return orderRepo.searchInPlants(customerId, status, from, to, scope.scoped(), scope.plantIds(), pageable)
                .map(o -> toDto(o, itemRepo.findBySalesOrderId(o.getId())));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private SalesOrderItem buildItem(Long orderId, int lineNo, AddSalesOrderItemRequest req) {
        SalesOrderItem item = new SalesOrderItem();
        item.setSalesOrderId(orderId);
        item.setLineNo(lineNo);
        item.setProductId(req.productId());
        item.setOrderedQty(req.orderedQty());
        item.setUnitPrice(req.unitPrice());
        item.setCustomerSpecRef(req.customerSpecRef());
        item.setNotes(req.notes());
        return item;
    }

    private SalesOrderDto toDto(SalesOrder order, List<SalesOrderItem> items) {
        String customerName = customerRepo.findById(order.getCustomerId())
                .map(c -> c.getName()).orElse("Unknown");

        List<SalesOrderDto.SalesOrderItemDto> itemDtos = items.stream().map(i -> {
            String productCode = productRepo.findById(i.getProductId())
                    .map(p -> p.getProductCode()).orElse("");
            String productName = productRepo.findById(i.getProductId())
                    .map(p -> p.getName()).orElse("");
            BigDecimal pending = i.getOrderedQty().subtract(i.getDispatchedQty());
            String fulfillment = computeFulfillment(i);
            return new SalesOrderDto.SalesOrderItemDto(
                    i.getId(), i.getLineNo(), i.getProductId(),
                    productCode, productName,
                    i.getOrderedQty(), i.getReservedQty(), i.getPackedQty(), i.getDispatchedQty(),
                    pending, i.getUnitPrice(), i.getCustomerSpecRef(), i.getNotes(), fulfillment);
        }).collect(Collectors.toList());

        BigDecimal total = itemDtos.stream()
                .filter(i -> i.unitPrice() != null)
                .map(i -> i.unitPrice().multiply(i.orderedQty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SalesOrderDto(
                order.getId(), order.getOrderNumber(),
                order.getCustomerId(), customerName,
                order.getOrderDate(), order.getRequiredDate(),
                order.getDeliveryAddress(), order.getPaymentTerms(), order.getNotes(),
                order.getStatus(), total, itemDtos,
                order.getCreatedBy(), order.getSubmittedBy(),
                allowedActions(order.getStatus()));
    }

    private String computeFulfillment(SalesOrderItem i) {
        if (i.getDispatchedQty().compareTo(i.getOrderedQty()) >= 0) return "COMPLETE";
        if (i.getDispatchedQty().compareTo(BigDecimal.ZERO) > 0) return "PARTIAL";
        return "PENDING";
    }

    private List<String> allowedActions(String status) {
        return switch (status) {
            case "DRAFT" -> List.of("confirm", "cancel");
            case "ORDERED" -> List.of("approve", "cancel");
            case "APPROVED" -> List.of("cancel");
            case "ALLOCATED" -> List.of("cancel");
            case "DISPATCHED", "PARTIALLY_DISPATCHED" -> List.of();
            default -> List.of();
        };
    }

    private void requireTransition(SalesOrder order, String from, String to) {
        if (!from.equals(order.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Expected status " + from + " but was " + order.getStatus());
        }
    }

    private SalesOrder requireOrder(Long id) {
        SalesOrder order = orderRepo.findById(id).orElseThrow(() -> new NotFoundException("SalesOrder", id));
        plantScope.assertVisible(order.getPlantId(), "SalesOrder", id);
        return order;
    }

    private Long currentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.mfg.ims.auth.service.UserPermissionCache.CachedUser user) {
                return user.id();
            }
        } catch (Exception ignored) {}
        return -1L;
    }
}
