package com.mfg.ims.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.controlling.service.ConversionAbsorptionPoster;
import com.mfg.ims.controlling.service.OrderConversionCostService;
import com.mfg.ims.controlling.service.ProductCostingService;
import com.mfg.ims.controlling.service.ProductionOrderVarianceService;
import com.mfg.ims.engineering.bom.BomDto;
import com.mfg.ims.engineering.bom.BomItemRequest;
import com.mfg.ims.engineering.bom.BomService;
import com.mfg.ims.engineering.bom.CreateBomRequest;
import com.mfg.ims.engineering.routing.RoutingService;
import com.mfg.ims.engineering.routing.SaveRoutingStepRequest;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.procurement.domain.PurchaseOrder;
import com.mfg.ims.procurement.dto.CreateGrRequest;
import com.mfg.ims.procurement.dto.CreatePoRequest;
import com.mfg.ims.procurement.repository.PurchaseOrderRepository;
import com.mfg.ims.procurement.service.GoodsReceiptService;
import com.mfg.ims.procurement.service.PurchaseOrderService;
import com.mfg.ims.product.domain.Product;
import com.mfg.ims.production.dto.CreateIssueRequest;
import com.mfg.ims.production.dto.CreateProductionOrderRequest;
import com.mfg.ims.production.dto.ProductionOperationDto;
import com.mfg.ims.production.dto.ProductionOrderDto;
import com.mfg.ims.production.dto.RecordOutputRequest;
import com.mfg.ims.production.service.MaterialIssueService;
import com.mfg.ims.production.service.OperationExecutionService;
import com.mfg.ims.production.service.ProductionOrderService;
import com.mfg.ims.quality.service.InspectionService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestClock;
import com.mfg.ims.support.TestData;

/**
 * Conversion-cost absorption (Dr 1501 WIP / Cr 5303 Conversion Cost Absorbed) end to end through the real services with
 * no surrounding transaction: the bridge journals and the absorption post after commit. The clock is pinned so every
 * operation takes exactly the hours the scenario says, which makes each figure hand-worked.
 *
 * <p>Common to all scenarios: a spring needs 0.8 kg of wire (50 a kg) = 40 a piece; the order is for 100 pieces, so
 * 80 kg are issued. The routing has three steps of 24 min run + 6 min set-up, so the standard is 0.5 h a step and
 * 1.5 h a piece. Labour is 20 an hour and overhead 10 % of labour, so the standard conversion is 30 + 3 = 33 a piece
 * and the released standard 73 a piece (7,300 for 100). All cost centres carry the same rates.
 */
class ConversionAbsorptionIT extends AbstractIntegrationTest {

    private static final long WAREHOUSE_ID = 1L;
    private static final long LOCATION_ID = 1L;
    private static final Instant START = Instant.parse("2026-10-07T08:00:00Z");

    @Autowired PurchaseOrderService poService;
    @Autowired PurchaseOrderRepository poRepo;
    @Autowired GoodsReceiptService grService;
    @Autowired InspectionService inspectionService;
    @Autowired BomService bomService;
    @Autowired RoutingService routingService;
    @Autowired ProductionOrderService orderService;
    @Autowired MaterialIssueService issueService;
    @Autowired OperationExecutionService executionService;
    @Autowired ProductCostingService costingService;
    @Autowired ProductionOrderVarianceService varianceService;
    @Autowired OrderConversionCostService conversionService;
    @Autowired ConversionAbsorptionPoster poster;
    @Autowired TestClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    private List<Map<String, Object>> savedRates;
    private List<Map<String, Object>> savedPeriods;

