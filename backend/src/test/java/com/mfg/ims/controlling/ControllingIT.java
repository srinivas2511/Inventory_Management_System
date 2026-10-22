package com.mfg.ims.controlling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.controlling.dto.CostCentreDto;
import com.mfg.ims.controlling.dto.CostEstimateDto;
import com.mfg.ims.controlling.dto.SaveCostCentreBudgetRequest;
import com.mfg.ims.controlling.dto.SaveCostCentreRequest;
import com.mfg.ims.controlling.dto.VarianceDto;
import com.mfg.ims.controlling.repository.CostCentreRepository;
import com.mfg.ims.controlling.repository.ProductionOrderVarianceRepository;
import com.mfg.ims.controlling.service.CostCentreService;
import com.mfg.ims.controlling.service.ProductCostingService;
import com.mfg.ims.controlling.service.ProductionOrderVarianceService;
import com.mfg.ims.finance.gl.dto.JournalHeaderDto;
import com.mfg.ims.finance.gl.dto.JournalLineDto;
import com.mfg.ims.finance.gl.repository.FiscalPeriodRepository;
import com.mfg.ims.finance.gl.repository.GlAccountRepository;
import com.mfg.ims.finance.gl.service.JournalService;
import com.mfg.ims.insight.report.ReportRequest;
import com.mfg.ims.insight.report.ReportResult;
import com.mfg.ims.insight.report.ReportService;
import com.mfg.ims.production.service.ProductionOrderService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;
import com.mfg.ims.material.repository.MaterialRepository;

/**
 * Integration tests for Release 10 - Controlling (R10-5.1). Fixtures are committed through JDBC because the
 * variance service runs in its own transaction and must see them. Every fixture uses unique codes.
 *
 * Standard cost fixture (lot of 100 pcs): material 670 (4.5 kg @100 + 10 kg +10% scrap @20), labour 375
 * (1.5 h @250), machine 400 (1 h @400), overhead 77.5 (10% of labour+machine), total 1522.5.
 */
