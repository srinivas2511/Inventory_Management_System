package com.springmfg.ims.sales.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.numbering.DocumentNumberService;
import com.springmfg.ims.finance.ar.service.ArService;
import com.springmfg.ims.inventory.domain.FgBatch;
import com.springmfg.ims.inventory.dto.PostingCommand;
import com.springmfg.ims.inventory.repository.FgBatchRepository;
import com.springmfg.ims.inventory.service.InventoryPostingService;
import com.springmfg.ims.partner.domain.Customer;
import com.springmfg.ims.partner.repository.CustomerRepository;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.product.repository.ProductRepository;
import com.springmfg.ims.sales.domain.Dispatch;
import com.springmfg.ims.sales.domain.DispatchItem;
import com.springmfg.ims.sales.domain.SalesOrder;
import com.springmfg.ims.sales.domain.SalesOrderItem;
import com.springmfg.ims.sales.domain.SalesReservation;
import com.springmfg.ims.sales.dto.CreateDispatchRequest;
import com.springmfg.ims.sales.dto.DispatchDto;
import com.springmfg.ims.sales.dto.DispatchNoteDto;
import com.springmfg.ims.sales.repository.CustomerProductSpecRepository;
import com.springmfg.ims.sales.repository.DispatchItemRepository;
import com.springmfg.ims.sales.repository.DispatchRepository;
import com.springmfg.ims.sales.repository.SalesOrderItemRepository;
import com.springmfg.ims.sales.repository.SalesOrderRepository;
import com.springmfg.ims.sales.repository.SalesReservationRepository;

import java.time.LocalDate;

@Service
@Transactional
public class DispatchService {

    private final DispatchRepository dispatchRepo;
    private final DispatchItemRepository dispatchItemRepo;
    private final SalesOrderRepository orderRepo;
    private final SalesOrderItemRepository itemRepo;
    private final SalesReservationRepository reservationRepo;
    private final FgBatchRepository fgBatchRepo;
    private final ProductRepository productRepo;
    private final CustomerRepository customerRepo;
    private final CustomerProductSpecRepository specRepo;
    private final InventoryPostingService postingService;
    private final DocumentNumberService numberService;
    private final AuditService auditService;
    private final ArService arService;

    public DispatchService(DispatchRepository dispatchRepo,
                           DispatchItemRepository dispatchItemRepo,
                           SalesOrderRepository orderRepo,
                           SalesOrderItemRepository itemRepo,
                           SalesReservationRepository reservationRepo,
                           FgBatchRepository fgBatchRepo,
                           ProductRepository productRepo,
                           CustomerRepository customerRepo,
                           CustomerProductSpecRepository specRepo,
                           InventoryPostingService postingService,
                           DocumentNumberService numberService,
                           AuditService auditService,
                           ArService arService) {
        this.dispatchRepo = dispatchRepo;
        this.dispatchItemRepo = dispatchItemRepo;
        this.orderRepo = orderRepo;
        this.itemRepo = itemRepo;
        this.reservationRepo = reservationRepo;
        this.fgBatchRepo = fgBatchRepo;
        this.productRepo = productRepo;
        this.customerRepo = customerRepo;
        this.specRepo = specRepo;
        this.postingService = postingService;
        this.numberService = numberService;
        this.auditService = auditService;
        this.arService = arService;
    }

