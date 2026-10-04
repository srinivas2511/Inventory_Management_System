package com.springmfg.ims.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.production.event.FinalInspectionRequiredEvent;
import com.springmfg.ims.inventory.domain.FgBatch;
import com.springmfg.ims.inventory.repository.FgBatchRepository;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.product.repository.ProductRepository;
import com.springmfg.ims.quality.domain.QualityInspection;
import com.springmfg.ims.quality.domain.QualityInspectionItem;
import com.springmfg.ims.quality.dto.FgBatchDto;
import com.springmfg.ims.quality.dto.FinalInspectionDecisionRequest;
import com.springmfg.ims.quality.dto.QualityInspectionDto;
import com.springmfg.ims.quality.repository.QualityInspectionItemRepository;
import com.springmfg.ims.quality.repository.QualityInspectionRepository;
import com.springmfg.ims.quality.service.FinalInspectionService;
import com.springmfg.ims.inventory.dto.PostingCommand;
import com.springmfg.ims.inventory.service.InventoryPostingService;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;
import com.springmfg.ims.warehouse.domain.Warehouse;
import com.springmfg.ims.warehouse.domain.WarehouseLocation;
import com.springmfg.ims.warehouse.repository.WarehouseLocationRepository;
import com.springmfg.ims.warehouse.repository.WarehouseRepository;

/**
 * Integration tests for Phase 5 — Quality and Finished Goods (PLAN task 5.7).
 */
class FinalInspectionIT extends AbstractIntegrationTest {

    @Autowired FinalInspectionService finalService;
    @Autowired FgBatchRepository fgBatchRepo;
    @Autowired QualityInspectionRepository inspectionRepo;
    @Autowired QualityInspectionItemRepository itemRepo;
    @Autowired ProductRepository productRepo;
    @Autowired TestData td;
    @Autowired InventoryPostingService postingService;
    @Autowired WarehouseRepository warehouseRepo;
    @Autowired WarehouseLocationRepository locationRepo;

    // ── helpers ───────────────────────────────────────────────────────────────

    private FgBatch createTestBatch(String batchNo) {
        Product product = td.product();
        // FinalInspectionService moves stock in the default warehouse (id 1, seeded by V2).
        Warehouse wh = warehouseRepo.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Seeded default warehouse missing"));

        // Ensure QUALITY_PENDING and FINISHED locations exist
        WarehouseLocation qualityPendingLoc = ensureLocation(wh, "QUALITY_PENDING");
        ensureLocation(wh, "FINISHED");
        ensureLocation(wh, "SCRAP");

        FgBatch batch = new FgBatch();
        batch.setBatchNumber(batchNo);
        batch.setProduct(product);
        batch.setProductionOrderId(999L);
        batch.setProducedQty(new BigDecimal("100.000"));
        batch.setProducedOn(LocalDate.now());
        batch.setQualityStatus("QUALITY_PENDING");
        batch.setUnitCost(new BigDecimal("50.0000"));
        FgBatch saved = fgBatchRepo.save(batch);

        // The batch's produced stock sits at the QUALITY_PENDING location awaiting the final decision.
        postingService.post(new PostingCommand(
                "PRODUCTION_RECEIPT", "PRODUCT",
                null, product.getId(), null, saved.getId(),
                wh.getId(), qualityPendingLoc.getId(), "QUALITY_PENDING",
                new BigDecimal("100.000"), product.getUom(),
                new BigDecimal("50.0000"),
                "FG_BATCH", saved.getId(), batchNo, "IT setup"));
        return saved;
    }

    private WarehouseLocation ensureLocation(Warehouse wh, String locType) {
        var existing = locationRepo.findByWarehouseIdAndActiveTrue(wh.getId()).stream()
                .filter(l -> locType.equals(l.getLocationType())).findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        WarehouseLocation loc = new WarehouseLocation();
        loc.setWarehouse(wh);
        loc.setLocationCode(locType + "-IT");
        loc.setName(locType);
        loc.setLocationType(locType);
        loc.setActive(true);
        return locationRepo.save(loc);
    }

    private QualityInspection createPendingFinalInspection(FgBatch batch) {
        QualityInspection insp = new QualityInspection();
        insp.setInspectionNumber(td.code("QI"));
        insp.setInspectionType("FINAL");
        insp.setStatus("PENDING");
        insp.setFgBatchId(batch.getId());
        insp.setProductionOrderId(batch.getProductionOrderId());
        insp.setInspectedQty(batch.getProducedQty());
        return inspectionRepo.save(insp);
    }

    // ── Test 1: Full approval ─────────────────────────────────────────────────

    @Test
    @Transactional
    void full_approval_moves_fg_to_approved_status() {
        FgBatch batch = createTestBatch("FG-IT-001");
        QualityInspection insp = createPendingFinalInspection(batch);

        FinalInspectionDecisionRequest req = new FinalInspectionDecisionRequest(
                new BigDecimal("100.000"), BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(), List.of(), "All good");

        QualityInspectionDto dto = finalService.completeFinalInspection(insp.getId(), req);

        assertThat(dto.status()).isEqualTo("APPROVED");
        FgBatch updated = fgBatchRepo.findById(batch.getId()).orElseThrow();
        assertThat(updated.getQualityStatus()).isEqualTo("APPROVED");
        assertThat(updated.getApprovedQty()).isEqualByComparingTo("100.000");
        assertThat(updated.getRejectedQty()).isEqualByComparingTo("0.000");
    }

