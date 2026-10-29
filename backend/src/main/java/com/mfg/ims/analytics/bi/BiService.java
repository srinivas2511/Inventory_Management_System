package com.mfg.ims.analytics.bi;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class BiService {

    private static final List<String> AREAS = List.of("sales", "purchase", "inventory", "production");

    private final JdbcTemplate jdbc;

    public BiService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> listAreas() {
        return AREAS;
    }

    public BiAreaDto getArea(String area) {
        return switch (area.toLowerCase()) {
            case "sales" -> querySales();
            case "purchase" -> queryPurchase();
            case "inventory" -> queryInventory();
            case "production" -> queryProduction();
            default -> new BiAreaDto(area, 0, BigDecimal.ZERO, Map.of());
        };
    }

    private BiAreaDto querySales() {
        var row = jdbc.queryForMap(
                "SELECT COUNT(*) AS cnt, COALESCE(SUM(grand_total),0) AS total FROM ims.bi_sales");
        return new BiAreaDto("sales", toLong(row.get("cnt")), toDecimal(row.get("total")), buildSummary(row));
    }

    private BiAreaDto queryPurchase() {
        var row = jdbc.queryForMap(
                "SELECT COUNT(*) AS cnt, COALESCE(SUM(grand_total),0) AS total FROM ims.bi_purchase");
        return new BiAreaDto("purchase", toLong(row.get("cnt")), toDecimal(row.get("total")), buildSummary(row));
    }

    private BiAreaDto queryInventory() {
        var row = jdbc.queryForMap(
                "SELECT COUNT(*) AS cnt, COALESCE(SUM(stock_value),0) AS total FROM ims.bi_inventory");
        return new BiAreaDto("inventory", toLong(row.get("cnt")), toDecimal(row.get("total")), buildSummary(row));
    }

    private BiAreaDto queryProduction() {
        var row = jdbc.queryForMap(
                "SELECT COUNT(*) AS cnt, COALESCE(SUM(planned_qty),0) AS total FROM ims.bi_production");
        return new BiAreaDto("production", toLong(row.get("cnt")), toDecimal(row.get("total")), buildSummary(row));
    }

    private Map<String, Object> buildSummary(Map<String, Object> row) {
        Map<String, Object> m = new HashMap<>(row);
        return m;
    }

    private long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        return 0L;
    }

    private BigDecimal toDecimal(Object v) {
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
