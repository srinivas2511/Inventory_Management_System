package com.mfg.ims.mrp.engine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.mfg.ims.mrp.engine.MrpModel.LotParams;

/** Lot-sizing rules (MRP-003): EXACT, FIXED, MIN_MAX and EOQ. */
public final class MrpLotSizing {

    private static final int SCALE = 3;

    private MrpLotSizing() {}

    /** One planned lot: {@code required} is the part of the net requirement it covers, {@code proposed} the lot size. */
    public record Chunk(BigDecimal required, BigDecimal proposed) {}

    public static List<Chunk> size(BigDecimal net, LotParams lot) {
        BigDecimal n = net.setScale(SCALE, RoundingMode.HALF_UP);
        List<Chunk> chunks = new ArrayList<>();
        switch (lot.rule()) {
            case "FIXED" -> {
                BigDecimal lots = n.divide(lot.fixedQty(), 0, RoundingMode.CEILING);
                chunks.add(new Chunk(n, lots.multiply(lot.fixedQty()).setScale(SCALE, RoundingMode.HALF_UP)));
            }
            case "MIN_MAX" -> minMax(n, lot, chunks);
            case "EOQ" -> chunks.add(new Chunk(n, n.max(eoq(lot))));
            default -> chunks.add(new Chunk(n, n));
        }
        return chunks.stream().map(c -> new Chunk(c.required().setScale(SCALE, RoundingMode.HALF_UP),
                c.proposed().setScale(SCALE, RoundingMode.HALF_UP))).toList();
    }

    /** Economic order quantity sqrt(2DS/H), rounded up to the quantity scale. */
    public static BigDecimal eoq(LotParams lot) {
        BigDecimal ratio = BigDecimal.valueOf(2).multiply(lot.eoqAnnualDemand()).multiply(lot.eoqOrderCost())
                .divide(lot.eoqHoldingCost(), MathContext.DECIMAL64);
        return ratio.sqrt(MathContext.DECIMAL64).setScale(SCALE, RoundingMode.UP);
    }

    private static void minMax(BigDecimal net, LotParams lot, List<Chunk> chunks) {
        BigDecimal min = lot.minQty() == null ? BigDecimal.ZERO : lot.minQty();
        BigDecimal max = lot.maxQty();
        BigDecimal remaining = net;
        if (max != null) {
            while (remaining.compareTo(max) > 0) {
                chunks.add(new Chunk(max, max));
                remaining = remaining.subtract(max);
            }
        }
        if (remaining.signum() > 0) {
            chunks.add(new Chunk(remaining, remaining.max(min)));
        }
    }
}
