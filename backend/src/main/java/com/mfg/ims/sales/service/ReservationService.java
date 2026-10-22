package com.mfg.ims.sales.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.inventory.domain.FgBatch;
import com.mfg.ims.inventory.repository.FgBatchRepository;
import com.mfg.ims.inventory.service.InventoryPostingService;
import com.mfg.ims.sales.domain.SalesOrder;
import com.mfg.ims.sales.domain.SalesOrderItem;
import com.mfg.ims.sales.domain.SalesReservation;
import com.mfg.ims.sales.dto.AvailableBatchDto;
import com.mfg.ims.sales.dto.ReservationDto;
import com.mfg.ims.sales.dto.ReserveRequest;
import com.mfg.ims.sales.repository.SalesOrderItemRepository;
import com.mfg.ims.sales.repository.SalesOrderRepository;
import com.mfg.ims.sales.repository.SalesReservationRepository;

@Service
@Transactional
public class ReservationService {

    private final SalesReservationRepository reservationRepo;
    private final SalesOrderItemRepository itemRepo;
    private final SalesOrderRepository orderRepo;
    private final FgBatchRepository fgBatchRepo;
    private final InventoryPostingService postingService;
    private final SystemSettingService settingService;
    private final AuditService auditService;

    private static final java.util.Set<String> RESERVABLE_STATUSES =
            java.util.Set.of("ORDERED", "APPROVED", "ALLOCATED", "PARTIALLY_DISPATCHED");

    private final com.mfg.ims.enterprise.scope.PlantScope plantScope;


    public ReservationService(SalesReservationRepository reservationRepo,
                              SalesOrderItemRepository itemRepo,
                              SalesOrderRepository orderRepo,
                              FgBatchRepository fgBatchRepo,
                              InventoryPostingService postingService,
                              SystemSettingService settingService,
                              AuditService auditService,

            com.mfg.ims.enterprise.scope.PlantScope plantScope) {

        this.plantScope = plantScope;
        this.reservationRepo = reservationRepo;
        this.itemRepo = itemRepo;
        this.orderRepo = orderRepo;
        this.fgBatchRepo = fgBatchRepo;
        this.postingService = postingService;
        this.settingService = settingService;
        this.auditService = auditService;
    }

    public ReservationDto reserve(ReserveRequest req) {
        SalesOrderItem item = itemRepo.findById(req.salesOrderItemId())
                .orElseThrow(() -> new NotFoundException("SalesOrderItem", req.salesOrderItemId()));
        SalesOrder order = orderRepo.findById(item.getSalesOrderId())
                .orElseThrow(() -> new NotFoundException("SalesOrder", item.getSalesOrderId()));
        plantScope.assertVisible(order.getPlantId(), "SalesOrder", item.getSalesOrderId());

        if (!RESERVABLE_STATUSES.contains(order.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Reservations can only be made for ORDERED, APPROVED, ALLOCATED or PARTIALLY_DISPATCHED orders");
        }

        FgBatch batch = fgBatchRepo.findById(req.fgBatchId())
                .orElseThrow(() -> new NotFoundException("FgBatch", req.fgBatchId()));
        if (!"APPROVED".equals(batch.getQualityStatus()) && !"PARTIALLY_APPROVED".equals(batch.getQualityStatus())) {
            throw new BusinessRuleException(ErrorCode.FG_NOT_APPROVED,
                    "Batch " + batch.getBatchNumber() + " is not approved for dispatch");
        }
        if (!batch.getProduct().getId().equals(item.getProductId())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Batch product does not match order item product");
        }

        // Check if a reservation already exists for this item+batch combo; if so, add to it
        var existing = reservationRepo.findBySalesOrderItemIdAndFgBatchIdAndStatus(
                req.salesOrderItemId(), req.fgBatchId(), "ACTIVE");

        // Determine warehouse/location from the batch's inventory balance
        // The caller must supply warehouseId and locationId (they're required in the request for dispatch)
        Long warehouseId = req.warehouseId();
        Long locationId = req.locationId();

        // Reserve in inventory balance
        postingService.reserveStock(req.fgBatchId(), item.getProductId(), warehouseId, locationId, req.qty());

        int maxDays = settingService.getInt("sales.reservation.max_days", 30);
        Instant expiresAt = Instant.now().plus(maxDays, ChronoUnit.DAYS);

        SalesReservation reservation;
        if (existing.isPresent()) {
            reservation = existing.get();
            reservation.setReservedQty(reservation.getReservedQty().add(req.qty()));
            reservation.setExpiresAt(expiresAt);
        } else {
            reservation = new SalesReservation();
            reservation.setSalesOrderItemId(req.salesOrderItemId());
            reservation.setFgBatchId(req.fgBatchId());
            reservation.setWarehouseId(warehouseId);
            reservation.setLocationId(locationId);
            reservation.setReservedQty(req.qty());
            reservation.setStatus("ACTIVE");
            reservation.setReservedBy(currentUserId());
            reservation.setExpiresAt(expiresAt);
        }
        reservation = reservationRepo.save(reservation);

        // Track reserved qty on the order item
        item.setReservedQty(item.getReservedQty().add(req.qty()));
        itemRepo.save(item);

        refreshAllocation(order);

        return toDto(reservation, batch.getBatchNumber());
    }

    public void releaseReservation(Long reservationId) {
        SalesReservation reservation = reservationRepo.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("SalesReservation", reservationId));
        if (!"ACTIVE".equals(reservation.getStatus())) return;

        SalesOrderItem item = itemRepo.findById(reservation.getSalesOrderItemId()).orElse(null);

        postingService.releaseReservedStock(
                reservation.getFgBatchId(),
                item != null ? item.getProductId() : null,
                reservation.getWarehouseId(),
                reservation.getLocationId(),
                reservation.getReservedQty());

        reservation.setStatus("RELEASED");
        reservationRepo.save(reservation);

        if (item != null) {
            BigDecimal newReserved = item.getReservedQty().subtract(reservation.getReservedQty()).max(BigDecimal.ZERO);
            item.setReservedQty(newReserved);
            itemRepo.save(item);
            orderRepo.findById(item.getSalesOrderId()).ifPresent(this::refreshAllocation);
        }
    }

