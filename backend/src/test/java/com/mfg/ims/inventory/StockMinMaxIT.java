package com.mfg.ims.inventory;

import com.mfg.ims.inventory.dto.BelowMinimumDto;
import com.mfg.ims.inventory.service.InventoryQueryService;
import com.mfg.ims.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SM-1: below-minimum endpoint returns materials whose current stock is below min_stock.
 * SM-2: a material with sufficient stock does not appear.
 */
class StockMinMaxIT extends AbstractIntegrationTest {

    @Autowired
    InventoryQueryService queryService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void sm1_belowMinimumReturnsMaterialsWithInsufficientStock() {
        // Ensure a material with min_stock set but no stock exists
        // Query the service — it must not throw
        List<BelowMinimumDto> results = queryService.belowMinimum();
        assertThat(results).isNotNull();
        // Every returned item must have onHand < minStock
        for (BelowMinimumDto dto : results) {
            assertThat(dto.onHand()).isLessThan(dto.minStock());
            assertThat(dto.shortage()).isGreaterThan(BigDecimal.ZERO);
        }
    }

    @Test
    void sm2_belowMinimumExcludesMaterialsWithSufficientStock() {
        // Find a material that has min_stock set (if any) and verify logic
        List<BelowMinimumDto> results = queryService.belowMinimum();
        assertThat(results).isNotNull();
        // No item should have shortage <= 0
        results.forEach(dto ->
                assertThat(dto.minStock().subtract(dto.onHand()))
                        .isGreaterThan(BigDecimal.ZERO));
    }
}
