package com.springmfg.ims.mrp.engine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import com.springmfg.ims.mrp.engine.MrpLotSizing.Chunk;
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

/**
 * Net-requirements calculation (DESIGN 17.2). Pure and deterministic: demands and supplies in, advisory
 * proposals out (business rule 20). Products are planned first; their BOM explosion adds dependent material
 * demand, then materials are planned. Net = gross - stock - open production/PO/PR supply arriving by need date.
 */
public final class MrpEngine {

    private static final int SCALE = 3;
    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    private final MrpInput in;
    private final Map<ItemKey, List<Slot>> pool = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();

    private MrpEngine(MrpInput in) {
        this.in = in;
    }

    public static MrpResult run(MrpInput input) {
        return new MrpEngine(input).execute();
    }

    private static final class Slot {
        final LocalDate date;
        BigDecimal remaining;

        Slot(LocalDate date, BigDecimal remaining) {
            this.date = date;
            this.remaining = remaining;
        }
    }

    private record Bucket(LocalDate date, BigDecimal qty, String sourceType, Long sourceId) {}

    private MrpResult execute() {
        for (Supply s : in.supplies()) {
            addSupply(s.item(), s.date(), s.qty(), s.kind());
        }
        List<ProposalDraft> production = new ArrayList<>();
        List<ProposalDraft> purchases = new ArrayList<>();
        List<Demand> materialDemands = new ArrayList<>();
        for (Demand d : in.demands()) {
            if (d.item().type() == ItemType.MATERIAL) {
                materialDemands.add(d);
            }
        }

        planProducts(production, materialDemands);
        planMaterials(purchases, materialDemands);
        checkCapacity(production);

        List<ProposalDraft> all = new ArrayList<>(production);
        all.addAll(purchases);
        return new MrpResult(all, warnings);
    }

    private void planProducts(List<ProposalDraft> production, List<Demand> materialDemands) {
        Map<Long, List<Demand>> byProduct = new TreeMap<>();
        for (Demand d : in.demands()) {
            if (d.item().type() == ItemType.PRODUCT) {
                byProduct.computeIfAbsent(d.item().id(), k -> new ArrayList<>()).add(d);
            }
        }
        byProduct.forEach((productId, demands) -> {
            ProductPlan plan = in.products().get(productId);
            if (plan == null) {
                warnings.add("Product " + productId + " has demand but is not plannable (inactive or missing)");
                return;
            }
            if (plan.bom() == null) {
                warnings.add("Product " + productId + " has demand but no ACTIVE BOM; skipped");
                return;
            }
            List<Demand> relevant = plan.mto()
                    ? demands.stream().filter(d -> "SALES_ORDER".equals(d.sourceType())).toList()
                    : demands;
            ItemKey key = new ItemKey(ItemType.PRODUCT, productId);
            planItem(key, relevant, plan.lot(), (bucket, chunk) -> {
                Dates dates = offset(bucket.date(), plan.leadDays());
                production.add(new ProposalDraft("PRODUCTION", null, productId, plan.bom().bomId(), plan.uom(),
                        chunk.required(), chunk.proposed(), dates.need(), dates.order(), plan.leadDays(),
                        dates.adjusted(), bucket.sourceType(), bucket.sourceId(), plan.lot().rule(), null, false));
                explode(plan.bom(), chunk.proposed(), dates.order(), materialDemands);
            });
        });
    }

    private void planMaterials(List<ProposalDraft> purchases, List<Demand> materialDemands) {
        Map<Long, List<Demand>> byMaterial = new TreeMap<>();
        for (Demand d : materialDemands) {
            byMaterial.computeIfAbsent(d.item().id(), k -> new ArrayList<>()).add(d);
        }
        byMaterial.forEach((materialId, demands) -> {
            MaterialPlan plan = in.materials().get(materialId);
            if (plan == null) {
                warnings.add("Material " + materialId + " has demand but is not plannable (inactive or missing)");
                return;
            }
            ItemKey key = new ItemKey(ItemType.MATERIAL, materialId);
            planItem(key, demands, plan.lot(), (bucket, chunk) -> {
                Dates dates = offset(bucket.date(), plan.leadDays());
                purchases.add(new ProposalDraft("PURCHASE", materialId, null, null, plan.uom(),
                        chunk.required(), chunk.proposed(), dates.need(), dates.order(), plan.leadDays(),
                        dates.adjusted(), bucket.sourceType(), bucket.sourceId(), plan.lot().rule(), null, false));
            });
        });
    }

