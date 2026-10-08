package com.springmfg.ims.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.controlling.dto.CostEstimateDto;
import com.springmfg.ims.controlling.service.ProductionOrderVarianceService;
import com.springmfg.ims.controlling.service.ProductCostingService;
import com.springmfg.ims.engineering.bom.BomDto;
import com.springmfg.ims.engineering.bom.BomItemRequest;
import com.springmfg.ims.engineering.bom.BomService;
import com.springmfg.ims.engineering.bom.CreateBomRequest;
import com.springmfg.ims.engineering.routing.RoutingService;
import com.springmfg.ims.engineering.routing.SaveRoutingStepRequest;
import com.springmfg.ims.material.domain.Material;
import com.springmfg.ims.procurement.domain.PurchaseOrder;
import com.springmfg.ims.procurement.dto.CreateGrRequest;
import com.springmfg.ims.procurement.dto.CreatePoRequest;
import com.springmfg.ims.procurement.repository.PurchaseOrderRepository;
import com.springmfg.ims.procurement.service.GoodsReceiptService;
import com.springmfg.ims.procurement.service.PurchaseOrderService;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.production.dto.CreateIssueRequest;
import com.springmfg.ims.production.dto.CreateProductionOrderRequest;
import com.springmfg.ims.production.dto.ProductionOperationDto;
import com.springmfg.ims.production.dto.ProductionOrderDto;
import com.springmfg.ims.production.dto.RecordOutputRequest;
import com.springmfg.ims.production.service.MaterialIssueService;
import com.springmfg.ims.production.service.OperationExecutionService;
import com.springmfg.ims.production.service.ProductionOrderService;
import com.springmfg.ims.quality.dto.FinalInspectionDecisionRequest;
import com.springmfg.ims.quality.service.FinalInspectionService;
import com.springmfg.ims.quality.service.InspectionService;
import com.springmfg.ims.sales.dto.AddSalesOrderItemRequest;
import com.springmfg.ims.sales.dto.CreateDispatchRequest;
import com.springmfg.ims.sales.dto.CreateSalesOrderRequest;
import com.springmfg.ims.sales.dto.DispatchDto;
import com.springmfg.ims.sales.dto.ReserveRequest;
import com.springmfg.ims.sales.dto.SalesOrderDto;
import com.springmfg.ims.sales.service.DispatchService;
import com.springmfg.ims.sales.service.ReservationService;
import com.springmfg.ims.sales.service.SalesOrderService;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;

/**
 * The money chain, end to end through the real services and with no surrounding transaction (the bridge journals
 * after commit): PO, goods receipt, incoming inspection, material issue, output, final inspection, reservation,
 * dispatch and the AR invoice. Every expected figure below is worked by hand in the comments.
 *
 * <p>Journals are read as "everything posted after the id mark taken at the start", so the shared database's older
 * data does not matter. Test classes run one after another, so nothing else posts while a scenario runs.
 */
class FinishedGoodsCostingIT extends AbstractIntegrationTest {

    private static final long WAREHOUSE_ID = 1L;
    private static final long LOCATION_ID = 1L;
    private static final BigDecimal TOLERANCE = new BigDecimal("0.0100");

    @Autowired PurchaseOrderService poService;
    @Autowired PurchaseOrderRepository poRepo;
    @Autowired GoodsReceiptService grService;
    @Autowired InspectionService inspectionService;
    @Autowired BomService bomService;
    @Autowired RoutingService routingService;
    @Autowired ProductionOrderService orderService;
    @Autowired MaterialIssueService issueService;
    @Autowired OperationExecutionService executionService;
    @Autowired FinalInspectionService finalInspectionService;
    @Autowired SalesOrderService salesOrderService;
    @Autowired ReservationService reservationService;
    @Autowired DispatchService dispatchService;
    @Autowired ProductCostingService costingService;
    @Autowired ProductionOrderVarianceService varianceService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    /** The seeded cost-centre rates, restored after each test: the numbers below are hand-worked without conversion cost. */
    private List<Map<String, Object>> seededRates = List.of();

