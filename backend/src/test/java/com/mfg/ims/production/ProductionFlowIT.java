package com.mfg.ims.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
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
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.engineering.bom.BomDto;
import com.mfg.ims.engineering.bom.BomItemRequest;
import com.mfg.ims.engineering.bom.BomService;
import com.mfg.ims.engineering.bom.CreateBomRequest;
import com.mfg.ims.engineering.routing.RoutingService;
import com.mfg.ims.engineering.routing.SaveRoutingStepRequest;
import com.mfg.ims.inventory.dto.OpeningBalanceLine;
import com.mfg.ims.inventory.service.OpeningBalanceService;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.product.domain.Product;
import com.mfg.ims.lookup.LookupDtos.DispatchableOrder;
import com.mfg.ims.lookup.LookupDtos.FgStock;
import com.mfg.ims.lookup.LookupService;
import com.mfg.ims.production.dto.CreateIssueRequest;
import com.mfg.ims.quality.dto.FinalInspectionDecisionRequest;
import com.mfg.ims.quality.service.FinalInspectionService;
import com.mfg.ims.sales.dto.AddSalesOrderItemRequest;
import com.mfg.ims.sales.dto.CreateDispatchRequest;
import com.mfg.ims.sales.dto.CreateSalesOrderRequest;
import com.mfg.ims.sales.dto.DispatchDto;
import com.mfg.ims.sales.dto.ReserveRequest;
import com.mfg.ims.sales.dto.SalesOrderDto;
import com.mfg.ims.sales.service.DispatchService;
import com.mfg.ims.sales.service.ReservationService;
import com.mfg.ims.sales.service.SalesOrderService;
import com.mfg.ims.production.dto.CreateProductionOrderRequest;
import com.mfg.ims.production.dto.ProductionOperationDto;
import com.mfg.ims.production.dto.ProductionOrderDto;
import com.mfg.ims.production.dto.RecordOutputRequest;
import com.mfg.ims.production.service.MaterialIssueService;
import com.mfg.ims.production.service.OperationExecutionService;
import com.mfg.ims.production.service.ProductionOrderService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * Drives a production order through the real services, the way the screens do: BOM, routing, release, material
 * issue, then every operation in turn. Deliberately not transactional, and it builds nothing by hand, because the
 * synthetic orders in {@link ProductionIT} never exercised the release step that sets the first operation's input.
 */
class ProductionFlowIT extends AbstractIntegrationTest {

    private static final long WAREHOUSE_ID = 1L;
    private static final long LOC_RM01 = 1L;

