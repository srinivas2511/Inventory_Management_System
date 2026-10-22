package com.mfg.ims.finance.ap.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The arithmetic of the AP three-way match, kept free of persistence so it can be tested with hand-worked numbers.
 *
 * <p>The receipt value is what the goods receipt credited to GR/IR: received quantity times the purchase-order rate.
 * An invoice may bill any part of the value still open (a partial invoice), but not more than that plus the tolerance
 * (the larger of a percentage of the open value and an absolute amount). What it bills above the open value, within
 * tolerance, is purchase price variance; GR/IR is cleared for the open value only.
 */
public final class ThreeWayMatchCalculator {

    public static final String MATCHED = "MATCHED";
    public static final String EXCEPTION = "EXCEPTION";

    private static final int SCALE = 4;

    /**
     * @param status    MATCHED or EXCEPTION
     * @param open      goods-receipt value still open for matching
     * @param cleared   the part of the invoice subtotal that clears GR/IR
     * @param variance  the part of the invoice subtotal above the open value (purchase price variance)
     * @param message   why the invoice is an exception; null when it matched
     */
    public record Outcome(String status, BigDecimal open, BigDecimal cleared, BigDecimal variance, String message) {
        public boolean matched() {
            return MATCHED.equals(status);
        }
    }

    private ThreeWayMatchCalculator() {
    }

    public static Outcome evaluate(BigDecimal subtotal, BigDecimal taxTotal, BigDecimal receiptValue,
            BigDecimal receiptTax, BigDecimal alreadyInvoiced, BigDecimal pricePct, BigDecimal absoluteTolerance) {
        BigDecimal open = receiptValue.subtract(alreadyInvoiced).max(BigDecimal.ZERO).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal tolerance = tolerance(open, pricePct, absoluteTolerance);
        BigDecimal cleared = subtotal.min(open).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal variance = subtotal.subtract(cleared).setScale(SCALE, RoundingMode.HALF_UP);

        if (subtotal.compareTo(open.add(tolerance)) > 0) {
            String why = open.signum() == 0
                    ? "The goods receipt is already fully invoiced (" + show(receiptValue) + " received, "
                            + show(alreadyInvoiced) + " invoiced), so a further invoice of " + show(subtotal)
                            + " does not match."
                    : "Invoice subtotal " + show(subtotal) + " exceeds the " + show(open)
                            + " of the goods receipt still open for matching (received value " + show(receiptValue)
                            + ", already invoiced " + show(alreadyInvoiced) + ", tolerance " + show(tolerance) + ").";
            return new Outcome(EXCEPTION, open, BigDecimal.ZERO, BigDecimal.ZERO, why
                    + " Correct the invoice, or ask a finance manager with AP_MATCH_OVERRIDE to approve the variance.");
        }

        BigDecimal rate = receiptValue.signum() == 0 ? BigDecimal.ZERO
                : receiptTax.divide(receiptValue, 10, RoundingMode.HALF_UP);
        BigDecimal expectedTax = cleared.multiply(rate).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal taxTolerance = tolerance(expectedTax, pricePct, absoluteTolerance);
        if (taxTotal.compareTo(expectedTax.add(taxTolerance)) > 0) {
            return new Outcome(EXCEPTION, open, BigDecimal.ZERO, BigDecimal.ZERO,
                    "Invoice tax " + show(taxTotal) + " exceeds the " + show(expectedTax)
                            + " implied by the purchase-order tax rates (tolerance " + show(taxTolerance)
                            + "). Correct the invoice, or ask a finance manager with AP_MATCH_OVERRIDE to approve it.");
        }
        return new Outcome(MATCHED, open, cleared, variance, null);
    }

    /** What an approved exception clears and books as variance, computed against the value open at approval time. */
    public static Outcome settle(BigDecimal subtotal, BigDecimal receiptValue, BigDecimal alreadyInvoiced) {
        BigDecimal open = receiptValue.subtract(alreadyInvoiced).max(BigDecimal.ZERO).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal cleared = subtotal.min(open).setScale(SCALE, RoundingMode.HALF_UP);
        return new Outcome("OVERRIDDEN", open, cleared, subtotal.subtract(cleared).setScale(SCALE, RoundingMode.HALF_UP), null);
    }

    static BigDecimal tolerance(BigDecimal base, BigDecimal pricePct, BigDecimal absoluteTolerance) {
        BigDecimal byPercent = base.multiply(pricePct).divide(new BigDecimal("100"), SCALE, RoundingMode.HALF_UP);
        return byPercent.max(absoluteTolerance).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static String show(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
