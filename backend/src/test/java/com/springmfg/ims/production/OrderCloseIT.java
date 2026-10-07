package com.springmfg.ims.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.settings.SystemSettingService;
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
import com.springmfg.ims.production.dto.ReturnMaterialRequest;
import com.springmfg.ims.production.service.MaterialIssueService;
import com.springmfg.ims.production.service.OperationExecutionService;
import com.springmfg.ims.production.service.OrderMaterialService;
import com.springmfg.ims.production.service.ProductionOrderService;
import com.springmfg.ims.quality.service.InspectionService;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;

/**
 * Closing a production order, end to end through the real services with no surrounding transaction (the bridge
 * journals and the variance posts after commit): material is issued, the pieces are made, the order completes and is
 * closed. Every expected figure is worked by hand in the comments.
 *
 * <p>Common to all scenarios: a spring needs 0.8 kg of wire (50 a kg), the order is for 100 pieces, so 80 kg are
 * planned and up to 88 kg may be issued (10 % tolerance). Journals are read as "everything posted after the id mark
 * taken at the start", so the shared database's older data does not matter.
 */
class OrderCloseIT extends AbstractIntegrationTest {

    private static final long WAREHOUSE_ID = 1L;
    private static final long LOCATION_ID = 1L;

    @Autowired PurchaseOrderService poService;
    @Autowired PurchaseOrderRepository poRepo;
    @Autowired GoodsReceiptService grService;
    @Autowired InspectionService inspectionService;
    @Autowired BomService bomService;
    @Autowired RoutingService routingService;
    @Autowired ProductionOrderService orderService;
    @Autowired OrderMaterialService materialService;
    @Autowired MaterialIssueService issueService;
    @Autowired OperationExecutionService executionService;
    @Autowired ProductCostingService costingService;
    @Autowired SystemSettingService settings;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    private final Map<String, String> restoredSettings = new HashMap<>();
    private List<Map<String, Object>> savedRates;

