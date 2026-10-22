package com.mfg.ims.finance.ap;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mfg.ims.finance.ap.service.ThreeWayMatchCalculator;
import com.mfg.ims.finance.ap.service.ThreeWayMatchCalculator.Outcome;

/** Every expected figure is worked by hand in the comment above its assertion. */
class ThreeWayMatchCalculatorTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static Outcome match(String subtotal, String tax, String receipt, String receiptTax, String invoiced,
            String pricePct, String absolute) {
        return ThreeWayMatchCalculator.evaluate(bd(subtotal), bd(tax), bd(receipt), bd(receiptTax), bd(invoiced),
                bd(pricePct), bd(absolute));
    }

    @Test
    @DisplayName("TWM-U1: an invoice equal to the received value matches and clears all of it")
    void exactMatch() {
        // receipt 100 x 200 = 20,000; invoice 20,000 -> open 20,000, cleared 20,000, no variance
        Outcome o = match("20000", "0", "20000", "0", "0", "0", "0");

        assertThat(o.matched()).isTrue();
        assertThat(o.open()).isEqualByComparingTo("20000");
        assertThat(o.cleared()).isEqualByComparingTo("20000");
        assertThat(o.variance()).isEqualByComparingTo("0");
        assertThat(o.message()).isNull();
    }

    @Test
    @DisplayName("TWM-U2: 25,000 against a 20,000 receipt with zero tolerance is an exception that names both figures")
    void overInvoicedIsAnException() {
        Outcome o = match("25000", "0", "20000", "0", "0", "0", "0");

        assertThat(o.matched()).isFalse();
        assertThat(o.cleared()).isEqualByComparingTo("0");
        assertThat(o.variance()).isEqualByComparingTo("0");
        assertThat(o.message()).contains("25000.00").contains("20000.00").contains("AP_MATCH_OVERRIDE");
    }

    @Test
    @DisplayName("TWM-U3: 2% tolerance on 20,000 is 400, so 20,300 matches with a price variance of 300")
    void withinPercentTolerance() {
        // tolerance = 20,000 x 2 / 100 = 400; 20,300 <= 20,400 -> cleared 20,000, variance 20,300 - 20,000 = 300
        Outcome o = match("20300", "0", "20000", "0", "0", "2", "0");

        assertThat(o.matched()).isTrue();
        assertThat(o.cleared()).isEqualByComparingTo("20000");
        assertThat(o.variance()).isEqualByComparingTo("300");
    }

    @Test
    @DisplayName("TWM-U4: the tolerance boundary is inclusive, one paisa over is an exception")
    void toleranceBoundary() {
        assertThat(match("20400.00", "0", "20000", "0", "0", "2", "0").matched()).isTrue();
        assertThat(match("20400.01", "0", "20000", "0", "0", "2", "0").matched()).isFalse();
    }

    @Test
    @DisplayName("TWM-U5: the larger of the percentage and the absolute tolerance applies")
    void absoluteToleranceWinsWhenLarger() {
        // 0.1% of 20,000 = 20 < absolute 50 -> tolerance 50
        assertThat(match("20050", "0", "20000", "0", "0", "0.1", "50").matched()).isTrue();
        assertThat(match("20050.01", "0", "20000", "0", "0", "0.1", "50").matched()).isFalse();
        // 5% of 20,000 = 1,000 > absolute 50 -> tolerance 1,000
        assertThat(match("21000", "0", "20000", "0", "0", "5", "50").matched()).isTrue();
    }

    @Test
    @DisplayName("TWM-U6: two partial invoices that sum to the receipt both match; a third is already fully invoiced")
    void partialInvoicesAddUpToTheReceipt() {
        // first 12,000 of 20,000
        Outcome first = match("12000", "0", "20000", "0", "0", "0", "0");
        assertThat(first.matched()).isTrue();
        assertThat(first.cleared()).isEqualByComparingTo("12000");
        // second 8,000 with 12,000 already invoiced -> open 8,000
        Outcome second = match("8000", "0", "20000", "0", "12000", "0", "0");
        assertThat(second.matched()).isTrue();
        assertThat(second.open()).isEqualByComparingTo("8000");
        assertThat(second.cleared()).isEqualByComparingTo("8000");
        // nothing open any more: even 1 is an exception, and the message says why
        Outcome third = match("1", "0", "20000", "0", "20000", "0", "0");
        assertThat(third.matched()).isFalse();
        assertThat(third.message()).contains("already fully invoiced");
    }

    @Test
    @DisplayName("TWM-U7: billing less than the open value is a partial invoice, not an exception")
    void underInvoicingIsAPartialInvoice() {
        Outcome o = match("15000", "0", "20000", "0", "0", "0", "0");

        assertThat(o.matched()).isTrue();
        assertThat(o.cleared()).isEqualByComparingTo("15000");
        assertThat(o.variance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("TWM-U8: invoice tax is checked against the purchase-order rate on what the invoice clears")
    void taxIsChecked() {
        // receipt 20,000 carrying PO tax 3,600 (18%): an invoice of 20,000 may carry 3,600
        assertThat(match("20000", "3600", "20000", "3600", "0", "0", "0").matched()).isTrue();
        // 3,700 is 100 more than the PO implies
        Outcome o = match("20000", "3700", "20000", "3600", "0", "0", "0");
        assertThat(o.matched()).isFalse();
        assertThat(o.message()).contains("3700.00").contains("3600.00");
        // an absolute tolerance of 100 covers it
        assertThat(match("20000", "3700", "20000", "3600", "0", "0", "100").matched()).isTrue();
        // a half invoice (10,000) implies 1,800
        assertThat(match("10000", "1800", "20000", "3600", "0", "0", "0").matched()).isTrue();
        assertThat(match("10000", "1801", "20000", "3600", "0", "0", "0").matched()).isFalse();
    }

    @Test
    @DisplayName("TWM-U9: approving an exception clears the open value and books the rest as price variance")
    void settleSplitsClearedAndVariance() {
        // 25,000 against 20,000 open: clears 20,000, variance 5,000
        Outcome o = ThreeWayMatchCalculator.settle(bd("25000"), bd("20000"), bd("0"));
        assertThat(o.cleared()).isEqualByComparingTo("20000");
        assertThat(o.variance()).isEqualByComparingTo("5000");
        // 20,000 already invoiced: nothing left to clear, the whole 25,000 is variance
        Outcome none = ThreeWayMatchCalculator.settle(bd("25000"), bd("20000"), bd("20000"));
        assertThat(none.cleared()).isEqualByComparingTo("0");
        assertThat(none.variance()).isEqualByComparingTo("25000");
        // cleared + variance always equals the invoice subtotal
        assertThat(o.cleared().add(o.variance())).isEqualByComparingTo("25000");
    }

    @Test
    @DisplayName("TWM-U10: amounts keep four decimals, rounded half up")
    void rounding() {
        // tolerance 1.5% of 333.3333 = 5.00000 -> 4.9999995 rounds half up to 5.0000
        Outcome o = match("338.3333", "0", "333.3333", "0", "0", "1.5", "0");
        assertThat(o.matched()).isTrue();
        assertThat(o.cleared()).isEqualByComparingTo("333.3333");
        assertThat(o.variance()).isEqualByComparingTo("5.0000");
        assertThat(match("338.3334", "0", "333.3333", "0", "0", "1.5", "0").matched()).isFalse();
    }
}