class ControllingIT extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired JdbcTemplate jdbc;
    @Autowired TestData testData;
    @Autowired MaterialRepository materialRepo;
    @Autowired ProductCostingService costingService;
    @Autowired ProductionOrderVarianceService varianceService;
    @Autowired ProductionOrderService orderService;
    @Autowired CostCentreService costCentreService;
    @Autowired JournalService journalService;
    @Autowired ReportService reportService;
    @Autowired GlAccountRepository glAccountRepo;
    @Autowired FiscalPeriodRepository periodRepo;
    @Autowired CostCentreRepository costCentreRepo;
    @Autowired ProductionOrderVarianceRepository varianceRepo;

    record Fixture(long productId, long bomId, long material1, long material2, long machineId) {}

    // ── fixtures ──────────────────────────────────────────────────────────────

    private static String uniq() {
        return String.format("%09d%02d", System.nanoTime() % 1_000_000_000L, SEQ.incrementAndGet() % 100);
    }

    private long insertReturningId(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long costedMaterial(String standardCost) {
        var m = testData.material();
        m.setStandardCost(new BigDecimal(standardCost));
        return materialRepo.save(m).getId();
    }

    private Fixture fixture(String bomStatus) {
        String u = uniq();
        long product = testData.product().getId();
        long m1 = costedMaterial("100.0000");
        long m2 = costedMaterial("20.0000");
        long bom = insertReturningId("INSERT INTO ims.bom (bom_number, revision, product_id, status, base_quantity) "
                + "VALUES (?, 'A', ?, ?, 100) RETURNING id", "BOM-IT-" + u, product, bomStatus);
        jdbc.update("INSERT INTO ims.bom_items (bom_id, line_no, item_kind, material_id, required_qty, uom, scrap_pct) "
                + "VALUES (?, 1, 'MATERIAL', ?, 4.5, 'KG', 0)", bom, m1);
        jdbc.update("INSERT INTO ims.bom_items (bom_id, line_no, item_kind, material_id, required_qty, uom, scrap_pct) "
                + "VALUES (?, 2, 'MATERIAL', ?, 10, 'KG', 10)", bom, m2);

        long machine = insertReturningId("INSERT INTO ims.machines (machine_code, name) VALUES (?, ?) RETURNING id",
                "MIT-" + u, "IT Coiler " + u);
        long coiling = insertReturningId("SELECT id FROM ims.operations WHERE operation_code = 'COILING'");
        long cutting = insertReturningId("SELECT id FROM ims.operations WHERE operation_code = 'CUTTING'");
        jdbc.update("INSERT INTO ims.product_routing (product_id, sequence_no, operation_id, machine_id, "
                + "standard_time_min, setup_time_min) VALUES (?, 1, ?, ?, 0.6, 0)", product, coiling, machine);
        jdbc.update("INSERT INTO ims.product_routing (product_id, sequence_no, operation_id, standard_time_min, "
                + "setup_time_min) VALUES (?, 2, ?, 0.3, 0)", product, cutting);
        return new Fixture(product, bom, m1, m2, machine);
    }

    private long completedOrder(Fixture fx, String status, String m1Qty, String m1Cost, String m2Qty, String m2Cost,
                                int op1Minutes, int op2Minutes) {
        String u = uniq();
        long order = insertReturningId("INSERT INTO ims.production_orders (order_number, product_id, bom_id, "
                + "planned_qty, good_qty, status) VALUES (?, ?, ?, 100, 100, ?) RETURNING id",
                "PO-IT-" + u, fx.productId(), fx.bomId(), status);
        long issue = insertReturningId("INSERT INTO ims.material_issues (issue_number, production_order_id, issued_by) "
                + "VALUES (?, ?, 1) RETURNING id", "MI-IT-" + u, order);
        issueMaterial(order, issue, fx.material1(), u + "A", m1Qty, m1Cost);
        issueMaterial(order, issue, fx.material2(), u + "B", m2Qty, m2Cost);

        Instant start = Instant.parse("2026-10-01T08:00:00Z");
        jdbc.update("INSERT INTO ims.production_operations (production_order_id, sequence_no, operation_id, machine_id, "
                + "status, started_at, completed_at) VALUES (?, 1, (SELECT id FROM ims.operations WHERE operation_code = 'COILING'), "
                + "?, 'COMPLETED', ?, ?)", order, fx.machineId(), Timestamp.from(start),
                Timestamp.from(start.plusSeconds(op1Minutes * 60L)));
        jdbc.update("INSERT INTO ims.production_operations (production_order_id, sequence_no, operation_id, "
                + "status, started_at, completed_at) VALUES (?, 2, (SELECT id FROM ims.operations WHERE operation_code = 'CUTTING'), "
                + "'COMPLETED', ?, ?)", order, Timestamp.from(start), Timestamp.from(start.plusSeconds(op2Minutes * 60L)));
        return order;
    }

    private void issueMaterial(long order, long issue, long material, String batchSuffix, String qty, String unitCost) {
        long batch = insertReturningId("INSERT INTO ims.material_batches (batch_number, material_id, received_date, "
                + "received_qty, remaining_qty, unit_cost, status) VALUES (?, ?, CURRENT_DATE, 1000, 1000, ?, 'AVAILABLE') "
                + "RETURNING id", "B-IT-" + batchSuffix, material, new BigDecimal(unitCost));
        jdbc.update("INSERT INTO ims.material_issue_lines (material_issue_id, material_id, material_batch_id, "
                + "from_location_id, to_location_id, quantity, unit_cost) VALUES (?, ?, ?, 1, 1, ?, ?)",
                issue, material, batch, new BigDecimal(qty), new BigDecimal(unitCost));
        jdbc.update("INSERT INTO ims.production_order_materials (production_order_id, material_id, planned_qty, "
                + "issued_qty, consumed_qty, uom) VALUES (?, ?, ?, ?, ?, 'KG')",
                order, material, new BigDecimal(qty), new BigDecimal(qty), new BigDecimal(qty));
    }

    private CostEstimateDto releasedEstimate(Fixture fx) {
        CostEstimateDto est = costingService.estimate(fx.productId(), fx.bomId());
        return costingService.release(est.id());
    }

    private static BigDecimal num(Object o) {
        return new BigDecimal(String.valueOf(o));
    }

    // ── R10-2: product costing ────────────────────────────────────────────────

    @Test
    @DisplayName("Cost estimate equals BOM material + routing labour/machine + overhead")
    void estimateMatchesBom() {
        Fixture fx = fixture("ACTIVE");

        CostEstimateDto est = costingService.estimate(fx.productId(), fx.bomId());

        assertThat(est.status()).isEqualTo("DRAFT");
        assertThat(est.costLotSize()).isEqualByComparingTo("100");
        assertThat(est.materialCost()).isEqualByComparingTo("670");
        assertThat(est.labourCost()).isEqualByComparingTo("375");
        assertThat(est.machineCost()).isEqualByComparingTo("400");
        assertThat(est.overheadCost()).isEqualByComparingTo("77.5");
        assertThat(est.totalStandardCost()).isEqualByComparingTo("1522.5");
        assertThat(est.unitStandardCost()).isEqualByComparingTo("15.225");
        assertThat(est.lines()).hasSize(6);
        assertThat(est.lines().stream().filter(l -> "MATERIAL".equals(l.costElement()))
                .map(l -> l.amount()).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("670");
        assertThat(est.allowedActions()).containsExactly("release");
    }

    @Test
    @DisplayName("Releasing an estimate makes the previous released one HISTORICAL; DRAFT only can be released")
    void releaseWorkflow() {
        Fixture fx = fixture("ACTIVE");
        CostEstimateDto first = releasedEstimate(fx);
        assertThat(first.status()).isEqualTo("RELEASED");
        assertThat(first.validFrom()).isEqualTo(LocalDate.now());

        CostEstimateDto second = costingService.estimate(fx.productId(), fx.bomId());
        CostEstimateDto secondReleased = costingService.release(second.id());

        assertThat(secondReleased.status()).isEqualTo("RELEASED");
        CostEstimateDto firstNow = costingService.findById(first.id());
        assertThat(firstNow.status()).isEqualTo("HISTORICAL");
        assertThat(firstNow.validTo()).isEqualTo(LocalDate.now());

        assertThatThrownBy(() -> costingService.release(second.id()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));
    }

    @Test
    @DisplayName("Estimating requires an APPROVED/ACTIVE BOM that belongs to the product")
    void estimateGuards() {
        Fixture draft = fixture("DRAFT");
        assertThatThrownBy(() -> costingService.estimate(draft.productId(), draft.bomId()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.BOM_NOT_ACTIVE));

        Fixture a = fixture("ACTIVE");
        Fixture b = fixture("ACTIVE");
        assertThatThrownBy(() -> costingService.estimate(a.productId(), b.bomId()))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    // ── R10-3: variance ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Unfavourable variance splits into price/quantity/efficiency/other and posts Dr 5204 / Cr 1501")
    void unfavourableVarianceIsPosted() {
        Fixture fx = fixture("ACTIVE");
        releasedEstimate(fx);
        // 5.0 kg @105 and 12 kg @20 vs standard 4.5 kg @100 and 11 kg @20; 1.0 h + 0.6 h vs 1.5 h standard
        long order = completedOrder(fx, "COMPLETED", "5.000", "105.0000", "12.000", "20.0000", 60, 36);

        VarianceDto v = varianceService.calculate(order);

        assertThat(v.status()).isEqualTo("POSTED");
        assertThat(v.standardCost()).isEqualByComparingTo("1522.5");
        assertThat(v.actualMaterialCost()).isEqualByComparingTo("765");
        assertThat(v.actualLabourCost()).isEqualByComparingTo("400");
        assertThat(v.actualMachineCost()).isEqualByComparingTo("400");
        assertThat(v.actualOverheadCost()).isEqualByComparingTo("80");
        assertThat(v.actualCost()).isEqualByComparingTo("1645");
        assertThat(v.priceVariance()).isEqualByComparingTo("25");
        assertThat(v.quantityVariance()).isEqualByComparingTo("70");
        assertThat(v.efficiencyVariance()).isEqualByComparingTo("25");
        assertThat(v.otherVariance()).isEqualByComparingTo("2.5");
        assertThat(v.totalVariance()).isEqualByComparingTo("122.5");
        assertThat(v.variancePct()).isEqualByComparingTo("8.05");
        assertThat(v.journalHeaderId()).isNotNull();

        JournalHeaderDto journal = journalService.findById(v.journalHeaderId());
        assertThat(journal.status()).isEqualTo("POSTED");
        assertThat(journal.sourceModule()).isEqualTo("CONTROLLING");
        assertThat(journal.sourceDocumentType()).isEqualTo("PRODUCTION_VARIANCE");
        assertThat(journal.sourceDocumentId()).isEqualTo(order);
        JournalLineDto dr = journal.lines().stream().filter(l -> l.debitAmount().signum() > 0).findFirst().orElseThrow();
        JournalLineDto cr = journal.lines().stream().filter(l -> l.creditAmount().signum() > 0).findFirst().orElseThrow();
        assertThat(dr.accountCode()).isEqualTo("5204");
        assertThat(cr.accountCode()).isEqualTo("1501");
        assertThat(dr.debitAmount()).isEqualByComparingTo("122.5");
        assertThat(cr.creditAmount()).isEqualByComparingTo("122.5");
        long prodCostCentre = costCentreRepo.findByCostCentreCode("CC-PROD-01").orElseThrow().getId();
        assertThat(dr.costCentreId()).isEqualTo(prodCostCentre);
        assertThat(cr.costCentreId()).isEqualTo(prodCostCentre);
        assertThat(dr.profitCentreId()).isNotNull();
    }

    @Test
    @DisplayName("Favourable variance posts Dr 1501 / Cr 4101")
    void favourableVarianceIsPosted() {
        Fixture fx = fixture("ACTIVE");
        releasedEstimate(fx);
        // 4.0 kg @100 and 10 kg @20 (less than standard), same operation hours as standard
        long order = completedOrder(fx, "COMPLETED", "4.000", "100.0000", "10.000", "20.0000", 60, 30);

        VarianceDto v = varianceService.calculate(order);

        assertThat(v.status()).isEqualTo("POSTED");
        assertThat(v.totalVariance()).isEqualByComparingTo("-70");
        assertThat(v.quantityVariance()).isEqualByComparingTo("-70");
        JournalHeaderDto journal = journalService.findById(v.journalHeaderId());
        JournalLineDto dr = journal.lines().stream().filter(l -> l.debitAmount().signum() > 0).findFirst().orElseThrow();
        JournalLineDto cr = journal.lines().stream().filter(l -> l.creditAmount().signum() > 0).findFirst().orElseThrow();
        assertThat(dr.accountCode()).isEqualTo("1501");
        assertThat(cr.accountCode()).isEqualTo("4101");
        assertThat(dr.debitAmount()).isEqualByComparingTo("70");
    }

    @Test
    @DisplayName("Calculating twice does not post a second journal")
    void calculateIsIdempotentOnceJournalled() {
        Fixture fx = fixture("ACTIVE");
        releasedEstimate(fx);
        long order = completedOrder(fx, "COMPLETED", "5.000", "105.0000", "12.000", "20.0000", 60, 36);

        VarianceDto first = varianceService.calculate(order);
        VarianceDto second = varianceService.calculate(order);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.journalHeaderId()).isEqualTo(first.journalHeaderId());
        Integer journals = jdbc.queryForObject("SELECT count(*) FROM ims.journal_headers "
                + "WHERE source_document_type = 'PRODUCTION_VARIANCE' AND source_document_id = ?", Integer.class, order);
        assertThat(journals).isEqualTo(1);
    }

    @Test
    @DisplayName("Without a released estimate the variance is recorded as NO_ESTIMATE and nothing is posted")
    void noEstimateMeansNoJournal() {
        Fixture fx = fixture("ACTIVE");
        long order = completedOrder(fx, "COMPLETED", "5.000", "105.0000", "12.000", "20.0000", 60, 36);

        VarianceDto v = varianceService.calculate(order);

        assertThat(v.status()).isEqualTo("NO_ESTIMATE");
        assertThat(v.journalHeaderId()).isNull();
        assertThat(v.actualMaterialCost()).isEqualByComparingTo("765");
        assertThat(v.totalVariance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Variance can only be calculated for COMPLETED or CLOSED orders")
    void variesOnlyForFinishedOrders() {
        Fixture fx = fixture("ACTIVE");
        releasedEstimate(fx);
        long order = completedOrder(fx, "IN_PROGRESS", "5.000", "105.0000", "12.000", "20.0000", 60, 36);

        assertThatThrownBy(() -> varianceService.calculate(order))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));
    }

    @Test
    @DisplayName("Closing a production order posts its variance journal after commit")
    void closingOrderPostsVarianceJournal() {
        Fixture fx = fixture("ACTIVE");
        releasedEstimate(fx);
        long order = completedOrder(fx, "COMPLETED", "5.000", "105.0000", "12.000", "20.0000", 60, 36);

        orderService.close(order, null);

        var variance = varianceRepo.findByProductionOrderId(order).orElseThrow();
        assertThat(variance.getStatus()).isEqualTo("POSTED");
        assertThat(variance.getJournalHeaderId()).isNotNull();
        assertThat(variance.getTotalVariance()).isEqualByComparingTo("122.5");
    }

    // ── R10-4: reports ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Posted variance flows into cost centre, product cost and profit centre reports")
    void varianceFlowsIntoReports() {
        long costCentreId = costCentreRepo.findByCostCentreCode("CC-PROD-01").orElseThrow().getId();
        long expenseAccount = glAccountRepo.findByAccountCode("5204").orElseThrow().getId();
        long period = periodRepo.findOpenPeriodForDate(LocalDate.now()).orElseThrow().getId();
        costCentreService.saveBudget(costCentreId, new SaveCostCentreBudgetRequest(expenseAccount, period, new BigDecimal("1000")));

        BigDecimal netBefore = costCentreNetActual(costCentreId);
        BigDecimal profitVarianceBefore = profitCentreVariance();

        Fixture fx = fixture("ACTIVE");
        releasedEstimate(fx);
        long order = completedOrder(fx, "COMPLETED", "5.000", "105.0000", "12.000", "20.0000", 60, 36);
        varianceService.calculate(order);

        ReportResult byProduct = reportService.run("CTR_COST_BY_PRODUCT",
                new ReportRequest(null, null, fx.productId(), null, 0, 100));
        assertThat(byProduct.rows()).hasSize(1);
        Map<String, Object> row = byProduct.rows().get(0);
        assertThat(num(row.get("Orders"))).isEqualByComparingTo("1");
        assertThat(num(row.get("Actual Cost"))).isEqualByComparingTo("1645");
        assertThat(num(row.get("Standard Cost"))).isEqualByComparingTo("1522.5");
        assertThat(num(row.get("Variance"))).isEqualByComparingTo("122.5");

        assertThat(costCentreNetActual(costCentreId).subtract(netBefore)).isEqualByComparingTo("122.5");
        ReportResult actuals = reportService.run("CTR_COST_CENTRE_ACTUALS",
                new ReportRequest(null, null, costCentreId, null, 0, 100));
        Map<String, Object> periodRow = actuals.rows().stream()
                .filter(r -> num(r.get("Budget")).compareTo(new BigDecimal("1000")) == 0)
                .findFirst().orElseThrow();
        assertThat(num(periodRow.get("Over/(Under) Budget")))
                .isEqualByComparingTo(num(periodRow.get("Net Actual")).subtract(new BigDecimal("1000")));

        assertThat(profitCentreVariance().subtract(profitVarianceBefore)).isEqualByComparingTo("122.5");
    }

    private BigDecimal costCentreNetActual(long costCentreId) {
        ReportResult r = reportService.run("CTR_COST_CENTRE_ACTUALS", new ReportRequest(null, null, costCentreId, null, 0, 500));
        return r.rows().stream().map(x -> num(x.get("Net Actual"))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal profitCentreVariance() {
        ReportResult r = reportService.run("CTR_PROFIT_CENTRE_MARGIN", new ReportRequest(null, null, null, null, 0, 100));
        return r.rows().stream()
                .filter(x -> String.valueOf(x.get("Profit Centre")).startsWith("PC-SPRINGS"))
                .map(x -> num(x.get("Net Production Variance")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── R10-1: cost centres ───────────────────────────────────────────────────

    @Test
    @DisplayName("Cost centre codes are unique, the hierarchy cannot loop and parents with active children stay active")
    void costCentreRules() {
        String u = uniq();
        CostCentreDto parent = costCentreService.create(request("CC-" + u + "A", null));
        CostCentreDto child = costCentreService.create(request("CC-" + u + "B", parent.id()));

        assertThatThrownBy(() -> costCentreService.create(request("CC-" + u + "A", null)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.DUPLICATE_KEY));

        assertThatThrownBy(() -> costCentreService.update(parent.id(), request("CC-" + u + "A", child.id())))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

        assertThatThrownBy(() -> costCentreService.setActive(parent.id(), false))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.DEACTIVATION_BLOCKED));

        costCentreService.setActive(child.id(), false);
        assertThat(costCentreService.setActive(parent.id(), false).active()).isFalse();
    }

    @Test
    @DisplayName("Saving a cost centre budget twice for the same account and period updates one row")
    void budgetUpsert() {
        CostCentreDto cc = costCentreService.create(request("CC-" + uniq(), null));
        long account = glAccountRepo.findByAccountCode("5201").orElseThrow().getId();
        long period = periodRepo.findOpenPeriodForDate(LocalDate.now()).orElseThrow().getId();
        int fiscalYear = periodRepo.findById(period).orElseThrow().getFiscalYear();

        costCentreService.saveBudget(cc.id(), new SaveCostCentreBudgetRequest(account, period, new BigDecimal("500")));
        costCentreService.saveBudget(cc.id(), new SaveCostCentreBudgetRequest(account, period, new BigDecimal("750")));

        var budgets = costCentreService.findBudgets(cc.id(), fiscalYear);
        assertThat(budgets).hasSize(1);
        assertThat(budgets.get(0).budgetedAmount()).isEqualByComparingTo("750");
    }

    @Test
    @DisplayName("Controlling permissions are seeded and the CONTROLLER role holds them")
    void permissionsSeeded() {
        List<String> codes = jdbc.queryForList("SELECT p.code FROM ims.role_permissions rp "
                + "JOIN ims.roles r ON r.id = rp.role_id JOIN ims.permissions p ON p.id = rp.permission_id "
                + "WHERE r.code = 'CONTROLLER'", String.class);
        assertThat(codes).contains("CONTROLLING_VIEW", "CONTROLLING_MANAGE", "COST_ESTIMATE_RELEASE", "REPORT_VIEW");
    }

    private SaveCostCentreRequest request(String code, Long parentId) {
        return new SaveCostCentreRequest(code, "IT " + code, parentId, null, null,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO);
    }
}
