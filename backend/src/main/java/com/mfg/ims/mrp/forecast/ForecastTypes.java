package com.mfg.ims.mrp.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Value types of the demand-forecast seam (DESIGN 25.2). */
public final class ForecastTypes {

    private ForecastTypes() {
    }

    /** One forecast quantity of a product on a date. */
    public record ForecastPoint(Long productId, LocalDate targetDate, BigDecimal quantity) {
    }

    /** What the model is given: dispatched quantity per month, oldest first. */
    public record ProductHistory(Long productId, String productCode, List<MonthlyQuantity> months) {
    }

    public record MonthlyQuantity(String month, BigDecimal quantity) {
    }

    /** What a generation run did. {@code provider} is the source that produced the proposals. */
    public record ForecastResult(String provider, boolean aiConfigured, int proposed, int created, int updated,
                                 int keptManual, int rejected) {
    }

    public record ProviderInfo(String active, boolean aiConfigured, String aiEndpointHost) {
    }
}
