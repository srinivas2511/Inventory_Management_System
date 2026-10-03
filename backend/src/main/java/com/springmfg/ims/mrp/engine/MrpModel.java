package com.springmfg.ims.mrp.engine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Plain input/output types of the MRP engine; the engine itself never touches the database. */
public final class MrpModel {

    private MrpModel() {}

    public enum ItemType { MATERIAL, PRODUCT }

    public enum SupplyKind { STOCK, OPEN_PRODUCTION, OPEN_PO, OPEN_PR, PLANNED }

    public record ItemKey(ItemType type, long id) {}

    public record Demand(ItemKey item, LocalDate needDate, BigDecimal qty, String sourceType, Long sourceId) {}

    public record Supply(ItemKey item, LocalDate date, BigDecimal qty, SupplyKind kind) {}

    public record LotParams(String rule, BigDecimal fixedQty, BigDecimal minQty, BigDecimal maxQty,
                            BigDecimal eoqAnnualDemand, BigDecimal eoqOrderCost, BigDecimal eoqHoldingCost) {
        public static LotParams exact() {
            return new LotParams("EXACT", null, null, null, null, null, null);
        }
    }

    public record BomLine(long materialId, BigDecimal qty, BigDecimal scrapPct) {}

    public record BomInfo(long bomId, BigDecimal baseQty, List<BomLine> lines) {}

    public record RoutingStep(Long machineId, BigDecimal standardTimeMin, BigDecimal setupTimeMin) {}

    /** {@code bom} is null when the product has no ACTIVE BOM. */
    public record ProductPlan(long productId, String uom, boolean mto, LotParams lot, int leadDays,
                              BomInfo bom, List<RoutingStep> routing) {}

    public record MaterialPlan(long materialId, String uom, LotParams lot, int leadDays) {}

    public record MrpInput(LocalDate today, LocalDate horizonEnd, List<Demand> demands, List<Supply> supplies,
                           Map<Long, ProductPlan> products, Map<Long, MaterialPlan> materials,
                           BigDecimal machineHoursPerWeek) {}

    public record ProposalDraft(String type, Long materialId, Long productId, Long bomId, String uom,
                                BigDecimal requiredQty, BigDecimal proposedQty, LocalDate needDate,
                                LocalDate orderDate, int leadDays, boolean leadTimeAdjusted,
                                String sourceType, Long sourceId, String lotRule,
                                BigDecimal machineHours, boolean capacityOverload) {

        public ProposalDraft withCapacity(BigDecimal hours, boolean overload) {
            return new ProposalDraft(type, materialId, productId, bomId, uom, requiredQty, proposedQty, needDate,
                    orderDate, leadDays, leadTimeAdjusted, sourceType, sourceId, lotRule, hours, overload);
        }
    }

    public record MrpResult(List<ProposalDraft> proposals, List<String> warnings) {}
}
