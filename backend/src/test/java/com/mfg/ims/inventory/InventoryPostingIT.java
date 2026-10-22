package com.mfg.ims.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mfg.ims.common.exception.BusinessRuleException;
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
 * Business-rule and concurrency tests for the inventory ledger (PLAN tasks INV-01 … INV-06).
 */
class InventoryPostingIT extends AbstractIntegrationTest {

    @Autowired InventoryPostingService postingService;
    @Autowired InventoryBalanceRepository balanceRepo;
    @Autowired InventoryTransactionRepository txRepo;
    @Autowired BatchService batchService;
    @Autowired MaterialRepository materialRepo;
    @Autowired JdbcTemplate jdbc;

    // Seed data — MAIN-WH warehouse=1, raw-material location=1 from V2 migration
    static final Long WH_ID  = 1L;
    static final Long LOC_ID = 1L;

    private Long matId;
    private Long testBatchId;

    @BeforeEach
    void setup() {
        // Create a fresh test material (unique code to avoid collisions across test runs)
        Material mat = new Material();
        mat.setMaterialCode("TST-MAT-" + System.nanoTime());
        mat.setName("Test Wire");
        mat.setMaterialType("WIRE");
        mat.setUom("KG");
        mat.setMinStock(BigDecimal.ZERO);
        mat.setReorderLevel(BigDecimal.ZERO);
        mat.setStandardCost(new BigDecimal("10.0000"));
        mat = materialRepo.save(mat);
        matId = mat.getId();

        // Create a fresh batch
        var batch = batchService.createMaterialBatch(
                matId, null, null, null,
                null, null, null,
                new BigDecimal("1000.000"), null, null,
                new BigDecimal("10.0000"), "AVAILABLE");
        testBatchId = batch.getId();
    }

    // ── INV-01: parallel issues never oversell ──────────────────────────────────

