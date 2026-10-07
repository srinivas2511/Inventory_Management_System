package com.springmfg.ims.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.inventory.domain.FgBatch;
import com.springmfg.ims.inventory.dto.PostingCommand;
import com.springmfg.ims.inventory.repository.FgBatchRepository;
import com.springmfg.ims.inventory.service.InventoryPostingService;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.sales.dto.AddSalesOrderItemRequest;
import com.springmfg.ims.sales.dto.CreateDispatchRequest;
import com.springmfg.ims.sales.dto.CreateSalesOrderRequest;
import com.springmfg.ims.sales.dto.ReservationDto;
import com.springmfg.ims.sales.dto.ReserveRequest;
import com.springmfg.ims.sales.dto.SalesOrderDto;
import com.springmfg.ims.sales.service.DispatchService;
import com.springmfg.ims.sales.service.ReservationService;
import com.springmfg.ims.sales.service.SalesOrderService;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;
import com.springmfg.ims.warehouse.domain.Warehouse;
import com.springmfg.ims.warehouse.domain.WarehouseLocation;
import com.springmfg.ims.warehouse.repository.WarehouseLocationRepository;
import com.springmfg.ims.warehouse.repository.WarehouseRepository;

/**
 * DESIGN §12 step 15 / REQUIREMENTS SAL-001: reserving stock moves an order to ALLOCATED once every line is covered,
 * and a release, expiry or cancellation puts it back. Deliberately NOT {@code @Transactional}: it drives the real
 * services and commits, as the application does.
 */
class ReservationAllocationIT extends AbstractIntegrationTest {

    @Autowired SalesOrderService orderService;
    @Autowired ReservationService reservationService;
    @Autowired DispatchService dispatchService;
    @Autowired FgBatchRepository fgBatchRepo;
    @Autowired InventoryPostingService postingService;
    @Autowired WarehouseRepository warehouseRepo;
    @Autowired WarehouseLocationRepository locationRepo;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    private Long customerId;
    private Long productId;
    private Long warehouseId;
    private Long locationId;
    private Long fgBatchId;

    @BeforeEach
    void setup() {
        var actor = new CachedUser(7003L, "alloc-it", true, 1, Set.of(), Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, List.of()));
        customerId = td.customer().getId();
        Product product = td.product();
        productId = product.getId();
        Warehouse wh = warehouseRepo.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No warehouse in test DB"));
        warehouseId = wh.getId();
        locationId = ensureLocation(wh, "FINISHED").getId();

