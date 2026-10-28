package com.mfg.ims.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

import com.mfg.ims.common.settings.SystemSettingRepository;
import com.mfg.ims.inventory.dto.PostingCommand;
import com.mfg.ims.inventory.dto.PostingResult;
import com.mfg.ims.inventory.repository.InventoryBalanceRepository;
import com.mfg.ims.inventory.repository.InventoryTransactionRepository;
import com.mfg.ims.inventory.service.BatchService;
import com.mfg.ims.inventory.service.InventoryPostingService;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.material.repository.MaterialRepository;
import com.mfg.ims.support.AbstractIntegrationTest;

/**
 * Verifies that MOVING_AVERAGE and STANDARD costing methods produce the correct
 * balance avgUnitCost and issue cost (PLAN R10-6.4).
 * Setting key: enterprise.costing_method (seeded in V54, default MOVING_AVERAGE).
 */
class CostingMethodIT extends AbstractIntegrationTest {

    static final Long WH_ID  = 1L;
    static final Long LOC_ID = 1L;

    @Autowired InventoryPostingService postingService;
    @Autowired InventoryBalanceRepository balanceRepo;
    @Autowired InventoryTransactionRepository txRepo;
    @Autowired BatchService batchService;
    @Autowired MaterialRepository materialRepo;
    @Autowired SystemSettingRepository settingRepo;
    @Autowired CacheManager cacheManager;

    private Long matId;
    private Long batchId;

    @BeforeEach
    void setup() {
        Material mat = new Material();
        mat.setMaterialCode("COST-TST-" + System.nanoTime());
        mat.setName("Costing Test Wire");
        mat.setMaterialType("WIRE");
        mat.setUom("KG");
        mat.setMinStock(BigDecimal.ZERO);
        mat.setReorderLevel(BigDecimal.ZERO);
        mat.setStandardCost(new BigDecimal("50.0000")); // standard cost = 50
        mat = materialRepo.save(mat);
        matId = mat.getId();

        var batch = batchService.createMaterialBatch(
                matId, null, null, null,
                null, null, null,
                new BigDecimal("1000.000"), null, null,
                new BigDecimal("50.0000"), "AVAILABLE");
        batchId = batch.getId();
    }

    @AfterEach
    void restoreMovingAverage() {
        settingRepo.findById("enterprise.costing_method").ifPresent(s -> {
            s.setValue("MOVING_AVERAGE");
            settingRepo.save(s);
        });
        var cache = cacheManager.getCache("systemSettings");
        if (cache != null) cache.evict("enterprise.costing_method");
    }

    // ── CMIT-1: MOVING_AVERAGE — balance average reflects actual receipt prices ─

    @Test
    @DisplayName("CMIT-1: MOVING_AVERAGE — two receipts at different prices yield a weighted average")
    void movingAverage_twoReceipts_yieldWeightedAverage() {
        // receipt 1: 100 kg at 40 → avg = 40
        receipt(new BigDecimal("100.000"), new BigDecimal("40.0000"));
        // receipt 2: 100 kg at 60 → avg = (100×40 + 100×60) / 200 = 50
        receipt(new BigDecimal("100.000"), new BigDecimal("60.0000"));

        var bal = balanceRepo.findAll().stream()
                .filter(b -> matId.equals(b.getMaterialId()) && WH_ID.equals(b.getWarehouseId()))
                .findFirst().orElseThrow();

        assertThat(bal.getAvgUnitCost()).isEqualByComparingTo("50.0000");
        assertThat(bal.getQtyOnHand()).isEqualByComparingTo("200.000");
    }

    // ── CMIT-2: STANDARD — balance average stays at standard_cost ────────────────

    @Test
    @DisplayName("CMIT-2: STANDARD costing — balance avgUnitCost equals standard_cost regardless of receipt price")
    void standardCosting_receipt_balanceCarriesStandardCost() {
        setStandardCosting();

        // receipt 1: 100 kg at 30 (below standard=50) → balance avg should stay at 50
        receipt(new BigDecimal("100.000"), new BigDecimal("30.0000"));
        // receipt 2: 100 kg at 80 (above standard=50) → balance avg should stay at 50
        receipt(new BigDecimal("100.000"), new BigDecimal("80.0000"));

        var bal = balanceRepo.findAll().stream()
                .filter(b -> matId.equals(b.getMaterialId()) && WH_ID.equals(b.getWarehouseId()))
                .findFirst().orElseThrow();

        assertThat(bal.getAvgUnitCost()).isEqualByComparingTo("50.0000");
        assertThat(bal.getQtyOnHand()).isEqualByComparingTo("200.000");
    }

    // ── CMIT-3: STANDARD — OUT leg is costed at standard_cost ───────────────────

    @Test
    @DisplayName("CMIT-3: STANDARD costing — issue unitCost equals standard_cost, not the receipt price")
    void standardCosting_issue_unitCostEqualsStandard() {
        setStandardCosting();

        // receive at 80 (above standard)
        receipt(new BigDecimal("100.000"), new BigDecimal("80.0000"));

        // issue some stock — should be costed at standard (50), not the receipt price (80)
        PostingResult issueResult = postingService.post(new PostingCommand(
                "MATERIAL_ISSUE", "MATERIAL", matId, null, batchId, null,
                WH_ID, LOC_ID, "AVAILABLE",
                new BigDecimal("10.000"), "KG", null,
                "MATERIAL_ISSUE", 1L, "REF-ISS-" + System.nanoTime(), "issue test"));

        assertThat(issueResult.unitCost()).isEqualByComparingTo("50.0000");
        var tx = txRepo.findById(issueResult.transactionId()).orElseThrow();
        assertThat(tx.getUnitCost()).isEqualByComparingTo("50.0000");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private PostingResult receipt(BigDecimal qty, BigDecimal unitCost) {
        return postingService.post(new PostingCommand(
                "GOODS_RECEIPT", "MATERIAL", matId, null, batchId, null,
                WH_ID, LOC_ID, "AVAILABLE",
                qty, "KG", unitCost,
                "GOODS_RECEIPT", 1L, "REF-GR-" + System.nanoTime(), "receipt"));
    }

    private void setStandardCosting() {
        settingRepo.findById("enterprise.costing_method").ifPresent(s -> {
            s.setValue("STANDARD");
            settingRepo.save(s);
        });
        var cache = cacheManager.getCache("systemSettings");
        if (cache != null) cache.evict("enterprise.costing_method");
    }
}
