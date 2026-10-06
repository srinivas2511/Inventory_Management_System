package com.springmfg.ims.mrp.forecast;

import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.springmfg.ims.mrp.forecast.ForecastTypes.ForecastPoint;

/** Default provider: the forecasts a planner entered, within the horizon. It creates nothing. */
@Component
public class ManualDemandForecastProvider implements DemandForecastProvider {

    public static final String NAME = "MANUAL";

    private final NamedParameterJdbcTemplate jdbc;

    public ManualDemandForecastProvider(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<ForecastPoint> forecast(int horizonDays) {
        LocalDate today = LocalDate.now();
        return jdbc.query("""
                SELECT product_id, target_date, quantity FROM ims.demand_forecasts
                 WHERE target_date BETWEEN :from AND :to ORDER BY target_date, product_id
                """,
                new MapSqlParameterSource("from", Date.valueOf(today)).addValue("to", Date.valueOf(today.plusDays(horizonDays))),
                (rs, i) -> new ForecastPoint(rs.getLong("product_id"), rs.getDate("target_date").toLocalDate(),
                        rs.getBigDecimal("quantity").setScale(3, RoundingMode.HALF_UP)));
    }
}
