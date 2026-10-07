package com.springmfg.ims.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.insight.report.ReportRequest;
import com.springmfg.ims.insight.report.definitions.InventoryStockReport;
import com.springmfg.ims.inventory.dto.InventoryBalanceDto;
import com.springmfg.ims.inventory.dto.PostingCommand;
import com.springmfg.ims.inventory.service.BatchService;
import com.springmfg.ims.inventory.service.InventoryPostingService;
import com.springmfg.ims.inventory.service.InventoryQueryService;
import com.springmfg.ims.material.domain.Material;
import com.springmfg.ims.material.repository.MaterialRepository;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;

/**
 * D-29 (UAT FIN-01): the inventory valuation report valued stock at the material's standard cost while the stock screen
 * values it at the moving-average cost carried on each balance. Both must show the ledger value.
 */
class InventoryValuationIT extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L);
    private static final long WAREHOUSE_ID = 1L;
    private static final long LOCATION_ID = 1L;

    @Autowired TestData data;
    @Autowired MaterialRepository materialRepo;
    @Autowired InventoryPostingService postingService;
    @Autowired InventoryQueryService inventoryQuery;
    @Autowired BatchService batchService;
    @Autowired InventoryStockReport report;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void actAsAUser() {
        String tag = "VAL" + SEQ.incrementAndGet();
        long userId = jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'Valuation IT', ?, 'x') RETURNING id""", Long.class, tag, tag + "@it.test");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CachedUser(userId, tag, true, 1, Set.of(), Set.of()), null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void receive(Material material, String qty, String unitCost, String status) {
        var batch = batchService.createMaterialBatch(material.getId(), null, null, null, null, null, null,
                new BigDecimal(qty), null, null, new BigDecimal(unitCost), status);
        postingService.post(new PostingCommand("PURCHASE_RECEIPT", "MATERIAL", material.getId(), null, batch.getId(),
                null, WAREHOUSE_ID, LOCATION_ID, status, new BigDecimal(qty), "KG", new BigDecimal(unitCost),
                "GOODS_RECEIPT", 1L, "REF-" + System.nanoTime(), "valuation IT"));
    }

    @Test
    @DisplayName("FIN-VAL-1 the valuation report shows the ledger value, equal to the stock screen, not standard cost")
    void reportEqualsTheStockScreenAtLedgerCost() {
        Material material = data.material(); // standard cost 50.0000
        material.setMaterialCode("0VAL" + SEQ.incrementAndGet()); // sorts first, so the report's page always has it
        materialRepo.save(material);

        receive(material, "100.000", "40.0000", "AVAILABLE");   // 100 x 40 = 4,000
        receive(material, "50.000", "60.0000", "QUARANTINE");   //  50 x 60 = 3,000  -> ledger value 7,000
        // standard cost would give 150 x 50 = 7,500: the old report's figure

        BigDecimal screen = inventoryQuery.queryBalances(null, material.getId(), null, null).stream()
                .map(InventoryBalanceDto::totalValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(screen).as("stock screen value for the material").isEqualByComparingTo("7000.0000");

        List<Map<String, Object>> rows = report.runQuery(new ReportRequest(null, null, null, null, 0, 500));
        Map<String, Object> row = rows.stream()
                .filter(r -> material.getMaterialCode().equals(String.valueOf(r.get("Material Code"))))
                .findFirst().orElseThrow(() -> new AssertionError("the material is missing from the valuation report"));
        BigDecimal reported = new BigDecimal(String.valueOf(row.get("Total Value (INR)")));

        assertThat(reported).as("valuation report value").isEqualByComparingTo("7000.0000");
        assertThat(reported).as("report equals the stock screen").isEqualByComparingTo(screen);
        assertThat(new BigDecimal(String.valueOf(row.get("Available Qty")))).isEqualByComparingTo("100.000");
    }
}