    @Autowired BomService bomService;
    @Autowired RoutingService routingService;
    @Autowired ProductionOrderService orderService;
    @Autowired MaterialIssueService issueService;
    @Autowired OperationExecutionService executionService;
    @Autowired OpeningBalanceService openingBalanceService;
    @Autowired FinalInspectionService finalInspectionService;
    @Autowired SalesOrderService salesOrderService;
    @Autowired ReservationService reservationService;
    @Autowired DispatchService dispatchService;
    @Autowired LookupService lookupService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    @BeforeEach
    void login() {
        Long userId = jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'Planner', ?, 'x') RETURNING id
                """, Long.class, td.code("plan"), td.code("plan") + "@example.com");
        String[] authorities = { "PRODUCTION_VIEW", "PRODUCTION_EXECUTE", "PRODUCTION_CREATE", "QUALITY_APPROVE",
                "SALES_VIEW", "SALES_CREATE", "SALES_APPROVE", "RESERVE_FG", "DISPATCH_CREATE" };
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CachedUser(userId, "u" + userId, true, 1, Set.of(), Set.copyOf(Arrays.asList(authorities))), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private ProductionOrderDto releasedOrder(BigDecimal plannedQty) {
        Product product = td.product();
        Material material = td.material();
        List<Long> masterOps = jdbc.queryForList("SELECT id FROM ims.operations ORDER BY id LIMIT 3", Long.class);

        routingService.saveSteps(product.getId(), List.of(
                new SaveRoutingStepRequest(10, masterOps.get(0), null, new BigDecimal("15"), new BigDecimal("10"), false),
                new SaveRoutingStepRequest(20, masterOps.get(1), null, new BigDecimal("20"), new BigDecimal("10"), false),
                new SaveRoutingStepRequest(30, masterOps.get(2), null, new BigDecimal("20"), new BigDecimal("5"), true)));

        BomDto bom = bomService.create(new CreateBomRequest(product.getId(), BigDecimal.ONE, "Flow IT BOM",
                List.of(new BomItemRequest(10, "MATERIAL", material.getId(), null,
                        new BigDecimal("0.045"), "KG", new BigDecimal("3"), "wire"))));
        bom = bomService.submitForReview(bom.id());
        bom = bomService.approve(bom.id());
        bom = bomService.activate(bom.id());

        ProductionOrderDto order = orderService.create(new CreateProductionOrderRequest(
                product.getId(), bom.id(), plannedQty, LocalDate.now(), LocalDate.now().plusDays(3),
                "LINE-A", null, null, null, (short) 2, "Flow IT"));
        order = orderService.approve(order.id());
        order = orderService.release(order.id());

        openingBalanceService.loadOpeningBalances(List.of(new OpeningBalanceLine(
                "MATERIAL", material.getId(), null, null, WAREHOUSE_ID, LOC_RM01, "AVAILABLE",
                new BigDecimal("50.000"), "KG", new BigDecimal("85.0000"), "Flow IT stock")));
        issueService.issue(new CreateIssueRequest(order.id(), WAREHOUSE_ID,
                List.of(new CreateIssueRequest.IssueLineRequest(material.getId(), new BigDecimal("4.635"), null)),
                "Flow IT issue"));
        return orderService.findById(order.id());
    }

    private static RecordOutputRequest output(Long operationId, String good, String rejected, String scrap) {
        return new RecordOutputRequest(operationId, new BigDecimal(good), new BigDecimal(rejected),
                new BigDecimal(scrap), null, null, null, null, "A", "flow");
    }

    @Test
    @DisplayName("PRD-FLOW-1: release gives the first operation the planned quantity and leaves later operations at 0")
    void releaseSetsTheFirstOperationInput() {
        ProductionOrderDto order = releasedOrder(new BigDecimal("100"));

        assertThat(order.operations()).hasSize(3);
        List<ProductionOperationDto> ops = order.operations().stream()
                .sorted(java.util.Comparator.comparing(ProductionOperationDto::sequenceNo)).toList();
        assertThat(ops.get(0).inputQty()).isEqualByComparingTo("100");
        assertThat(ops.get(1).inputQty()).isEqualByComparingTo("0");
        assertThat(ops.get(2).inputQty()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("PRD-FLOW-2: output is accepted on every operation in turn and each operation's good output feeds the next")
    void outputIsAcceptedAcrossTheWholeRouting() {
        ProductionOrderDto order = releasedOrder(new BigDecimal("100"));
        List<ProductionOperationDto> ops = order.operations().stream()
                .sorted(java.util.Comparator.comparing(ProductionOperationDto::sequenceNo)).toList();

        executionService.startOperation(ops.get(0).id(), null);
        executionService.recordOutput(output(ops.get(0).id(), "95", "5", "0"));
        executionService.completeOperation(ops.get(0).id());

        // the second operation only receives the good pieces of the first
        BigDecimal secondInput = jdbc.queryForObject(
                "SELECT input_qty FROM ims.production_operations WHERE id = ?", BigDecimal.class, ops.get(1).id());
        assertThat(secondInput).isEqualByComparingTo("95");

        executionService.startOperation(ops.get(1).id(), null);
        executionService.recordOutput(output(ops.get(1).id(), "95", "0", "0"));
        executionService.completeOperation(ops.get(1).id());
        executionService.startOperation(ops.get(2).id(), null);
        executionService.recordOutput(output(ops.get(2).id(), "95", "0", "0"));
        executionService.completeOperation(ops.get(2).id());

        assertThat(orderService.findById(order.id()).status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("PRD-FLOW-6: a paused operation stays in My Work, resumes and completes, and the last completion raises the final inspection")
    void pausedOperationsStayInMyWorkAndTheLastCompletionRaisesTheFinalInspection() {
        ProductionOrderDto order = releasedOrder(new BigDecimal("100"));
        Long me = ((CachedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).id();
        jdbc.update("UPDATE ims.production_operations SET assigned_operator_id = ? WHERE production_order_id = ?",
                me, order.id());
        List<ProductionOperationDto> ops = order.operations().stream()
                .sorted(java.util.Comparator.comparing(ProductionOperationDto::sequenceNo)).toList();
        assertThat(executionService.getMyWork(me)).extracting(ProductionOperationDto::status).containsOnly("PENDING");

        // operation 1: pause keeps it on the screen, resume then output then complete takes it off
        Long first = ops.get(0).id();
        executionService.startOperation(first, null);
        executionService.pauseOperation(first);
        assertThat(executionService.getMyWork(me)).filteredOn(o -> o.id().equals(first))
                .singleElement().extracting(ProductionOperationDto::status).isEqualTo("PAUSED");
        executionService.resumeOperation(first);
        executionService.recordOutput(output(first, "100", "0", "0"));
        ProductionOperationDto done = executionService.completeOperation(first);
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.finalOperation()).isFalse();
        assertThat(executionService.getMyWork(me)).extracting(ProductionOperationDto::id).doesNotContain(first);

        // operation 2: completing from PAUSED is allowed and the audit trail records the real previous status
        Long second = ops.get(1).id();
        executionService.startOperation(second, null);
        executionService.recordOutput(output(second, "100", "0", "0"));
        executionService.pauseOperation(second);
        executionService.completeOperation(second);
        String oldValue = jdbc.queryForObject("""
                SELECT old_value::text FROM ims.audit_logs
                 WHERE action = 'OP_COMPLETE' AND entity = 'ProductionOperation' AND entity_id = ?
                """, String.class, String.valueOf(second));
        assertThat(oldValue).contains("PAUSED");

        // the last operation is flagged, and completing it produces the FG batch and the FINAL inspection after commit
        Long last = ops.get(2).id();
        assertThat(executionService.getMyWork(me)).filteredOn(o -> o.id().equals(last))
                .singleElement().extracting(ProductionOperationDto::finalOperation).isEqualTo(true);
        executionService.startOperation(last, null);
        executionService.recordOutput(output(last, "100", "0", "0"));
        assertThat(executionService.completeOperation(last).finalOperation()).isTrue();

        assertThat(orderService.findById(order.id()).status()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
                "SELECT quality_status FROM ims.fg_batches WHERE production_order_id = ?", String.class, order.id()))
                .isEqualTo("QUALITY_PENDING");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM ims.quality_inspections qi JOIN ims.fg_batches b ON b.id = qi.fg_batch_id
                 WHERE b.production_order_id = ? AND qi.inspection_type = 'FINAL' AND qi.status = 'PENDING'
                """, Integer.class, order.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("PRD-FLOW-3: output above the remaining input is still refused")
    void outputAboveTheRemainingInputIsRefused() {
        ProductionOrderDto order = releasedOrder(new BigDecimal("100"));
        Long first = order.operations().stream()
                .min(java.util.Comparator.comparing(ProductionOperationDto::sequenceNo)).orElseThrow().id();
        executionService.startOperation(first, null);

        assertThatThrownBy(() -> executionService.recordOutput(output(first, "101", "0", "0")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.OPERATION_QTY_EXCEEDS_INPUT);
        executionService.recordOutput(output(first, "100", "0", "0"));
    }

    @Test
    @DisplayName("PRD-FLOW-4: scrap is recorded as an IN-leg into the SCRAP location and the bridge journals the loss")
    void scrapIsPostedIntoTheScrapLocationAndJournalled() {
        ProductionOrderDto order = releasedOrder(new BigDecimal("100"));
        jdbc.update("UPDATE ims.products SET standard_cost = 40.0000 WHERE id = ?", order.productId());
        Long first = order.operations().stream()
                .min(java.util.Comparator.comparing(ProductionOperationDto::sequenceNo)).orElseThrow().id();
        executionService.startOperation(first, null);

        executionService.recordOutput(output(first, "90", "4", "6"));

        List<java.util.Map<String, Object>> txs = jdbc.queryForList("""
                SELECT t.id, t.quantity, t.stock_status, t.unit_cost, t.total_cost, t.reference_type, t.reference_id, l.location_type
                  FROM ims.inventory_transactions t JOIN ims.warehouse_locations l ON l.id = t.location_id
                 WHERE t.transaction_type = 'PRODUCTION_REJECTION' AND t.product_id = ?
                """, order.productId());
        assertThat(txs).hasSize(1);
        assertThat((BigDecimal) txs.get(0).get("quantity")).isEqualByComparingTo("6");
        assertThat(txs.get(0).get("stock_status")).isEqualTo("SCRAP");
        assertThat(txs.get(0).get("location_type")).isEqualTo("SCRAP");
        assertThat((BigDecimal) txs.get(0).get("total_cost")).isEqualByComparingTo("240.0000");

        BigDecimal onHand = jdbc.queryForObject("""
                SELECT COALESCE(SUM(b.qty_on_hand), 0) FROM ims.inventory_balances b
                  JOIN ims.warehouse_locations l ON l.id = b.location_id
                 WHERE b.product_id = ? AND l.location_type = 'SCRAP'
                """, BigDecimal.class, order.productId());
        assertThat(onHand).isEqualByComparingTo("6");

        // after-commit bridge, keyed on the originating production output: Dr 5202 Scrap Loss / Cr 1501 WIP, 6 x 40
        String refType = (String) txs.get(0).get("reference_type");
        Long refId = ((Number) txs.get(0).get("reference_id")).longValue();
        assertThat(refType).isEqualTo("PRODUCTION_OUTPUT");
        List<java.util.Map<String, Object>> lines = jdbc.queryForList("""
                SELECT a.account_code, jl.debit_amount, jl.credit_amount
                  FROM ims.journal_headers jh
                  JOIN ims.journal_lines jl ON jl.journal_header_id = jh.id
                  JOIN ims.gl_accounts a ON a.id = jl.gl_account_id
                 WHERE jh.source_document_type = ? AND jh.source_document_id = ?
                 ORDER BY jl.line_number
                """, refType, refId);
        assertThat(lines).hasSize(2);
        BigDecimal dr = lines.stream().map(r -> (BigDecimal) r.get("debit_amount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cr = lines.stream().map(r -> (BigDecimal) r.get("credit_amount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(dr).isEqualByComparingTo("240");
        assertThat(cr).isEqualByComparingTo(dr);
        assertThat(lines.stream().map(r -> r.get("account_code")).toList()).containsExactlyInAnyOrder("5202", "1501");
    }

    @Test
    @DisplayName("PRD-FLOW-5: produced goods flow through final inspection, reservation and dispatch to an AR invoice")
    void producedGoodsCanBeInspectedReservedAndDispatched() {
        ProductionOrderDto order = releasedOrder(new BigDecimal("100"));
        List<ProductionOperationDto> ops = order.operations().stream()
                .sorted(java.util.Comparator.comparing(ProductionOperationDto::sequenceNo)).toList();
        for (ProductionOperationDto op : ops) {
            executionService.startOperation(op.id(), null);
            executionService.recordOutput(output(op.id(), "100", "0", "0"));
            executionService.completeOperation(op.id());
        }
        assertThat(orderService.findById(order.id()).status()).isEqualTo("COMPLETED");

        Long fgBatchId = jdbc.queryForObject(
                "SELECT id FROM ims.fg_batches WHERE production_order_id = ?", Long.class, order.id());
        Long inspectionId = jdbc.queryForObject(
                "SELECT id FROM ims.quality_inspections WHERE fg_batch_id = ? AND inspection_type = 'FINAL'",
                Long.class, fgBatchId);
        finalInspectionService.completeFinalInspection(inspectionId, new FinalInspectionDecisionRequest(
                new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, null, null, "flow"));

        Long fgLocation = jdbc.queryForObject("""
                SELECT location_id FROM ims.inventory_balances
                 WHERE fg_batch_id = ? AND stock_status = 'AVAILABLE' AND qty_on_hand > 0
                """, Long.class, fgBatchId);
        BigDecimal fgWarehouse = jdbc.queryForObject(
                "SELECT warehouse_id FROM ims.warehouse_locations WHERE id = ?", BigDecimal.class, fgLocation);
        long warehouseId = fgWarehouse.longValue();

        SalesOrderDto so = salesOrderService.create(new CreateSalesOrderRequest(td.customer().getId(),
                LocalDate.now(), LocalDate.now().plusDays(7), "Dispatch address", "NET30", "flow",
                List.of(new AddSalesOrderItemRequest(order.productId(), new BigDecimal("80"),
                        new BigDecimal("55.0000"), null, null))));
        salesOrderService.confirm(so.id());
        Long soId = so.id();
        so = td.asSecondUser(() -> salesOrderService.approve(soId), "SALES_APPROVE");
        Long itemId = so.items().get(0).id();

        // the lookups behind the reserve and dispatch forms see the released stock and the open order
        List<FgStock> stock = lookupService.finishedGoodsStock(order.productId());
        assertThat(stock).singleElement().satisfies(st -> {
            assertThat(st.fgBatchId()).isEqualTo(fgBatchId);
            assertThat(st.available()).isEqualByComparingTo("100");
        });
        // QCF-05: a batch that is on HOLD is not offered for reservation (the reservation itself would be refused)
        String qualityStatus = jdbc.queryForObject("SELECT quality_status FROM ims.fg_batches WHERE id = ?",
                String.class, fgBatchId);
        jdbc.update("UPDATE ims.fg_batches SET quality_status = 'HOLD' WHERE id = ?", fgBatchId);
        assertThat(lookupService.finishedGoodsStock(order.productId()))
                .as("a batch on HOLD is not offered to the reserve and dispatch forms").isEmpty();
        jdbc.update("UPDATE ims.fg_batches SET quality_status = ? WHERE id = ?", qualityStatus, fgBatchId);
        assertThat(lookupService.finishedGoodsStock(order.productId())).hasSize(1);
        Long approvedOrderId = so.id();
        DispatchableOrder dispatchable = lookupService.dispatchableOrders().stream()
                .filter(o -> o.id().equals(approvedOrderId)).findFirst().orElseThrow();
        assertThat(dispatchable.items()).singleElement().satisfies(i -> {
            assertThat(i.salesOrderItemId()).isEqualTo(itemId);
            assertThat(i.remainingQty()).isEqualByComparingTo("80");
        });

        reservationService.reserve(new ReserveRequest(itemId, fgBatchId, warehouseId, fgLocation,
                new BigDecimal("80")));
        DispatchDto dispatch = dispatchService.create(new CreateDispatchRequest(so.id(), LocalDate.now(),
                "Carrier", "MH12AB1234", "LR-1", "Dispatch address", null,
                List.of(new CreateDispatchRequest.DispatchLineRequest(itemId, fgBatchId, new BigDecimal("80"),
                        warehouseId, fgLocation, null)), "flow"));

        BigDecimal onHand = jdbc.queryForObject("""
                SELECT qty_on_hand FROM ims.inventory_balances
                 WHERE fg_batch_id = ? AND location_id = ? AND stock_status = 'AVAILABLE'
                """, BigDecimal.class, fgBatchId, fgLocation);
        BigDecimal reserved = jdbc.queryForObject("""
                SELECT qty_reserved FROM ims.inventory_balances
                 WHERE fg_batch_id = ? AND location_id = ? AND stock_status = 'AVAILABLE'
                """, BigDecimal.class, fgBatchId, fgLocation);
        assertThat(onHand).isEqualByComparingTo("20");
        assertThat(reserved).isEqualByComparingTo("0");

        // the AR invoice is created after the dispatch commits
        BigDecimal invoiceTotal = jdbc.queryForObject(
                "SELECT total_amount FROM ims.ar_invoices WHERE dispatch_id = ?", BigDecimal.class, dispatch.id());
        assertThat(invoiceTotal).isEqualByComparingTo("4400.0000");
    }
}
