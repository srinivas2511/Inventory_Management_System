package com.springmfg.ims.mrp;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.springmfg.ims.mrp.engine.MrpEngine;
import com.springmfg.ims.mrp.engine.MrpModel.BomInfo;
import com.springmfg.ims.mrp.engine.MrpModel.BomLine;
import com.springmfg.ims.mrp.engine.MrpModel.Demand;
import com.springmfg.ims.mrp.engine.MrpModel.ItemKey;
import com.springmfg.ims.mrp.engine.MrpModel.ItemType;
import com.springmfg.ims.mrp.engine.MrpModel.LotParams;
import com.springmfg.ims.mrp.engine.MrpModel.MaterialPlan;
import com.springmfg.ims.mrp.engine.MrpModel.MrpInput;
import com.springmfg.ims.mrp.engine.MrpModel.MrpResult;
import com.springmfg.ims.mrp.engine.MrpModel.ProductPlan;
import com.springmfg.ims.mrp.engine.MrpModel.ProposalDraft;
import com.springmfg.ims.mrp.engine.MrpModel.RoutingStep;
import com.springmfg.ims.mrp.engine.MrpModel.Supply;
import com.springmfg.ims.mrp.engine.MrpModel.SupplyKind;

/** Pure unit tests of the MRP engine: netting, BOM explosion, lot sizing, lead times (rule 21), MTS/MTO, capacity. */
class MrpEngineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final LocalDate END = TODAY.plusDays(90);
    private static final long PRODUCT = 1L;
    private static final long WIRE = 10L;
    private static final long PACK = 11L;

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static ItemKey product(long id) {
        return new ItemKey(ItemType.PRODUCT, id);
    }

    private static ItemKey material(long id) {
        return new ItemKey(ItemType.MATERIAL, id);
    }

    private static Demand salesDemand(long productId, String qty, int inDays) {
        return new Demand(product(productId), TODAY.plusDays(inDays), bd(qty), "SALES_ORDER", 500L + inDays);
    }

    private static BomInfo bom() {
        return new BomInfo(77L, BigDecimal.ONE, List.of(
                new BomLine(WIRE, bd("0.5"), bd("10")),
                new BomLine(PACK, bd("1"), BigDecimal.ZERO)));
    }

    private static ProductPlan productPlan(boolean mto, LotParams lot, int lead, List<RoutingStep> routing) {
        return new ProductPlan(PRODUCT, "PCS", mto, lot, lead, bom(), routing);
    }

    private static Map<Long, MaterialPlan> materials(int wireLead, LotParams wireLot) {
        return Map.of(
                WIRE, new MaterialPlan(WIRE, "KG", wireLot, wireLead),
                PACK, new MaterialPlan(PACK, "PCS", LotParams.exact(), 2));
    }

    private static MrpInput input(List<Demand> demands, List<Supply> supplies, ProductPlan plan,
                                  Map<Long, MaterialPlan> materials) {
        return new MrpInput(TODAY, END, demands, supplies, Map.of(PRODUCT, plan), materials, bd("80"));
    }

    private static MrpInput simple(List<Demand> demands, List<Supply> supplies) {
        return input(demands, supplies, productPlan(false, LotParams.exact(), 3, List.of()), materials(5, LotParams.exact()));
    }

    private static List<ProposalDraft> of(MrpResult r, String type, long id) {
        return r.proposals().stream().filter(p -> type.equals(p.type())
                && (("PRODUCTION".equals(type) ? p.productId() : p.materialId()) == id)).toList();
    }

    @Test
    @DisplayName("sales demand with no stock yields a production proposal and exploded material purchases with scrap")
    void bomExplosionCascades() {
        MrpResult r = MrpEngine.run(simple(List.of(salesDemand(PRODUCT, "100", 30)), List.of()));

        List<ProposalDraft> prod = of(r, "PRODUCTION", PRODUCT);
        assertThat(prod).hasSize(1);
        assertThat(prod.get(0).proposedQty()).isEqualByComparingTo("100");
        assertThat(prod.get(0).needDate()).isEqualTo(TODAY.plusDays(30));
        assertThat(prod.get(0).orderDate()).isEqualTo(TODAY.plusDays(27));
        assertThat(prod.get(0).bomId()).isEqualTo(77L);

        List<ProposalDraft> wire = of(r, "PURCHASE", WIRE);
        assertThat(wire).hasSize(1);
        assertThat(wire.get(0).proposedQty()).isEqualByComparingTo("55.000");   // 100 x 0.5 x 1.10
        assertThat(wire.get(0).needDate()).isEqualTo(TODAY.plusDays(27));       // production start date
        assertThat(wire.get(0).orderDate()).isEqualTo(TODAY.plusDays(22));      // minus 5 days supplier lead time
        assertThat(of(r, "PURCHASE", PACK).get(0).proposedQty()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("netting subtracts available stock, open production supply and open PO/PR arriving by the need date")
    void nettingUsesStockAndOpenSupply() {
        List<Supply> supplies = List.of(
                new Supply(product(PRODUCT), TODAY, bd("30"), SupplyKind.STOCK),
                new Supply(product(PRODUCT), TODAY.plusDays(10), bd("20"), SupplyKind.OPEN_PRODUCTION),
                new Supply(material(WIRE), TODAY.plusDays(15), bd("10"), SupplyKind.OPEN_PO),
                new Supply(material(WIRE), TODAY.plusDays(20), bd("5"), SupplyKind.OPEN_PR));
        MrpResult r = MrpEngine.run(simple(List.of(salesDemand(PRODUCT, "100", 30)), supplies));

        assertThat(of(r, "PRODUCTION", PRODUCT).get(0).proposedQty()).isEqualByComparingTo("50");
        // wire need 50 x 0.5 x 1.1 = 27.5 at day 27; the PO (10) and PR (5) both arrive before that
        assertThat(of(r, "PURCHASE", WIRE).get(0).proposedQty()).isEqualByComparingTo("12.500");
    }

    @Test
    @DisplayName("supply arriving after the need date does not reduce the net requirement")
    void lateSupplyIsIgnored() {
        List<Supply> supplies = List.of(new Supply(product(PRODUCT), TODAY.plusDays(45), bd("100"), SupplyKind.OPEN_PRODUCTION));
        MrpResult r = MrpEngine.run(simple(List.of(salesDemand(PRODUCT, "100", 30)), supplies));
        assertThat(of(r, "PRODUCTION", PRODUCT).get(0).proposedQty()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("fully covered demand produces no proposals")
    void coveredDemandProducesNothing() {
        List<Supply> supplies = List.of(new Supply(product(PRODUCT), TODAY, bd("150"), SupplyKind.STOCK));
        MrpResult r = MrpEngine.run(simple(List.of(salesDemand(PRODUCT, "100", 30)), supplies));
        assertThat(r.proposals()).isEmpty();
    }

    @Test
    @DisplayName("demand beyond the planning horizon is ignored")
    void horizonIsRespected() {
        MrpResult r = MrpEngine.run(simple(List.of(salesDemand(PRODUCT, "100", 120)), List.of()));
        assertThat(r.proposals()).isEmpty();
    }

    @Test
    @DisplayName("rule 21: no proposal violates its lead time, even when demand is due sooner than the lead time")
    void leadTimeIsNeverViolated() {
        MrpResult r = MrpEngine.run(simple(List.of(salesDemand(PRODUCT, "10", 1)), List.of()));

        assertThat(r.proposals()).isNotEmpty();
        for (ProposalDraft p : r.proposals()) {
            assertThat(p.orderDate()).isAfterOrEqualTo(TODAY);
            assertThat(p.orderDate().plusDays(p.leadDays())).isBeforeOrEqualTo(p.needDate());
        }
        ProposalDraft prod = of(r, "PRODUCTION", PRODUCT).get(0);
        assertThat(prod.leadTimeAdjusted()).isTrue();
        assertThat(prod.orderDate()).isEqualTo(TODAY);
        assertThat(prod.needDate()).isEqualTo(TODAY.plusDays(3));
        ProposalDraft wire = of(r, "PURCHASE", WIRE).get(0);
        assertThat(wire.needDate()).isEqualTo(TODAY.plusDays(5));
    }

    @Test
    @DisplayName("overdue demand is planned from today")
    void overdueDemandIsPlannedFromToday() {
        MrpResult r = MrpEngine.run(simple(List.of(salesDemand(PRODUCT, "10", -4)), List.of()));
        assertThat(of(r, "PRODUCTION", PRODUCT).get(0).orderDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("FIXED lot sizing rounds up and the surplus covers later demand")
    void fixedLotSurplusCarriesForward() {
        ProductPlan plan = productPlan(false, new LotParams("FIXED", bd("100"), null, null, null, null, null), 3, List.of());
        MrpResult r = MrpEngine.run(input(
                List.of(salesDemand(PRODUCT, "60", 10), salesDemand(PRODUCT, "30", 20), salesDemand(PRODUCT, "30", 40)),
                List.of(), plan, materials(5, LotParams.exact())));

        List<ProposalDraft> prod = of(r, "PRODUCTION", PRODUCT);
        assertThat(prod).hasSize(2);                         // 100 covers 60 + 30; 30 more needs another lot
        assertThat(prod.get(0).proposedQty()).isEqualByComparingTo("100");
        assertThat(prod.get(0).requiredQty()).isEqualByComparingTo("60");
        assertThat(prod.get(1).proposedQty()).isEqualByComparingTo("100");
        assertThat(prod.get(1).needDate()).isEqualTo(TODAY.plusDays(40));
    }

    @Test
    @DisplayName("MIN_MAX raises small requirements to the minimum and splits large ones at the maximum")
    void minMaxLotSizing() {
        LotParams lot = new LotParams("MIN_MAX", null, bd("50"), bd("200"), null, null, null);
        assertThat(sizes(bd("20"), lot)).containsExactly("50.000");
        assertThat(sizes(bd("120"), lot)).containsExactly("120.000");
        assertThat(sizes(bd("450"), lot)).containsExactly("200.000", "200.000", "50.000");
        assertThat(sizes(bd("410"), lot)).containsExactly("200.000", "200.000", "50.000");
    }

    @Test
    @DisplayName("EOQ orders at least sqrt(2DS/H)")
    void eoqLotSizing() {
        LotParams lot = new LotParams("EOQ", null, null, null, bd("1000"), bd("50"), bd("10"));  // sqrt(10000) = 100
        assertThat(sizes(bd("30"), lot)).containsExactly("100.000");
        assertThat(sizes(bd("250"), lot)).containsExactly("250.000");
    }

    private static List<String> sizes(BigDecimal net, LotParams lot) {
        return com.springmfg.ims.mrp.engine.MrpLotSizing.size(net, lot).stream()
                .map(c -> c.proposed().toPlainString()).toList();
    }

    @Test
    @DisplayName("MTO products plan only against sales orders and ignore stock, forecasts and safety stock")
    void mtoPlansOnlySalesOrderDemand() {
        ProductPlan mto = productPlan(true, LotParams.exact(), 3, List.of());
        List<Demand> demands = List.of(
                salesDemand(PRODUCT, "40", 20),
                new Demand(product(PRODUCT), TODAY.plusDays(20), bd("500"), "FORECAST", 1L),
                new Demand(product(PRODUCT), TODAY, bd("25"), "SAFETY_STOCK", null));
        List<Supply> supplies = List.of(new Supply(product(PRODUCT), TODAY, bd("1000"), SupplyKind.STOCK));

        MrpResult r = MrpEngine.run(input(demands, supplies, mto, materials(5, LotParams.exact())));

        assertThat(of(r, "PRODUCTION", PRODUCT)).hasSize(1);
        assertThat(of(r, "PRODUCTION", PRODUCT).get(0).proposedQty()).isEqualByComparingTo("40");
    }

    @Test
    @DisplayName("MTS products net sales orders, forecasts and safety stock against stock")
    void mtsUsesAllDemandSources() {
        List<Demand> demands = List.of(
                salesDemand(PRODUCT, "40", 20),
                new Demand(product(PRODUCT), TODAY.plusDays(20), bd("60"), "FORECAST", 1L),
                new Demand(product(PRODUCT), TODAY, bd("25"), "SAFETY_STOCK", null));
        List<Supply> supplies = List.of(new Supply(product(PRODUCT), TODAY, bd("100"), SupplyKind.STOCK));

        MrpResult r = MrpEngine.run(simple(demands, supplies));

        // 100 stock against 25 safety (today) then 100 of sales + forecast (day 20) => 25 short at day 20
        assertThat(of(r, "PRODUCTION", PRODUCT)).hasSize(1);
        assertThat(of(r, "PRODUCTION", PRODUCT).get(0).proposedQty()).isEqualByComparingTo("25");
    }

    @Test
    @DisplayName("a product without an ACTIVE BOM is skipped with a warning")
    void productWithoutBomIsSkipped() {
        ProductPlan noBom = new ProductPlan(PRODUCT, "PCS", false, LotParams.exact(), 3, null, List.of());
        MrpResult r = MrpEngine.run(input(List.of(salesDemand(PRODUCT, "10", 10)), List.of(), noBom, materials(5, LotParams.exact())));
        assertThat(r.proposals()).isEmpty();
        assertThat(r.warnings()).singleElement().asString().contains("no ACTIVE BOM");
    }

    @Test
    @DisplayName("rough-cut capacity flags production proposals that overload a machine in a week")
    void capacityOverloadIsFlagged() {
        // 100 pcs x 30 min = 50 h per proposal on machine 5; capacity 80 h/week
        List<RoutingStep> routing = List.of(new RoutingStep(5L, bd("30"), BigDecimal.ZERO));
        ProductPlan plan = productPlan(false, LotParams.exact(), 3, routing);
        MrpResult r = MrpEngine.run(input(
                List.of(salesDemand(PRODUCT, "100", 20), salesDemand(PRODUCT, "100", 21)),
                List.of(), plan, materials(5, LotParams.exact())));

        List<ProposalDraft> prod = new ArrayList<>(of(r, "PRODUCTION", PRODUCT));
        assertThat(prod).hasSize(2);
        assertThat(prod).allSatisfy(p -> assertThat(p.machineHours()).isEqualByComparingTo("50.000"));
        assertThat(prod.stream().filter(ProposalDraft::capacityOverload)).hasSize(1);
    }

    @Test
    @DisplayName("the engine is deterministic: same input, same proposals")
    void deterministic() {
        MrpInput in = simple(List.of(salesDemand(PRODUCT, "100", 30), salesDemand(PRODUCT, "50", 10)), List.of());
        assertThat(MrpEngine.run(in).proposals()).isEqualTo(MrpEngine.run(in).proposals());
    }
}