    /** Nets each date bucket against the supplies arriving by that date; lot-sizes what remains. */
    private void planItem(ItemKey item, List<Demand> demands, LotParams lot,
                          BiConsumer<Bucket, Chunk> emit) {
        for (Bucket bucket : buckets(demands)) {
            BigDecimal need = bucket.qty();
            for (Slot slot : pool.getOrDefault(item, List.of())) {
                if (need.signum() <= 0) {
                    break;
                }
                if (slot.date.isAfter(bucket.date()) || slot.remaining.signum() <= 0) {
                    continue;
                }
                BigDecimal take = need.min(slot.remaining);
                slot.remaining = slot.remaining.subtract(take);
                need = need.subtract(take);
            }
            if (need.compareTo(new BigDecimal("0.0005")) < 0) {
                continue;
            }
            BigDecimal surplus = BigDecimal.ZERO;
            for (Chunk chunk : MrpLotSizing.size(need, lot)) {
                emit.accept(bucket, chunk);
                surplus = surplus.add(chunk.proposed().subtract(chunk.required()));
            }
            if (surplus.signum() > 0) {
                addSupply(item, bucket.date(), surplus, SupplyKind.PLANNED);
            }
        }
    }

    private List<Bucket> buckets(List<Demand> demands) {
        Map<LocalDate, List<Demand>> byDate = new TreeMap<>();
        for (Demand d : demands) {
            LocalDate date = d.needDate().isBefore(in.today()) ? in.today() : d.needDate();
            if (date.isAfter(in.horizonEnd()) || d.qty().signum() <= 0) {
                continue;
            }
            byDate.computeIfAbsent(date, k -> new ArrayList<>()).add(d);
        }
        List<Bucket> buckets = new ArrayList<>();
        byDate.forEach((date, list) -> {
            BigDecimal total = list.stream().map(Demand::qty).reduce(BigDecimal.ZERO, BigDecimal::add);
            Demand dominant = list.stream().max(Comparator.comparing(Demand::qty)).orElseThrow();
            buckets.add(new Bucket(date, total, dominant.sourceType(), dominant.sourceId()));
        });
        return buckets;
    }

    private void explode(BomInfo bom, BigDecimal producedQty, LocalDate startDate, List<Demand> materialDemands) {
        for (BomLine line : bom.lines()) {
            BigDecimal scrapFactor = BigDecimal.ONE.add(
                    line.scrapPct().divide(BigDecimal.valueOf(100), MathContext.DECIMAL64));
            BigDecimal qty = producedQty.multiply(line.qty()).divide(bom.baseQty(), MathContext.DECIMAL64)
                    .multiply(scrapFactor).setScale(SCALE, RoundingMode.CEILING);
            materialDemands.add(new Demand(new ItemKey(ItemType.MATERIAL, line.materialId()), startDate, qty,
                    "BOM_EXPLOSION", null));
        }
    }

    private record Dates(LocalDate order, LocalDate need, boolean adjusted) {}

    /** Rule 21: the order date never precedes today and need date never precedes order date + lead time. */
    private Dates offset(LocalDate needDate, int leadDays) {
        LocalDate order = needDate.minusDays(leadDays);
        if (order.isBefore(in.today())) {
            order = in.today();
        }
        LocalDate earliest = order.plusDays(leadDays);
        boolean adjusted = earliest.isAfter(needDate);
        return new Dates(order, adjusted ? earliest : needDate, adjusted);
    }

    private void addSupply(ItemKey item, LocalDate date, BigDecimal qty, SupplyKind kind) {
        if (qty.signum() <= 0) {
            return;
        }
        boolean mtoProduct = item.type() == ItemType.PRODUCT
                && in.products().containsKey(item.id()) && in.products().get(item.id()).mto();
        if (kind == SupplyKind.STOCK && mtoProduct) {
            return;
        }
        List<Slot> slots = pool.computeIfAbsent(item, k -> new ArrayList<>());
        slots.add(new Slot(date, qty));
        slots.sort(Comparator.comparing(s -> s.date));
    }

    private void checkCapacity(List<ProposalDraft> production) {
        Map<String, BigDecimal> load = new HashMap<>();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < production.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparing(i -> production.get(i).orderDate()));
        ProposalDraft[] checked = new ProposalDraft[production.size()];
        for (int i : order) {
            ProposalDraft p = production.get(i);
            ProductPlan plan = in.products().get(p.productId());
            BigDecimal total = BigDecimal.ZERO;
            boolean overload = false;
            LocalDate week = p.orderDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            for (RoutingStep step : plan.routing()) {
                BigDecimal hours = p.proposedQty().multiply(step.standardTimeMin())
                        .add(step.setupTimeMin()).divide(MINUTES_PER_HOUR, SCALE, RoundingMode.HALF_UP);
                total = total.add(hours);
                if (step.machineId() == null) {
                    continue;
                }
                String key = step.machineId() + "@" + week;
                BigDecimal next = load.getOrDefault(key, BigDecimal.ZERO).add(hours);
                load.put(key, next);
                if (next.compareTo(in.machineHoursPerWeek()) > 0) {
                    overload = true;
                }
            }
            checked[i] = p.withCapacity(total, overload);
        }
        for (int i = 0; i < checked.length; i++) {
            production.set(i, checked[i]);
        }
    }
}
