package com.springmfg.ims.wms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.inventory.domain.FgBatch;
import com.springmfg.ims.inventory.dto.PostingCommand;
import com.springmfg.ims.inventory.repository.FgBatchRepository;
import com.springmfg.ims.inventory.repository.StockAdjustmentRepository;
import com.springmfg.ims.inventory.service.InventoryPostingService;
import com.springmfg.ims.inventory.service.StockLocationQueryService;
import com.springmfg.ims.inventory.service.StockLocationQueryService.LocationStock;
import com.springmfg.ims.procurement.dto.CreateGrRequest;
import com.springmfg.ims.procurement.dto.CreatePoRequest;
import com.springmfg.ims.procurement.dto.GoodsReceiptDto;
import com.springmfg.ims.procurement.repository.PurchaseOrderRepository;
import com.springmfg.ims.procurement.service.GoodsReceiptService;
import com.springmfg.ims.procurement.service.PurchaseOrderService;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.quality.repository.QualityInspectionRepository;
import com.springmfg.ims.quality.service.InspectionService;
import com.springmfg.ims.sales.dto.AddSalesOrderItemRequest;
import com.springmfg.ims.sales.dto.CreateSalesOrderRequest;
import com.springmfg.ims.sales.service.SalesOrderService;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;
import com.springmfg.ims.wms.domain.WmsEnums.CycleCountStatus;
import com.springmfg.ims.wms.domain.WmsEnums.CycleCountType;
import com.springmfg.ims.wms.domain.WmsEnums.MovementType;
import com.springmfg.ims.wms.domain.WmsEnums.PutAwayStrategy;
import com.springmfg.ims.wms.domain.WmsEnums.TransferLineStatus;
import com.springmfg.ims.wms.domain.WmsEnums.TransferOrderStatus;
import com.springmfg.ims.wms.domain.WmsEnums.WaveStatus;
import com.springmfg.ims.wms.domain.WmsEnums.ZoneType;
import com.springmfg.ims.wms.dto.WmsDtos.BinDto;
import com.springmfg.ims.wms.dto.WmsDtos.ConfirmLineRequest;
import com.springmfg.ims.wms.dto.WmsDtos.ConfirmRequest;
import com.springmfg.ims.wms.dto.WmsDtos.CountEntry;
import com.springmfg.ims.wms.dto.WmsDtos.CreateCycleCountRequest;
import com.springmfg.ims.wms.dto.WmsDtos.CreateTransferRequest;
import com.springmfg.ims.wms.dto.WmsDtos.CreateWaveRequest;
import com.springmfg.ims.wms.dto.WmsDtos.CrossDockRequest;
import com.springmfg.ims.wms.dto.WmsDtos.CycleCountDto;
import com.springmfg.ims.wms.dto.WmsDtos.PickListLineDto;
import com.springmfg.ims.wms.dto.WmsDtos.RecordCountsRequest;
import com.springmfg.ims.wms.dto.WmsDtos.SaveAssignmentRequest;
import com.springmfg.ims.wms.dto.WmsDtos.SaveBinRequest;
import com.springmfg.ims.wms.dto.WmsDtos.SavePutAwayRuleRequest;
import com.springmfg.ims.wms.dto.WmsDtos.SaveZoneRequest;
import com.springmfg.ims.wms.dto.WmsDtos.TransferLineRequest;
import com.springmfg.ims.wms.dto.WmsDtos.TransferOrderDto;
import com.springmfg.ims.wms.dto.WmsDtos.ZoneDto;
import com.springmfg.ims.wms.repository.TransferOrderRepository;
import com.springmfg.ims.wms.service.BinService;
import com.springmfg.ims.wms.service.CycleCountService;
import com.springmfg.ims.wms.service.PickingService;
import com.springmfg.ims.wms.service.PutAwayService;
import com.springmfg.ims.wms.service.TransferOrderService;

/**
 * Integration tests for R14 WMS against real PostgreSQL. The tests commit, because put-away and picking are
 * planned after the triggering transaction commits; every test builds its own warehouse so tests do not see each
 * other's bins or stock.
 */
@WithMockUser(authorities = { "PUTAWAY_MANAGE", "PICKING_MANAGE", "TRANSFER_MANAGE", "WAVE_MANAGE",
        "CYCLE_COUNT_MANAGE", "BIN_MANAGE", "WMS_VIEW" })
class WmsIT extends AbstractIntegrationTest {

    private static final String ENFORCE_KEY = "wms.bin_capacity.enforce";

