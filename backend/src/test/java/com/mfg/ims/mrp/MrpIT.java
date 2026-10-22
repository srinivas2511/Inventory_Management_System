package com.mfg.ims.mrp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ConflictException;
import com.mfg.ims.engineering.bom.Bom;
import com.mfg.ims.engineering.bom.BomRepository;
import com.mfg.ims.inventory.dto.PostingCommand;
import com.mfg.ims.inventory.service.BatchService;
import com.mfg.ims.inventory.service.InventoryPostingService;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.mrp.dto.ConvertAllResultDto;
import com.mfg.ims.mrp.dto.MrpProposalDto;
import com.mfg.ims.mrp.dto.MrpRunDto;
import com.mfg.ims.mrp.dto.RunMrpRequest;
import com.mfg.ims.mrp.dto.SaveDemandForecastRequest;
import com.mfg.ims.mrp.dto.SavePlanningParamRequest;
import com.mfg.ims.mrp.service.DemandForecastService;
import com.mfg.ims.mrp.service.MrpProposalService;
import com.mfg.ims.mrp.service.MrpRunService;
import com.mfg.ims.mrp.service.PlanningParamService;
import com.mfg.ims.product.domain.Product;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/** Integration tests for Release 11 - MRP (PLAN tasks R11-3.1, R11-3.2, R11-4.2) against real PostgreSQL. */
@Transactional
class MrpIT extends AbstractIntegrationTest {

    @Autowired MrpRunService runService;
    @Autowired MrpProposalService proposalService;
    @Autowired DemandForecastService forecastService;
    @Autowired PlanningParamService paramService;
    @Autowired BomRepository bomRepo;
    @Autowired InventoryPostingService postingService;
    @Autowired BatchService batchService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    private final LocalDate today = LocalDate.now();

    /** A product with an ACTIVE BOM: 0.5 kg wire (10% scrap) and 1 pack per piece. */
    private record Fixture(Long productId, Long bomId, Long wireId, Long packId, Long customerId) {}

    private Fixture fixture() {
        Product product = td.product();
        Material wire = td.material();
        Material pack = td.material();
        Bom bom = td.draftBom(product.getId());
        bom.setStatus("ACTIVE");
        bomRepo.saveAndFlush(bom);
        jdbc.update("""
                INSERT INTO ims.bom_items (bom_id, line_no, item_kind, material_id, required_qty, uom, scrap_pct)
                VALUES (?, 1, 'MATERIAL', ?, 0.5, 'KG', 10), (?, 2, 'MATERIAL', ?, 1, 'KG', 0)
                """, bom.getId(), wire.getId(), bom.getId(), pack.getId());
        return new Fixture(product.getId(), bom.getId(), wire.getId(), pack.getId(), td.customer().getId());
    }

    private Long salesOrder(Fixture f, int dueInDays, String qty) {
        Long id = jdbc.queryForObject("""
                INSERT INTO ims.sales_orders (order_number, customer_id, required_date, status)
                VALUES (?, ?, ?, 'APPROVED') RETURNING id
                """, Long.class, td.code("SO"), f.customerId(), Date.valueOf(today.plusDays(dueInDays)));
        jdbc.update("INSERT INTO ims.sales_order_items (sales_order_id, line_no, product_id, ordered_qty) VALUES (?, 1, ?, ?)",
                id, f.productId(), new BigDecimal(qty));
        return id;
    }

    private void openPo(Long materialId, String ordered, String received, int deliveryInDays) {
        Long id = jdbc.queryForObject("""
                INSERT INTO ims.purchase_orders (po_number, supplier_id, po_date, expected_delivery_date, status)
                VALUES (?, ?, ?, ?, 'APPROVED') RETURNING id
                """, Long.class, td.code("PO"), td.supplier().getId(), Date.valueOf(today),
                Date.valueOf(today.plusDays(deliveryInDays)));
        jdbc.update("""
                INSERT INTO ims.purchase_order_items (purchase_order_id, line_no, material_id, ordered_qty, received_qty, rate, line_total)
                VALUES (?, 1, ?, ?, ?, 1, 1)
                """, id, materialId, new BigDecimal(ordered), new BigDecimal(received));
    }