    @Test
    @DisplayName("INV-01: parallel material issues do not oversell — balance stays non-negative")
    void parallelIssues_neverOversell() throws Exception {
        receipt(new BigDecimal("100.000"));

        int threads = 8;
        BigDecimal issueQty = new BigDecimal("20.000"); // 8 × 20 = 160 > 100
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go    = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    issue(issueQty);
                    return true;
                } catch (BusinessRuleException e) {
                    return false;
                }
            }));
        }

        ready.await();
        go.countDown();

        int successes = 0;
        for (var f : futures) {
            if (f.get()) successes++;
        }
        pool.shutdown();

        // At most 5 issues can succeed (5 × 20 = 100)
        assertThat(successes).isLessThanOrEqualTo(5);

        // Balance must never go negative
        balanceRepo.findBalance(WH_ID, LOC_ID, "MATERIAL",
                matId, null, testBatchId, null, "AVAILABLE")
                .ifPresent(b ->
                        assertThat(b.getQtyOnHand()).isGreaterThanOrEqualTo(BigDecimal.ZERO));
    }

    // ── INV-02: each posting generates a unique transaction number ──────────────

    @Test
    @DisplayName("INV-02: each posting generates a unique transaction number")
    void eachPosting_hasUniqueTransactionNumber() {
        PostingResult r1 = receipt(new BigDecimal("10.000"));
        PostingResult r2 = receipt(new BigDecimal("10.000"));

        assertThat(r1.transactionNumber()).isNotEqualTo(r2.transactionNumber());
        assertThat(txRepo.findByTransactionNumber(r1.transactionNumber())).isPresent();
        assertThat(txRepo.findByTransactionNumber(r2.transactionNumber())).isPresent();
    }

    // ── INV-03: Σ(ledger) == qty_on_hand after mixed postings ───────────────────

    @Test
    @DisplayName("INV-03: ledger sum equals balance qty_on_hand after mixed postings")
    void ledgerSum_equalsBalance_afterMixedPostings() {
        receipt(new BigDecimal("200.000"));
        receipt(new BigDecimal("50.000"));
        issue(new BigDecimal("30.000"));
        receipt(new BigDecimal("10.000"));

        var balance = balanceRepo.findBalance(WH_ID, LOC_ID, "MATERIAL",
                matId, null, testBatchId, null, "AVAILABLE");

        assertThat(balance).isPresent();
        BigDecimal onHand = balance.get().getQtyOnHand();

        BigDecimal ledgerSum = txRepo.sumBalanceLedger(
                WH_ID, LOC_ID, "MATERIAL", matId, null, testBatchId, null,
                "AVAILABLE", java.time.Instant.now());

        assertThat(onHand).isEqualByComparingTo(ledgerSum);
    }

    // ── INV-04: business rules 1–4 ──────────────────────────────────────────────

    @Test
    @DisplayName("INV-04a: MATERIAL_ISSUE without stock throws BusinessRuleException (rule 1 — no negative stock)")
    void issueWithoutStock_throwsInsufficientStock() {
        assertThatThrownBy(() -> issue(new BigDecimal("1.000")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("INV-04b: issue more than available throws BusinessRuleException (rule 2)")
    void issueMoreThanAvailable_throws() {
        receipt(new BigDecimal("10.000"));

        assertThatThrownBy(() -> issue(new BigDecimal("99.000")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("INV-04c: MATERIAL_ISSUE from QUARANTINE location is blocked (rule 3)")
    void issueFromQuarantine_blocked() {
        // Stock in QUARANTINE status
        postingService.post(new PostingCommand(
                "GOODS_RECEIPT", "MATERIAL", matId, null, testBatchId, null,
                WH_ID, LOC_ID, "QUARANTINE",
                new BigDecimal("50.000"), "KG", new BigDecimal("10.0000"),
                "GOODS_RECEIPT", 1L, "REF-Q-" + System.nanoTime(), "quarantine receipt"));

        assertThatThrownBy(() ->
                postingService.post(new PostingCommand(
                        "MATERIAL_ISSUE", "MATERIAL", matId, null, testBatchId, null,
                        WH_ID, LOC_ID, "QUARANTINE",
                        new BigDecimal("10.000"), "KG", null,
                        "MATERIAL_ISSUE", 1L, "REF-QI-" + System.nanoTime(), "try quarantine issue")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Quarantine");
    }

    @Test
    @DisplayName("INV-04d: MATERIAL_ISSUE from REJECTED location is blocked (rule 4)")
    void issueFromRejected_blocked() {
        postingService.post(new PostingCommand(
                "GOODS_RECEIPT", "MATERIAL", matId, null, testBatchId, null,
                WH_ID, LOC_ID, "REJECTED",
                new BigDecimal("50.000"), "KG", new BigDecimal("10.0000"),
                "GOODS_RECEIPT", 1L, "REF-R-" + System.nanoTime(), "rejected receipt"));

        assertThatThrownBy(() ->
                postingService.post(new PostingCommand(
                        "MATERIAL_ISSUE", "MATERIAL", matId, null, testBatchId, null,
                        WH_ID, LOC_ID, "REJECTED",
                        new BigDecimal("10.000"), "KG", null,
                        "MATERIAL_ISSUE", 1L, "REF-RI-" + System.nanoTime(), "try rejected issue")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Rejected");
    }

    // ── INV-05: ledger immutability — SQL UPDATE/DELETE raises exception ─────────

    @Test
    @DisplayName("INV-05: direct UPDATE on inventory_transactions is blocked by trigger")
    void ledgerImmutability_updateBlocked() {
        PostingResult r = receipt(new BigDecimal("5.000"));

        assertThatThrownBy(() ->
                jdbc.update("UPDATE ims.inventory_transactions SET quantity = 999 WHERE id = ?",
                        r.transactionId()))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("immutable");
    }

    @Test
    @DisplayName("INV-05: direct DELETE on inventory_transactions is blocked by trigger")
    void ledgerImmutability_deleteBlocked() {
        PostingResult r = receipt(new BigDecimal("5.000"));

        assertThatThrownBy(() ->
                jdbc.update("DELETE FROM ims.inventory_transactions WHERE id = ?",
                        r.transactionId()))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("immutable");
    }

    // ── INV-06: reversal creates an offsetting entry ────────────────────────────

    @Test
    @DisplayName("INV-06: reversing a transaction creates an offsetting ledger entry")
    void reversal_createsOffsettingEntry() {
        PostingResult r = receipt(new BigDecimal("50.000"));

        PostingResult rev = postingService.reverse(r.transactionId(), "test reversal");

        assertThat(rev.transactionId()).isNotEqualTo(r.transactionId());

        var original   = txRepo.findById(r.transactionId()).orElseThrow();
        var reversalTx = txRepo.findById(rev.transactionId()).orElseThrow();
        assertThat(reversalTx.getQuantity()).isEqualByComparingTo(original.getQuantity().negate());
        assertThat(reversalTx.getReversalOfId()).isEqualTo(original.getId());
    }

    @Test
    @DisplayName("INV-06b: reversing a reversal is forbidden")
    void cannotReverseAReversal() {
        PostingResult r   = receipt(new BigDecimal("20.000"));
        PostingResult rev = postingService.reverse(r.transactionId(), "reversal");

        assertThatThrownBy(() -> postingService.reverse(rev.transactionId(), "re-reversal"))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private PostingResult receipt(BigDecimal qty) {
        return postingService.post(new PostingCommand(
                "GOODS_RECEIPT", "MATERIAL", matId, null, testBatchId, null,
                WH_ID, LOC_ID, "AVAILABLE",
                qty, "KG", new BigDecimal("10.0000"),
                "GOODS_RECEIPT", 1L, "REF-" + System.nanoTime(), "receipt"));
    }

    private PostingResult issue(BigDecimal qty) {
        return postingService.post(new PostingCommand(
                "MATERIAL_ISSUE", "MATERIAL", matId, null, testBatchId, null,
                WH_ID, LOC_ID, "AVAILABLE",
                qty, "KG", null,
                "MATERIAL_ISSUE", 1L, "REF-" + System.nanoTime(), "issue"));
    }
}
