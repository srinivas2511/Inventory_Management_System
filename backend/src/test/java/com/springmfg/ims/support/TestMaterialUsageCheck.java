package com.springmfg.ims.support;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.springmfg.ims.masterdata.MaterialUsageCheck;

/**
 * Test-only: stands in for the stock and purchasing checks of Phases 2 and 3. A material whose code starts with
 * {@code LOCK.} is "in use": it warns on deactivation and may not change its unit of measure.
 */
@Component
public class TestMaterialUsageCheck implements MaterialUsageCheck {

    private final JdbcTemplate jdbc;

    public TestMaterialUsageCheck(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private boolean locked(long materialId) {
        String code = jdbc.queryForObject("SELECT material_code FROM materials WHERE id = ?", String.class, materialId);
        return code != null && code.startsWith("LOCK.");
    }

    @Override
    public List<String> deactivationWarnings(long materialId) {
        return locked(materialId) ? List.of("12.000 KG in stock", "2 open purchase orders") : List.of();
    }

    @Override
    public List<String> unitChangeBlockers(long materialId) {
        return locked(materialId) ? List.of("stock exists in KG") : List.of();
    }
}
