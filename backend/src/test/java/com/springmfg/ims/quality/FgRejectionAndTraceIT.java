package com.springmfg.ims.quality;

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
import com.springmfg.ims.engineering.bom.BomDto;
import com.springmfg.ims.engineering.bom.BomItemRequest;
import com.springmfg.ims.engineering.bom.BomService;
import com.springmfg.ims.engineering.bom.CreateBomRequest;
import com.springmfg.ims.engineering.routing.RoutingService;
import com.springmfg.ims.engineering.routing.SaveRoutingStepRequest;
import com.springmfg.ims.insight.traceability.TraceabilityDto;
import com.springmfg.ims.insight.traceability.TraceabilityService;
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
 * Two end-to-end defects from the browser release validation, driven through the real services with no surrounding
 * transaction (the bridge journals after commit):
 *
 * <ul>
 *   <li>D-07: finished goods rejected at final inspection were parked in the scrap location with stock status
 *       AVAILABLE, so available stock was overstated and no scrap journal existed;</li>
 *   <li>D-12: tracing any batch with a receipt, issue or dispatch returned 500 because the DATE columns of those
 *       documents arrive as {@code java.sql.Date}, which the trace could not turn into an Instant.</li>
 * </ul>
 *
 * <p>Scenario, worked by hand: two receipts of 40 kg at 50 and 60, 80 kg issued FIFO = 2,000 + 2,400 = 4,400, 100
 * good pieces = 44.0000 each = Dr 1410 / Cr 1501 4,400 (WIP back to 0). Final inspection accepts 98 and rejects 2:
 * the 2 are written off at 2 x 44 = 88 (Dr 5202 / Cr 1410), 1410 holds 98 x 44 = 4,312.
 */
class FgRejectionAndTraceIT extends AbstractIntegrationTest {

    private static final long WAREHOUSE_ID = 1L;
    private static final long LOCATION_ID = 1L;

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
    @Autowired TraceabilityService traceabilityService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;
    @Autowired org.springframework.cache.CacheManager cacheManager;