    public DispatchDto create(CreateDispatchRequest req) {
        SalesOrder order = orderRepo.findById(req.salesOrderId())
                .orElseThrow(() -> new NotFoundException("SalesOrder", req.salesOrderId()));

        if ("CANCELLED".equals(order.getStatus()) || "DISPATCHED".equals(order.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Cannot dispatch a " + order.getStatus() + " order");
        }

        Dispatch dispatch = new Dispatch();
        dispatch.setDispatchNumber(numberService.next("DN"));
        dispatch.setSalesOrderId(req.salesOrderId());
        dispatch.setCustomerId(order.getCustomerId());
        dispatch.setDispatchDate(req.dispatchDate() != null ? req.dispatchDate() : LocalDate.now());
        dispatch.setTransporter(req.transporter());
        dispatch.setVehicleNo(req.vehicleNo());
        dispatch.setLrNumber(req.lrNumber());
        dispatch.setDeliveryAddress(req.deliveryAddress());
        dispatch.setInvoiceReference(req.invoiceReference());
        dispatch.setStatus("DISPATCHED");
        dispatch.setDispatchedBy(currentUserId());
        dispatch.setNotes(req.notes());
        dispatch = dispatchRepo.save(dispatch);

        List<DispatchItem> savedItems = new ArrayList<>();
        for (CreateDispatchRequest.DispatchLineRequest line : req.lines()) {
            SalesOrderItem soItem = itemRepo.findById(line.salesOrderItemId())
                    .orElseThrow(() -> new NotFoundException("SalesOrderItem", line.salesOrderItemId()));

            if (!soItem.getSalesOrderId().equals(req.salesOrderId())) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Order item " + line.salesOrderItemId() + " does not belong to this order");
            }

            BigDecimal alreadyDispatched = soItem.getDispatchedQty();
            BigDecimal remaining = soItem.getOrderedQty().subtract(alreadyDispatched);
            if (line.quantity().compareTo(remaining) > 0) {
                throw new BusinessRuleException(ErrorCode.DISPATCH_EXCEEDS_STOCK,
                        String.format("Dispatch qty %.3f exceeds remaining %.3f for item %d",
                                line.quantity(), remaining, line.salesOrderItemId()));
            }

            FgBatch batch = fgBatchRepo.findById(line.fgBatchId())
                    .orElseThrow(() -> new NotFoundException("FgBatch", line.fgBatchId()));
            if (!"APPROVED".equals(batch.getQualityStatus()) && !"PARTIALLY_APPROVED".equals(batch.getQualityStatus())) {
                throw new BusinessRuleException(ErrorCode.FG_NOT_APPROVED,
                        "Batch " + batch.getBatchNumber() + " is not approved");
            }

            Long warehouseId = line.warehouseId();
            Long locationId = line.locationId();

            // If there is an active reservation for this item+batch, release it first to free qty_reserved
            reservationRepo.findBySalesOrderItemIdAndFgBatchIdAndStatus(
                    line.salesOrderItemId(), line.fgBatchId(), "ACTIVE").ifPresent(res -> {
                postingService.releaseReservedStock(
                        res.getFgBatchId(), soItem.getProductId(),
                        res.getWarehouseId(), res.getLocationId(), res.getReservedQty());
                res.setStatus("USED");
                reservationRepo.save(res);
            });

            // Post the SALES_DISPATCH ledger entry
            BigDecimal unitPrice = line.unitPrice() != null ? line.unitPrice() : soItem.getUnitPrice();
            postingService.post(new PostingCommand(
                    "SALES_DISPATCH", "PRODUCT",
                    null, soItem.getProductId(),
                    null, line.fgBatchId(),
                    warehouseId, locationId,
                    "APPROVED",
                    line.quantity(), batch.getProduct().getUom(),
                    unitPrice,
                    "DISPATCH", dispatch.getId(), dispatch.getDispatchNumber(),
                    "Dispatch to customer " + order.getCustomerId()));

            // Update FG batch remaining qty
            BigDecimal newRemaining = batch.getRemainingQty().subtract(line.quantity()).max(BigDecimal.ZERO);
            batch.setRemainingQty(newRemaining);
            fgBatchRepo.save(batch);

            // Update order item dispatched qty
            soItem.setDispatchedQty(soItem.getDispatchedQty().add(line.quantity()));
            itemRepo.save(soItem);

            DispatchItem di = new DispatchItem();
            di.setDispatchId(dispatch.getId());
            di.setSalesOrderItemId(line.salesOrderItemId());
            di.setProductId(soItem.getProductId());
            di.setFgBatchId(line.fgBatchId());
            di.setQuantity(line.quantity());
            di.setWarehouseId(warehouseId);
            di.setLocationId(locationId);
            di.setUnitPrice(unitPrice);
            savedItems.add(dispatchItemRepo.save(di));
        }

        // Update order status
        updateOrderStatus(order);

        auditService.record("DISPATCH_CREATED", "DISPATCH", null, "DISPATCHED",
                dispatch.getDispatchNumber(), null);

        try {
            arService.autoCreateFromDispatch(dispatch.getId());
        } catch (Exception e) {
            // AR invoice creation is best-effort; dispatch has already succeeded
            org.slf4j.LoggerFactory.getLogger(DispatchService.class)
                    .error("AR invoice creation failed for dispatch {}: {}",
                            dispatch.getDispatchNumber(), e.getMessage(), e);
        }

        return toDto(dispatch, savedItems);
    }

    @Transactional(readOnly = true)
    public DispatchDto findById(Long dispatchId) {
        Dispatch dispatch = dispatchRepo.findById(dispatchId)
                .orElseThrow(() -> new NotFoundException("Dispatch", dispatchId));
        List<DispatchItem> items = dispatchItemRepo.findByDispatchId(dispatchId);
        return toDto(dispatch, items);
    }

    @Transactional(readOnly = true)
    public Page<DispatchDto> search(Long customerId, LocalDate from, LocalDate to, Pageable pageable) {
        return dispatchRepo.search(customerId, from, to, pageable)
                .map(d -> toDto(d, dispatchItemRepo.findByDispatchId(d.getId())));
    }

