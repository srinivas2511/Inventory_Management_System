package com.springmfg.ims.mrp.forecast;

import java.sql.Date;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.springmfg.ims.mrp.forecast.AiForecastAdapter.Batch;
import com.springmfg.ims.mrp.forecast.ForecastTypes.ForecastPoint;
import com.springmfg.ims.mrp.forecast.ForecastTypes.ForecastResult;
import com.springmfg.ims.mrp.forecast.ForecastTypes.ProviderInfo;
import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.mrp.dto.SaveDemandForecastRequest;
import com.springmfg.ims.mrp.service.DemandForecastService;

/**
 * Generates demand forecasts. With an AI endpoint configured the validated proposals are stored in
 * {@code demand_forecasts}, where MRP already reads them; a forecast a planner entered by hand is never overwritten,
 * only rows this service created earlier (notes starting "AI forecast") are refreshed. Without an endpoint the
 * default provider just reports the manual forecasts and creates nothing.
 */
@Service
public class ForecastService {

    private static final Logger log = LoggerFactory.getLogger(ForecastService.class);
    static final String AI_NOTE = "AI forecast";
    static final int DEFAULT_HORIZON_DAYS = 90;
    static final int MAX_HORIZON_DAYS = 365;

    private final ManualDemandForecastProvider manual;
    private final AiForecastAdapter ai;
    private final DemandForecastService forecasts;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;

    public ForecastService(ManualDemandForecastProvider manual, AiForecastAdapter ai, DemandForecastService forecasts,
                           NamedParameterJdbcTemplate jdbc, AuditService audit) {
        this.manual = manual;
        this.ai = ai;
        this.forecasts = forecasts;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public ProviderInfo info() {
        return new ProviderInfo(ai.isConfigured() ? AiForecastAdapter.NAME : ManualDemandForecastProvider.NAME,
                ai.isConfigured(), ai.endpointHost());
    }

    public ForecastResult generate(Integer horizonDays) {
        int horizon = horizonDays == null ? DEFAULT_HORIZON_DAYS : Math.min(Math.max(horizonDays, 1), MAX_HORIZON_DAYS);
        if (!ai.isConfigured()) {
            return new ForecastResult(ManualDemandForecastProvider.NAME, false, manual.forecast(horizon).size(), 0, 0, 0, 0);
        }
        Batch batch = ai.fetch(horizon);
        int created = 0;
        int updated = 0;
        int keptManual = 0;
        int rejected = batch.rejected();
        String note = AI_NOTE + " " + LocalDate.now();
        for (ForecastPoint p : batch.points()) {
            try {
                var existing = jdbc.query("""
                        SELECT id, notes FROM ims.demand_forecasts WHERE product_id = :p AND target_date = :d
                        """, new MapSqlParameterSource("p", p.productId()).addValue("d", Date.valueOf(p.targetDate())),
                        (rs, i) -> new Object[] {rs.getLong("id"), rs.getString("notes")});
                var request = new SaveDemandForecastRequest(p.productId(), p.targetDate(), p.quantity(), note);
                if (existing.isEmpty()) {
                    forecasts.create(request);
                    created++;
                } else if (existing.get(0)[1] != null && ((String) existing.get(0)[1]).startsWith(AI_NOTE)) {
                    forecasts.update((Long) existing.get(0)[0], request);
                    updated++;
                } else {
                    keptManual++;
                }
            } catch (RuntimeException e) {
                rejected++;
                log.info("AI forecast point for product {} on {} was not stored: {}", p.productId(), p.targetDate(),
                        e.getClass().getSimpleName());
            }
        }
        audit.record("AI_FORECAST_IMPORT", "DEMAND_FORECAST", null, null,
                "{\"horizonDays\":" + horizon + ",\"proposed\":" + batch.points().size() + ",\"created\":" + created
                        + ",\"updated\":" + updated + ",\"keptManual\":" + keptManual + ",\"rejected\":" + rejected + "}",
                null);
        return new ForecastResult(AiForecastAdapter.NAME, true, batch.points().size(), created, updated, keptManual,
                rejected);
    }
}
