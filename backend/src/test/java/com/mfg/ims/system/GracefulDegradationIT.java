package com.mfg.ims.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mfg.ims.analytics.AnalyticsDtos.RefreshResult;
import com.mfg.ims.analytics.AnalyticsViewRefresher;
import com.mfg.ims.analytics.ReportingHealthIndicator;
import com.mfg.ims.inventory.dto.PostingCommand;
import com.mfg.ims.inventory.dto.PostingResult;
import com.mfg.ims.inventory.service.BatchService;
import com.mfg.ims.inventory.service.InventoryPostingService;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.material.repository.MaterialRepository;
import com.mfg.ims.support.AbstractIntegrationTest;

/**
 * NFR-003: Graceful degradation — reporting/analytics failure must never block inventory posting.
 *
 * <p>Strategy: drop the unique index that {@code REFRESH MATERIALIZED VIEW CONCURRENTLY} requires,
 * then prove that (a) the refresh failure is captured rather than propagated, and (b) inventory
 * posting in a completely separate transaction succeeds anyway.  The unique index is restored in
 * {@code @AfterAll} so the shared test container is not permanently damaged.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class GracefulDegradationIT extends AbstractIntegrationTest {

    private static final long WH_ID  = 1L;
    private static final long LOC_ID = 1L;

    @Autowired AnalyticsViewRefresher refresher;
    @Autowired ReportingHealthIndicator reportingHealth;
    @Autowired InventoryPostingService postingService;
    @Autowired MaterialRepository materialRepo;
    @Autowired BatchService batchService;
    @Autowired JdbcTemplate jdbc;

    private Long matId;
    private Long batchId;

    @BeforeAll
    void createTestMaterial() {
        Material mat = new Material();
        mat.setMaterialCode("DEG-MAT-" + System.nanoTime());
        mat.setName("Degradation Test Wire");
        mat.setMaterialType("WIRE");
        mat.setUom("KG");
        mat.setMinStock(BigDecimal.ZERO);
        mat.setReorderLevel(BigDecimal.ZERO);
        mat.setStandardCost(new BigDecimal("10.0000"));
        mat = materialRepo.save(mat);
        matId = mat.getId();

        var batch = batchService.createMaterialBatch(
                matId, null, null, null,
                null, null, null,
                new BigDecimal("500.000"), null, null,
                new BigDecimal("10.0000"), "AVAILABLE");
        batchId = batch.getId();
    }

    @AfterAll
    void restoreUniqueIndex() {
        // Recreate the index that NFR-003-1 drops so no other IT is affected.
        try {
            jdbc.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS ux_mv_prod_monthly " +
                    "ON ims.mv_production_monthly (month)");
        } catch (Exception ignored) {
            // index already present — nothing to do
        }
    }

    @Test
    @Order(1)
    @DisplayName("NFR-003-1: refreshAll captures a failed view rather than propagating the exception")
    void brokenView_isCapturedNotPropagated() {
        // Drop the unique index so CONCURRENT refresh is impossible for this view.
        jdbc.execute("DROP INDEX IF EXISTS ims.ux_mv_prod_monthly");

        RefreshResult result = refresher.refreshAll();

        assertThat(result.failed())
                .as("mv_production_monthly must appear in the failed map")
                .containsKey("mv_production_monthly");
        assertThat(result.refreshed())
                .as("failed view must not appear in the refreshed list")
                .doesNotContain("mv_production_monthly");
    }

    @Test
    @Order(2)
    @DisplayName("NFR-003-2: inventory posting succeeds while analytics views are broken")
    void inventoryPosting_succeedsWithBrokenAnalytics() {
        // Index is still absent from test 1; confirm posting is completely independent.
        PostingResult receipt = postingService.post(new PostingCommand(
                "GOODS_RECEIPT", "MATERIAL", matId, null, batchId, null,
                WH_ID, LOC_ID, "AVAILABLE",
                new BigDecimal("50.000"), "KG", new BigDecimal("10.0000"),
                "GOODS_RECEIPT", 1L, "DEG-" + System.nanoTime(), "graceful degradation test"));

        assertThat(receipt.transactionId()).isPositive();
    }

    @Test
    @Order(3)
    @DisplayName("NFR-003-3: /actuator/health/readiness stays UP when reporting is degraded")
    void readinessProbe_staysUp_whileReportingDegraded() throws Exception {
        // Simulate a failed refresh result in the health indicator.
        reportingHealth.updateLastResult(new RefreshResult(
                List.of(), Map.of("mv_production_monthly", "MissingUniqueIndexError")));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @Order(4)
    @DisplayName("NFR-003-4: /actuator/health/reporting group shows DEGRADED when views fail to refresh")
    void reportingGroup_showsDegradedStatus() throws Exception {
        reportingHealth.updateLastResult(new RefreshResult(
                List.of(), Map.of("mv_production_monthly", "MissingUniqueIndexError")));

        mockMvc.perform(get("/actuator/health/reporting"))
                .andExpect(status().isOk())       // DEGRADED is mapped to HTTP 200 (non-critical)
                .andExpect(jsonPath("$.status").value("DEGRADED"));
    }
}