    @Transactional(readOnly = true)
    public DispatchNoteDto generateDispatchNote(Long dispatchId) {
        Dispatch dispatch = dispatchRepo.findById(dispatchId)
                .orElseThrow(() -> new NotFoundException("Dispatch", dispatchId));
        List<DispatchItem> items = dispatchItemRepo.findByDispatchId(dispatchId);

        SalesOrder order = orderRepo.findById(dispatch.getSalesOrderId())
                .orElseThrow(() -> new NotFoundException("SalesOrder", dispatch.getSalesOrderId()));
        Customer customer = customerRepo.findById(dispatch.getCustomerId())
                .orElseThrow(() -> new NotFoundException("Customer", dispatch.getCustomerId()));

        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        List<DispatchNoteDto.DispatchNoteLineDto> lines = new ArrayList<>();
        int seq = 1;
        for (DispatchItem di : items) {
            Product product = productRepo.findById(di.getProductId()).orElse(null);
            FgBatch batch = fgBatchRepo.findById(di.getFgBatchId()).orElse(null);
            String cPartNo = specRepo.findByCustomerIdAndProductId(dispatch.getCustomerId(), di.getProductId())
                    .map(s -> s.getCustomerPartNumber()).orElse(null);
            String drawingNo = specRepo.findByCustomerIdAndProductId(dispatch.getCustomerId(), di.getProductId())
                    .map(s -> s.getDrawingNumber()).orElse(null);

            BigDecimal lineValue = di.getUnitPrice() != null ? di.getUnitPrice().multiply(di.getQuantity()) : BigDecimal.ZERO;
            totalQty = totalQty.add(di.getQuantity());
            totalValue = totalValue.add(lineValue);

            lines.add(new DispatchNoteDto.DispatchNoteLineDto(
                    seq++,
                    product != null ? product.getProductCode() : "",
                    product != null ? product.getName() : "",
                    batch != null ? batch.getBatchNumber() : "",
                    di.getQuantity(),
                    product != null ? product.getUom() : "PCS",
                    di.getUnitPrice(),
                    lineValue,
                    cPartNo,
                    drawingNo));
        }

        return new DispatchNoteDto(
                dispatch.getDispatchNumber(),
                order.getOrderNumber(),
                customer.getName(),
                customer.getGstNumber(),
                dispatch.getDispatchDate(),
                dispatch.getTransporter(),
                dispatch.getVehicleNo(),
                dispatch.getLrNumber(),
                dispatch.getDeliveryAddress(),
                dispatch.getInvoiceReference(),
                lines, totalQty, totalValue);
    }

    private void updateOrderStatus(SalesOrder order) {
        List<SalesOrderItem> items = itemRepo.findBySalesOrderId(order.getId());
        long totalItems = items.size();
        long fullyDispatched = items.stream()
                .filter(i -> i.getDispatchedQty().compareTo(i.getOrderedQty()) >= 0)
                .count();
        long partiallyDispatched = items.stream()
                .filter(i -> i.getDispatchedQty().compareTo(BigDecimal.ZERO) > 0
                        && i.getDispatchedQty().compareTo(i.getOrderedQty()) < 0)
                .count();

        if (fullyDispatched == totalItems) {
            order.setStatus("DISPATCHED");
        } else if (fullyDispatched > 0 || partiallyDispatched > 0) {
            order.setStatus("PARTIALLY_DISPATCHED");
        } else {
            order.setStatus("ALLOCATED");
        }
        orderRepo.save(order);
    }

    private DispatchDto toDto(Dispatch dispatch, List<DispatchItem> items) {
        String orderNumber = orderRepo.findById(dispatch.getSalesOrderId())
                .map(SalesOrder::getOrderNumber).orElse("");
        String customerName = customerRepo.findById(dispatch.getCustomerId())
                .map(Customer::getName).orElse("");

        List<DispatchDto.DispatchItemDto> itemDtos = items.stream().map(di -> {
            Product p = productRepo.findById(di.getProductId()).orElse(null);
            FgBatch b = fgBatchRepo.findById(di.getFgBatchId()).orElse(null);
            String spec = null;
            return new DispatchDto.DispatchItemDto(
                    di.getId(), di.getProductId(),
                    p != null ? p.getProductCode() : "",
                    p != null ? p.getName() : "",
                    di.getFgBatchId(),
                    b != null ? b.getBatchNumber() : "",
                    di.getQuantity(), di.getUnitPrice(), spec);
        }).collect(Collectors.toList());

        return new DispatchDto(
                dispatch.getId(), dispatch.getDispatchNumber(),
                dispatch.getSalesOrderId(), orderNumber,
                dispatch.getCustomerId(), customerName,
                dispatch.getDispatchDate(), dispatch.getTransporter(),
                dispatch.getVehicleNo(), dispatch.getLrNumber(),
                dispatch.getDeliveryAddress(), dispatch.getInvoiceReference(),
                dispatch.getStatus(), dispatch.getNotes(), itemDtos);
    }

    private Long currentUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.springmfg.ims.auth.service.UserPermissionCache.CachedUser user) {
                return user.id();
            }
        } catch (Exception ignored) {}
        return -1L;
    }
}