    @Autowired BinService binService;
    @Autowired PutAwayService putAwayService;
    @Autowired TransferOrderService toService;
    @Autowired PickingService pickingService;
    @Autowired CycleCountService cycleCountService;
    @Autowired PurchaseOrderService poService;
    @Autowired GoodsReceiptService grService;
    @Autowired PurchaseOrderRepository poRepo;
    @Autowired InspectionService inspectionService;
    @Autowired QualityInspectionRepository inspectionRepo;
    @Autowired SalesOrderService orderService;
    @Autowired FgBatchRepository fgBatchRepo;
    @Autowired InventoryPostingService posting;
    @Autowired StockLocationQueryService stock;
    @Autowired TransferOrderRepository toRepo;
    @Autowired StockAdjustmentRepository adjRepo;
    @Autowired SystemSettingService settings;
    @Autowired CacheManager cacheManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    @AfterEach
    void resetSettings() {
        settings.update(ENFORCE_KEY, "false");
        settings.update("wms.putaway.default_strategy", "FIXED_BIN");
        settings.update("wms.picking.auto_create", "true");
        var cache = cacheManager.getCache("systemSettings");
        if (cache != null) {
            cache.clear();
        }
        SecurityContextHolder.clearContext();
    }

    // ---- fixtures ------------------------------------------------------------------------------------------

    private Long warehouse() {
        return jdbc.queryForObject("INSERT INTO ims.warehouses (warehouse_code, name) VALUES (?, 'WMS IT') RETURNING id",
                Long.class, td.code("W"));
    }

    private ZoneDto zone(Long warehouseId, String prefix, ZoneType type) {
        return binService.createZone(new SaveZoneRequest(warehouseId, prefix + td.code(""), "IT zone", type, null, null));
    }

    private BinDto bin(ZoneDto zone, BigDecimal maxWeightKg, String allowedTypes) {
        return binService.createBin(new SaveBinRequest(zone.id(), td.code("B"), null, null, null, null, null,
                maxWeightKg, null, allowedTypes, null, null, null));
    }

    private BinDto bin(ZoneDto zone) {
        return bin(zone, null, null);
    }

    private void evictSettings() {
        var cache = cacheManager.getCache("systemSettings");
        if (cache != null) {
            cache.clear();
        }
    }

    private Long approvedPurchaseOrder(Long supplierId, List<Long> materialIds, BigDecimal qty) {
        List<CreatePoRequest.PoLineRequest> lines = new ArrayList<>();
        for (Long m : materialIds) {
            lines.add(new CreatePoRequest.PoLineRequest(m, qty, new BigDecimal("50.0000"), BigDecimal.ZERO));
        }
        var po = poService.create(new CreatePoRequest(supplierId, LocalDate.now(), null, null, null, null, lines));
        var entity = poRepo.findById(po.id()).orElseThrow();
        entity.setStatus("APPROVED");
        entity.setSubmittedBy(-99L);
        poRepo.save(entity);
        return po.id();
    }

    /** Receives each material in full into the given location; the put-away order is planned after commit. */
    private GoodsReceiptDto receive(Long warehouseId, Long locationId, BigDecimal qty, Long... materialIds) {
        Long poId = approvedPurchaseOrder(td.supplier().getId(), List.of(materialIds), qty);
        var po = poService.findById(poId);
        List<CreateGrRequest.GrLineRequest> lines = new ArrayList<>();
        for (var item : po.items()) {
            lines.add(new CreateGrRequest.GrLineRequest(item.id(), qty, locationId, "H-" + item.id(), "C-" + item.id(),
                    null, true));
        }
        return grService.receive(new CreateGrRequest(poId, LocalDate.now(), warehouseId, null, null, lines));
    }

    private void release(Long batchId) {
        var inspection = inspectionRepo.findByMaterialBatchId(batchId).orElseThrow();
        inspectionService.accept(inspection.getId());
    }

    private TransferOrderDto putAwayOrder(Long grId) {
        return toRepo.findBySourceDocumentTypeAndSourceDocumentId("GOODS_RECEIPT", grId).stream()
                .filter(t -> t.getMovementType() == MovementType.PUTAWAY).findFirst()
                .map(t -> toService.get(t.getId())).orElseThrow(() -> new AssertionError("no put-away order for " + grId));
    }