        FgBatch batch = new FgBatch();
        batch.setBatchNumber("FG-ALLOC-IT-" + System.nanoTime());
        batch.setProduct(product);
        batch.setProductionOrderId(999L);
        batch.setProducedQty(new BigDecimal("1000.000"));
        batch.setApprovedQty(new BigDecimal("1000.000"));
        batch.setRemainingQty(new BigDecimal("1000.000"));
        batch.setProducedOn(LocalDate.now());
        batch.setQualityStatus("APPROVED");
        batch.setUnitCost(new BigDecimal("50.0000"));
        fgBatchId = fgBatchRepo.save(batch).getId();
        postingService.post(new PostingCommand(
                "FG_RECEIPT", "PRODUCT", null, productId, null, fgBatchId,
                warehouseId, locationId, "AVAILABLE",
                new BigDecimal("1000.000"), product.getUom(), new BigDecimal("50.0000"),
                "FG_BATCH", fgBatchId, batch.getBatchNumber(), "IT setup"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("ALLOC-01: reserving the whole line moves the order to ALLOCATED")
    void fullReservation_allocatesTheOrder() {
        SalesOrderDto order = approvedOrder("100");

        reserve(order, 0, "100");

        assertThat(status(order)).isEqualTo("ALLOCATED");
        assertThat(reservedOnBalance()).isEqualByComparingTo("100.000");
    }

    @Test
    @DisplayName("ALLOC-02: a partial reservation keeps the order APPROVED until the line is covered")
    void partialReservation_doesNotAllocate() {
        SalesOrderDto order = approvedOrder("100");

        reserve(order, 0, "40");
        assertThat(status(order)).isEqualTo("APPROVED");

        reserve(order, 0, "60");
        assertThat(status(order)).isEqualTo("ALLOCATED");
        assertThat(reservedOnBalance()).isEqualByComparingTo("100.000");
    }

    @Test
    @DisplayName("ALLOC-03: a multi-line order is ALLOCATED only when every line is covered")
    void multiLine_needsEveryLineCovered() {
        SalesOrderDto order = approvedOrder("30", "70");

        reserve(order, 0, "30");
        assertThat(status(order)).isEqualTo("APPROVED");

        reserve(order, 1, "70");
        assertThat(status(order)).isEqualTo("ALLOCATED");
    }

    @Test
    @DisplayName("ALLOC-04: releasing a reservation puts an ALLOCATED order back to APPROVED and frees the stock")
    void release_revertsTheOrder() {
        SalesOrderDto order = approvedOrder("100");
        ReservationDto reservation = reserve(order, 0, "100");
        assertThat(status(order)).isEqualTo("ALLOCATED");

        reservationService.releaseReservation(reservation.id());

        assertThat(status(order)).isEqualTo("APPROVED");
        assertThat(reservedOnBalance()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("ALLOC-05: cancelling an ALLOCATED order releases every reservation and cancels the order")
    void cancel_releasesTheStock() {
        SalesOrderDto order = approvedOrder("100");
        reserve(order, 0, "100");
        assertThat(reservedOnBalance()).isEqualByComparingTo("100.000");

        orderService.cancel(order.id(), "customer withdrew");

        assertThat(status(order)).isEqualTo("CANCELLED");
        assertThat(reservedOnBalance()).isEqualByComparingTo("0.000");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM sales_reservations r JOIN sales_order_items i ON i.id = r.sales_order_item_id
                 WHERE i.sales_order_id = ? AND r.status = 'ACTIVE'""", Integer.class, order.id())).isZero();
    }

    @Test
    @DisplayName("ALLOC-06: an ALLOCATED order can be dispatched, which uses the reservation")
    void allocatedOrder_dispatches() {
        SalesOrderDto order = approvedOrder("100");
        reserve(order, 0, "100");

        dispatchService.create(new CreateDispatchRequest(order.id(), LocalDate.now(), null, null, null,
                "123 Test St", null, List.of(new CreateDispatchRequest.DispatchLineRequest(
                        order.items().get(0).id(), fgBatchId, new BigDecimal("100"), warehouseId, locationId,
                        new BigDecimal("60.0000"))), null));

        assertThat(status(order)).isEqualTo("DISPATCHED");
        assertThat(reservedOnBalance()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("ALLOC-07: once dispatching has started the order stays PARTIALLY_DISPATCHED when more is reserved")
    void partiallyDispatched_keepsItsStatus() {
        SalesOrderDto order = approvedOrder("100");
        reserve(order, 0, "40");
        dispatchService.create(new CreateDispatchRequest(order.id(), LocalDate.now(), null, null, null,
                "123 Test St", null, List.of(new CreateDispatchRequest.DispatchLineRequest(
                        order.items().get(0).id(), fgBatchId, new BigDecimal("40"), warehouseId, locationId,
                        new BigDecimal("60.0000"))), null));
        assertThat(status(order)).isEqualTo("PARTIALLY_DISPATCHED");

        reserve(order, 0, "60");

        assertThat(status(order)).isEqualTo("PARTIALLY_DISPATCHED");
    }

    private SalesOrderDto approvedOrder(String... lineQuantities) {
        List<AddSalesOrderItemRequest> items = new ArrayList<>();
        for (String qty : lineQuantities) {
            items.add(new AddSalesOrderItemRequest(productId, new BigDecimal(qty), new BigDecimal("60.0000"),
                    null, null));
        }
        SalesOrderDto created = orderService.create(new CreateSalesOrderRequest(
                customerId, LocalDate.now(), LocalDate.now().plusDays(30), "123 Test St", "NET30", null, items));
        orderService.confirm(created.id());
        return orderService.approve(created.id());
    }

    private ReservationDto reserve(SalesOrderDto order, int line, String qty) {
        return reservationService.reserve(new ReserveRequest(order.items().get(line).id(), fgBatchId, warehouseId,
                locationId, new BigDecimal(qty)));
    }

    private String status(SalesOrderDto order) {
        return orderService.findById(order.id()).status();
    }

    private BigDecimal reservedOnBalance() {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(qty_reserved), 0) FROM inventory_balances WHERE fg_batch_id = ?",
                BigDecimal.class, fgBatchId);
    }

    private WarehouseLocation ensureLocation(Warehouse wh, String locType) {
        return locationRepo.findByWarehouseIdAndActiveTrue(wh.getId()).stream()
                .filter(l -> locType.equals(l.getLocationType()))
                .findFirst()
                .orElseGet(() -> {
                    WarehouseLocation loc = new WarehouseLocation();
                    loc.setWarehouse(wh);
                    loc.setLocationCode(td.code("L"));
                    loc.setName(locType);
                    loc.setLocationType(locType);
                    loc.setActive(true);
                    return locationRepo.save(loc);
                });
    }
}
