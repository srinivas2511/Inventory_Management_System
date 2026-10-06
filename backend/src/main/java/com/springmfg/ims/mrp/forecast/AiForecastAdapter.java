package com.springmfg.ims.mrp.forecast;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.springmfg.ims.mrp.forecast.ForecastTypes.ForecastPoint;
import com.springmfg.ims.mrp.forecast.ForecastTypes.ProductHistory;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;

/**
 * The AI seam (R19-3.2): builds twelve months of dispatched quantity per product, asks the {@link AiForecastClient}
 * for a forecast, and keeps only what passes validation: a product that was in the history, a date inside the
 * horizon, a positive quantity below a sanity ceiling, and no duplicates.
 */
@Component
public class AiForecastAdapter implements DemandForecastProvider {

    public static final String NAME = "AI";
    static final int HISTORY_MONTHS = 12;
    static final int MAX_POINTS = 10_000;
    static final BigDecimal MAX_QUANTITY = new BigDecimal("1000000000");

    public record Batch(List<ForecastPoint> points, int rejected) {
    }

    private final AiForecastClient client;
    private final SalesHistorySource historySource;

    public AiForecastAdapter(AiForecastClient client, SalesHistorySource historySource) {
        this.client = client;
        this.historySource = historySource;
    }

    @Override
    public String name() {
        return NAME;
    }

    public boolean isConfigured() {
        return client.isConfigured();
    }

    public String endpointHost() {
        return client.endpointHost();
    }

    @Override
    public List<ForecastPoint> forecast(int horizonDays) {
        return fetch(horizonDays).points();
    }

    public Batch fetch(int horizonDays) {
        if (!client.isConfigured()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "No AI forecast endpoint is configured");
        }
        List<ProductHistory> history = historySource.history(HISTORY_MONTHS);
        if (history.isEmpty()) {
            return new Batch(List.of(), 0);
        }
        List<ForecastPoint> returned;
        try {
            returned = client.forecast(history, horizonDays);
        } catch (IOException e) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "The forecast service could not be reached or answered badly: " + e.getMessage());
        }
        return validate(returned, history, horizonDays, LocalDate.now());
    }

    static Batch validate(List<ForecastPoint> returned, List<ProductHistory> history, int horizonDays, LocalDate today) {
        Set<Long> known = new HashSet<>();
        history.forEach(h -> known.add(h.productId()));
        LocalDate last = today.plusDays(horizonDays);
        Set<String> seen = new HashSet<>();
        List<ForecastPoint> accepted = new ArrayList<>();
        int rejected = 0;
        for (ForecastPoint p : returned) {
            boolean ok = p != null && p.productId() != null && known.contains(p.productId())
                    && p.targetDate() != null && !p.targetDate().isBefore(today) && !p.targetDate().isAfter(last)
                    && p.quantity() != null && p.quantity().signum() > 0 && p.quantity().compareTo(MAX_QUANTITY) <= 0
                    && accepted.size() < MAX_POINTS && seen.add(p.productId() + "@" + p.targetDate());
            if (ok) {
                accepted.add(new ForecastPoint(p.productId(), p.targetDate(), p.quantity().setScale(3, RoundingMode.HALF_UP)));
            } else {
                rejected++;
            }
        }
        return new Batch(accepted, rejected);
    }
}