    @BeforeEach
    void login() {
        Long userId = jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'Rejection IT', ?, 'x') RETURNING id
                """, Long.class, td.code("rej"), td.code("rej") + "@example.com");
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

    private void assertEveryJournalBalances(long mark) {
        List<Long> unbalanced = jdbc.queryForList("""
                SELECT jh.id FROM ims.journal_headers jh
                  JOIN ims.journal_lines jl ON jl.journal_header_id = jh.id
                 WHERE jh.id > ?
                 GROUP BY jh.id HAVING SUM(jl.debit_amount) <> SUM(jl.credit_amount)
                """, Long.class, mark);
        assertThat(unbalanced).as("journals where Dr <> Cr").isEmpty();
    }

    /** PO, approval, goods receipt and incoming accept; returns the material batch id. */
    private Long receiveMaterial(Material material, String qty, String price) {
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
        return batchId;
    }

    private record Produced(Product product, Material material, ProductionOrderDto order, Long firstMaterialBatchId) {
    }

    /** A released order for 100 pieces at 0.8 kg each, with 80 kg issued from the two receipts. */
    private Produced releasedOrder() {
        Product product = td.product();
        Material material = td.material();
        Long firstBatch = receiveMaterial(material, "40.000", "50.0000");
        receiveMaterial(material, "40.000", "60.0000");
        List<Long> masterOps = jdbc.queryForList("SELECT id FROM ims.operations ORDER BY id LIMIT 3", Long.class);
        routingService.saveSteps(product.getId(), List.of(
                new SaveRoutingStepRequest(10, masterOps.get(0), null, new BigDecimal("15"), new BigDecimal("10"), false),
                new SaveRoutingStepRequest(20, masterOps.get(1), null, new BigDecimal("20"), new BigDecimal("10"), false),
                new SaveRoutingStepRequest(30, masterOps.get(2), null, new BigDecimal("20"), new BigDecimal("5"), true)));
        BomDto bom = bomService.create(new CreateBomRequest(product.getId(), BigDecimal.ONE, "Rejection IT BOM",
                List.of(new BomItemRequest(10, "MATERIAL", material.getId(), null,
                        new BigDecimal("0.8"), "KG", BigDecimal.ZERO, "wire"))));
        bom = bomService.submitForReview(bom.id());
        bom = bomService.approve(bom.id());
        bom = bomService.activate(bom.id());
        ProductionOrderDto order = orderService.create(new CreateProductionOrderRequest(product.getId(), bom.id(),
                new BigDecimal("100"), LocalDate.now(), LocalDate.now().plusDays(3), "LINE-A", null, null, null,
                (short) 2, "Rejection IT"));
        order = orderService.approve(order.id());
        order = orderService.release(order.id());
        issueService.issue(new CreateIssueRequest(order.id(), WAREHOUSE_ID,
                List.of(new CreateIssueRequest.IssueLineRequest(material.getId(), new BigDecimal("80.000"), null)),
                "Rejection IT issue"));
        return new Produced(product, material, orderService.findById(order.id()), firstBatch);
    }

    private void runEveryOperation(ProductionOrderDto order) {
        List<ProductionOperationDto> ops = order.operations().stream()
                .sorted(Comparator.comparing(ProductionOperationDto::sequenceNo)).toList();
        for (int i = 0; i < ops.size(); i++) {
            Long id = ops.get(i).id();
            executionService.startOperation(id, null);
            executionService.recordOutput(i == 0
                    ? new RecordOutputRequest(id, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                            null, null, null, null, "A", "rej")
                    : new RecordOutputRequest(id, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                            null, null, null, null, "A", "rej"));
            executionService.completeOperation(id);
        }
    }

    private Long fgBatchOf(Long orderId) {
        return jdbc.queryForObject("SELECT id FROM ims.fg_batches WHERE production_order_id = ?", Long.class, orderId);
    }

    private void finalDecision(Long fgBatchId, String approved, String rejected) {
        Long inspectionId = jdbc.queryForObject(
                "SELECT id FROM ims.quality_inspections WHERE fg_batch_id = ? AND inspection_type = 'FINAL'",
                Long.class, fgBatchId);
        finalInspectionService.completeFinalInspection(inspectionId, new FinalInspectionDecisionRequest(
                new BigDecimal(approved), new BigDecimal(rejected), BigDecimal.ZERO, null, null, "rejection IT"));
    }

    private Map<String, BigDecimal> onHandByStatus(Long fgBatchId) {
        Map<String, BigDecimal> out = new HashMap<>();
        jdbc.query("""
                SELECT stock_status, SUM(qty_on_hand) AS qty FROM ims.inventory_balances
                 WHERE fg_batch_id = ? GROUP BY stock_status
                """, rs -> {
            out.put(rs.getString("stock_status"), rs.getBigDecimal("qty"));
        }, fgBatchId);
        return out;
    }

    private SalesOrderDto approvedOrderFor(Product product, String qty) {
        SalesOrderDto so = salesOrderService.create(new CreateSalesOrderRequest(td.customer().getId(),
                LocalDate.now(), LocalDate.now().plusDays(7), "Address", "NET30", "rej",
                List.of(new AddSalesOrderItemRequest(product.getId(), new BigDecimal(qty),
                        new BigDecimal("100.0000"), null, null))));
        salesOrderService.confirm(so.id());
        return salesOrderService.approve(so.id());
    }

    private Long availableLocation(Long fgBatchId) {
        return jdbc.queryForObject("""
                SELECT location_id FROM ims.inventory_balances
                 WHERE fg_batch_id = ? AND stock_status = 'AVAILABLE' AND qty_on_hand > 0
                """, Long.class, fgBatchId);
    }

    private DispatchDto reserveAndDispatch(Produced p, Long fgBatchId, String qty) {
        Long location = availableLocation(fgBatchId);
        long warehouseId = jdbc.queryForObject(
                "SELECT warehouse_id FROM ims.warehouse_locations WHERE id = ?", Long.class, location);
        SalesOrderDto so = approvedOrderFor(p.product(), qty);
        Long itemId = so.items().get(0).id();
        reservationService.reserve(new ReserveRequest(itemId, fgBatchId, warehouseId, location, new BigDecimal(qty)));
        return dispatchService.create(new CreateDispatchRequest(so.id(), LocalDate.now(), "Carrier", "MH12AB1234",
                "LR-1", "Address", null,
                List.of(new CreateDispatchRequest.DispatchLineRequest(itemId, fgBatchId, new BigDecimal(qty),
                        warehouseId, location, null)), "rej"));
    }

    // ---- D-07 -----------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("FGR-IT-1 (D-07): rejected final-inspection quantity is REJECTED stock, written off at cost, never AVAILABLE")
    void rejectedFinishedGoodsAreRejectedStockWrittenOffAtCost() {
        long mark = journalMark();
        long alertMark = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM ims.alerts", Long.class);
        Produced p = releasedOrder();
        runEveryOperation(p.order());
        Long fgBatchId = fgBatchOf(p.order().id());

        finalDecision(fgBatchId, "98", "2");

        Map<String, BigDecimal> stock = onHandByStatus(fgBatchId);
        assertThat(stock.get("AVAILABLE")).as("only the accepted pieces are available").isEqualByComparingTo("98");
        assertThat(stock.get("REJECTED")).as("the rejected pieces are rejected stock").isEqualByComparingTo("2");
        assertThat(stock.getOrDefault("QUALITY_PENDING", BigDecimal.ZERO)).isEqualByComparingTo("0");

        String rejectedLocationType = jdbc.queryForObject("""
                SELECT l.location_type FROM ims.inventory_balances b
                  JOIN ims.warehouse_locations l ON l.id = b.location_id
                 WHERE b.fg_batch_id = ? AND b.stock_status = 'REJECTED'
                """, String.class, fgBatchId);
        assertThat(rejectedLocationType).isEqualTo("SCRAP");
        BigDecimal rejectedValue = jdbc.queryForObject("""
                SELECT SUM(qty_on_hand * avg_unit_cost) FROM ims.inventory_balances
                 WHERE fg_batch_id = ? AND stock_status = 'REJECTED'
                """, BigDecimal.class, fgBatchId);
        assertThat(rejectedValue).as("written off, so it carries no value").isEqualByComparingTo("0");

        Map<String, BigDecimal> gl = netByAccount(mark);
        assertThat(net(gl, "5202")).as("scrap loss: 2 x 44").isEqualByComparingTo("88");
        assertThat(net(gl, "1410")).as("FG stock: 4,400 in, 88 out").isEqualByComparingTo("4312");
        assertThat(net(gl, "1501")).as("WIP is relieved once and only once").isEqualByComparingTo("0");
        assertThat(gl.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).as("trial balance")
                .isEqualByComparingTo("0");
        BigDecimal ledgerValue = jdbc.queryForObject("""
                SELECT COALESCE(SUM(qty_on_hand * avg_unit_cost), 0) FROM ims.inventory_balances
                 WHERE item_type = 'PRODUCT' AND product_id = ?
                """, BigDecimal.class, p.product().getId());
        assertThat(net(gl, "1410")).as("GL FG stock equals the ledger valuation (98 x 44)").isEqualByComparingTo(ledgerValue);
        assertEveryJournalBalances(mark);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ims.alerts WHERE id > ? AND alert_type = 'MISSING_JOURNAL'
                """, Long.class, alertMark)).as("no accounting alert").isZero();

        assertThat(jdbc.queryForObject(
                "SELECT quality_status FROM ims.fg_batches WHERE id = ?", String.class, fgBatchId))
                .isEqualTo("PARTIALLY_APPROVED");
    }

    @Test
    @DisplayName("FGR-IT-2 (D-07): rejected pieces cannot be reserved or dispatched; the accepted ones can")
    void rejectedFinishedGoodsCannotBeReservedOrDispatched() {
        long mark = journalMark();
        Produced p = releasedOrder();
        runEveryOperation(p.order());
        Long fgBatchId = fgBatchOf(p.order().id());
        finalDecision(fgBatchId, "98", "2");

        Long location = availableLocation(fgBatchId);
        SalesOrderDto tooMuch = approvedOrderFor(p.product(), "99");
        Long itemId = tooMuch.items().get(0).id();
        assertThatThrownBy(() -> reservationService.reserve(
                new ReserveRequest(itemId, fgBatchId, WAREHOUSE_ID, location, new BigDecimal("99"))))
                .as("98 are available, the 2 rejected ones must not make 99 reservable")
                .isInstanceOf(BusinessRuleException.class);

        DispatchDto dispatch = reserveAndDispatch(p, fgBatchId, "98");
        assertThat(dispatch).isNotNull();
        Map<String, BigDecimal> stock = onHandByStatus(fgBatchId);
        assertThat(stock.getOrDefault("AVAILABLE", BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(stock.get("REJECTED")).as("the rejected pieces stay where they are").isEqualByComparingTo("2");

        Map<String, BigDecimal> gl = netByAccount(mark);
        assertThat(net(gl, "5001")).as("COGS 98 x 44").isEqualByComparingTo("4312");
        assertThat(net(gl, "5202")).as("scrap loss 2 x 44").isEqualByComparingTo("88");
        assertThat(net(gl, "1410")).as("FG stock is empty in the GL too").isEqualByComparingTo("0");
        assertEveryJournalBalances(mark);
    }

    // ---- D-12 -----------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("TRC-IT-1 (D-12): tracing a dispatched FG batch and a received material batch works in both directions")
    void tracingWorksForDispatchedFinishedGoodsAndIssuedMaterial() {
        Produced p = releasedOrder();
        runEveryOperation(p.order());
        Long fgBatchId = fgBatchOf(p.order().id());
        finalDecision(fgBatchId, "100", "0");
        reserveAndDispatch(p, fgBatchId, "60");

        TraceabilityDto fg = traceabilityService.traceFgBatch(fgBatchId);
        assertThat(fg.trail()).extracting(TraceabilityDto.TraceStep::stepType)
                .contains("PRODUCED", "QUALITY_INSPECTION", "DISPATCHED");
        assertThat(fg.trail()).allSatisfy(step -> assertThat(step.timestamp()).isNotNull());
        TraceabilityDto.TraceStep dispatched = fg.trail().stream()
                .filter(s -> "DISPATCHED".equals(s.stepType())).findFirst().orElseThrow();
        assertThat(dispatched.description()).contains("60");
        assertThat(dispatched.timestamp()).as("a date-only value is the start of that day in UTC")
                .isEqualTo(LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant());

        TraceabilityDto material = traceabilityService.traceMaterialBatch(p.firstMaterialBatchId());
        assertThat(material.trail()).extracting(TraceabilityDto.TraceStep::stepType)
                .contains("GRN_RECEIPT", "QUALITY_INSPECTION", "MATERIAL_ISSUED");
        assertThat(material.trail()).allSatisfy(step -> assertThat(step.timestamp()).isNotNull());
    }

    // ---- D-25 -----------------------------------------------------------------------------------------------------

    private record MaterialBatchRow(Long id, String number, String heat, String certificate, String poNumber) {
    }

    private List<MaterialBatchRow> materialBatchesIssuedTo(Long orderId) {
        return jdbc.query("""
                SELECT mb.id, mb.batch_number, mb.heat_number, mb.certificate_number, po.po_number
                  FROM ims.material_issue_lines mil
                  JOIN ims.material_issues mi ON mi.id = mil.material_issue_id
                  JOIN ims.material_batches mb ON mb.id = mil.material_batch_id
                  LEFT JOIN ims.purchase_orders po ON po.id = mb.purchase_order_id
                 WHERE mi.production_order_id = ?
                 GROUP BY mb.id, mb.batch_number, mb.heat_number, mb.certificate_number, po.po_number
                 ORDER BY mb.batch_number
                """, (rs, i) -> new MaterialBatchRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5)), orderId);
    }

    @Test
    @DisplayName("TRC-IT-2 (D-25): the FG trace names every material batch, quantity, supplier, PO and heat that fed it; the material trace reaches the FG batch, dispatch and customer")
    void backwardTraceNamesTheMaterialBatchesAndForwardTraceReachesTheCustomer() {
        Produced p = releasedOrder();
        runEveryOperation(p.order());
        Long fgBatchId = fgBatchOf(p.order().id());
        finalDecision(fgBatchId, "100", "0");
        DispatchDto created = reserveAndDispatch(p, fgBatchId, "60");

        // D-26: the dispatch audit row carries the dispatch id and its state in the right columns.
        var audit = jdbc.queryForMap("""
                SELECT old_value::text AS old_value, new_value::text AS new_value
                  FROM ims.audit_logs WHERE action = 'DISPATCH_CREATED' AND entity_id = ?
                """, String.valueOf(created.id()));
        assertThat(audit.get("old_value")).isNull();
        assertThat((String) audit.get("new_value")).contains("DISPATCHED");

        List<MaterialBatchRow> batches = materialBatchesIssuedTo(p.order().id());
        assertThat(batches).as("80 kg were issued from the two receipts of 40 kg").hasSize(2);

        TraceabilityDto fg = traceabilityService.traceFgBatch(fgBatchId);
        List<TraceabilityDto.TraceStep> sources = fg.trail().stream()
                .filter(s -> "MATERIAL_SOURCE".equals(s.stepType())).toList();
        assertThat(sources).extracting(TraceabilityDto.TraceStep::reference)
                .containsExactlyElementsOf(batches.stream().map(MaterialBatchRow::number).toList());
        for (int i = 0; i < batches.size(); i++) {
            MaterialBatchRow batch = batches.get(i);
            assertThat(sources.get(i).description())
                    .contains("qty used: 40.000")
                    .contains("supplier: IT Supplier")
                    .contains("PO: " + batch.poNumber())
                    .contains("heat: " + batch.heat())
                    .contains("certificate: " + batch.certificate());
            assertThat(sources.get(i).timestamp()).isNotNull();
        }

        String customer = jdbc.queryForObject("""
                SELECT c.name FROM ims.dispatches d
                  JOIN ims.dispatch_items di ON di.dispatch_id = d.id
                  JOIN ims.customers c ON c.id = d.customer_id
                 WHERE di.fg_batch_id = ?
                """, String.class, fgBatchId);
        String fgNumber = jdbc.queryForObject("SELECT batch_number FROM ims.fg_batches WHERE id = ?", String.class, fgBatchId);
        TraceabilityDto forward = traceabilityService.traceMaterialBatch(batches.get(0).id());
        TraceabilityDto.TraceStep produced = forward.trail().stream()
                .filter(s -> "PRODUCED".equals(s.stepType())).findFirst().orElseThrow();
        assertThat(produced.reference()).isEqualTo(fgNumber);
        assertThat(produced.description()).contains(p.order().orderNumber()).contains("produced qty: 100");
        TraceabilityDto.TraceStep dispatched = forward.trail().stream()
                .filter(s -> "DISPATCHED".equals(s.stepType())).findFirst().orElseThrow();
        assertThat(dispatched.description()).contains("Dispatched qty: 60").contains(fgNumber).contains(customer);
    }

    @Test
    @DisplayName("TRC-IT-3 (D-25): with batch-level granularity the FG batch carries source links, and the trace reads them")
    void batchGranularityRecordsTheSourceLinksAndTheTraceUsesThem() {
        jdbc.update("UPDATE ims.system_settings SET value = 'BATCH' WHERE key = 'quality.trace_granularity'");
        cacheManager.getCache("systemSettings").clear();
        try {
            Produced p = releasedOrder();
            runEveryOperation(p.order());
            Long fgBatchId = fgBatchOf(p.order().id());

            List<MaterialBatchRow> batches = materialBatchesIssuedTo(p.order().id());
            List<BigDecimal> linked = jdbc.queryForList("""
                    SELECT quantity FROM ims.fg_batch_material_sources WHERE fg_batch_id = ? ORDER BY material_batch_id
                    """, BigDecimal.class, fgBatchId);
            assertThat(linked).as("one link per issued material batch").hasSize(batches.size());
            assertThat(linked).allSatisfy(q -> assertThat(q).isEqualByComparingTo("40"));

            long sources = traceabilityService.traceFgBatch(fgBatchId).trail().stream()
                    .filter(s -> "MATERIAL_SOURCE".equals(s.stepType())).count();
            assertThat(sources).isEqualTo(2);
        } finally {
            jdbc.update("UPDATE ims.system_settings SET value = 'ORDER' WHERE key = 'quality.trace_granularity'");
            cacheManager.getCache("systemSettings").clear();
        }
    }

    // ---- D-15 / D-22b -----------------------------------------------------------------------------------------------

    private List<ProductionOperationDto> orderedOperations(Long orderId) {
        return orderService.findById(orderId).operations().stream()
                .sorted(Comparator.comparing(ProductionOperationDto::sequenceNo)).toList();
    }

    @Test
    @DisplayName("PRD-IT-SEQ (D-15): an operation cannot start before the previous one is complete, and says why")
    void anOperationCannotStartBeforeThePreviousOneIsComplete() {
        Produced p = releasedOrder();
        List<ProductionOperationDto> ops = orderedOperations(p.order().id());
        Long first = ops.get(0).id();
        Long second = ops.get(1).id();
        assertThat(ops.get(0).startBlockedReason()).as("the first operation can start").isNull();
        assertThat(ops.get(1).startBlockedReason()).contains("Operation 10 must be completed first");

        assertThatThrownBy(() -> executionService.startOperation(second, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Operation 20 cannot start")
                .hasMessageContaining("Operation 10 must be completed first");

        executionService.startOperation(first, null);
        assertThatThrownBy(() -> executionService.startOperation(second, null))
                .as("started is not completed")
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("it is IN_PROGRESS");

        executionService.recordOutput(new RecordOutputRequest(first, new BigDecimal("100"), BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, null, null, "A", "seq"));
        executionService.completeOperation(first);

        assertThat(orderedOperations(p.order().id()).get(1).startBlockedReason()).isNull();
        assertThat(executionService.startOperation(second, null).status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("PRD-IT-PLAN (D-22b): the plan applies from the moment of release: the issue made right after release counted against it, and nothing outside it or above it is issued")
    void theMaterialPlanAppliesRightAfterReleaseAndRefusesUnplannedOrExcessMaterial() {
        Produced p = releasedOrder();
        BigDecimal issued = jdbc.queryForObject(
                "SELECT issued_qty FROM ims.production_order_materials WHERE production_order_id = ?",
                BigDecimal.class, p.order().id());
        assertThat(issued).as("the 80 kg issued straight after release were counted against the plan snapshot")
                .isEqualByComparingTo("80");

        Material other = td.material();
        assertThatThrownBy(() -> issueService.issue(new CreateIssueRequest(p.order().id(), WAREHOUSE_ID,
                List.of(new CreateIssueRequest.IssueLineRequest(other.getId(), new BigDecimal("1.000"), null)), "x")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not part of the plan");

        assertThatThrownBy(() -> issueService.issue(new CreateIssueRequest(p.order().id(), WAREHOUSE_ID,
                List.of(new CreateIssueRequest.IssueLineRequest(p.material().getId(), new BigDecimal("100.000"), null)),
                "x")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceed planned");
    }
}
