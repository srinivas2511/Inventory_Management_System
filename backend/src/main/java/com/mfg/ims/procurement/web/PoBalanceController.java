package com.mfg.ims.procurement.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.procurement.dto.PoBalanceDto;

@RestController
@RequestMapping("/api/procurement/po-balance")
public class PoBalanceController {

    private final JdbcTemplate jdbc;

    public PoBalanceController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    public List<PoBalanceDto> balance(@RequestParam(required = false) Long poId) {
        String sql = poId != null
                ? "SELECT * FROM ims.po_balance WHERE po_id = ? ORDER BY line_id"
                : "SELECT * FROM ims.po_balance ORDER BY po_id DESC, line_id LIMIT 500";
        List<Map<String, Object>> rows = poId != null
                ? jdbc.queryForList(sql, poId)
                : jdbc.queryForList(sql);
        return rows.stream()
                .map(r -> new PoBalanceDto(
                        toLong(r.get("line_id")),
                        toLong(r.get("po_id")),
                        (String) r.get("po_number"),
                        toLong(r.get("material_id")),
                        (String) r.get("material_name"),
                        toBigDecimal(r.get("ordered_qty")),
                        toBigDecimal(r.get("received_qty")),
                        toBigDecimal(r.get("balance_qty")),
                        toBigDecimal(r.get("unit_price"))))
                .toList();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(v.toString());
    }
}