    /**
     * ALLOCATED means every line is covered: its active reservations plus what has already been dispatched reach the
     * ordered quantity (DESIGN §12 step 15, REQUIREMENTS SAL-001). The order goes ORDERED/APPROVED → ALLOCATED when
     * the last line is covered, and back to APPROVED when a release or expiry uncovers a line. Orders that have started
     * dispatching, or are finished or cancelled, keep their own status.
     */
    private void refreshAllocation(SalesOrder order) {
        String status = order.getStatus();
        boolean open = "ORDERED".equals(status) || "APPROVED".equals(status);
        if (!open && !"ALLOCATED".equals(status)) return;

        List<SalesOrderItem> items = itemRepo.findBySalesOrderId(order.getId());
        boolean covered = !items.isEmpty() && items.stream().allMatch(i -> {
            BigDecimal active = reservationRepo.findBySalesOrderItemIdAndStatus(i.getId(), "ACTIVE").stream()
                    .map(SalesReservation::getReservedQty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return active.add(i.getDispatchedQty()).compareTo(i.getOrderedQty()) >= 0;
        });

        if (covered && open) {
            order.setStatus("ALLOCATED");
            orderRepo.save(order);
            auditService.record("SO_ALLOCATED", "SALES_ORDER", String.valueOf(order.getId()), status, "ALLOCATED",
                    "All order lines are reserved");
        } else if (!covered && "ALLOCATED".equals(status)) {
            order.setStatus("APPROVED");
            orderRepo.save(order);
            auditService.record("SO_ALLOCATION_RELEASED", "SALES_ORDER", String.valueOf(order.getId()), "ALLOCATED",
                    "APPROVED", "A reservation was released or expired");
        }
    }

    public void releaseAll(Long salesOrderId) {
        List<SalesReservation> active = reservationRepo.findActiveByOrderId(salesOrderId);
        for (SalesReservation r : active) {
            releaseReservation(r.getId());
        }
    }

    @Transactional(readOnly = true)
    public List<ReservationDto> findByOrderItem(Long salesOrderItemId) {
        return reservationRepo.findBySalesOrderItemIdAndStatus(salesOrderItemId, "ACTIVE")
                .stream()
                .map(r -> {
                    String batchNum = fgBatchRepo.findById(r.getFgBatchId())
                            .map(FgBatch::getBatchNumber).orElse("");
                    return toDto(r, batchNum);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AvailableBatchDto> findAvailableBatches(Long productId) {
        return fgBatchRepo.findByQualityStatus("APPROVED").stream()
                .filter(b -> b.getProduct().getId().equals(productId))
                .filter(b -> b.getRemainingQty().compareTo(BigDecimal.ZERO) > 0)
                .map(b -> new AvailableBatchDto(
                        b.getId(), b.getBatchNumber(), b.getQualityStatus(),
                        b.getRemainingQty(), b.getUnitCost(), b.getProducedOn(), null))
                .collect(Collectors.toList());
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expireReservations() {
        List<SalesReservation> expired = reservationRepo.findExpired(Instant.now());
        for (SalesReservation r : expired) {
            releaseReservation(r.getId());
        }
    }

    private ReservationDto toDto(SalesReservation r, String batchNumber) {
        return new ReservationDto(r.getId(), r.getSalesOrderItemId(), r.getFgBatchId(),
                batchNumber, r.getReservedQty(), r.getStatus(), r.getExpiresAt());
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