    private MrpRunDto run() {
        return runService.runMrp(new RunMrpRequest(90, false));
    }

    private List<MrpProposalDto> proposals(MrpRunDto run, Long... itemIds) {
        Set<Long> ids = new HashSet<>(Arrays.asList(itemIds));
        return proposalService.search(run.id(), null, null, null, null, null, PageRequest.of(0, 20000))
                .getContent().stream()
                .filter(p -> ids.contains(p.productId()) || ids.contains(p.materialId())).toList();
    }

    private static MrpProposalDto only(List<MrpProposalDto> list, String type, Long itemId) {
        List<MrpProposalDto> match = list.stream().filter(p -> type.equals(p.proposalType())
                && (itemId.equals(p.productId()) || itemId.equals(p.materialId()))).toList();
        assertThat(match).as("%s proposals for item %d", type, itemId).hasSize(1);
        return match.get(0);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM ims." + table, Long.class);
    }

    @Test
    @DisplayName("sales order demand drives a production proposal and BOM explosion cascades into material purchases")
    void salesOrderDemandDrivesProposals() {
        Fixture f = fixture();
        Long soId = salesOrder(f, 30, "100");

        MrpRunDto run = run();
        assertThat(run.status()).isEqualTo("COMPLETED");
        List<MrpProposalDto> list = proposals(run, f.productId(), f.wireId(), f.packId());

        MrpProposalDto prod = only(list, "PRODUCTION", f.productId());
        assertThat(prod.proposedQty()).isEqualByComparingTo("100");
        assertThat(prod.needDate()).isEqualTo(today.plusDays(30));
        assertThat(prod.proposedOrderDate()).isEqualTo(today.plusDays(27));
        assertThat(prod.bomId()).isEqualTo(f.bomId());
        assertThat(prod.sourceDemandType()).isEqualTo("SALES_ORDER");
        assertThat(prod.sourceDemandId()).isEqualTo(soId);
        assertThat(prod.status()).isEqualTo("OPEN");

        MrpProposalDto wire = only(list, "PURCHASE", f.wireId());
        assertThat(wire.proposedQty()).isEqualByComparingTo("55.000");        // 100 x 0.5 x 1.10
        assertThat(wire.needDate()).isEqualTo(today.plusDays(27));
        assertThat(wire.proposedOrderDate()).isEqualTo(today.plusDays(20));    // default supplier lead time 7 days
        assertThat(wire.materialCode()).isNotNull();
        assertThat(only(list, "PURCHASE", f.packId()).proposedQty()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("MRP-001: netting includes open POs and PRs arriving by the need date, partial receipts and ignores late supply")
    void nettingIncludesOpenSupply() {
        Fixture covered = fixture();        // open PO covers the whole wire need
        Fixture late = fixture();           // PO arrives after the need date
        Fixture partial = fixture();        // PO partly received
        Fixture withPr = fixture();         // open PR covers the wire need
        for (Fixture f : List.of(covered, late, partial, withPr)) {
            salesOrder(f, 30, "100");
        }
        openPo(covered.wireId(), "100", "0", 10);
        openPo(late.wireId(), "100", "0", 45);
        openPo(partial.wireId(), "100", "60", 10);
        jdbc.update("""
                INSERT INTO ims.purchase_requisitions (pr_number, material_id, requested_qty, uom, needed_by, status, requested_by)
                VALUES (?, ?, 55, 'KG', ?, 'OPEN', -1)
                """, td.code("PR"), withPr.wireId(), Date.valueOf(today.plusDays(20)));

        MrpRunDto run = run();
        List<MrpProposalDto> all = proposals(run, covered.wireId(), late.wireId(), partial.wireId(), withPr.wireId(),
                covered.packId());

        assertThat(all.stream().filter(p -> covered.wireId().equals(p.materialId()))).isEmpty();
        assertThat(only(all, "PURCHASE", late.wireId()).proposedQty()).isEqualByComparingTo("55.000");
        assertThat(only(all, "PURCHASE", partial.wireId()).proposedQty()).isEqualByComparingTo("15.000");  // 55 - (100-60)
        assertThat(all.stream().filter(p -> withPr.wireId().equals(p.materialId()))).isEmpty();
        assertThat(only(all, "PURCHASE", covered.packId()).proposedQty()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("netting subtracts open production orders from product demand")
    void nettingIncludesOpenProduction() {
        Fixture f = fixture();
        salesOrder(f, 30, "100");
        jdbc.update("""
                INSERT INTO ims.production_orders (order_number, product_id, bom_id, planned_qty, expected_completion_date, status)
                VALUES (?, ?, ?, 40, ?, 'DRAFT')
                """, td.code("MO"), f.productId(), f.bomId(), Date.valueOf(today.plusDays(20)));

        MrpRunDto run = run();
        assertThat(only(proposals(run, f.productId()), "PRODUCTION", f.productId()).proposedQty())
                .isEqualByComparingTo("60");
    }

    @Test
    @DisplayName("netting subtracts AVAILABLE material stock but not quarantined stock")
    void materialStockIsNetted() {
        Fixture f = fixture();
        salesOrder(f, 30, "100");
        postStock(f.wireId(), "AVAILABLE", "20");
        postStock(f.packId(), "QUARANTINE", "500");

        List<MrpProposalDto> list = proposals(run(), f.productId(), f.wireId(), f.packId());

        assertThat(only(list, "PURCHASE", f.wireId()).proposedQty()).isEqualByComparingTo("35.000");   // 55 - 20
        assertThat(only(list, "PURCHASE", f.packId()).proposedQty()).isEqualByComparingTo("100");
    }

    private void postStock(Long materialId, String stockStatus, String qty) {
        var batch = batchService.createMaterialBatch(materialId, null, null, null, null, null, null,
                new BigDecimal(qty), null, null, new BigDecimal("10.0000"), stockStatus);
        postingService.post(new PostingCommand("GOODS_RECEIPT", "MATERIAL", materialId, null, batch.getId(), null,
                1L, 1L, stockStatus, new BigDecimal(qty), "KG", new BigDecimal("10.0000"),
                "GOODS_RECEIPT", 1L, "MRP-IT-" + System.nanoTime(), "receipt"));
    }

    @Test
    @DisplayName("rule 20: an MRP run is advisory - it creates no requisitions, orders or stock movements")
    void runIsAdvisoryOnly() {
        Fixture f = fixture();
        salesOrder(f, 30, "100");
        long prs = count("purchase_requisitions");
        long pos = count("purchase_orders");
        long productionOrders = count("production_orders");
        long ledger = count("inventory_transactions");

        MrpRunDto run = run();

        assertThat(proposals(run, f.productId(), f.wireId(), f.packId())).isNotEmpty()
                .allSatisfy(p -> assertThat(p.status()).isEqualTo("OPEN"));
        assertThat(count("purchase_requisitions")).isEqualTo(prs);
        assertThat(count("purchase_orders")).isEqualTo(pos);
        assertThat(count("production_orders")).isEqualTo(productionOrders);
        assertThat(count("inventory_transactions")).isEqualTo(ledger);
    }

    @Test
    @DisplayName("rule 21: purchase proposals never violate the lead time even when demand is due sooner")
    void leadTimesAreRespected() {
        Fixture f = fixture();
        salesOrder(f, 2, "10");

        List<MrpProposalDto> list = proposals(run(), f.productId(), f.wireId(), f.packId());

        assertThat(list).isNotEmpty().allSatisfy(p -> {
            assertThat(p.proposedOrderDate()).isAfterOrEqualTo(today);
            assertThat(p.proposedOrderDate().plusDays(p.leadTimeDays())).isBeforeOrEqualTo(p.needDate());
        });
        assertThat(only(list, "PURCHASE", f.wireId()).leadTimeAdjusted()).isTrue();
    }

    @Test
    @DisplayName("converting proposals creates a requisition and a DRAFT production order, and a rerun does not duplicate them")
    void conversionCreatesDocumentsAndRerunDoesNotDuplicate() {
        Fixture f = fixture();
        salesOrder(f, 30, "100");
        MrpRunDto run = run();
        List<MrpProposalDto> list = proposals(run, f.productId(), f.wireId(), f.packId());

        MrpProposalDto wire = proposalService.convertToRequisition(only(list, "PURCHASE", f.wireId()).id());
        assertThat(wire.status()).isEqualTo("CONVERTED");
        assertThat(wire.convertedDocumentType()).isEqualTo("PR");
        Map<String, Object> pr = jdbc.queryForMap(
                "SELECT material_id, requested_qty, needed_by, status FROM ims.purchase_requisitions WHERE id = ?",
                wire.convertedDocumentId());
        assertThat(pr.get("material_id")).isEqualTo(f.wireId());
        assertThat((BigDecimal) pr.get("requested_qty")).isEqualByComparingTo("55");
        assertThat(pr.get("status")).isEqualTo("OPEN");
        assertThat(((Date) pr.get("needed_by")).toLocalDate()).isEqualTo(today.plusDays(27));

        MrpProposalDto prod = proposalService.convertToProductionOrder(only(list, "PRODUCTION", f.productId()).id());
        assertThat(prod.convertedDocumentType()).isEqualTo("PRODUCTION_ORDER");
        Map<String, Object> order = jdbc.queryForMap(
                "SELECT status, planned_qty, start_date, expected_completion_date, bom_id FROM ims.production_orders WHERE id = ?",
                prod.convertedDocumentId());
        assertThat(order.get("status")).isEqualTo("DRAFT");
        assertThat((BigDecimal) order.get("planned_qty")).isEqualByComparingTo("100");
        assertThat(((Date) order.get("start_date")).toLocalDate()).isEqualTo(today.plusDays(27));
        assertThat(((Date) order.get("expected_completion_date")).toLocalDate()).isEqualTo(today.plusDays(30));
        assertThat(order.get("bom_id")).isEqualTo(f.bomId());

        assertThatThrownBy(() -> proposalService.convertToRequisition(wire.id()))
                .isInstanceOf(BusinessRuleException.class);

        // The DRAFT order is planned supply and its BOM still creates material demand; the PR covers the wire.
        List<MrpProposalDto> rerun = proposals(run(), f.productId(), f.wireId(), f.packId());
        assertThat(rerun.stream().filter(p -> "PRODUCTION".equals(p.proposalType()))).isEmpty();
        assertThat(rerun.stream().filter(p -> f.wireId().equals(p.materialId()))).isEmpty();
        assertThat(only(rerun, "PURCHASE", f.packId()).proposedQty()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("batch conversion converts every open proposal of the chosen type atomically")
    void convertAllConvertsOpenProposalsOfType() {
        Fixture f = fixture();
        salesOrder(f, 30, "100");
        MrpRunDto run = run();

        ConvertAllResultDto result = proposalService.convertAll(run.id(), "PURCHASE");

        List<MrpProposalDto> purchases = proposals(run, f.wireId(), f.packId());
        assertThat(purchases).hasSize(2).allSatisfy(p -> assertThat(p.status()).isEqualTo("CONVERTED"));
        assertThat(result.proposalIds()).containsAll(purchases.stream().map(MrpProposalDto::id).toList());
        assertThat(proposals(run, f.productId())).allSatisfy(p -> assertThat(p.status()).isEqualTo("OPEN"));
        assertThatThrownBy(() -> proposalService.convertAll(run.id(), "BOGUS")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("discarded proposals cannot be converted; filters narrow the proposal list")
    void discardAndFilter() {
        Fixture f = fixture();
        salesOrder(f, 30, "100");
        MrpRunDto run = run();
        MrpProposalDto wire = only(proposals(run, f.wireId()), "PURCHASE", f.wireId());

        MrpProposalDto discarded = proposalService.discard(wire.id(), "Supplier on allocation");
        assertThat(discarded.status()).isEqualTo("DISCARDED");
        assertThat(discarded.discardReason()).isEqualTo("Supplier on allocation");
        assertThatThrownBy(() -> proposalService.convertToRequisition(wire.id())).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> proposalService.discard(wire.id(), "again")).isInstanceOf(BusinessRuleException.class);

        assertThat(proposalService.search(run.id(), "PURCHASE", "DISCARDED", f.wireId(), null, null, PageRequest.of(0, 10))
                .getContent()).extracting(MrpProposalDto::id).containsExactly(wire.id());
        assertThat(proposalService.search(run.id(), "PRODUCTION", null, f.wireId(), null, null, PageRequest.of(0, 10))
                .getContent()).isEmpty();
    }

    @Test
    @DisplayName("forecasts drive MTS planning, MTO ignores them, and FIXED lot sizing and lead time come from planning parameters")
    void forecastsAndPlanningParameters() {
        Fixture f = fixture();
        forecastService.create(new SaveDemandForecastRequest(f.productId(), today.plusDays(20), new BigDecimal("40"), null));
        assertThatThrownBy(() -> forecastService.create(
                new SaveDemandForecastRequest(f.productId(), today.plusDays(20), new BigDecimal("5"), null)))
                .isInstanceOf(ConflictException.class);

        MrpProposalDto fromForecast = only(proposals(run(), f.productId()), "PRODUCTION", f.productId());
        assertThat(fromForecast.proposedQty()).isEqualByComparingTo("40");
        assertThat(fromForecast.sourceDemandType()).isEqualTo("FORECAST");

        paramService.save(new SavePlanningParamRequest(null, f.productId(), "MTO", "EXACT",
                null, null, null, null, null, null, null));
        assertThat(proposals(run(), f.productId())).isEmpty();

        paramService.save(new SavePlanningParamRequest(null, f.productId(), "MTS", "FIXED",
                new BigDecimal("80"), null, null, null, null, null, 5));
        salesOrder(f, 30, "100");
        List<MrpProposalDto> list = proposals(run(), f.productId(), f.wireId());
        List<MrpProposalDto> production = list.stream().filter(p -> "PRODUCTION".equals(p.proposalType())).toList();
        assertThat(production).isNotEmpty();
        MrpProposalDto salesLot = production.stream().filter(p -> p.needDate().equals(today.plusDays(30))).findFirst().orElseThrow();
        assertThat(salesLot.proposedQty().remainder(new BigDecimal("80"))).isEqualByComparingTo("0");
        assertThat(salesLot.proposedOrderDate()).isEqualTo(today.plusDays(25));      // lead time 5 from the parameters
        assertThat(salesLot.lotSizeRule()).isEqualTo("FIXED");
    }

    @Test
    @DisplayName("planning parameter validation")
    void planningParameterValidation() {
        Fixture f = fixture();
        assertThatThrownBy(() -> paramService.save(new SavePlanningParamRequest(f.wireId(), null, "MTO", "EXACT",
                null, null, null, null, null, null, null))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> paramService.save(new SavePlanningParamRequest(f.wireId(), f.productId(), "MTS", "EXACT",
                null, null, null, null, null, null, null))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> paramService.save(new SavePlanningParamRequest(null, f.productId(), "MTS", "FIXED",
                null, null, null, null, null, null, null))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> paramService.save(new SavePlanningParamRequest(null, f.productId(), "MTS", "MIN_MAX",
                null, new BigDecimal("50"), new BigDecimal("10"), null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("safety stock (materials.min_stock) creates a requirement only when included in the run")
    void safetyStockIsOptional() {
        Material material = td.material();
        jdbc.update("UPDATE ims.materials SET min_stock = 30, reorder_level = 30 WHERE id = ?", material.getId());

        MrpRunDto without = runService.runMrp(new RunMrpRequest(90, false));
        assertThat(proposals(without, material.getId())).isEmpty();

        MrpRunDto with = runService.runMrp(new RunMrpRequest(90, true));
        MrpProposalDto safety = only(proposals(with, material.getId()), "PURCHASE", material.getId());
        assertThat(safety.proposedQty()).isEqualByComparingTo("30");
        assertThat(safety.sourceDemandType()).isEqualTo("SAFETY_STOCK");
    }

    @Test
    @DisplayName("rough-cut capacity: a production proposal overloading a machine's weekly hours is flagged")
    void capacityOverloadIsFlagged() {
        Fixture f = fixture();
        Long machineId = jdbc.queryForObject(
                "INSERT INTO ims.machines (machine_code, name) VALUES (?, 'IT Coiler') RETURNING id", Long.class, td.code("MC"));
        Long operationId = jdbc.queryForObject("SELECT id FROM ims.operations ORDER BY id LIMIT 1", Long.class);
        jdbc.update("""
                INSERT INTO ims.product_routing (product_id, sequence_no, operation_id, machine_id, standard_time_min, setup_time_min)
                VALUES (?, 10, ?, ?, 30, 0)
                """, f.productId(), operationId, machineId);
        salesOrder(f, 30, "200");        // 200 pcs x 30 min = 100 h against 80 h per week

        MrpRunDto run = run();

        MrpProposalDto prod = only(proposals(run, f.productId()), "PRODUCTION", f.productId());
        assertThat(prod.machineHoursRequired()).isEqualByComparingTo("100");
        assertThat(prod.capacityOverload()).isTrue();
        assertThat(run.capacityOverloads()).isGreaterThanOrEqualTo(1);
        assertThat(proposalService.search(run.id(), null, null, null, f.productId(), true, PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("R11-3.2: a run over 1,000 active sales order lines completes in under 30 seconds")
    void thousandSalesOrderLinesRunUnderThirtySeconds() {
        int products = 50;
        List<Fixture> fixtures = new ArrayList<>();
        for (int i = 0; i < products; i++) {
            fixtures.add(fixture());
        }
        Long customerId = fixtures.get(0).customerId();
        for (int order = 0; order < 100; order++) {
            Long soId = jdbc.queryForObject("""
                    INSERT INTO ims.sales_orders (order_number, customer_id, required_date, status)
                    VALUES (?, ?, ?, 'APPROVED') RETURNING id
                    """, Long.class, td.code("SO"), customerId, Date.valueOf(today.plusDays(1 + order % 60)));
            List<Object[]> lines = new ArrayList<>();
            for (int line = 0; line < 10; line++) {
                lines.add(new Object[] { soId, line + 1, fixtures.get((order + line) % products).productId(),
                        new BigDecimal(10 + line) });
            }
            jdbc.batchUpdate("INSERT INTO ims.sales_order_items (sales_order_id, line_no, product_id, ordered_qty) VALUES (?, ?, ?, ?)",
                    lines);
        }
        Long lines = jdbc.queryForObject("""
                SELECT count(*) FROM ims.sales_order_items soi JOIN ims.sales_orders so ON so.id = soi.sales_order_id
                 WHERE so.customer_id = ? AND so.status = 'APPROVED'
                """, Long.class, customerId);
        assertThat(lines).isEqualTo(1000L);

        long start = System.nanoTime();
        MrpRunDto run = run();
        long seconds = (System.nanoTime() - start) / 1_000_000_000L;

        assertThat(run.status()).isEqualTo("COMPLETED");
        assertThat(run.totalProposals()).isGreaterThan(products);
        assertThat(seconds).as("MRP run duration in seconds").isLessThan(30L);
    }
}