    @BeforeEach
    void login() {
        Long userId = jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'Conversion IT', ?, 'x') RETURNING id
                """, Long.class, td.code("conv"), td.code("conv") + "@example.com");
        String[] authorities = { "PURCHASE_CREATE", "PURCHASE_APPROVE", "INVENTORY_RECEIVE", "QUALITY_VIEW",
                "QUALITY_INSPECT", "QUALITY_APPROVE", "PRODUCTION_VIEW", "PRODUCTION_EXECUTE", "PRODUCTION_CREATE",
                "PRODUCTION_CLOSE", "SETTINGS_MANAGE" };
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CachedUser(userId, "u" + userId, true, 1, Set.of(), Set.copyOf(Arrays.asList(authorities))), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
        savedRates = jdbc.queryForList(
                "SELECT id, labour_rate_per_hour, machine_rate_per_hour, overhead_pct FROM ims.cost_centres");
        savedPeriods = null;
        setRates("20", "0", "10");
    }

    @AfterEach
    void cleanUp() {
        clock.release();
        for (Map<String, Object> r : savedRates) {
            jdbc.update("""
                    UPDATE ims.cost_centres SET labour_rate_per_hour = ?, machine_rate_per_hour = ?, overhead_pct = ?
                     WHERE id = ?
                    """, r.get("labour_rate_per_hour"), r.get("machine_rate_per_hour"), r.get("overhead_pct"), r.get("id"));
        }
        restorePeriods();
        SecurityContextHolder.clearContext();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private void setRates(String labour, String machine, String overheadPct) {
        jdbc.update("UPDATE ims.cost_centres SET labour_rate_per_hour = ?, machine_rate_per_hour = ?, overhead_pct = ?",
                new BigDecimal(labour), new BigDecimal(machine), new BigDecimal(overheadPct));
    }

    private void closeCurrentPeriod() {
        savedPeriods = jdbc.queryForList("""
                SELECT id, status FROM ims.fiscal_periods WHERE start_date <= CURRENT_DATE AND end_date >= CURRENT_DATE
                """);
        jdbc.update("""
                UPDATE ims.fiscal_periods SET status = 'CLOSED'
                 WHERE start_date <= CURRENT_DATE AND end_date >= CURRENT_DATE
                """);
    }

    private void restorePeriods() {
        if (savedPeriods == null) return;
        for (Map<String, Object> p : savedPeriods) {
            jdbc.update("UPDATE ims.fiscal_periods SET status = ? WHERE id = ?", p.get("status"), p.get("id"));
        }
        savedPeriods = null;
    }

    private long journalMark() {
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM ims.journal_headers", Long.class);
        return max == null ? 0 : max;
    }

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

    private void assertBooksBalance(long mark) {
        List<Long> unbalanced = jdbc.queryForList("""
                SELECT jh.id FROM ims.journal_headers jh
                  JOIN ims.journal_lines jl ON jl.journal_header_id = jh.id
                 WHERE jh.id > ?
                 GROUP BY jh.id HAVING SUM(jl.debit_amount) <> SUM(jl.credit_amount)
                """, Long.class, mark);
        assertThat(unbalanced).as("journals where Dr <> Cr").isEmpty();
        assertThat(netByAccount(mark).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .as("trial balance").isEqualByComparingTo("0");
    }

    private int absorptionJournals(Long orderId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM ims.journal_headers
                 WHERE source_document_type = 'CONVERSION_ABSORPTION' AND source_document_id = ? AND status = 'POSTED'
                """, Integer.class, orderId);
    }

    private Map<String, Object> conversionRow(Long orderId) {
        return jdbc.queryForMap("SELECT * FROM ims.production_order_conversion_costs WHERE production_order_id = ?", orderId);
    }

    private int openAlerts(Long orderId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM ims.alerts
                 WHERE alert_type = 'MISSING_JOURNAL' AND entity_type = 'PRODUCTION_ORDER' AND entity_id = ?
                   AND resolved_at IS NULL
                """, Integer.class, orderId);
    }

    private BigDecimal fgUnitCost(Long orderId) {
        return jdbc.queryForObject("SELECT unit_cost FROM ims.fg_batches WHERE production_order_id = ?",
                BigDecimal.class, orderId);
    }

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

    private record Run(Product product, Material material, BomDto bom, ProductionOrderDto order) {
    }

    /** 100 kg at 50, a released order for 100 pieces with the routing above, 80 kg issued; optionally a released estimate. */
    private Run releasedOrder(boolean releaseEstimate) {
        Product product = td.product();
        Material material = td.material();
        jdbc.update("UPDATE ims.products SET standard_cost = 73.0000 WHERE id = ?", product.getId());
        jdbc.update("UPDATE ims.materials SET standard_cost = 50.0000 WHERE id = ?", material.getId());
        receiveMaterial(material, "100.000", "50.0000");
        List<Long> masterOps = jdbc.queryForList("SELECT id FROM ims.operations ORDER BY id LIMIT 3", Long.class);
        routingService.saveSteps(product.getId(), List.of(
                new SaveRoutingStepRequest(10, masterOps.get(0), null, new BigDecimal("24"), new BigDecimal("6"), false),
                new SaveRoutingStepRequest(20, masterOps.get(1), null, new BigDecimal("24"), new BigDecimal("6"), false),
                new SaveRoutingStepRequest(30, masterOps.get(2), null, new BigDecimal("24"), new BigDecimal("6"), true)));
        BomDto bom = bomService.create(new CreateBomRequest(product.getId(), BigDecimal.ONE, "Conversion IT BOM",
                List.of(new BomItemRequest(10, "MATERIAL", material.getId(), null,
                        new BigDecimal("0.8"), "KG", BigDecimal.ZERO, "wire"))));
        bom = bomService.activate(bomService.approve(bomService.submitForReview(bom.id()).id()).id());
        if (releaseEstimate) {
            costingService.release(costingService.estimate(product.getId(), bom.id()).id());
        }
        ProductionOrderDto order = orderService.create(new CreateProductionOrderRequest(product.getId(), bom.id(),
                new BigDecimal("100"), LocalDate.now(), LocalDate.now().plusDays(3), "LINE-A", null, null, null,
                (short) 2, "Conversion IT"));
        order = orderService.release(orderService.approve(order.id()).id());
        issueService.issue(new CreateIssueRequest(order.id(), WAREHOUSE_ID,
                List.of(new CreateIssueRequest.IssueLineRequest(material.getId(), new BigDecimal("80.000"), null)),
                "Conversion IT issue"));
        return new Run(product, material, bom, orderService.findById(order.id()));
    }

    /** Every operation takes {@code hoursEach} hours on the pinned clock; {@code beforeLast} runs before the last one. */
    private void runOperations(ProductionOrderDto order, String good, String scrap, long hoursEach, Runnable beforeLast) {
        List<ProductionOperationDto> ops = order.operations().stream()
                .sorted(Comparator.comparing(ProductionOperationDto::sequenceNo)).toList();
        Instant t = START;
        try {
            for (int i = 0; i < ops.size(); i++) {
                if (i == ops.size() - 1 && beforeLast != null) {
                    beforeLast.run();
                }
                Long id = ops.get(i).id();
                clock.pin(t);
                executionService.startOperation(id, null);
                executionService.recordOutput(i == 0
                        ? new RecordOutputRequest(id, new BigDecimal(good), BigDecimal.ZERO, new BigDecimal(scrap),
                                null, null, null, null, "A", "conv")
                        : new RecordOutputRequest(id, new BigDecimal(good), BigDecimal.ZERO, BigDecimal.ZERO,
                                null, null, null, null, "A", "conv"));
                t = t.plus(Duration.ofHours(hoursEach));
                clock.pin(t);
                executionService.completeOperation(id);
            }
        } finally {
            clock.release();
        }
    }

    // ---- scenarios ------------------------------------------------------------------------------------------------

    /**
     * Each of the three operations takes 50 h, which is exactly the standard (0.5 h x 100 pieces).
     *
     * <pre>
     * issue       80 kg x 50 = 4,000                           Dr 1501 / Cr 1401
     * absorbed    150 h x 20 = 3,000 labour + 10 % = 300       Dr 1501 3,300 / Cr 5303 3,300
     * FG          100 x 73.0000 (released standard) = 7,300    Dr 1410 / Cr 1501
     * variance    actual 4,000 + 3,300 = 7,300 - standard 7,300 = 0, no journal
     * WIP         4,000 + 3,300 - 7,300 = 0
     * </pre>
     */
    @Test
    @DisplayName("CA-IT-1: actual conversion equal to standard leaves WIP at zero and no variance journal")
    void actualEqualsStandard() {
        long mark = journalMark();
        Run run = releasedOrder(true);
        Long orderId = run.order().id();

        runOperations(run.order(), "100", "0", 50, null);

        assertThat(fgUnitCost(orderId)).as("FG at the released standard").isEqualByComparingTo("73.0000");
        Map<String, Object> row = conversionRow(orderId);
        assertThat(row.get("status")).isEqualTo("POSTED");
        assertThat((BigDecimal) row.get("actual_hours")).isEqualByComparingTo("150");
        assertThat((BigDecimal) row.get("labour_cost")).isEqualByComparingTo("3000");
        assertThat((BigDecimal) row.get("overhead_cost")).isEqualByComparingTo("300");
        assertThat((BigDecimal) row.get("total_cost")).isEqualByComparingTo("3300");
        assertThat(row.get("journal_header_id")).isNotNull();
        assertThat(absorptionJournals(orderId)).isEqualTo(1);

        orderService.close(orderId, null);

        assertThat(jdbc.queryForObject(
                "SELECT status FROM ims.production_order_variances WHERE production_order_id = ?", String.class, orderId))
                .isEqualTo("NO_VARIANCE");
        Map<String, BigDecimal> gl = netByAccount(mark);
        assertThat(net(gl, "5303")).as("conversion absorbed (credit)").isEqualByComparingTo("-3300");
        assertThat(net(gl, "1410")).isEqualByComparingTo("7300");
        assertThat(net(gl, "5204")).as("no variance").isEqualByComparingTo("0");
        assertThat(net(gl, "1501")).as("WIP: 4,000 + 3,300 - 7,300").isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("""
                SELECT COALESCE(SUM(qty_on_hand * avg_unit_cost), 0) FROM ims.inventory_balances
                 WHERE item_type = 'PRODUCT' AND product_id = ?
                """, BigDecimal.class, run.product().getId())).as("ledger value of the FG").isEqualByComparingTo("7300");
        assertBooksBalance(mark);
    }

    /**
     * Each operation takes 60 h, 30 h over the standard in all.
     *
     * <pre>
     * absorbed    180 h x 20 = 3,600 labour + 360 overhead = 3,960      Dr 1501 / Cr 5303
     * FG          7,300 (standard)
     * variance    actual 4,000 + 3,960 = 7,960 - 7,300 = 660     Dr 5204 / Cr 1501 660
     *             efficiency (180 - 150) h x 20 = 600, other (overhead on the 30 h) 60
     * WIP         4,000 + 3,960 - 7,300 - 660 = 0
     * </pre>
     */
    @Test
    @DisplayName("CA-IT-2: hours above standard are absorbed at actual and the difference goes through the variance")
    void hoursAboveStandard() {
        long mark = journalMark();
        Run run = releasedOrder(true);
        Long orderId = run.order().id();

        runOperations(run.order(), "100", "0", 60, null);
        orderService.close(orderId, null);

        assertThat((BigDecimal) conversionRow(orderId).get("total_cost")).isEqualByComparingTo("3960");
        Map<String, Object> variance = jdbc.queryForMap(
                "SELECT * FROM ims.production_order_variances WHERE production_order_id = ?", orderId);
        assertThat(variance.get("status")).isEqualTo("POSTED");
        assertThat((BigDecimal) variance.get("actual_cost")).isEqualByComparingTo("7960");
        assertThat((BigDecimal) variance.get("total_variance")).isEqualByComparingTo("660");
        assertThat((BigDecimal) variance.get("efficiency_variance")).isEqualByComparingTo("600");
        assertThat((BigDecimal) variance.get("other_variance")).isEqualByComparingTo("60");
        Map<String, BigDecimal> gl = netByAccount(mark);
        assertThat(net(gl, "5303")).isEqualByComparingTo("-3960");
        assertThat(net(gl, "5204")).isEqualByComparingTo("660");
        assertThat(net(gl, "1410")).isEqualByComparingTo("7300");
        assertThat(net(gl, "1501")).as("WIP: 4,000 + 3,960 - 7,300 - 660").isEqualByComparingTo("0");
        assertBooksBalance(mark);
    }

    /**
     * 94 good and 6 scrap, each operation 50 h; the product standard is 73 so the scrap is relieved at 6 x 73.
     *
     * <pre>
     * issue       4,000 (80 kg consumed by the 100 pieces)         absorbed 3,300
     * FG          94 x 73 = 6,862                                  scrap 6 x 73 = 438   Dr 5202 / Cr 1501
     * variance    actual 4,000 + 3,300 = 7,300 - 6,862 = 438, of which scrap 438 is already charged -> journal 0
     * WIP         4,000 + 3,300 - 6,862 - 438 = 0
     * </pre>
     */
    @Test
    @DisplayName("CA-IT-3: scrap and conversion cost are each charged once")
    void scrapAndConversion() {
        long mark = journalMark();
        Run run = releasedOrder(true);
        Long orderId = run.order().id();

        runOperations(run.order(), "94", "6", 50, null);
        orderService.close(orderId, null);

        Map<String, Object> variance = jdbc.queryForMap(
                "SELECT * FROM ims.production_order_variances WHERE production_order_id = ?", orderId);
        assertThat(variance.get("status")).isEqualTo("NO_VARIANCE");
        assertThat((BigDecimal) variance.get("total_variance")).isEqualByComparingTo("438");
        assertThat((BigDecimal) variance.get("scrap_relieved")).isEqualByComparingTo("438");
        Map<String, BigDecimal> gl = netByAccount(mark);
        assertThat(net(gl, "5202")).as("scrap loss").isEqualByComparingTo("438");
        assertThat(net(gl, "5204")).as("no second charge").isEqualByComparingTo("0");
        assertThat(net(gl, "5303")).isEqualByComparingTo("-3300");
        assertThat(net(gl, "1410")).isEqualByComparingTo("6862");
        assertThat(net(gl, "1501")).as("WIP").isEqualByComparingTo("0");
        assertBooksBalance(mark);
    }

    /**
     * No released estimate: FG is received at the actual cost, material plus the conversion absorbed.
     *
     * <pre>
     * FG   (4,000 material + 3,300 conversion) / 100 = 73.0000 each      WIP 4,000 + 3,300 - 7,300 = 0
     * </pre>
     */
    @Test
    @DisplayName("CA-IT-4: without a released estimate the FG cost is actual material plus absorbed conversion")
    void noEstimate() {
        long mark = journalMark();
        Run run = releasedOrder(false);
        Long orderId = run.order().id();

        runOperations(run.order(), "100", "0", 50, null);
        orderService.close(orderId, null);

        assertThat(fgUnitCost(orderId)).isEqualByComparingTo("73.0000");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM ims.production_order_variances WHERE production_order_id = ?", String.class, orderId))
                .isEqualTo("NO_ESTIMATE");
        Map<String, BigDecimal> gl = netByAccount(mark);
        assertThat(net(gl, "5303")).isEqualByComparingTo("-3300");
        assertThat(net(gl, "1410")).isEqualByComparingTo("7300");
        assertThat(net(gl, "1501")).as("WIP").isEqualByComparingTo("0");
        assertBooksBalance(mark);
    }

    @Test
    @DisplayName("CA-IT-5: with no cost centre rate nothing is absorbed, no journal and no alert, and the standard is material only")
    void zeroRates() {
        setRates("0", "0", "0");
        long mark = journalMark();
        Run run = releasedOrder(true);
        Long orderId = run.order().id();

        runOperations(run.order(), "100", "0", 50, null);
        orderService.close(orderId, null);

        Map<String, Object> row = conversionRow(orderId);
        assertThat(row.get("status")).isEqualTo("NOT_REQUIRED");
        assertThat((BigDecimal) row.get("total_cost")).isEqualByComparingTo("0");
        assertThat(absorptionJournals(orderId)).isZero();
        assertThat(openAlerts(orderId)).isZero();
        assertThat(fgUnitCost(orderId)).as("material only: 0.8 x 50").isEqualByComparingTo("40.0000");
        Map<String, BigDecimal> gl = netByAccount(mark);
        assertThat(net(gl, "5303")).isEqualByComparingTo("0");
        assertThat(net(gl, "1501")).as("WIP").isEqualByComparingTo("0");
        assertBooksBalance(mark);
    }

    @Test
    @DisplayName("CA-IT-6: the absorption posts once however often it is retried, and a later rate change moves nothing")
    void idempotent() {
        long mark = journalMark();
        Run run = releasedOrder(true);
        Long orderId = run.order().id();
        runOperations(run.order(), "100", "0", 50, null);
        assertThat(absorptionJournals(orderId)).isEqualTo(1);

        setRates("99", "99", "99");
        assertThat(conversionService.freezeConversionCost(orderId)).as("fixed when the order completed")
                .isEqualByComparingTo("3300");
        assertThat(poster.postAndReport(orderId)).as("nothing left to post").isFalse();
        orderService.close(orderId, null);
        varianceService.calculate(orderId);
        varianceService.calculate(orderId);

        assertThat(absorptionJournals(orderId)).isEqualTo(1);
        assertThat(netByAccount(mark).get("5303")).isEqualByComparingTo("-3300");
        assertThat((BigDecimal) conversionRow(orderId).get("total_cost")).isEqualByComparingTo("3300");
        assertThat(net(netByAccount(mark), "1501")).as("WIP").isEqualByComparingTo("0");
        assertBooksBalance(mark);
    }

    /**
     * The fiscal period is closed just before the last operation completes: stock and the order are not blocked,
     * the absorption is recorded as failed with an alert, and it is posted when the period is open again.
     */
    @Test
    @DisplayName("CA-IT-7: a closed fiscal period never blocks completion; the failure is visible and is retried")
    void closedPeriodDoesNotBlock() {
        Run run = releasedOrder(false);
        Long orderId = run.order().id();

        runOperations(run.order(), "100", "0", 50, this::closeCurrentPeriod);

        assertThat(orderService.findById(orderId).status()).as("completion not blocked").isEqualTo("COMPLETED");
        assertThat(fgUnitCost(orderId)).as("FG still received at cost").isEqualByComparingTo("73.0000");
        Map<String, Object> failed = conversionRow(orderId);
        assertThat(failed.get("status")).isEqualTo("FAILED");
        assertThat((String) failed.get("failure_reason")).isNotBlank();
        assertThat(absorptionJournals(orderId)).isZero();
        assertThat(openAlerts(orderId)).as("MISSING_JOURNAL alert on the order").isEqualTo(1);

        restorePeriods();
        long mark = journalMark();
        varianceService.calculate(orderId);

        assertThat(conversionRow(orderId).get("status")).isEqualTo("POSTED");
        assertThat(absorptionJournals(orderId)).isEqualTo(1);
        assertThat(openAlerts(orderId)).as("alert resolved once posted").isZero();
        assertThat(net(netByAccount(mark), "5303")).isEqualByComparingTo("-3300");
        assertBooksBalance(mark);
    }
}