    private BigDecimal qtyAt(Long locationId, String itemType, Long itemId, Long batchId) {
        return stock.stockOfBatchAt(locationId, itemType, itemId, batchId).stream().map(LocationStock::qtyOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Long fgBatch(Product product, int daysOld, BigDecimal qty, Long warehouseId, Long locationId) {
        FgBatch b = new FgBatch();
        b.setBatchNumber(td.code("FG-WMS-"));
        b.setProduct(product);
        b.setProductionOrderId(999L);
        b.setProducedQty(qty);
        b.setApprovedQty(qty);
        b.setRemainingQty(qty);
        b.setProducedOn(LocalDate.now().minusDays(daysOld));
        b.setQualityStatus("APPROVED");
        b.setUnitCost(new BigDecimal("50.0000"));
        Long id = fgBatchRepo.save(b).getId();
        posting.post(new PostingCommand("FG_RECEIPT", "PRODUCT", null, product.getId(), null, id, warehouseId,
                locationId, "AVAILABLE", qty, product.getUom(), new BigDecimal("50.0000"), "FG_BATCH", id,
                b.getBatchNumber(), "WMS IT"));
        return id;
    }

    private Long approvedSalesOrder(Long customerId, Long productId, BigDecimal qty) {
        var order = orderService.create(new CreateSalesOrderRequest(customerId, LocalDate.now(),
                LocalDate.now().plusDays(30), "1 Test St", "NET30", "wms it",
                List.of(new AddSalesOrderItemRequest(productId, qty, new BigDecimal("60.0000"), null, null))));
        orderService.confirm(order.id());
        td.runAsSecondUser(() -> orderService.approve(order.id()), "SALES_APPROVE");
        return order.id();
    }

    private ConfirmRequest confirmAll(TransferOrderDto to, Long toBinId) {
        List<ConfirmLineRequest> lines = to.lines().stream()
                .map(l -> new ConfirmLineRequest(l.id(), l.plannedQty().subtract(l.confirmedQty()), null, null, null,
                        toBinId))
                .toList();
        return new ConfirmRequest(lines);
    }

    // ---- put-away --------------------------------------------------------------------------------------------

    @Test
    void goodsReceipt_plansPutAwayToFixedBin_andConfirmationMovesStockAfterInspection() {
        Long wh = warehouse();
        BinDto receiving = bin(zone(wh, "R", ZoneType.RECEIVING));
        ZoneDto bulk = zone(wh, "K", ZoneType.BULK);
        BinDto first = bin(bulk);
        BinDto fixed = bin(bulk);
        Long material = td.material().getId();
        binService.addAssignment(fixed.id(), new SaveAssignmentRequest(material, null, true));

        GoodsReceiptDto gr = receive(wh, receiving.locationId(), new BigDecimal("50.000"), material);

        TransferOrderDto to = putAwayOrder(gr.id());
        assertThat(to.status()).isEqualTo(TransferOrderStatus.CREATED);
        assertThat(to.sourceDocumentType()).isEqualTo("GOODS_RECEIPT");
        assertThat(to.lines()).hasSize(1);
        var line = to.lines().get(0);
        assertThat(line.toBinId()).as("fixed bin beats the earlier empty bin %s", first.binCode()).isEqualTo(fixed.id());
        assertThat(line.fromBinId()).isEqualTo(receiving.id());
        assertThat(line.plannedQty()).isEqualByComparingTo("50.000");

        Long batchId = gr.items().get(0).materialBatchId();
        assertThatThrownBy(() -> toService.confirm(to.id(), confirmAll(to, null)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.PUTAWAY_BLOCKED));
        assertThat(qtyAt(receiving.locationId(), "MATERIAL", material, batchId)).isEqualByComparingTo("50.000");

        release(batchId);
        var result = toService.confirm(to.id(), confirmAll(to, null));

        assertThat(result.order().status()).isEqualTo(TransferOrderStatus.COMPLETED);
        assertThat(qtyAt(receiving.locationId(), "MATERIAL", material, batchId)).isEqualByComparingTo("0");
        assertThat(qtyAt(fixed.locationId(), "MATERIAL", material, batchId)).isEqualByComparingTo("50.000");
        Integer transfers = jdbc.queryForObject("SELECT COUNT(*) FROM ims.inventory_transactions WHERE transaction_type = 'STOCK_TRANSFER'"
                + " AND reference_type = 'TRANSFER_ORDER' AND reference_id = ?", Integer.class, to.id());
        assertThat(transfers).as("one out leg and one in leg").isEqualTo(2);
        assertThat(binService.getBin(fixed.id()).currentWeightKg()).as("KG material weighs its quantity")
                .isEqualByComparingTo("50.000");
    }

    @Test
    void nearestEmptyStrategy_givesEachLineItsOwnEmptyBin_inCodeOrder() {
        Long wh = warehouse();
        BinDto receiving = bin(zone(wh, "R", ZoneType.RECEIVING));
        ZoneDto bulk = zone(wh, "K", ZoneType.BULK);
        BinDto b1 = bin(bulk);
        BinDto b2 = bin(bulk);
        binService.saveRule(new SavePutAwayRuleRequest(wh, null, PutAwayStrategy.NEAREST_EMPTY));

        GoodsReceiptDto gr = receive(wh, receiving.locationId(), new BigDecimal("10.000"), td.material().getId(),
                td.material().getId());

        TransferOrderDto to = putAwayOrder(gr.id());
        assertThat(to.lines()).hasSize(2);
        List<Long> chosen = to.lines().stream().map(l -> l.toBinId()).toList();
        assertThat(chosen).containsExactlyInAnyOrder(b1.id(), b2.id());
        assertThat(chosen).doesNotHaveDuplicates();
    }

    @Test
    void bulkFirstStrategy_prefersTheBulkZoneEvenWhenAPickingZoneSortsFirst() {
        Long wh = warehouse();
        BinDto receiving = bin(zone(wh, "R", ZoneType.RECEIVING));
        BinDto picking = bin(zone(wh, "AP", ZoneType.PICKING));
        BinDto bulk = bin(zone(wh, "ZB", ZoneType.BULK));
        binService.saveRule(new SavePutAwayRuleRequest(wh, "SPRING_STEEL", PutAwayStrategy.BULK_FIRST));

        GoodsReceiptDto gr = receive(wh, receiving.locationId(), new BigDecimal("10.000"), td.material().getId());

        assertThat(putAwayOrder(gr.id()).lines().get(0).toBinId()).isEqualTo(bulk.id()).isNotEqualTo(picking.id());
    }

    @Test
    void putAwayPlanning_skipsBinsThatBreachCapacityOrDisallowTheMaterialType() {
        Long wh = warehouse();
        BinDto receiving = bin(zone(wh, "R", ZoneType.RECEIVING));
        ZoneDto bulk = zone(wh, "K", ZoneType.BULK);
        BinDto tooSmall = bin(bulk, new BigDecimal("5.000"), null);
        BinDto wrongType = bin(bulk, null, "STAINLESS");
        BinDto fits = bin(bulk, new BigDecimal("500.000"), "SPRING_STEEL");
        binService.saveRule(new SavePutAwayRuleRequest(wh, null, PutAwayStrategy.NEAREST_EMPTY));

        GoodsReceiptDto gr = receive(wh, receiving.locationId(), new BigDecimal("20.000"), td.material().getId());

        assertThat(putAwayOrder(gr.id()).lines().get(0).toBinId()).isEqualTo(fits.id())
                .isNotIn(tooSmall.id(), wrongType.id());
    }

    // ---- confirmation: scans, capacity, concurrency -------------------------------------------------------------

    @Test
    void scannedBinThatDoesNotMatchIsRefusedAndNothingMoves() {
        Long wh = warehouse();
        BinDto receiving = bin(zone(wh, "R", ZoneType.RECEIVING));
        ZoneDto bulk = zone(wh, "K", ZoneType.BULK);
        BinDto target = bin(bulk);
        BinDto other = bin(bulk);
        Long material = td.material().getId();
        GoodsReceiptDto gr = receive(wh, receiving.locationId(), new BigDecimal("30.000"), material);
        Long batchId = gr.items().get(0).materialBatchId();
        release(batchId);
        TransferOrderDto to = putAwayOrder(gr.id());
        Long lineId = to.lines().get(0).id();

        assertThatThrownBy(() -> toService.confirm(to.id(), new ConfirmRequest(List.of(new ConfirmLineRequest(lineId,
                new BigDecimal("30.000"), null, other.binCode(), null, target.id())))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.SCAN_MISMATCH));
        assertThatThrownBy(() -> toService.confirm(to.id(), new ConfirmRequest(List.of(new ConfirmLineRequest(lineId,
                new BigDecimal("30.000"), receiving.binCode(), target.binCode(), "WRONG-BATCH", target.id())))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.SCAN_MISMATCH));
        assertThat(qtyAt(receiving.locationId(), "MATERIAL", material, batchId)).isEqualByComparingTo("30.000");

        String batchNumber = stock.batchNumber("MATERIAL", batchId).orElseThrow();
        var ok = toService.confirm(to.id(), new ConfirmRequest(List.of(new ConfirmLineRequest(lineId,
                new BigDecimal("30.000"), receiving.binCode(), target.binCode(), batchNumber, null))));
        assertThat(ok.order().status()).isEqualTo(TransferOrderStatus.COMPLETED);
        assertThat(qtyAt(target.locationId(), "MATERIAL", material, batchId)).isEqualByComparingTo("30.000");
    }

    @Test
    void partialConfirmation_leavesTheOrderInProgressUntilTheRestIsConfirmed() {
        Long wh = warehouse();
        BinDto receiving = bin(zone(wh, "R", ZoneType.RECEIVING));
        BinDto target = bin(zone(wh, "K", ZoneType.BULK));
        Long material = td.material().getId();
        GoodsReceiptDto gr = receive(wh, receiving.locationId(), new BigDecimal("40.000"), material);
        Long batchId = gr.items().get(0).materialBatchId();
        release(batchId);
        TransferOrderDto to = putAwayOrder(gr.id());
        Long lineId = to.lines().get(0).id();

        var first = toService.confirm(to.id(), new ConfirmRequest(List.of(new ConfirmLineRequest(lineId,
                new BigDecimal("15.000"), null, null, null, target.id()))));
        assertThat(first.order().status()).isEqualTo(TransferOrderStatus.IN_PROGRESS);
        assertThat(first.order().lines().get(0).status()).isEqualTo(TransferLineStatus.PARTIAL);
        assertThat(first.order().allowedActions()).doesNotContain("CANCEL");

        assertThatThrownBy(() -> toService.confirm(to.id(), new ConfirmRequest(List.of(new ConfirmLineRequest(lineId,
                new BigDecimal("26.000"), null, null, null, target.id())))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("exceeds the remaining");

        var second = toService.confirm(to.id(), new ConfirmRequest(List.of(new ConfirmLineRequest(lineId,
                new BigDecimal("25.000"), null, null, null, null))));
        assertThat(second.order().status()).isEqualTo(TransferOrderStatus.COMPLETED);
        assertThat(qtyAt(target.locationId(), "MATERIAL", material, batchId)).isEqualByComparingTo("40.000");
    }

    @Test
    void binCapacity_isAdvisoryByDefault_andRejectsWhenEnforcementIsOn() {
        Long wh = warehouse();
        BinDto receiving = bin(zone(wh, "R", ZoneType.RECEIVING));
        BinDto small = bin(zone(wh, "K", ZoneType.BULK), new BigDecimal("10.000"), null);
        Long material = td.material().getId();
        GoodsReceiptDto gr = receive(wh, receiving.locationId(), new BigDecimal("50.000"), material);
        Long batchId = gr.items().get(0).materialBatchId();
        release(batchId);

        CreateTransferRequest request = new CreateTransferRequest(wh, "capacity test", List.of(new TransferLineRequest(
                "MATERIAL", material, null, batchId, receiving.id(), null, small.id(), new BigDecimal("20.000"))));

        settings.update(ENFORCE_KEY, "true");
        evictSettings();
        assertThatThrownBy(() -> toService.createInternalTransfer(request))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.BIN_CAPACITY_EXCEEDED));

        settings.update(ENFORCE_KEY, "false");
        evictSettings();
        TransferOrderDto to = toService.createInternalTransfer(request);
        assertThat(to.lines().get(0).warning()).contains("capacity");
        var result = toService.confirm(to.id(), confirmAll(to, null));
        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.order().status()).isEqualTo(TransferOrderStatus.COMPLETED);
        assertThat(qtyAt(small.locationId(), "MATERIAL", material, batchId)).isEqualByComparingTo("20.000");

        settings.update(ENFORCE_KEY, "true");
        evictSettings();
        assertThatThrownBy(() -> toService.createInternalTransfer(new CreateTransferRequest(wh, null, List.of(
                new TransferLineRequest("MATERIAL", material, null, batchId, receiving.id(), null, small.id(),
                        new BigDecimal("1.000"))))))
                .as("the bin already holds 20 kg against a 10 kg limit").isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void twoConcurrentConfirmationsOfTheSameLine_moveTheStockOnce() throws Exception {
        Long wh = warehouse();
        BinDto receiving = bin(zone(wh, "R", ZoneType.RECEIVING));
        BinDto target = bin(zone(wh, "K", ZoneType.BULK));
        Long material = td.material().getId();
        GoodsReceiptDto gr = receive(wh, receiving.locationId(), new BigDecimal("50.000"), material);
        Long batchId = gr.items().get(0).materialBatchId();
        release(batchId);
        TransferOrderDto to = putAwayOrder(gr.id());
        ConfirmRequest request = confirmAll(to, target.id());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("t", "n",
                        List.of(new SimpleGrantedAuthority("PUTAWAY_MANAGE"))));
                start.await();
                try {
                    toService.confirm(to.id(), request);
                    return true;
                } catch (BusinessRuleException e) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) succeeded++;
        }
        pool.shutdown();