    @BeforeEach
    void login() {
        Long userId = jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'Order close IT', ?, 'x') RETURNING id
                """, Long.class, td.code("close"), td.code("close") + "@example.com");
        String[] authorities = { "PURCHASE_CREATE", "PURCHASE_APPROVE", "INVENTORY_RECEIVE", "QUALITY_VIEW",
                "QUALITY_INSPECT", "QUALITY_APPROVE", "PRODUCTION_VIEW", "PRODUCTION_EXECUTE", "PRODUCTION_CREATE",
                "PRODUCTION_CLOSE", "SETTINGS_MANAGE" };
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CachedUser(userId, "u" + userId, true, 1, Set.of(), Set.copyOf(Arrays.asList(authorities))), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
        savedRates = jdbc.queryForList(
                "SELECT id, labour_rate_per_hour, machine_rate_per_hour, overhead_pct FROM ims.cost_centres");
    }

    @AfterEach
    void cleanUp() {
        restoredSettings.forEach(settings::update);
        for (Map<String, Object> r : savedRates) {
            jdbc.update("""
                    UPDATE ims.cost_centres SET labour_rate_per_hour = ?, machine_rate_per_hour = ?, overhead_pct = ?
                     WHERE id = ?
                    """, r.get("labour_rate_per_hour"), r.get("machine_rate_per_hour"), r.get("overhead_pct"), r.get("id"));
        }
        SecurityContextHolder.clearContext();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

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

    /** 100 kg received at {@code price}, a released order for 100 pieces, {@code issueKg} kg issued to it. */
    private Run releasedOrder(String price, String issueKg) {
        Product product = td.product();
        Material material = td.material();
        jdbc.update("UPDATE ims.products SET standard_cost = 40.0000 WHERE id = ?", product.getId());
        receiveMaterial(material, "100.000", price);
        List<Long> masterOps = jdbc.queryForList("SELECT id FROM ims.operations ORDER BY id LIMIT 3", Long.class);
        routingService.saveSteps(product.getId(), List.of(
                new SaveRoutingStepRequest(10, masterOps.get(0), null, new BigDecimal("15"), new BigDecimal("10"), false),
                new SaveRoutingStepRequest(20, masterOps.get(1), null, new BigDecimal("20"), new BigDecimal("10"), false),
                new SaveRoutingStepRequest(30, masterOps.get(2), null, new BigDecimal("20"), new BigDecimal("5"), true)));
        BomDto bom = bomService.create(new CreateBomRequest(product.getId(), BigDecimal.ONE, "Close IT BOM",
                List.of(new BomItemRequest(10, "MATERIAL", material.getId(), null,
                        new BigDecimal("0.8"), "KG", BigDecimal.ZERO, "wire"))));
        bom = bomService.activate(bomService.approve(bomService.submitForReview(bom.id()).id()).id());
        ProductionOrderDto order = orderService.create(new CreateProductionOrderRequest(product.getId(), bom.id(),
                new BigDecimal("100"), LocalDate.now(), LocalDate.now().plusDays(3), "LINE-A", null, null, null,
                (short) 2, "Close IT"));
        order = orderService.release(orderService.approve(order.id()).id());
        issueService.issue(new CreateIssueRequest(order.id(), WAREHOUSE_ID,
                List.of(new CreateIssueRequest.IssueLineRequest(material.getId(), new BigDecimal(issueKg), null)),
                "Close IT issue"));
        return new Run(product, material, bom, orderService.findById(order.id()));
    }

    private void runEveryOperation(ProductionOrderDto order, String good, String rejected, String scrap) {
        List<ProductionOperationDto> ops = order.operations().stream()
                .sorted(Comparator.comparing(ProductionOperationDto::sequenceNo)).toList();
        for (int i = 0; i < ops.size(); i++) {
            Long id = ops.get(i).id();
            executionService.startOperation(id, null);
            executionService.recordOutput(i == 0
                    ? new RecordOutputRequest(id, new BigDecimal(good), new BigDecimal(rejected), new BigDecimal(scrap),
                            null, null, null, null, "A", "close")
                    : new RecordOutputRequest(id, new BigDecimal(good), BigDecimal.ZERO, BigDecimal.ZERO,
                            null, null, null, null, "A", "close"));
            executionService.completeOperation(id);
        }
    }

    private BigDecimal lineSum(Long orderId, String column) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(l.%s), 0) FROM ims.material_issue_lines l
                  JOIN ims.material_issues i ON i.id = l.material_issue_id WHERE i.production_order_id = ?
                """.formatted(column), BigDecimal.class, orderId);
    }

    private BigDecimal stock(Long materialId, String status) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(qty_on_hand), 0) FROM ims.inventory_balances
                 WHERE item_type = 'MATERIAL' AND material_id = ? AND stock_status = ?
                """, BigDecimal.class, materialId, status);
    }

    private BigDecimal stockValue(Long materialId, String status) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(qty_on_hand * avg_unit_cost), 0) FROM ims.inventory_balances
                 WHERE item_type = 'MATERIAL' AND material_id = ? AND stock_status = ?
                """, BigDecimal.class, materialId, status);
    }

    private BigDecimal fgUnitCost(Long orderId) {
        return jdbc.queryForObject("SELECT unit_cost FROM ims.fg_batches WHERE production_order_id = ?",
                BigDecimal.class, orderId);
    }

    private int missingJournalAlertsSince(long alertMark) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ims.alerts WHERE id > ? AND alert_type = 'MISSING_JOURNAL'", Integer.class, alertMark);
    }

    private long alertMark() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM ims.alerts", Long.class);
    }

    private void zeroConversionRates() {
        jdbc.update("UPDATE ims.cost_centres SET labour_rate_per_hour = 0, machine_rate_per_hour = 0, overhead_pct = 0");
    }

    // ---- scenarios ------------------------------------------------------------------------------------------------

    /**
     * Exactly the planned wire is issued, so nothing is left over and the order closes without any reason.
     *
     * <pre>
     * issue    80 kg x 50 = 4,000    Dr 1501 / Cr 1401
     * output   100 good -> consumption 0.8 x 100 = 80 kg (nothing outstanding)
     * FG       (80 kg x 50) / 100 = 40.0000 each, 4,000   Dr 1410 / Cr 1501      WIP: 4,000 - 4,000 = 0
     * </pre>
     */
    @Test
    @DisplayName("OC-IT-1: an order whose material was all used closes with no return and WIP at zero")
    void closesWhenEverythingWasConsumed() {
        long mark = journalMark();
        long alerts = alertMark();
        Run run = releasedOrder("50.0000", "80.000");
        runEveryOperation(run.order(), "100", "0", "0");
        Long orderId = run.order().id();

        assertThat(lineSum(orderId, "consumed_qty")).as("consumed at completion").isEqualByComparingTo("80");
        assertThat(fgUnitCost(orderId)).isEqualByComparingTo("40.0000");
        assertThat(orderService.findById(orderId).status()).isEqualTo("COMPLETED");

        ProductionOrderDto closed = orderService.close(orderId, null);

        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(stock(run.material().getId(), "WIP")).as("nothing left in WIP").isEqualByComparingTo("0");
        assertThat(stockValue(run.material().getId(), "WIP")).isEqualByComparingTo("0");
        Map<String, BigDecimal> gl = netByAccount(mark);
        assertThat(net(gl, "1501")).as("WIP: 4,000 in, 4,000 to FG").isEqualByComparingTo("0");
        assertThat(net(gl, "1410")).isEqualByComparingTo("4000");
        assertThat(missingJournalAlertsSince(alerts)).as("no MISSING_JOURNAL alert for the issue's WIP in-leg").isZero();
        assertBooksBalance(mark);

        assertThatThrownBy(() -> orderService.close(orderId, null)).as("a second close")
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION);
    }

    @Test
    @DisplayName("OC-IT-2: an order that is not COMPLETED cannot be closed, and nothing can be returned to a CLOSED one")
    void stateGuards() {
        Run run = releasedOrder("50.0000", "80.000");
        Long orderId = run.order().id();

        assertThatThrownBy(() -> orderService.close(orderId, null)).isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION);

        runEveryOperation(run.order(), "100", "0", "0");
        orderService.close(orderId, null);
        assertThatThrownBy(() -> materialService.returnMaterial(orderId,
                new ReturnMaterialRequest(run.material().getId(), BigDecimal.ONE, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    /**
     * 88 kg are issued (the 10 % tolerance), 80 kg are used, 8 kg are left over and have to come back.
     *
     * <pre>
     * issue    88 kg x 50 = 4,400                     Dr 1501 / Cr 1401
     * output   100 good -> consumption 80 kg, 8 kg outstanding
     * FG       4,000 / 100 = 40.0000 each (what was consumed, not what was issued)   Dr 1410 4,000 / Cr 1501
     * close    refused: "RM... (remaining: 8.000 KG)"
     * return   8 kg x 50 = 400                        Dr 1401 / Cr 1501              WIP: 4,400 - 4,000 - 400 = 0
     * stock    12 kg left in the store + 8 returned = 20 kg
     * </pre>
     */
    @Test
    @DisplayName("OC-IT-3: leftover issued material blocks the close until it is returned, then the books are square")
    void leftoverMustBeReturned() {
        long mark = journalMark();
        Run run = releasedOrder("50.0000", "88.000");
        Long orderId = run.order().id();
        Long materialId = run.material().getId();
        runEveryOperation(run.order(), "100", "0", "0");

        assertThat(fgUnitCost(orderId)).as("FG carries what was consumed").isEqualByComparingTo("40.0000");
        assertThat(stock(materialId, "AVAILABLE")).isEqualByComparingTo("12");
        assertThatThrownBy(() -> orderService.close(orderId, null)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("remaining: 8.000")
                .extracting("code").isEqualTo(ErrorCode.ORDER_LOCKED);

        assertThatThrownBy(() -> materialService.returnMaterial(orderId, new ReturnMaterialRequest(materialId,
                new BigDecimal("8.001"), null))).as("more than is left over")
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo(ErrorCode.OVER_CONSUMPTION);

        materialService.returnMaterial(orderId, new ReturnMaterialRequest(materialId, new BigDecimal("8.000"), "unused wire"));

        assertThat(stock(materialId, "AVAILABLE")).as("12 kg left + 8 kg returned").isEqualByComparingTo("20");
        assertThat(jdbc.queryForObject("SELECT remaining_qty FROM ims.material_batches WHERE material_id = ?",
                BigDecimal.class, materialId)).as("the batch has its quantity back").isEqualByComparingTo("20");
        assertThat(lineSum(orderId, "returned_qty")).isEqualByComparingTo("8");

        ProductionOrderDto closed = orderService.close(orderId, null);

        assertThat(closed.status()).isEqualTo("CLOSED");
        Map<String, BigDecimal> gl = netByAccount(mark);
        assertThat(net(gl, "1501")).as("WIP: 4,400 in, 4,000 to FG, 400 returned").isEqualByComparingTo("0");
        assertThat(net(gl, "1410")).isEqualByComparingTo("4000");
        assertThat(net(gl, "1401")).as("stock: 5,000 received, 4,400 issued, 400 returned").isEqualByComparingTo("1000");
        assertThat(stock(materialId, "WIP")).isEqualByComparingTo("0");
        assertBooksBalance(mark);
    }

    /** Same leftover, closed with a reason instead: the 8 kg are written off as scrap, 8 x 50 = 400 (Dr 5202 / Cr 1501). */
    @Test
    @DisplayName("OC-IT-4: closing with a write-off reason scraps the leftover; without a reason the close is refused")
    void leftoverCanBeWrittenOff() {
        long mark = journalMark();
        Run run = releasedOrder("50.0000", "88.000");
        Long orderId = run.order().id();
        Long materialId = run.material().getId();
        runEveryOperation(run.order(), "100", "0", "0");

        assertThatThrownBy(() -> orderService.close(orderId, "  ")).as("a blank reason is no reason")
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo(ErrorCode.ORDER_LOCKED);

        ProductionOrderDto closed = orderService.close(orderId, "wire offcuts too short to reuse");

        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(lineSum(orderId, "written_off_qty")).isEqualByComparingTo("8");
        assertThat(stock(materialId, "WIP")).isEqualByComparingTo("0");
        assertThat(stock(materialId, "SCRAP")).as("8 kg in the scrap location").isEqualByComparingTo("8");
        assertThat(stockValue(materialId, "SCRAP")).isEqualByComparingTo("400");
        Map<String, BigDecimal> gl = netByAccount(mark);
        assertThat(net(gl, "5202")).as("scrap loss 8 x 50").isEqualByComparingTo("400");
        assertThat(net(gl, "1501")).as("WIP: 4,400 in, 4,000 to FG, 400 scrapped").isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ims.audit_logs WHERE action = 'PROD_ORDER_MATERIAL_WRITE_OFF'
                   AND entity = 'ProductionOrder' AND entity_id = ? AND reason LIKE '%offcuts%'
                """, Integer.class, String.valueOf(orderId))).as("the write-off is audited with its reason").isEqualTo(1);
        assertBooksBalance(mark);
    }

    /**
     * Scrap under standard costing is charged once.
     *
     * <pre>
     * standard  0.8 kg x 50 = 40.0000 per piece (conversion rates zeroed to keep it hand-worked)
     * issue     88 kg x 50 = 4,400                                 Dr 1501 / Cr 1401
     * output    94 good + 6 scrap = 100 pieces -> consumption 80 kg = 4,000;  8 kg left over, returned = 400   Dr 1401 / Cr 1501
     * FG        94 x 40 = 3,760                                    Dr 1410 / Cr 1501
     * scrap     6 x 40 (product standard) = 240                    Dr 5202 / Cr 1501
     * variance  actual 4,000 - standard 3,760 = 240 total, of which scrap 240 is already charged -> journal 0
     * WIP       4,400 - 3,760 - 240 - 400 - 0 = 0
     * </pre>
     * Before the fix the variance journal posted the whole 240 again (Dr 5204 / Cr 1501) and WIP ended at -240.
     */
    @Test
    @DisplayName("OC-IT-5: with a released estimate the scrap is charged once and WIP ends at zero")
    void scrapIsChargedOnceUnderStandardCosting() {
        zeroConversionRates();
        long mark = journalMark();
        Run run = releasedOrder("50.0000", "88.000");
        Long orderId = run.order().id();
        costingService.release(costingService.estimate(run.product().getId(), run.bom().id()).id());

        runEveryOperation(run.order(), "94", "0", "6");
        assertThat(fgUnitCost(orderId)).as("received at standard").isEqualByComparingTo("40.0000");
        materialService.returnMaterial(orderId, new ReturnMaterialRequest(run.material().getId(), new BigDecimal("8"), null));
        orderService.close(orderId, null);

        var variance = jdbc.queryForMap(
                "SELECT status, total_variance, scrap_relieved FROM ims.production_order_variances WHERE production_order_id = ?",
                orderId);
        assertThat(variance.get("status")).isEqualTo("NO_VARIANCE");
        assertThat((BigDecimal) variance.get("total_variance")).as("4,000 - 3,760").isEqualByComparingTo("240");
        assertThat((BigDecimal) variance.get("scrap_relieved")).as("6 x 40").isEqualByComparingTo("240");
        Map<String, BigDecimal> gl = netByAccount(mark);
        assertThat(net(gl, "5202")).as("scrap loss").isEqualByComparingTo("240");
        assertThat(net(gl, "5204")).as("no second charge through the variance").isEqualByComparingTo("0");
        assertThat(net(gl, "1410")).isEqualByComparingTo("3760");
        assertThat(net(gl, "1501")).as("WIP").isEqualByComparingTo("0");
        assertBooksBalance(mark);
    }

    /**
     * Price variance on top of scrap: the wire cost 55 a kg, the standard is 50.
     *
     * <pre>
     * issue     80 kg x 55 = 4,400                                 Dr 1501 / Cr 1401
     * output    94 good + 6 scrap -> consumption 80 kg = 4,400
     * FG        94 x 40 = 3,760;  scrap 6 x 40 = 240
     * variance  actual 4,400 - standard 3,760 = 640 total; scrap 240 already charged -> journal 400   Dr 5204 / Cr 1501
     * WIP       4,400 - 3,760 - 240 - 400 = 0
     * </pre>
     */
    @Test
    @DisplayName("OC-IT-6: the variance journal posts only the part the scrap journal did not already charge")
    void varianceJournalExcludesScrap() {
        zeroConversionRates();
        long mark = journalMark();
        Run run = releasedOrder("55.0000", "80.000");
        Long orderId = run.order().id();
        jdbc.update("UPDATE ims.materials SET standard_cost = 50.0000 WHERE id = ?", run.material().getId());
        costingService.release(costingService.estimate(run.product().getId(), run.bom().id()).id());

        runEveryOperation(run.order(), "94", "0", "6");
        orderService.close(orderId, null);

        assertThat(jdbc.queryForObject(
                "SELECT status FROM ims.production_order_variances WHERE production_order_id = ?", String.class, orderId))
                .isEqualTo("POSTED");
        Map<String, BigDecimal> gl = netByAccount(mark);
        assertThat(net(gl, "5204")).as("640 - 240").isEqualByComparingTo("400");
        assertThat(net(gl, "5202")).isEqualByComparingTo("240");
        assertThat(net(gl, "1410")).isEqualByComparingTo("3760");
        assertThat(net(gl, "1501")).as("WIP").isEqualByComparingTo("0");
        assertBooksBalance(mark);
    }

    /**
     * Backflush with the routing numbered 10, 20, 30 (it only ever looked for sequence 1) and a real location.
     *
     * <pre>
     * first output   50 pieces  -> 0.8 x 50  = 40 kg consumed
     * second output  50 pieces  -> 0.8 x 100 = 80 kg consumed in total
     * completion consumes nothing more
     * </pre>
     */
    @Test
    @DisplayName("OC-IT-7: backflush consumes with the first operation's output even though routing steps start at 10")
    void backflushWorksWithRealSequenceNumbers() {
        String old = settings.get("production.auto_backflush", "false");
        restoredSettings.put("production.auto_backflush", old);
        settings.update("production.auto_backflush", "true");

        Run run = releasedOrder("50.0000", "80.000");
        Long orderId = run.order().id();
        List<ProductionOperationDto> ops = run.order().operations().stream()
                .sorted(Comparator.comparing(ProductionOperationDto::sequenceNo)).toList();
        Long first = ops.get(0).id();
        executionService.startOperation(first, null);

        executionService.recordOutput(new RecordOutputRequest(first, new BigDecimal("50"), BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, null, null, "A", "close"));
        assertThat(lineSum(orderId, "consumed_qty")).as("after 50 pieces").isEqualByComparingTo("40");
        assertThat(stock(run.material().getId(), "WIP")).as("40 of the 80 kg still in WIP").isEqualByComparingTo("40");

        executionService.recordOutput(new RecordOutputRequest(first, new BigDecimal("50"), BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, null, null, "A", "close"));
        assertThat(lineSum(orderId, "consumed_qty")).as("after 100 pieces").isEqualByComparingTo("80");
        executionService.completeOperation(first);
        for (int i = 1; i < ops.size(); i++) {
            Long id = ops.get(i).id();
            executionService.startOperation(id, null);
            executionService.recordOutput(new RecordOutputRequest(id, new BigDecimal("100"), BigDecimal.ZERO,
                    BigDecimal.ZERO, null, null, null, null, "A", "close"));
            executionService.completeOperation(id);
        }

        assertThat(lineSum(orderId, "consumed_qty")).as("completion consumed nothing twice").isEqualByComparingTo("80");
        assertThat(jdbc.queryForObject(
                "SELECT consumed_qty FROM ims.production_order_materials WHERE production_order_id = ?",
                BigDecimal.class, orderId)).isEqualByComparingTo("80");
        assertThat(fgUnitCost(orderId)).isEqualByComparingTo("40.0000");
        assertThat(orderService.close(orderId, null).status()).isEqualTo("CLOSED");
    }
}
