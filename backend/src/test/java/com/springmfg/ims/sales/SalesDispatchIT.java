package com.springmfg.ims.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.inventory.domain.FgBatch;
import com.springmfg.ims.inventory.dto.PostingCommand;
import com.springmfg.ims.inventory.repository.FgBatchRepository;
import com.springmfg.ims.inventory.repository.InventoryBalanceRepository;
import com.springmfg.ims.inventory.service.InventoryPostingService;
import com.springmfg.ims.partner.domain.Customer;
import com.springmfg.ims.partner.repository.CustomerRepository;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.product.repository.ProductRepository;
import com.springmfg.ims.sales.domain.SalesOrder;
import com.springmfg.ims.sales.dto.AddSalesOrderItemRequest;
import com.springmfg.ims.sales.dto.CreateDispatchRequest;
import com.springmfg.ims.sales.dto.CreateSalesOrderRequest;
import com.springmfg.ims.sales.dto.DispatchDto;
import com.springmfg.ims.sales.dto.SalesOrderDto;
import com.springmfg.ims.sales.repository.SalesOrderRepository;
import com.springmfg.ims.sales.service.DispatchService;
import com.springmfg.ims.sales.service.SalesOrderService;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;
import com.springmfg.ims.warehouse.domain.Warehouse;
import com.springmfg.ims.warehouse.domain.WarehouseLocation;
import com.springmfg.ims.warehouse.repository.WarehouseLocationRepository;
import com.springmfg.ims.warehouse.repository.WarehouseRepository;

/**
 * Integration tests for Phase 6 — Sales and Dispatch (PLAN task 6.5).
 */
class SalesDispatchIT extends AbstractIntegrationTest {

    @Autowired SalesOrderService orderService;
    @Autowired DispatchService dispatchService;
    @Autowired SalesOrderRepository orderRepo;
    @Autowired FgBatchRepository fgBatchRepo;
    @Autowired InventoryPostingService postingService;
    @Autowired InventoryBalanceRepository balanceRepo;
    @Autowired CustomerRepository customerRepo;
    @Autowired ProductRepository productRepo;
    @Autowired WarehouseRepository warehouseRepo;
    @Autowired WarehouseLocationRepository locationRepo;
    @Autowired TestData td;

    private Long customerId;
    private Long productId;
    private Long warehouseId;
    private Long locationId;
    private Long fgBatchId;

    @BeforeEach
    void setup() {
        // Customer
        customerId = td.customer().getId();

        // Product — reuse first product from seed data
        Product product = td.product();
        productId = product.getId();

        // Warehouse and FG location
        Warehouse wh = warehouseRepo.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No warehouse in test DB"));
        warehouseId = wh.getId();
        locationId = ensureLocation(wh, "FINISHED").getId();

        // FG batch in APPROVED state
        FgBatch batch = new FgBatch();
        batch.setBatchNumber("FG-DISP-IT-" + System.nanoTime());
        batch.setProduct(product);
        batch.setProductionOrderId(999L);
        batch.setProducedQty(new BigDecimal("200.000"));
        batch.setApprovedQty(new BigDecimal("200.000"));
        batch.setRemainingQty(new BigDecimal("200.000"));
        batch.setProducedOn(LocalDate.now());
        batch.setQualityStatus("APPROVED");
        batch.setUnitCost(new BigDecimal("50.0000"));
        fgBatchId = fgBatchRepo.save(batch).getId();

        // Post FG stock into inventory
        postingService.post(new PostingCommand(
                "FG_RECEIPT", "PRODUCT",
                null, productId, null, fgBatchId,
                warehouseId, locationId, "APPROVED",
                new BigDecimal("200.000"), product.getUom(),
                new BigDecimal("50.0000"),
                "FG_BATCH", fgBatchId, batch.getBatchNumber(), "IT setup"));
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

    // ── SD-01: order lifecycle DRAFT → ORDERED → APPROVED ─────────────────────

    @Test
    @DisplayName("SD-01: sales order follows DRAFT → ORDERED → APPROVED state machine")
    @Transactional
    void salesOrder_lifecycle() {
        SalesOrderDto created = orderService.create(new CreateSalesOrderRequest(
                customerId, LocalDate.now(), LocalDate.now().plusDays(30),
                "123 Test St", "NET30", "Test order",
                List.of(new AddSalesOrderItemRequest(productId, new BigDecimal("100.000"),
                        new BigDecimal("60.0000"), null, null))));

        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.items()).hasSize(1);

        SalesOrderDto confirmed = orderService.confirm(created.id());
        assertThat(confirmed.status()).isEqualTo("ORDERED");

        SalesOrderDto approved = orderService.approve(created.id());
        assertThat(approved.status()).isEqualTo("APPROVED");
    }

    // ── SD-02: full dispatch moves order to DISPATCHED ─────────────────────────