    @BeforeEach
    void zeroConversionRatesAndLogin() {
        // Operations are timed by the real clock, so with the seeded non-zero rates a slow run (a cold JVM, a loaded
        // machine) would absorb a few cents of conversion cost and move the unit cost in the fourth decimal.
        seededRates = jdbc.queryForList(
                "SELECT id, labour_rate_per_hour, machine_rate_per_hour, overhead_pct FROM ims.cost_centres");
        jdbc.update("UPDATE ims.cost_centres SET labour_rate_per_hour = 0, machine_rate_per_hour = 0, overhead_pct = 0");
        Long userId = jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'Costing IT', ?, 'x') RETURNING id
                """, Long.class, td.code("cost"), td.code("cost") + "@example.com");
        String[] authorities = { "PURCHASE_CREATE", "PURCHASE_APPROVE", "INVENTORY_RECEIVE", "QUALITY_VIEW",
                "QUALITY_INSPECT", "QUALITY_APPROVE", "PRODUCTION_VIEW", "PRODUCTION_EXECUTE", "PRODUCTION_CREATE",
                "SALES_VIEW", "SALES_CREATE", "SALES_APPROVE", "RESERVE_FG", "DISPATCH_CREATE" };
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CachedUser(userId, "u" + userId, true, 1, Set.of(), Set.copyOf(Arrays.asList(authorities))), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        for (Map<String, Object> r : seededRates) {
            jdbc.update("""
                    UPDATE ims.cost_centres SET labour_rate_per_hour = ?, machine_rate_per_hour = ?, overhead_pct = ?
                     WHERE id = ?
                    """, r.get("labour_rate_per_hour"), r.get("machine_rate_per_hour"), r.get("overhead_pct"), r.get("id"));
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private long journalMark() {
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM ims.journal_headers", Long.class);
        return max == null ? 0 : max;
    }

    /** Net debit (positive) or credit (negative) per account code over the journals posted after the mark. */
    private Map<String, BigDecimal> netByAccount(long mark) {
        Map<String, BigDecimal> out = new HashMap<>();
        jdbc.query("""
                SELECT a.account_code, SUM(jl.debit_amount - jl.credit_amount) AS net
                  FROM ims.journal_lines jl
                  JOIN ims.journal_headers jh ON jh.id = jl.journal_header_id
                  JOIN ims.gl_accounts a ON a.id = jl.gl_account_id
                 WHERE jh.id > ? AND jh.status = 'POSTED'
                 GROUP BY a.account_code
                """, rs -> {
            out.put(rs.getString("account_code"), rs.getBigDecimal("net"));
        }, mark);
        return out;
    }

    private static BigDecimal net(Map<String, BigDecimal> byAccount, String code) {
        return byAccount.getOrDefault(code, BigDecimal.ZERO);
    }

    private void assertEveryJournalBalances(long mark) {
        List<Long> unbalanced = jdbc.queryForList("""
                SELECT jh.id FROM ims.journal_headers jh
                  JOIN ims.journal_lines jl ON jl.journal_header_id = jh.id
                 WHERE jh.id > ?
                 GROUP BY jh.id HAVING SUM(jl.debit_amount) <> SUM(jl.credit_amount)
                """, Long.class, mark);
        assertThat(unbalanced).as("journals where Dr <> Cr").isEmpty();
    }

    /** PO, approval, goods receipt and incoming inspection accept: the material ends up AVAILABLE at the price. */
    private void receiveMaterial(Material material, String qty, String price) {
        var po = poService.create(new CreatePoRequest(td.supplier().getId(), LocalDate.now(),
                LocalDate.now().plusDays(7), "NET30", null, null,
                List.of(new CreatePoRequest.PoLineRequest(material.getId(), new BigDecimal(qty),
                        new BigDecimal(price), new BigDecimal("0.00")))));
        po = poService.submit(po.id());
        PurchaseOrder entity = poRepo.findById(po.id()).orElseThrow();
        entity.setSubmittedBy(-99L);
        poRepo.save(entity);
        po = poService.approve(po.id());
        var gr = grService.receive(new CreateGrRequest(po.id(), LocalDate.now(), WAREHOUSE_ID,
                td.code("CH"), "MH12AB0001",
                List.of(new CreateGrRequest.GrLineRequest(po.items().get(0).id(), new BigDecimal(qty), LOCATION_ID,
                        td.code("HEAT"), td.code("CERT"), null, true))));
        Long batchId = gr.items().get(0).materialBatchId();
        Long inspectionId = jdbc.queryForObject("""
                SELECT id FROM ims.quality_inspections WHERE material_batch_id = ? AND inspection_type = 'INCOMING'
                """, Long.class, batchId);
        inspectionService.accept(inspectionId);
    }

    private record Produced(Product product, Material material, BomDto bom, ProductionOrderDto order) {
    }

    /** A released order for 100 pieces at 0.8 kg each, with 80 kg issued FIFO from the two receipts. */
    private Produced releasedOrderWithTwoReceipts() {
        Product product = td.product();
        Material material = td.material();
        receiveMaterial(material, "40.000", "50.0000");
        receiveMaterial(material, "40.000", "60.0000");
        List<Long> masterOps = jdbc.queryForList("SELECT id FROM ims.operations ORDER BY id LIMIT 3", Long.class);
        routingService.saveSteps(product.getId(), List.of(
                new SaveRoutingStepRequest(10, masterOps.get(0), null, new BigDecimal("15"), new BigDecimal("10"), false),
                new SaveRoutingStepRequest(20, masterOps.get(1), null, new BigDecimal("20"), new BigDecimal("10"), false),
                new SaveRoutingStepRequest(30, masterOps.get(2), null, new BigDecimal("20"), new BigDecimal("5"), true)));
        BomDto bom = bomService.create(new CreateBomRequest(product.getId(), BigDecimal.ONE, "Costing IT BOM",
                List.of(new BomItemRequest(10, "MATERIAL", material.getId(), null,
                        new BigDecimal("0.8"), "KG", BigDecimal.ZERO, "wire"))));
        bom = bomService.submitForReview(bom.id());
        bom = bomService.approve(bom.id());
        bom = bomService.activate(bom.id());
        ProductionOrderDto order = orderService.create(new CreateProductionOrderRequest(product.getId(), bom.id(),
                new BigDecimal("100"), LocalDate.now(), LocalDate.now().plusDays(3), "LINE-A", null, null, null,
                (short) 2, "Costing IT"));
        order = orderService.approve(order.id());
        order = orderService.release(order.id());
        issueService.issue(new CreateIssueRequest(order.id(), WAREHOUSE_ID,
                List.of(new CreateIssueRequest.IssueLineRequest(material.getId(), new BigDecimal("80.000"), null)),
                "Costing IT issue"));
        return new Produced(product, material, bom, orderService.findById(order.id()));
    }

    private void runEveryOperation(ProductionOrderDto order, String good, String rejected, String scrap) {
        List<ProductionOperationDto> ops = order.operations().stream()
                .sorted(Comparator.comparing(ProductionOperationDto::sequenceNo)).toList();
        for (int i = 0; i < ops.size(); i++) {
            Long id = ops.get(i).id();
            executionService.startOperation(id, null);
            executionService.recordOutput(i == 0
                    ? new RecordOutputRequest(id, new BigDecimal(good), new BigDecimal(rejected), new BigDecimal(scrap),
                            null, null, null, null, "A", "cost")
                    : new RecordOutputRequest(id, new BigDecimal(good), BigDecimal.ZERO, BigDecimal.ZERO,
                            null, null, null, null, "A", "cost"));
            executionService.completeOperation(id);
        }
    }

    private Long fgBatchOf(Long orderId) {
        return jdbc.queryForObject("SELECT id FROM ims.fg_batches WHERE production_order_id = ?", Long.class, orderId);
    }

    private void approveFinalInspection(Long fgBatchId, String accepted) {
        Long inspectionId = jdbc.queryForObject(
                "SELECT id FROM ims.quality_inspections WHERE fg_batch_id = ? AND inspection_type = 'FINAL'",
                Long.class, fgBatchId);
        finalInspectionService.completeFinalInspection(inspectionId, new FinalInspectionDecisionRequest(
                new BigDecimal(accepted), BigDecimal.ZERO, BigDecimal.ZERO, null, null, "cost"));
    }

    /** Reserves and dispatches {@code qty} at a selling price of 100 each; returns the dispatch. */
    private DispatchDto dispatch(Produced p, Long fgBatchId, String qty) {
        Long fgLocation = jdbc.queryForObject("""
                SELECT location_id FROM ims.inventory_balances
                 WHERE fg_batch_id = ? AND stock_status = 'AVAILABLE' AND qty_on_hand > 0
                """, Long.class, fgBatchId);
        long warehouseId = jdbc.queryForObject(
                "SELECT warehouse_id FROM ims.warehouse_locations WHERE id = ?", Long.class, fgLocation);
        SalesOrderDto so = salesOrderService.create(new CreateSalesOrderRequest(td.customer().getId(),
                LocalDate.now(), LocalDate.now().plusDays(7), "Address", "NET30", "cost",
                List.of(new AddSalesOrderItemRequest(p.product().getId(), new BigDecimal(qty),
                        new BigDecimal("100.0000"), null, null))));
        salesOrderService.confirm(so.id());
        so = salesOrderService.approve(so.id());
        Long itemId = so.items().get(0).id();
        reservationService.reserve(new ReserveRequest(itemId, fgBatchId, warehouseId, fgLocation, new BigDecimal(qty)));
        return dispatchService.create(new CreateDispatchRequest(so.id(), LocalDate.now(), "Carrier", "MH12AB1234",
                "LR-1", "Address", null,
                List.of(new CreateDispatchRequest.DispatchLineRequest(itemId, fgBatchId, new BigDecimal(qty),
                        warehouseId, fgLocation, null)), "cost"));
    }

    private BigDecimal ledgerValueOfFinishedGoods(Long productId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(qty_on_hand * avg_unit_cost), 0) FROM ims.inventory_balances
                 WHERE item_type = 'PRODUCT' AND product_id = ?
                """, BigDecimal.class, productId);
    }

    // ---- scenarios ------------------------------------------------------------------------------------------------

    /**
     * Two receipts at different prices, no scrap, no released estimate, partial dispatch.
     *
     * <pre>
     * GR 1   40 kg x 50   = 2,000   Dr 1401 / Cr 2401
     * GR 2   40 kg x 60   = 2,400   Dr 1401 / Cr 2401
     * issue  80 kg (FIFO) = 2,000 + 2,400 = 4,400   Dr 1501 WIP / Cr 1401
     * output 100 good     -> 4,400 / 100 = 44.0000 each   Dr 1410 4,400 / Cr 1501 4,400  (WIP back to 0)
     * dispatch 60 pcs     -> COGS 60 x 44 = 2,640   Dr 5001 / Cr 1410;  revenue 60 x 100 = 6,000   Dr 1101 / Cr 4001
     * left on hand        40 pcs x 44 = 1,760 = GL 1410
     * </pre>
     */
    @Test
    @DisplayName("FGC-IT-1: FG is received at the actual material cost and the partial dispatch books COGS at that cost")
    void actualMaterialCostFlowsThroughToCogs() {
        long mark = journalMark();
        long alertMark = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM ims.alerts", Long.class);
        Produced p = releasedOrderWithTwoReceipts();
        runEveryOperation(p.order(), "100", "0", "0");
        Long fgBatchId = fgBatchOf(p.order().id());

        assertThat(jdbc.queryForObject("SELECT unit_cost FROM ims.fg_batches WHERE id = ?", BigDecimal.class, fgBatchId))
                .isEqualByComparingTo("44.0000");
        approveFinalInspection(fgBatchId, "100");
        DispatchDto dispatch = dispatch(p, fgBatchId, "60");

        Map<String, BigDecimal> gl = netByAccount(mark);
        assertThat(net(gl, "1401")).as("raw material stock: in 4,400, out 4,400").isEqualByComparingTo("0");
        assertThat(net(gl, "2401")).as("GR/IR clearing").isEqualByComparingTo("-4400");
        assertThat(net(gl, "1501")).as("WIP fully relieved").isEqualByComparingTo("0");
        assertThat(net(gl, "1410")).as("FG stock: 4,400 in, 2,640 out").isEqualByComparingTo("1760");
        assertThat(net(gl, "5001")).as("cost of goods sold").isEqualByComparingTo("2640");
        assertThat(net(gl, "1101")).as("receivable").isEqualByComparingTo("6000");
        assertThat(net(gl, "4001")).as("revenue").isEqualByComparingTo("-6000");
        assertThat(net(gl, "1410")).as("GL FG stock equals the ledger valuation (40 x 44)")
                .isEqualByComparingTo(ledgerValueOfFinishedGoods(p.product().getId()));
        assertThat(gl.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).as("trial balance").isEqualByComparingTo("0");
        assertEveryJournalBalances(mark);

        BigDecimal dispatchCost = jdbc.queryForObject("""
                SELECT -SUM(total_cost) FROM ims.inventory_transactions
                 WHERE transaction_type = 'SALES_DISPATCH' AND reference_id = ?
                """, BigDecimal.class, dispatch.id());
        assertThat(dispatchCost == null ? BigDecimal.ZERO : dispatchCost.abs()).isEqualByComparingTo("2640");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ims.alerts WHERE id > ? AND alert_type = 'MISSING_JOURNAL'
                   AND (message LIKE '%PRODUCTION_RECEIPT%' OR message LIKE '%SALES_DISPATCH%')
                """, Integer.class, alertMark)).as("no missing-journal alert for the FG receipt or the dispatch").isZero();
    }

    /**
     * Scrap is relieved from WIP at the product's standard cost and netted off the cost of the good output.
     *
     * <pre>
     * issue  100 pcs x 0.8 kg = 80 kg at 50 = 4,000   Dr 1501 / Cr 1401
     * output 80 good + 14 rejected + 6 scrap
     * scrap  6 x 40 (product standard) = 240   Dr 5202 / Cr 1501
     * good   (4,000 - 240) / 80 = 47.0000 each = 3,760   Dr 1410 / Cr 1501      WIP: 4,000 - 240 - 3,760 = 0
     * </pre>
     */
    @Test
    @DisplayName("FGC-IT-2: scrap is relieved once and the good output carries the rest of the material cost")
    void scrapIsNettedOffTheGoodOutput() {
        long mark = journalMark();
        Product product = td.product();
        Material material = td.material();
        receiveMaterial(material, "80.000", "50.0000");
        jdbc.update("UPDATE ims.products SET standard_cost = 40.0000 WHERE id = ?", product.getId());
        List<Long> masterOps = jdbc.queryForList("SELECT id FROM ims.operations ORDER BY id LIMIT 3", Long.class);
        routingService.saveSteps(product.getId(), List.of(
                new SaveRoutingStepRequest(10, masterOps.get(0), null, new BigDecimal("15"), new BigDecimal("10"), false),
                new SaveRoutingStepRequest(20, masterOps.get(1), null, new BigDecimal("20"), new BigDecimal("10"), false),
                new SaveRoutingStepRequest(30, masterOps.get(2), null, new BigDecimal("20"), new BigDecimal("5"), true)));
        BomDto bom = bomService.create(new CreateBomRequest(product.getId(), BigDecimal.ONE, "Costing scrap BOM",
                List.of(new BomItemRequest(10, "MATERIAL", material.getId(), null,
                        new BigDecimal("0.8"), "KG", BigDecimal.ZERO, "wire"))));
        bom = bomService.activate(bomService.approve(bomService.submitForReview(bom.id()).id()).id());
        ProductionOrderDto order = orderService.create(new CreateProductionOrderRequest(product.getId(), bom.id(),
                new BigDecimal("100"), LocalDate.now(), LocalDate.now().plusDays(3), "LINE-A", null, null, null,
                (short) 2, "Costing scrap"));
        order = orderService.release(orderService.approve(order.id()).id());
        issueService.issue(new CreateIssueRequest(order.id(), WAREHOUSE_ID,
                List.of(new CreateIssueRequest.IssueLineRequest(material.getId(), new BigDecimal("80.000"), null)),
                "Costing scrap issue"));
        order = orderService.findById(order.id());

        runEveryOperation(order, "80", "14", "6");
        Long fgBatchId = fgBatchOf(order.id());

        assertThat(jdbc.queryForObject("SELECT unit_cost FROM ims.fg_batches WHERE id = ?", BigDecimal.class, fgBatchId))
                .isEqualByComparingTo("47.0000");
        Map<String, BigDecimal> gl = netByAccount(mark);
        assertThat(net(gl, "5202")).as("scrap loss 6 x 40").isEqualByComparingTo("240");
        assertThat(net(gl, "1410")).as("FG 80 x 47").isEqualByComparingTo("3760");
        assertThat(net(gl, "1501")).as("WIP: 4,000 in, 240 scrap + 3,760 FG out").isEqualByComparingTo("0");
        assertEveryJournalBalances(mark);
    }

    /**
     * With a RELEASED estimate the output is received at standard, and the controlling variance journal then relieves
     * the rest, so WIP is relieved exactly once. Conversion rates are zeroed so the numbers stay hand-worked.
     *
     * <pre>
     * standard  0.8 kg x 50 = 40.0000 per piece          -> FG receipt 100 x 40 = 4,000   Dr 1410 / Cr 1501
     * actual    2,000 + 2,400 = 4,400 of material        -> variance 4,400 - 4,000 = 400 unfavourable
     * variance  Dr 5204 400 / Cr 1501 400                                                   WIP: 4,400 - 4,000 - 400 = 0
     * </pre>
     */
    @Test
    @DisplayName("FGC-IT-3: with a released estimate FG is received at standard and the variance journal does not relieve WIP twice")
    void standardCostReceiptAndVarianceDoNotDoubleRelieveWip() {
        List<Map<String, Object>> rates = jdbc.queryForList(
                "SELECT id, labour_rate_per_hour, machine_rate_per_hour, overhead_pct FROM ims.cost_centres");
        jdbc.update("UPDATE ims.cost_centres SET labour_rate_per_hour = 0, machine_rate_per_hour = 0, overhead_pct = 0");
        try {
            long mark = journalMark();
            Produced p = releasedOrderWithTwoReceipts();
            CostEstimateDto estimate = costingService.release(costingService.estimate(p.product().getId(), p.bom().id()).id());
            assertThat(estimate.totalStandardCost()).as("0.8 kg x 50 per piece, no conversion").isEqualByComparingTo("40.0000");

            runEveryOperation(p.order(), "100", "0", "0");
            Long fgBatchId = fgBatchOf(p.order().id());
            assertThat(jdbc.queryForObject("SELECT unit_cost FROM ims.fg_batches WHERE id = ?", BigDecimal.class, fgBatchId))
                    .isEqualByComparingTo("40.0000");
            approveFinalInspection(fgBatchId, "100");
            // A COMPLETED order is enough for the variance; closing also needs every issued kilo consumed or returned,
            // which no screen can do yet (see decisions.md), so the variance is run the way the controlling API runs it.
            varianceService.calculate(p.order().id());

            Map<String, BigDecimal> gl = netByAccount(mark);
            assertThat(net(gl, "1410")).as("FG at standard, 100 x 40").isEqualByComparingTo("4000");
            assertThat(net(gl, "5204")).as("unfavourable variance").isEqualByComparingTo("400");
            assertThat(net(gl, "1501").abs()).as("WIP: 4,400 issued, 4,000 to FG, 400 to variance")
                    .isLessThanOrEqualTo(TOLERANCE);
            assertEveryJournalBalances(mark);
        } finally {
            for (Map<String, Object> r : rates) {
                jdbc.update("""
                        UPDATE ims.cost_centres SET labour_rate_per_hour = ?, machine_rate_per_hour = ?, overhead_pct = ?
                         WHERE id = ?
                        """, r.get("labour_rate_per_hour"), r.get("machine_rate_per_hour"), r.get("overhead_pct"), r.get("id"));
            }
        }
    }

    @Test
    @DisplayName("FGC-IT-4: a zero-value movement of real stock raises a MISSING_JOURNAL alert instead of vanishing")
    void zeroValueMovementIsVisible() {
        Product product = td.product();
        Material material = td.material();
        jdbc.update("UPDATE ims.materials SET standard_cost = 0 WHERE id = ?", material.getId());
        receiveMaterial(material, "10.000", "0.0000");

        Integer alerts = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ims.alerts WHERE alert_type = 'MISSING_JOURNAL' AND resolved_at IS NULL
                   AND message LIKE '%zero-value movement%'
                """, Integer.class);
        assertThat(alerts).isGreaterThanOrEqualTo(1);
        assertThat(product.getId()).isNotNull();
    }
}