    // ── Test 2: Partial rejection ─────────────────────────────────────────────

    @Test
    @Transactional
    void partial_rejection_sets_partially_approved() {
        FgBatch batch = createTestBatch("FG-IT-002");
        QualityInspection insp = createPendingFinalInspection(batch);

        FinalInspectionDecisionRequest req = new FinalInspectionDecisionRequest(
                new BigDecimal("80.000"), new BigDecimal("20.000"), BigDecimal.ZERO,
                List.of(), List.of(), "20 pcs rejected");

        QualityInspectionDto dto = finalService.completeFinalInspection(insp.getId(), req);

        assertThat(dto.status()).isEqualTo("APPROVED");
        FgBatch updated = fgBatchRepo.findById(batch.getId()).orElseThrow();
        assertThat(updated.getQualityStatus()).isEqualTo("PARTIALLY_APPROVED");
        assertThat(updated.getApprovedQty()).isEqualByComparingTo("80.000");
        assertThat(updated.getRejectedQty()).isEqualByComparingTo("20.000");
    }

    // ── Test 3: Hold path ─────────────────────────────────────────────────────

    @Test
    @Transactional
    void hold_qty_sets_batch_to_hold_status() {
        FgBatch batch = createTestBatch("FG-IT-003");
        QualityInspection insp = createPendingFinalInspection(batch);

        FinalInspectionDecisionRequest req = new FinalInspectionDecisionRequest(
                new BigDecimal("50.000"), BigDecimal.ZERO, new BigDecimal("50.000"),
                List.of(), List.of(), "50 pcs on hold for retest");

        finalService.completeFinalInspection(insp.getId(), req);

        FgBatch updated = fgBatchRepo.findById(batch.getId()).orElseThrow();
        assertThat(updated.getQualityStatus()).isEqualTo("HOLD");
    }

    // ── Test 4: Mandatory fail without override blocks approval ───────────────

    @Test
    @Transactional
    void mandatory_fail_without_deviation_blocks_approval() {
        FgBatch batch = createTestBatch("FG-IT-004");
        QualityInspection insp = createPendingFinalInspection(batch);

        // Add a mandatory FAIL item
        QualityInspectionItem item = new QualityInspectionItem();
        item.setInspection(insp);
        item.setSeq(1);
        item.setParameterName("Hardness");
        item.setLowerLimit(new BigDecimal("30.0000"));
        item.setUpperLimit(new BigDecimal("40.0000"));
        item.setActualValue(new BigDecimal("25.0000")); // below limit → FAIL
        item.setMandatory(true);
        item.computeResult();
        itemRepo.save(item);

        FinalInspectionDecisionRequest req = new FinalInspectionDecisionRequest(
                new BigDecimal("100.000"), BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(new FinalInspectionDecisionRequest.ItemResultEntry(1, new BigDecimal("25.0000"), null, null)),
                List.of(), "Trying to approve despite fail");

        assertThatThrownBy(() -> finalService.completeFinalInspection(insp.getId(), req))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── Test 5: Qty exceeding inspected rejected ──────────────────────────────

    @Test
    @Transactional
    void decision_qty_exceeding_inspected_is_rejected() {
        FgBatch batch = createTestBatch("FG-IT-005");
        QualityInspection insp = createPendingFinalInspection(batch);

        // approved + rejected > inspected
        FinalInspectionDecisionRequest req = new FinalInspectionDecisionRequest(
                new BigDecimal("90.000"), new BigDecimal("20.000"), BigDecimal.ZERO,
                List.of(), List.of(), "bad qty");

        assertThatThrownBy(() -> finalService.completeFinalInspection(insp.getId(), req))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── Duplicate open FINAL inspections must neither be created nor break the batch list ───────────

    @Test
    @Transactional
    void repeated_final_inspection_event_creates_a_single_open_inspection() {
        FgBatch batch = createTestBatch("FG-IT-DUP-1");
        var event = new FinalInspectionRequiredEvent(batch.getProductionOrderId(), batch.getProduct().getId(),
                "PO-DUP", new BigDecimal("100.000"), new BigDecimal("100.000"), batch.getId());

        finalService.onFinalInspectionRequired(event);
        finalService.onFinalInspectionRequired(event);

        assertThat(inspectionRepo.findOpenFinalsByFgBatch(batch.getId())).hasSize(1);
    }

    @Test
    @Transactional
    void fg_batch_list_survives_a_batch_with_two_open_final_inspections() throws Exception {
        FgBatch batch = createTestBatch("FG-IT-DUP-2");
        QualityInspection first = createPendingFinalInspection(batch);
        createPendingFinalInspection(batch); // legacy duplicate

        mockMvc.perform(get("/api/quality/fg-batches").param("size", "1000")
                        .with(user("qa").authorities(new SimpleGrantedAuthority("QUALITY_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.batchNumber=='FG-IT-DUP-2')].pendingInspectionId")
                        .value(first.getId().intValue()));
    }
}
