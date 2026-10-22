package com.mfg.ims.mrp.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mfg.ims.mrp.forecast.AiForecastAdapter.Batch;
import com.mfg.ims.mrp.forecast.ForecastTypes.ForecastPoint;
import com.mfg.ims.mrp.forecast.ForecastTypes.MonthlyQuantity;
import com.mfg.ims.mrp.forecast.ForecastTypes.ProductHistory;
import com.mfg.ims.common.exception.BusinessRuleException;

/** The model is outside the trust boundary: only validated points get through. */
class AiForecastAdapterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final List<ProductHistory> HISTORY = List.of(
            new ProductHistory(1L, "P-1", List.of(new MonthlyQuantity("2026-09", new BigDecimal("100")))),
            new ProductHistory(2L, "P-2", List.of(new MonthlyQuantity("2026-09", new BigDecimal("40")))));

    private static ForecastPoint p(Long product, LocalDate date, String qty) {
        return new ForecastPoint(product, date, qty == null ? null : new BigDecimal(qty));
    }

    @Test
    void validationKeepsOnlyWhatIsSaneAndCountsTheRest() {
        List<ForecastPoint> returned = Arrays.asList(
                p(1L, TODAY, "100"),                                  // accepted: today is inside the horizon
                p(1L, TODAY.plusDays(30), "50.1234"),                 // accepted: last day, rounded to 3 places
                p(2L, TODAY.plusDays(15), "10"),                      // accepted
                p(3L, TODAY.plusDays(5), "10"),                       // rejected: not a product in the history
                p(1L, TODAY.minusDays(1), "10"),                      // rejected: in the past
                p(1L, TODAY.plusDays(31), "10"),                      // rejected: beyond the horizon
                p(1L, TODAY.plusDays(6), "0"),                        // rejected: zero
                p(1L, TODAY.plusDays(7), "-5"),                       // rejected: negative
                p(1L, TODAY, "7"),                                    // rejected: duplicate of the first
                p(1L, TODAY.plusDays(8), "2000000000"),               // rejected: above the sanity ceiling
                p(null, TODAY.plusDays(9), "1"),                      // rejected: no product
                p(1L, TODAY.plusDays(10), null),                      // rejected: no quantity
                null);                                                // rejected: not a point

        Batch batch = AiForecastAdapter.validate(returned, HISTORY, 30, TODAY);

        assertThat(batch.points()).extracting(ForecastPoint::productId, ForecastPoint::targetDate)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, TODAY),
                        org.assertj.core.groups.Tuple.tuple(1L, TODAY.plusDays(30)),
                        org.assertj.core.groups.Tuple.tuple(2L, TODAY.plusDays(15)));
        assertThat(batch.points().get(1).quantity()).isEqualByComparingTo("50.123");
        assertThat(batch.rejected()).isEqualTo(10);
    }

    @Test
    void aRunawayModelIsCappedAtTenThousandPoints() {
        List<ForecastPoint> many = new ArrayList<>();
        for (int i = 0; i < 10_001; i++) {
            many.add(p(1L, TODAY.plusDays(i % 365), "1"));
        }
        // 365 distinct dates for product 1 are accepted; the repeats are duplicates
        Batch batch = AiForecastAdapter.validate(many, HISTORY, 365, TODAY);
        assertThat(batch.points()).hasSize(365);
        assertThat(batch.rejected()).isEqualTo(10_001 - 365);

        List<ForecastPoint> distinct = new ArrayList<>();
        List<ProductHistory> wide = new ArrayList<>();
        for (long id = 1; id <= 10_001; id++) {
            wide.add(new ProductHistory(id, "P" + id, List.of()));
            distinct.add(p(id, TODAY.plusDays(1), "1"));
        }
        Batch capped = AiForecastAdapter.validate(distinct, wide, 30, TODAY);
        assertThat(capped.points()).hasSize(AiForecastAdapter.MAX_POINTS);
        assertThat(capped.rejected()).isEqualTo(1);
    }

    @Test
    void anEmptyHistoryNeverCallsTheModel() {
        boolean[] called = {false};
        AiForecastClient client = new AiForecastClient() {
            @Override public boolean isConfigured() { return true; }
            @Override public String endpointHost() { return "model.example"; }
            @Override public List<ForecastPoint> forecast(List<ProductHistory> h, int days) {
                called[0] = true;
                return List.of();
            }
        };
        var adapter = new AiForecastAdapter(client, months -> List.of());
        assertThat(adapter.fetch(30).points()).isEmpty();
        assertThat(called[0]).isFalse();
    }

    @Test
    void anUnconfiguredClientIsAnErrorNotASilentEmptyResult() {
        AiForecastClient off = new AiForecastClient() {
            @Override public boolean isConfigured() { return false; }
            @Override public String endpointHost() { return null; }
            @Override public List<ForecastPoint> forecast(List<ProductHistory> h, int days) { return List.of(); }
        };
        var adapter = new AiForecastAdapter(off, months -> HISTORY);
        assertThatThrownBy(() -> adapter.fetch(30)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aFailingModelBecomesABusinessError() {
        AiForecastClient broken = new AiForecastClient() {
            @Override public boolean isConfigured() { return true; }
            @Override public String endpointHost() { return "model.example"; }
            @Override public List<ForecastPoint> forecast(List<ProductHistory> h, int days) throws IOException {
                throw new IOException("The forecast service answered HTTP 503");
            }
        };
        var adapter = new AiForecastAdapter(broken, months -> HISTORY);
        assertThatThrownBy(() -> adapter.fetch(30)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("HTTP 503");
    }
}