        assertThat(succeeded).as("exactly one confirmation wins").isEqualTo(1);
        assertThat(qtyAt(target.locationId(), "MATERIAL", material, batchId)).isEqualByComparingTo("50.000");
        assertThat(qtyAt(receiving.locationId(), "MATERIAL", material, batchId)).isEqualByComparingTo("0");
    }

    @Test
    void cancelledOrderCannotBeConfirmed_andConfirmedQuantityBlocksCancellation() {
        Long wh = warehouse();
        BinDto receiving = bin(zone(wh, "R", ZoneType.RECEIVING));
        BinDto target = bin(zone(wh, "K", ZoneType.BULK));
        Long material = td.material().getId();
        GoodsReceiptDto gr = receive(wh, receiving.locationId(), new BigDecimal("20.000"), material);
        release(gr.items().get(0).materialBatchId());
        TransferOrderDto to = putAwayOrder(gr.id());

        toService.cancel(to.id(), "wrong bin plan");
        assertThatThrownBy(() -> toService.confirm(to.id(), confirmAll(to, target.id())))
                .isInstanceOf(BusinessRuleException.class);

        GoodsReceiptDto gr2 = receive(wh, receiving.locationId(), new BigDecimal("20.000"), material);
        release(gr2.items().get(0).materialBatchId());
        TransferOrderDto to2 = putAwayOrder(gr2.id());
        toService.confirm(to2.id(), new ConfirmRequest(List.of(new ConfirmLineRequest(to2.lines().get(0).id(),
                new BigDecimal("5.000"), null, null, null, target.id()))));
        assertThatThrownBy(() -> toService.cancel(to2.id(), "too late"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("confirmed");
    }

    // ---- picking and waves -----------------------------------------------------------------------------------

    @Test
    void approvedSalesOrder_plansPickingInFifoBatchOrder_andVerifiedPickPostsNothing() {
        Long wh = warehouse();
        ZoneDto pickZone = zone(wh, "P", ZoneType.PICKING);
        BinDto older = bin(pickZone);
        BinDto newer = bin(pickZone);
        Product product = td.product();
        Long oldBatch = fgBatch(product, 20, new BigDecimal("40.000"), wh, older.locationId());
        Long newBatch = fgBatch(product, 2, new BigDecimal("100.000"), wh, newer.locationId());

        Long orderId = approvedSalesOrder(td.customer().getId(), product.getId(), new BigDecimal("60.000"));

        List<TransferOrderDto> picks = toRepo.findBySourceDocumentTypeAndSourceDocumentId("SALES_ORDER", orderId).stream()
                .map(t -> toService.get(t.getId())).toList();
        assertThat(picks).hasSize(1);
        TransferOrderDto pick = picks.get(0);
        assertThat(pick.movementType()).isEqualTo(MovementType.PICKING);
        assertThat(pick.lines()).hasSize(2);
        assertThat(pick.lines().get(0).batchId()).as("oldest batch first").isEqualTo(oldBatch);
        assertThat(pick.lines().get(0).plannedQty()).isEqualByComparingTo("40.000");
        assertThat(pick.lines().get(0).fromBinId()).isEqualTo(older.id());
        assertThat(pick.lines().get(1).batchId()).isEqualTo(newBatch);
        assertThat(pick.lines().get(1).plannedQty()).isEqualByComparingTo("20.000");

        assertThatThrownBy(() -> toService.confirm(pick.id(), new ConfirmRequest(List.of(new ConfirmLineRequest(
                pick.lines().get(0).id(), new BigDecimal("40.000"), older.binCode(), null, "NOT-THE-BATCH", null)))))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.SCAN_MISMATCH));

        var done = toService.confirm(pick.id(), confirmAll(pick, null));
        assertThat(done.order().status()).isEqualTo(TransferOrderStatus.COMPLETED);
        assertThat(qtyAt(older.locationId(), "PRODUCT", product.getId(), oldBatch)).as("picking posts no stock")
                .isEqualByComparingTo("40.000");
        Integer transfers = jdbc.queryForObject("SELECT COUNT(*) FROM ims.inventory_transactions WHERE reference_type = 'TRANSFER_ORDER'"
                + " AND reference_id = ?", Integer.class, pick.id());
        assertThat(transfers).isZero();
    }

    @Test
    void waveConsolidatesTheOrdersPickLists_byZoneAndBin_andCompletesWithItsOrders() {
        Long wh = warehouse();
        ZoneDto zoneA = zone(wh, "PA", ZoneType.PICKING);
        ZoneDto zoneB = zone(wh, "PB", ZoneType.PICKING);
        BinDto binA = bin(zoneA);
        BinDto binB = bin(zoneB);
        Product p1 = td.product();
        Product p2 = td.product();
        fgBatch(p1, 5, new BigDecimal("100.000"), wh, binA.locationId());
        fgBatch(p2, 5, new BigDecimal("100.000"), wh, binB.locationId());
        settings.update("wms.picking.auto_create", "false");
        evictSettings();
        Long customer = td.customer().getId();
        Long order1 = approvedSalesOrder(customer, p1.getId(), new BigDecimal("10.000"));
        Long order2 = approvedSalesOrder(customer, p1.getId(), new BigDecimal("15.000"));
        Long order3 = approvedSalesOrder(customer, p2.getId(), new BigDecimal("7.000"));
        assertThat(toRepo.findBySourceDocumentTypeAndSourceDocumentId("SALES_ORDER", order1)).isEmpty();

        var wave = pickingService.createWave(new CreateWaveRequest(List.of(order1, order2, order3), wh, "morning wave"));
        assertThat(wave.status()).isEqualTo(WaveStatus.CREATED);
        assertThat(wave.transferOrderIds()).hasSize(3);

        List<PickListLineDto> list = pickingService.pickList(wave.id(), null);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).zoneCode()).startsWith("PA");
        assertThat(list.get(0).totalQty()).as("two orders for the same product and batch are summed")
                .isEqualByComparingTo("25.000");
        assertThat(list.get(0).orders()).hasSize(2);
        assertThat(list.get(1).zoneCode()).startsWith("PB");
        assertThat(list.get(1).totalQty()).isEqualByComparingTo("7.000");
        assertThat(pickingService.pickList(wave.id(), zoneB.id())).hasSize(1);

        pickingService.releaseWave(wave.id());
        for (Long toId : wave.transferOrderIds()) {
            TransferOrderDto to = toService.get(toId);
            toService.confirm(toId, confirmAll(to, null));
        }
        assertThat(pickingService.getWave(wave.id()).status()).isEqualTo(WaveStatus.COMPLETED);
        assertThat(pickingService.pickList(wave.id(), null)).isEmpty();
    }

    @Test
    void cancellingAWave_cancelsItsOpenPickingOrders_andFreesTheSalesOrdersForANewWave() {
        Long wh = warehouse();
        BinDto bin = bin(zone(wh, "P", ZoneType.PICKING));
        Product product = td.product();
        fgBatch(product, 5, new BigDecimal("100.000"), wh, bin.locationId());
        settings.update("wms.picking.auto_create", "false");
        evictSettings();
        Long order = approvedSalesOrder(td.customer().getId(), product.getId(), new BigDecimal("10.000"));

        var wave = pickingService.createWave(new CreateWaveRequest(List.of(order), wh, null));
        assertThatThrownBy(() -> pickingService.createWave(new CreateWaveRequest(List.of(order), wh, null)))
                .hasMessageContaining("already in wave");
        pickingService.cancelWave(wave.id(), "customer changed order");

        assertThat(toService.get(wave.transferOrderIds().get(0)).status()).isEqualTo(TransferOrderStatus.CANCELLED);
        assertThat(pickingService.createWave(new CreateWaveRequest(List.of(order), wh, null)).status())
                .isEqualTo(WaveStatus.CREATED);
    }

    // ---- cross-docking ---------------------------------------------------------------------------------------

    @Test
    void crossDock_replacesTheOpenPutAway_andStagesTheReceiptForTheOrder() {
        Long wh = warehouse();
        BinDto receiving = bin(zone(wh, "R", ZoneType.RECEIVING));
        bin(zone(wh, "K", ZoneType.BULK));
        BinDto staging = bin(zone(wh, "S", ZoneType.STAGING));
        Long material = td.material().getId();
        GoodsReceiptDto gr = receive(wh, receiving.locationId(), new BigDecimal("25.000"), material);
        Long batchId = gr.items().get(0).materialBatchId();
        Product product = td.product();
        Long orderId = approvedSalesOrder(td.customer().getId(), product.getId(), new BigDecimal("5.000"));
        TransferOrderDto putAway = putAwayOrder(gr.id());

        TransferOrderDto cross = putAwayService.crossDock(new CrossDockRequest(gr.id(), "SALES_ORDER", orderId, "rush"));

        assertThat(toService.get(putAway.id()).status()).isEqualTo(TransferOrderStatus.CANCELLED);
        assertThat(cross.movementType()).isEqualTo(MovementType.CROSS_DOCK);
        assertThat(cross.crossDockTargetType()).isEqualTo("SALES_ORDER");
        assertThat(cross.crossDockTargetId()).isEqualTo(orderId);
        assertThat(cross.lines().get(0).toBinId()).isEqualTo(staging.id());

        release(batchId);
        toService.confirm(cross.id(), confirmAll(cross, null));
        assertThat(qtyAt(staging.locationId(), "MATERIAL", material, batchId)).isEqualByComparingTo("25.000");
    }

    @Test
    void crossDock_withoutAStagingBin_isRefused() {
        Long wh = warehouse();
        BinDto receiving = bin(zone(wh, "R", ZoneType.RECEIVING));
        GoodsReceiptDto gr = receive(wh, receiving.locationId(), new BigDecimal("5.000"), td.material().getId());
        Long orderId = approvedSalesOrder(td.customer().getId(), td.product().getId(), new BigDecimal("1.000"));

        assertThatThrownBy(() -> putAwayService.crossDock(new CrossDockRequest(gr.id(), "SALES_ORDER", orderId, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("staging bin");
    }

    // ---- cycle counts ----------------------------------------------------------------------------------------

    @Test
    void zoneCycleCount_isBlind_andPostingRaisesAnAdjustmentWithoutChangingStock() {
        Long wh = warehouse();
        ZoneDto zone = zone(wh, "P", ZoneType.PICKING);
        BinDto bin = bin(zone);
        Product product = td.product();
        Long batch = fgBatch(product, 3, new BigDecimal("30.000"), wh, bin.locationId());

        CycleCountDto cc = cycleCountService.create(new CreateCycleCountRequest(wh, CycleCountType.ZONE, zone.id(),
                null, null, "monthly"));
        assertThat(cc.status()).isEqualTo(CycleCountStatus.CREATED);
        assertThat(cc.lines()).hasSize(1);
        assertThat(cc.lines().get(0).systemQty()).as("blind count hides the system quantity").isNull();
        Long lineId = cc.lines().get(0).id();

        assertThatThrownBy(() -> cycleCountService.review(cc.id())).isInstanceOf(BusinessRuleException.class);
        cycleCountService.recordCounts(cc.id(), new RecordCountsRequest(List.of(new CountEntry(lineId,
                new BigDecimal("28.000")))));
        CycleCountDto reviewed = cycleCountService.review(cc.id());
        assertThat(reviewed.status()).isEqualTo(CycleCountStatus.REVIEW);
        assertThat(reviewed.lines().get(0).systemQty()).isEqualByComparingTo("30.000");
        assertThat(reviewed.lines().get(0).varianceQty()).isEqualByComparingTo("-2.000");
        assertThat(reviewed.varianceLineCount()).isEqualTo(1);

        CycleCountDto posted = cycleCountService.post(cc.id());
        assertThat(posted.status()).isEqualTo(CycleCountStatus.POSTED);
        Long adjustmentId = posted.lines().get(0).adjustmentId();
        assertThat(adjustmentId).isNotNull();
        var adjustment = adjRepo.findById(adjustmentId).orElseThrow();
        assertThat(adjustment.getStatus()).as("goes through the approval workflow").isEqualTo("REQUESTED");
        assertThat(adjustment.varianceQty()).isEqualByComparingTo("-2.000");
        assertThat(qtyAt(bin.locationId(), "PRODUCT", product.getId(), batch)).as("posting a count never moves stock")
                .isEqualByComparingTo("30.000");

        assertThatThrownBy(() -> cycleCountService.recordCounts(cc.id(), new RecordCountsRequest(List.of(
                new CountEntry(lineId, BigDecimal.ONE))))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cycleCountWithNoVariance_raisesNoAdjustment_andReviewUsesStockAsItIsNow() {
        Long wh = warehouse();
        ZoneDto zone = zone(wh, "P", ZoneType.PICKING);
        BinDto bin = bin(zone);
        Product product = td.product();
        Long batch = fgBatch(product, 3, new BigDecimal("30.000"), wh, bin.locationId());
        CycleCountDto cc = cycleCountService.create(new CreateCycleCountRequest(wh, CycleCountType.ZONE, zone.id(),
                null, null, null));
        Long lineId = cc.lines().get(0).id();

        posting.post(new PostingCommand("FG_RECEIPT", "PRODUCT", null, product.getId(), null, batch, wh,
                bin.locationId(), "AVAILABLE", new BigDecimal("5.000"), product.getUom(), new BigDecimal("50.0000"),
                "FG_BATCH", batch, "late receipt", "arrived while counting"));

        cycleCountService.recordCounts(cc.id(), new RecordCountsRequest(List.of(new CountEntry(lineId,
                new BigDecimal("35.000")))));
        CycleCountDto reviewed = cycleCountService.review(cc.id());
        assertThat(reviewed.lines().get(0).systemQty()).isEqualByComparingTo("35.000");
        assertThat(reviewed.varianceLineCount()).isZero();
        assertThat(cycleCountService.post(cc.id()).lines().get(0).adjustmentId()).isNull();
    }

    @Test
    void randomCycleCount_takesTheSampleSize_andAbcCountNeedsAClass() {
        Long wh = warehouse();
        ZoneDto zone = zone(wh, "P", ZoneType.PICKING);
        for (int i = 0; i < 4; i++) {
            fgBatch(td.product(), i, new BigDecimal("10.000"), wh, bin(zone).locationId());
        }
        CycleCountDto random = cycleCountService.create(new CreateCycleCountRequest(wh, CycleCountType.RANDOM, null,
                null, 3, null));
        assertThat(random.lines()).hasSize(3);

        assertThatThrownBy(() -> cycleCountService.create(new CreateCycleCountRequest(wh, CycleCountType.ABC, null,
                null, null, null))).isInstanceOf(BusinessRuleException.class);
        CycleCountDto abc = cycleCountService.create(new CreateCycleCountRequest(wh, CycleCountType.ABC, null, "C",
                null, null));
        assertThat(abc.lines()).as("no consumption history: every item is class C").hasSize(4);
        assertThatThrownBy(() -> cycleCountService.create(new CreateCycleCountRequest(wh, CycleCountType.ABC, null, "A",
                null, null))).isInstanceOf(BusinessRuleException.class).hasMessageContaining("No stock matches");
    }

    // ---- bin master rules ------------------------------------------------------------------------------------

    @Test
    void binWithStockCannotBeDeactivated_andDuplicateCodesAreRefused() {
        Long wh = warehouse();
        ZoneDto zone = zone(wh, "P", ZoneType.PICKING);
        BinDto bin = bin(zone);
        fgBatch(td.product(), 1, new BigDecimal("5.000"), wh, bin.locationId());

        assertThatThrownBy(() -> binService.updateBin(bin.id(), new SaveBinRequest(zone.id(), bin.binCode(), null, null,
                null, null, null, null, null, null, null, false, bin.version())))
                .hasMessageContaining("holds stock");
        assertThatThrownBy(() -> binService.createBin(new SaveBinRequest(zone.id(), bin.binCode(), null, null, null,
                null, null, null, null, null, null, null, null))).hasMessageContaining("already exists");
        assertThatThrownBy(() -> binService.updateBin(bin.id(), new SaveBinRequest(zone.id(), "OTHER-CODE", null, null,
                null, null, null, null, null, null, null, null, bin.version())))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cannot change");
    }
}
