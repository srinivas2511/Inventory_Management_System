package com.springmfg.ims.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.inventory.domain.FgBatch;
import com.springmfg.ims.inventory.domain.MaterialBatch;
import com.springmfg.ims.inventory.dto.PostingCommand;
import com.springmfg.ims.inventory.repository.FgBatchRepository;
import com.springmfg.ims.inventory.repository.MaterialBatchRepository;
import com.springmfg.ims.inventory.service.InventoryPostingService;
import com.springmfg.ims.material.domain.Material;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.production.event.FinalInspectionRequiredEvent;
import com.springmfg.ims.quality.domain.QualityInspection;
import com.springmfg.ims.quality.domain.QualityInspectionItem;
import com.springmfg.ims.quality.domain.RejectionReason;
import com.springmfg.ims.quality.dto.CreateFinalInspectionRequest;
import com.springmfg.ims.quality.dto.FinalInspectionDecisionRequest;
import com.springmfg.ims.quality.dto.InspectionResultRequest;
import com.springmfg.ims.quality.dto.QualityInspectionDto;
import com.springmfg.ims.quality.repository.QualityInspectionItemRepository;
import com.springmfg.ims.quality.repository.QualityInspectionRepository;
import com.springmfg.ims.quality.repository.RejectionReasonRepository;
import com.springmfg.ims.quality.repository.RejectionRepository;
import com.springmfg.ims.quality.service.FinalInspectionService;
import com.springmfg.ims.quality.service.InspectionService;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;
import com.springmfg.ims.warehouse.domain.Warehouse;
import com.springmfg.ims.warehouse.domain.WarehouseLocation;
import com.springmfg.ims.warehouse.repository.WarehouseLocationRepository;
import com.springmfg.ims.warehouse.repository.WarehouseRepository;

/**
 * Inspection decisions through the commit path. Deliberately NOT {@code @Transactional}: {@code FinalInspectionIT}
 * wraps each test in one transaction, so everything it asserts is read back inside the writer's own persistence
 * context. Here every service call commits and every assertion reads fresh state, the way the next HTTP request would.
 */
class InspectionCommitPathIT extends AbstractIntegrationTest {

    @Autowired InspectionService inspectionService;
    @Autowired FinalInspectionService finalService;
    @Autowired QualityInspectionRepository inspectionRepo;
    @Autowired QualityInspectionItemRepository itemRepo;
    @Autowired FgBatchRepository fgBatchRepo;
    @Autowired MaterialBatchRepository materialBatchRepo;
    @Autowired RejectionRepository rejectionRepo;
    @Autowired RejectionReasonRepository reasonRepo;
    @Autowired InventoryPostingService postingService;
    @Autowired WarehouseRepository warehouseRepo;
    @Autowired WarehouseLocationRepository locationRepo;
    @Autowired ApplicationEventPublisher events;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    private Long userId;

