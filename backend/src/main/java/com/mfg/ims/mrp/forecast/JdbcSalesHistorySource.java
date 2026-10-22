package com.mfg.ims.mrp.forecast;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.mfg.ims.mrp.forecast.ForecastTypes.MonthlyQuantity;
import com.mfg.ims.mrp.forecast.ForecastTypes.ProductHistory;

@Component
public class JdbcSalesHistorySource implements SalesHistorySource {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSalesHistorySource(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ProductHistory> history(int months) {
        LocalDate since = LocalDate.now().minusMonths(months).withDayOfMonth(1);
        Map<Long, String> codes = new LinkedHashMap<>();
        Map<Long, List<MonthlyQuantity>> perProduct = new LinkedHashMap<>();
        jdbc.query("""
                SELECT di.product_id, p.product_code, to_char(date_trunc('month', d.dispatch_date), 'YYYY-MM') AS month,
                       SUM(di.quantity) AS qty
                  FROM ims.dispatch_items di
                  JOIN ims.dispatches d ON d.id = di.dispatch_id
                  JOIN ims.products p ON p.id = di.product_id
                 WHERE d.dispatch_date >= :since
                 GROUP BY di.product_id, p.product_code, date_trunc('month', d.dispatch_date)
                 ORDER BY di.product_id, date_trunc('month', d.dispatch_date)
                """, new MapSqlParameterSource("since", Date.valueOf(since)), rs -> {
            long id = rs.getLong("product_id");
            codes.put(id, rs.getString("product_code"));
            perProduct.computeIfAbsent(id, k -> new ArrayList<>())
                    .add(new MonthlyQuantity(rs.getString("month"), rs.getBigDecimal("qty")));
        });
        List<ProductHistory> out = new ArrayList<>();
        codes.forEach((id, code) -> out.add(new ProductHistory(id, code, perProduct.get(id))));
        return out;
    }
}
