package com.springmfg.ims.masterdata;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** A material that is the primary material of active products is in use. */
@Component
class ProductMaterialUsage implements MaterialUsageCheck {

    private final JdbcTemplate jdbc;

    ProductMaterialUsage(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> deactivationWarnings(long materialId) {
        Integer products = jdbc.queryForObject(
                "SELECT count(*) FROM products WHERE primary_material_id = ? AND active", Integer.class, materialId);
        return products == null || products == 0 ? List.of()
                : List.of(products + (products == 1 ? " active product uses" : " active products use") + " it as primary material");
    }
}