    @BeforeEach
    void login() {
        userId = jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'Inspector', ?, 'x') RETURNING id
                """, Long.class, td.code("insp"), td.code("insp") + "@example.com");
        String[] authorities = { "QUALITY_VIEW", "QUALITY_INSPECT", "QUALITY_APPROVE" };
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CachedUser(userId, "u" + userId, true, 1, Set.of(), Set.copyOf(Arrays.asList(authorities))), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private record IncomingFixture(Long inspectionId, Long batchId) {}

    private IncomingFixture incoming() {
        Material material = td.material();
        MaterialBatch batch = new MaterialBatch();
        batch.setBatchNumber(td.code("MB"));
        batch.setMaterial(material);
        batch.setReceivedDate(LocalDate.now());
        batch.setReceivedQty(new BigDecimal("100.000"));
        batch.setRemainingQty(new BigDecimal("100.000"));
        batch.setStatus("QUARANTINE");
        batch = materialBatchRepo.save(batch);

        // Goods receipt posts the received stock as QUARANTINE; the inspection decision releases it.
        Warehouse wh = warehouseRepo.findById(1L).orElseThrow();
        WarehouseLocation loc = ensureLocation(wh, "RAW_MATERIAL");
        postingService.post(new PostingCommand(
                "PURCHASE_RECEIPT", "MATERIAL", material.getId(), null, batch.getId(), null,
                wh.getId(), loc.getId(), "QUARANTINE",
                new BigDecimal("100.000"), material.getUom(), new BigDecimal("50.0000"),
                "GOODS_RECEIPT", batch.getId(), batch.getBatchNumber(), "IT setup"));

        QualityInspection insp = new QualityInspection();
        insp.setInspectionNumber(td.code("QI"));
        insp.setInspectionType("INCOMING");
        insp.setStatus("PENDING");
        insp.setMaterialBatchId(batch.getId());
        insp.setMaterialId(material.getId());
        insp.setInspectedQty(new BigDecimal("100.000"));
        insp = inspectionRepo.save(insp);

        QualityInspectionItem item = new QualityInspectionItem();
        item.setInspection(insp);
        item.setSeq(1);
        item.setParameterName("Wire diameter");
        item.setLowerLimit(new BigDecimal("1.97"));
        item.setUpperLimit(new BigDecimal("2.03"));
        item.setUnit("mm");
        item.setMandatory(true);
        itemRepo.save(item);
        return new IncomingFixture(insp.getId(), batch.getId());
    }

    private WarehouseLocation ensureLocation(Warehouse wh, String locType) {
        return locationRepo.findByWarehouseIdAndActiveTrue(wh.getId()).stream()
                .filter(l -> locType.equals(l.getLocationType())).findFirst()
                .orElseGet(() -> {
                    WarehouseLocation loc = new WarehouseLocation();
                    loc.setWarehouse(wh);
                    loc.setLocationCode(locType + "-CP");
                    loc.setName(locType);
                    loc.setLocationType(locType);
                    loc.setActive(true);
                    return locationRepo.save(loc);
                });
    }

    /** A produced FG batch whose 100 units wait in QUALITY_PENDING, as production leaves them. */
    private FgBatch pendingFgBatch() {
        Product product = td.product();
        Warehouse wh = warehouseRepo.findById(1L).orElseThrow();
        WarehouseLocation pending = ensureLocation(wh, "QUALITY_PENDING");
        ensureLocation(wh, "FINISHED");
        ensureLocation(wh, "SCRAP");

        FgBatch batch = new FgBatch();
        batch.setBatchNumber(td.code("FG-CP"));
        batch.setProduct(product);
        batch.setProductionOrderId(999L);
        batch.setProducedQty(new BigDecimal("100.000"));
        batch.setProducedOn(LocalDate.now());
        batch.setQualityStatus("QUALITY_PENDING");
        batch.setUnitCost(new BigDecimal("50.0000"));
        FgBatch saved = fgBatchRepo.save(batch);
        postingService.post(new PostingCommand(
                "PRODUCTION_RECEIPT", "PRODUCT", null, product.getId(), null, saved.getId(),
                wh.getId(), pending.getId(), "QUALITY_PENDING",
                new BigDecimal("100.000"), product.getUom(), new BigDecimal("50.0000"),
                "FG_BATCH", saved.getId(), saved.getBatchNumber(), "IT setup"));
        return saved;
    }

    private Map<String, BigDecimal> balancesByStatus(Long fgBatchId) {
        Map<String, BigDecimal> out = new HashMap<>();
        jdbc.query("SELECT stock_status, SUM(qty_on_hand) AS qty FROM ims.inventory_balances WHERE fg_batch_id = ? GROUP BY stock_status",
                rs -> {
                    out.put(rs.getString("stock_status"), rs.getBigDecimal("qty"));
                }, fgBatchId);
        return out;
    }

    private Map<String, BigDecimal> materialBalancesByStatus(Long materialBatchId) {
        Map<String, BigDecimal> out = new HashMap<>();
        jdbc.query("SELECT stock_status, SUM(qty_on_hand) AS qty FROM ims.inventory_balances WHERE material_batch_id = ? GROUP BY stock_status",
                rs -> {
                    out.put(rs.getString("stock_status"), rs.getBigDecimal("qty"));
                }, materialBatchId);
        return out;
    }

    private static BigDecimal qty(Map<String, BigDecimal> balances, String status) {
        return balances.getOrDefault(status, BigDecimal.ZERO);
    }

    private Long measuredIncoming(IncomingFixture f) {
        Long itemId = itemRepo.findByInspectionId(f.inspectionId()).get(0).getId();
        inspectionService.submitResult(f.inspectionId(), results(itemId, "2.00", "0", "0"));
        return itemId;
    }

    private static InspectionResultRequest results(Long itemId, String value, String accepted, String rejected) {
        return new InspectionResultRequest(new BigDecimal(accepted), new BigDecimal(rejected), null,
                List.of(new InspectionResultRequest.ItemResultRequest(itemId, new BigDecimal(value), null, null)));
    }

    // ---- incoming ------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("INS-CP-1: an incoming inspection needs its mandatory parameter measured, then acceptance releases the batch")
    void incomingAcceptCommitsTheBatchRelease() {
        IncomingFixture f = incoming();
        Long itemId = itemRepo.findByInspectionId(f.inspectionId()).get(0).getId();

        assertThatThrownBy(() -> inspectionService.accept(f.inspectionId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("mandatory");
        assertThat(materialBatchRepo.findById(f.batchId()).orElseThrow().getStatus()).isEqualTo("QUARANTINE");

        QualityInspectionDto submitted = inspectionService.submitResult(f.inspectionId(), results(itemId, "2.00", "98", "2"));
        assertThat(submitted.status()).isEqualTo("IN_PROGRESS");
        assertThat(submitted.items().get(0).result()).isEqualTo("PASS");

        inspectionService.accept(f.inspectionId());

        QualityInspection stored = inspectionRepo.findById(f.inspectionId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo("APPROVED");
        assertThat(stored.getDecisionBy()).isEqualTo(userId);
        assertThat(stored.getDecisionAt()).isNotNull();
        assertThat(stored.getInspectorId()).isEqualTo(userId);
        assertThat(materialBatchRepo.findById(f.batchId()).orElseThrow().getStatus()).isEqualTo("AVAILABLE");
        assertThat(stored.getAcceptedQty()).isEqualByComparingTo("98");
        assertThat(stored.getRejectedQty()).isEqualByComparingTo("2");

        Map<String, BigDecimal> balances = materialBalancesByStatus(f.batchId());
        assertThat(qty(balances, "AVAILABLE")).isEqualByComparingTo("98");
        assertThat(qty(balances, "REJECTED")).isEqualByComparingTo("2");
        assertThat(qty(balances, "QUARANTINE")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("INS-CP-6: accepting with explicit quantities moves exactly those quantities out of QUARANTINE")
    void incomingAcceptWithQuantitiesMovesStock() {
        IncomingFixture f = incoming();
        measuredIncoming(f);

        inspectionService.accept(f.inspectionId(), new BigDecimal("90"), new BigDecimal("10"));

        QualityInspection stored = inspectionRepo.findById(f.inspectionId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo("APPROVED");
        assertThat(stored.getAcceptedQty()).isEqualByComparingTo("90");
        assertThat(stored.getRejectedQty()).isEqualByComparingTo("10");
        Map<String, BigDecimal> balances = materialBalancesByStatus(f.batchId());
        assertThat(qty(balances, "AVAILABLE")).isEqualByComparingTo("90");
        assertThat(qty(balances, "REJECTED")).isEqualByComparingTo("10");
        assertThat(qty(balances, "QUARANTINE")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("INS-CP-7: accepting with no quantities anywhere accepts the whole lot and releases all of it")
    void incomingAcceptWithNothingStoredAcceptsTheWholeLot() {
        IncomingFixture f = incoming();
        measuredIncoming(f);

        inspectionService.accept(f.inspectionId());

        QualityInspection stored = inspectionRepo.findById(f.inspectionId()).orElseThrow();
        assertThat(stored.getAcceptedQty()).isEqualByComparingTo("100");
        assertThat(stored.getRejectedQty()).isEqualByComparingTo("0");
        Map<String, BigDecimal> balances = materialBalancesByStatus(f.batchId());
        assertThat(qty(balances, "AVAILABLE")).isEqualByComparingTo("100");
        assertThat(qty(balances, "QUARANTINE")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("INS-CP-8: only the accepted quantity is needed, the rest of the lot is the rejected quantity")
    void incomingAcceptWithOnlyAcceptedQuantityRejectsTheRemainder() {
        IncomingFixture f = incoming();
        measuredIncoming(f);

        inspectionService.accept(f.inspectionId(), new BigDecimal("75"), null);

        Map<String, BigDecimal> balances = materialBalancesByStatus(f.batchId());
        assertThat(qty(balances, "AVAILABLE")).isEqualByComparingTo("75");
        assertThat(qty(balances, "REJECTED")).isEqualByComparingTo("25");
    }

    @Test
    @DisplayName("INS-CP-9: quantities that do not add up to the inspected quantity are refused and no stock moves")
    void incomingAcceptWithWrongTotalIsRefused() {
        IncomingFixture f = incoming();
        measuredIncoming(f);

        assertThatThrownBy(() -> inspectionService.accept(f.inspectionId(), new BigDecimal("60"), new BigDecimal("10")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must equal the inspected quantity");
        assertThatThrownBy(() -> inspectionService.accept(f.inspectionId(), BigDecimal.ZERO, new BigDecimal("100")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("use Reject");

        assertThat(inspectionRepo.findById(f.inspectionId()).orElseThrow().getStatus()).isEqualTo("IN_PROGRESS");
        Map<String, BigDecimal> balances = materialBalancesByStatus(f.batchId());
        assertThat(qty(balances, "QUARANTINE")).isEqualByComparingTo("100");
        assertThat(qty(balances, "AVAILABLE")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("INS-CP-2: rejecting an incoming inspection keeps the batch out of stock and stores the reason")
    void incomingRejectCommitsTheBatchRejection() {
        IncomingFixture f = incoming();
        Long itemId = itemRepo.findByInspectionId(f.inspectionId()).get(0).getId();
        inspectionService.submitResult(f.inspectionId(), results(itemId, "2.20", "0", "100"));

        QualityInspectionDto afterResults = inspectionService.findById(f.inspectionId());
        assertThat(afterResults.items().get(0).result()).isEqualTo("FAIL");

        inspectionService.reject(f.inspectionId(), "Diameter above the upper limit");

        QualityInspection stored = inspectionRepo.findById(f.inspectionId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo("REJECTED");
        assertThat(stored.getRemarks()).isEqualTo("Diameter above the upper limit");
        assertThat(materialBatchRepo.findById(f.batchId()).orElseThrow().getStatus()).isEqualTo("REJECTED");
        assertThat(stored.getRejectedQty()).isEqualByComparingTo("100");
        assertThat(stored.getAcceptedQty()).isEqualByComparingTo("0");

        Map<String, BigDecimal> balances = materialBalancesByStatus(f.batchId());
        assertThat(qty(balances, "REJECTED")).isEqualByComparingTo("100");
        assertThat(qty(balances, "QUARANTINE")).isEqualByComparingTo("0");
        assertThat(qty(balances, "AVAILABLE")).isEqualByComparingTo("0");
    }

    // ---- final ---------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("INS-CP-3: a split final decision commits the FG batch status, the stock moves and the rejection record")
    void finalDecisionCommitsStockMovesAndRejections() {
        FgBatch batch = pendingFgBatch();
        QualityInspectionDto created = finalService.createFinal(new CreateFinalInspectionRequest(batch.getId(), new BigDecimal("100.000"), 8));
        RejectionReason reason = reasonRepo.findAll().get(0);

        finalService.completeFinalInspection(created.id(), new FinalInspectionDecisionRequest(
                new BigDecimal("90.000"), new BigDecimal("6.000"), new BigDecimal("4.000"),
                List.of(), List.of(new FinalInspectionDecisionRequest.RejectionReasonLine(reason.getId(), new BigDecimal("6.000"), "coil")),
                "split lot"));

        FgBatch stored = fgBatchRepo.findById(batch.getId()).orElseThrow();
        assertThat(stored.getQualityStatus()).isEqualTo("HOLD");
        assertThat(stored.getApprovedQty()).isEqualByComparingTo("90.000");
        assertThat(stored.getRejectedQty()).isEqualByComparingTo("6.000");

        QualityInspection insp = inspectionRepo.findById(created.id()).orElseThrow();
        assertThat(insp.getStatus()).isEqualTo("HOLD");
        assertThat(insp.getHoldQty()).isEqualByComparingTo("4.000");
        assertThat(insp.getDecisionBy()).isEqualTo(userId);

        assertThat(rejectionRepo.findByInspectionId(created.id())).hasSize(1)
                .allSatisfy(r -> assertThat(r.getQuantity()).isEqualByComparingTo("6.000"));

        // 90 released to FINISHED are AVAILABLE; the 6 rejected sit in the scrap location as REJECTED stock (DESIGN
        // step 14, QCF-04) and so are not available; 4 are on HOLD; nothing is left pending.
        Map<String, BigDecimal> balances = balancesByStatus(batch.getId());
        assertThat(balances.get("AVAILABLE")).isEqualByComparingTo("90.000");
        assertThat(balances.get("REJECTED")).isEqualByComparingTo("6.000");
        assertThat(balances.get("HOLD")).isEqualByComparingTo("4.000");
        assertThat(balances.getOrDefault("QUALITY_PENDING", BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("INS-CP-4: a decision needs the batch to be QUALITY_PENDING and cannot be taken twice")
    void finalDecisionCannotBeRepeated() {
        FgBatch batch = pendingFgBatch();
        QualityInspectionDto created = finalService.createFinal(new CreateFinalInspectionRequest(batch.getId(), new BigDecimal("100.000"), null));
        FinalInspectionDecisionRequest all = new FinalInspectionDecisionRequest(
                new BigDecimal("100.000"), BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), null);
        finalService.completeFinalInspection(created.id(), all);

        assertThatThrownBy(() -> finalService.completeFinalInspection(created.id(), all))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(fgBatchRepo.findById(batch.getId()).orElseThrow().getQualityStatus()).isEqualTo("APPROVED");
        assertThat(balancesByStatus(batch.getId()).get("AVAILABLE")).isEqualByComparingTo("100.000");
    }

    @Test
    @DisplayName("INS-CP-5: a repeated final-inspection-required event leaves exactly one open FINAL inspection")
    void repeatedFinalInspectionRequiredEventCreatesOneInspection() {
        FgBatch batch = pendingFgBatch();
        FinalInspectionRequiredEvent event = new FinalInspectionRequiredEvent(
                999L, batch.getProduct().getId(), "PO-CP", new BigDecimal("100.000"), new BigDecimal("100.000"), batch.getId());

        events.publishEvent(event);
        events.publishEvent(event);

        Integer open = jdbc.queryForObject("""
                SELECT count(*) FROM ims.quality_inspections
                WHERE fg_batch_id = ? AND inspection_type = 'FINAL' AND status IN ('PENDING','IN_PROGRESS')
                """, Integer.class, batch.getId());
        assertThat(open).isEqualTo(1);
    }
}