    @Test
    @DisplayName("SD-02: dispatching all items moves order status to DISPATCHED")
    @Transactional
    void fullDispatch_movesOrderToDispatched() {
        SalesOrderDto order = createApprovedOrder(new BigDecimal("100.000"));
        Long itemId = order.items().get(0).id();

        DispatchDto dispatch = dispatchService.create(new CreateDispatchRequest(
                order.id(), LocalDate.now(), "TruckCo", "MH01AB1234", "LR-001",
                "123 Test St", "INV-001",
                List.of(new CreateDispatchRequest.DispatchLineRequest(
                        itemId, fgBatchId, new BigDecimal("100.000"),
                        warehouseId, locationId, new BigDecimal("60.0000"))),
                null));

        assertThat(dispatch.dispatchNumber()).startsWith("DN/");
        SalesOrderDto updated = orderService.findById(order.id());
        assertThat(updated.status()).isEqualTo("DISPATCHED");
    }

    // ── SD-03: partial dispatch moves order to PARTIALLY_DISPATCHED ───────────

    @Test
    @DisplayName("SD-03: partial dispatch moves order status to PARTIALLY_DISPATCHED")
    @Transactional
    void partialDispatch_movesOrderToPartiallyDispatched() {
        SalesOrderDto order = createApprovedOrder(new BigDecimal("100.000"));
        Long itemId = order.items().get(0).id();

        dispatchService.create(new CreateDispatchRequest(
                order.id(), LocalDate.now(), null, null, null,
                "123 Test St", null,
                List.of(new CreateDispatchRequest.DispatchLineRequest(
                        itemId, fgBatchId, new BigDecimal("50.000"),
                        warehouseId, locationId, null)),
                null));

        SalesOrderDto updated = orderService.findById(order.id());
        assertThat(updated.status()).isEqualTo("PARTIALLY_DISPATCHED");
        assertThat(updated.items().get(0).dispatchedQty()).isEqualByComparingTo("50.000");
        assertThat(updated.items().get(0).pendingQty()).isEqualByComparingTo("50.000");
    }

    // ── SD-04: inventory is debited after dispatch ─────────────────────────────

    @Test
    @DisplayName("SD-04: SALES_DISPATCH posts negative stock movement to inventory")
    @Transactional
    void dispatch_deductsInventory() {
        BigDecimal before = balanceRepo.findByProductId(productId).stream()
                .filter(b -> fgBatchId.equals(b.getFgBatchId()))
                .map(b -> b.getQtyOnHand())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        SalesOrderDto order = createApprovedOrder(new BigDecimal("60.000"));
        Long itemId = order.items().get(0).id();

        dispatchService.create(new CreateDispatchRequest(
                order.id(), LocalDate.now(), null, null, null,
                "123 Test St", null,
                List.of(new CreateDispatchRequest.DispatchLineRequest(
                        itemId, fgBatchId, new BigDecimal("60.000"),
                        warehouseId, locationId, null)),
                null));

        BigDecimal after = balanceRepo.findByProductId(productId).stream()
                .filter(b -> fgBatchId.equals(b.getFgBatchId()))
                .map(b -> b.getQtyOnHand())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(before.subtract(after)).isEqualByComparingTo("60.000");
    }

    // ── SD-05: cannot dispatch more than ordered quantity ─────────────────────

    @Test
    @DisplayName("SD-05: dispatch exceeding ordered qty raises DISPATCH_EXCEEDS_STOCK")
    @Transactional
    void dispatch_exceedsOrderedQty_throws() {
        SalesOrderDto order = createApprovedOrder(new BigDecimal("50.000"));
        Long itemId = order.items().get(0).id();

        assertThatThrownBy(() ->
            dispatchService.create(new CreateDispatchRequest(
                    order.id(), LocalDate.now(), null, null, null,
                    "123 Test St", null,
                    List.of(new CreateDispatchRequest.DispatchLineRequest(
                            itemId, fgBatchId, new BigDecimal("99.000"),
                            warehouseId, locationId, null)),
                    null))
        ).isInstanceOf(BusinessRuleException.class);
    }

    // ── SD-06: cannot cancel a DISPATCHED order ───────────────────────────────

    @Test
    @DisplayName("SD-06: cancelling a DISPATCHED order raises ILLEGAL_STATE_TRANSITION")
    @Transactional
    void cancel_dispatchedOrder_throws() {
        SalesOrderDto order = createApprovedOrder(new BigDecimal("100.000"));
        Long itemId = order.items().get(0).id();

        dispatchService.create(new CreateDispatchRequest(
                order.id(), LocalDate.now(), null, null, null,
                "123 Test St", null,
                List.of(new CreateDispatchRequest.DispatchLineRequest(
                        itemId, fgBatchId, new BigDecimal("100.000"),
                        warehouseId, locationId, null)),
                null));

        assertThatThrownBy(() -> orderService.cancel(order.id(), "Test cancel"))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private SalesOrderDto createApprovedOrder(BigDecimal qty) {
        SalesOrderDto created = orderService.create(new CreateSalesOrderRequest(
                customerId, LocalDate.now(), LocalDate.now().plusDays(30),
                "123 Test St", "NET30", null,
                List.of(new AddSalesOrderItemRequest(productId, qty,
                        new BigDecimal("60.0000"), null, null))));
        orderService.confirm(created.id());
        return orderService.approve(created.id());
    }
}
