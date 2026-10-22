package com.mfg.ims.advmfg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.insight.alert.AlertSweepJob;
import com.mfg.ims.insight.report.ReportRequest;
import com.mfg.ims.inventory.dto.PostingCommand;
import com.mfg.ims.inventory.repository.AlertRepository;
import com.mfg.ims.inventory.service.BatchService;
import com.mfg.ims.inventory.service.InventoryPostingService;
import com.mfg.ims.inventory.service.InventoryQueryService;
import com.mfg.ims.maintenance.domain.MaintenanceRequest;
import com.mfg.ims.maintenance.dto.CreateMaintenanceRequestDto;
import com.mfg.ims.maintenance.dto.MaintenanceWorkDtos.MaintenanceCostDto;
import com.mfg.ims.maintenance.dto.MaintenanceWorkDtos.RecordActivityRequest;
import com.mfg.ims.maintenance.dto.SparePartDtos.IssueSparePartRequest;
import com.mfg.ims.maintenance.dto.SparePartDtos.SaveSparePartRequest;
import com.mfg.ims.maintenance.dto.SparePartDtos.SparePartDto;
import com.mfg.ims.maintenance.dto.SparePartDtos.SparePartIssueLineDto;
import com.mfg.ims.maintenance.report.MaintenanceCostReport;
import com.mfg.ims.maintenance.repository.MaintenanceRequestRepository;
import com.mfg.ims.maintenance.service.MaintenanceCostPoster;
import com.mfg.ims.maintenance.service.MaintenanceCostService;
import com.mfg.ims.maintenance.service.MaintenanceService;
import com.mfg.ims.maintenance.service.SparePartService;
import com.mfg.ims.material.repository.MaterialRepository;
import com.mfg.ims.support.AbstractIntegrationTest;

/**
 * Spare parts and maintenance cost (R18-1.x, R18-5.1, R18-4.1). Deliberately NOT {@code @Transactional}: the
 * accounting for an issue and for a completion runs after the business transaction commits, and a test-managed
 * transaction would hide exactly that behaviour.
 */
class SparePartAndMaintenanceCostIT extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000_000L);
    private static final long WAREHOUSE_ID = 1L;
    private static final long LOCATION_ID = 1L;

    @Autowired SparePartService spareParts;
    @Autowired MaintenanceService maintenance;
    @Autowired MaintenanceCostService costs;
    @Autowired MaintenanceCostPoster poster;
    @Autowired MaintenanceRequestRepository requestRepo;
    @Autowired MaterialRepository materialRepo;
    @Autowired InventoryPostingService postingService;
    @Autowired InventoryQueryService inventoryQuery;
    @Autowired BatchService batchService;
    @Autowired AlertRepository alertRepo;
    @Autowired AlertSweepJob alertSweep;
    @Autowired MaintenanceCostReport costReport;
    @Autowired JdbcTemplate jdbc;

    private long userId;
    private long costCentreId;
    private long machineId;

    @BeforeEach
    void setup() {
        String tag = "MFG" + SEQ.incrementAndGet();
        userId = jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'Maintenance IT', ?, 'x') RETURNING id""", Long.class, tag, tag + "@it.test");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CachedUser(userId, tag, true, 1, Set.of(), Set.of()), null, List.of()));
        costCentreId = jdbc.queryForObject("""
                INSERT INTO ims.cost_centres (cost_centre_code, name, labour_rate_per_hour)
                VALUES (?, 'IT maintenance centre', 300.0000) RETURNING id""", Long.class, "CC-" + tag);
        machineId = jdbc.queryForObject("""
                INSERT INTO ims.machines (machine_code, name, machine_type, cost_centre_id)
                VALUES (?, 'IT coiler', ?, ?) RETURNING id""", Long.class, "MC-" + tag, "TYPE-" + tag, costCentreId);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private SparePartDto newPart(String minStock, String reorder) {
        return spareParts.create(new SaveSparePartRequest("SP" + SEQ.incrementAndGet(), "Bearing 6204", "IT part", "PCS",
                "TYPE-X", new BigDecimal(minStock), new BigDecimal(reorder), new BigDecimal("40.0000"), null));
    }

    private void stock(SparePartDto part, String qty) {
        var batch = batchService.createMaterialBatch(part.materialId(), null, null, null, null, null, null,
                new BigDecimal(qty), null, null, new BigDecimal("40.0000"), "AVAILABLE");
        postingService.post(new PostingCommand("PURCHASE_RECEIPT", "MATERIAL", part.materialId(), null, batch.getId(),
                null, WAREHOUSE_ID, LOCATION_ID, "AVAILABLE", new BigDecimal(qty), "PCS", new BigDecimal("40.0000"),
                "GOODS_RECEIPT", 1L, "REF-" + System.nanoTime(), "IT stock"));
    }

    private long newRequest(String type) {
        return maintenance.create(new CreateMaintenanceRequestDto(machineId, type, 3, "IT breakdown", null, null)).id();
    }

    private List<Map<String, Object>> journalLines(String sourceType, long sourceId) {
        return jdbc.queryForList("""
                SELECT ga.account_code, jl.debit_amount, jl.credit_amount, jl.cost_centre_id
                  FROM ims.journal_lines jl
                  JOIN ims.journal_headers jh ON jh.id = jl.journal_header_id
                  JOIN ims.gl_accounts ga ON ga.id = jl.gl_account_id
                 WHERE jh.source_document_type = ? AND jh.source_document_id = ? AND jh.status = 'POSTED'
                 ORDER BY jl.line_number""", sourceType, sourceId);
    }

    // ── spare parts ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    void a_spare_part_is_a_material_whose_minimum_stock_lives_on_the_material() {
        SparePartDto part = newPart("2", "5");

        var material = materialRepo.findById(part.materialId()).orElseThrow();
        assertThat(material.getMaterialType()).isEqualTo("SPARE_PART");
        assertThat(material.getMaterialCode()).isEqualTo(part.sparePartCode());
        assertThat(material.getMinStock()).isEqualByComparingTo("2");
        assertThat(material.getReorderLevel()).isEqualByComparingTo("5");

        SparePartDto changed = spareParts.update(part.id(), new SaveSparePartRequest(part.sparePartCode(), "Bearing 6205",
                null, "PCS", null, new BigDecimal("3"), new BigDecimal("8"), new BigDecimal("45"), part.version()));
        assertThat(changed.name()).isEqualTo("Bearing 6205");
        assertThat(materialRepo.findById(part.materialId()).orElseThrow().getMinStock()).isEqualByComparingTo("3");
    }

    @Test
    void a_part_code_cannot_be_reused_or_changed_and_minimum_may_not_exceed_reorder_level() {
        SparePartDto part = newPart("1", "2");

        assertThatThrownBy(() -> spareParts.create(new SaveSparePartRequest(part.sparePartCode(), "Dup", null, "PCS",
                null, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already exists");
        assertThatThrownBy(() -> spareParts.update(part.id(), new SaveSparePartRequest("OTHER-CODE", "x", null, "PCS",
                null, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cannot be changed");
        assertThatThrownBy(() -> spareParts.create(new SaveSparePartRequest("SP" + SEQ.incrementAndGet(), "Bad", null,
                "PCS", null, new BigDecimal("9"), new BigDecimal("2"), BigDecimal.ONE, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Minimum stock");
    }

    @Test
    void issuing_a_part_moves_stock_through_the_ledger_records_its_cost_and_charges_the_machines_cost_centre() {
        SparePartDto part = newPart("8", "9");
        stock(part, "10");
        long requestId = newRequest("CORRECTIVE");

        SparePartIssueLineDto line = spareParts.issue(requestId,
                new IssueSparePartRequest(part.id(), WAREHOUSE_ID, new BigDecimal("3"), "bearing swap"));

        assertThat(line.issuedQty()).isEqualByComparingTo("3");
        assertThat(line.unitCost()).isEqualByComparingTo("40");
        assertThat(line.totalCost()).isEqualByComparingTo("120");
        assertThat(line.issuedBy()).isEqualTo(userId);

        assertThat(inventoryQuery.availableQtyByMaterial(List.of(part.materialId())).get(part.materialId()))
                .isEqualByComparingTo("7");
        List<Map<String, Object>> ledger = jdbc.queryForList("""
                SELECT transaction_type, quantity FROM ims.inventory_transactions
                 WHERE reference_type = 'SPARE_PART_ISSUE' AND reference_id = ?""", line.id());
        assertThat(ledger).singleElement().satisfies(tx -> {
            assertThat(tx.get("transaction_type")).isEqualTo("MAINTENANCE_ISSUE");
            assertThat((BigDecimal) tx.get("quantity")).isEqualByComparingTo("-3");
        });

        // After commit the bridge posted Dr Repairs and Maintenance / Cr Raw Material Stock, on the MACHINE's cost
        // centre (not the default on the account assignment).
        List<Map<String, Object>> journal = journalLines("SPARE_PART_ISSUE", line.id());
        assertThat(journal).hasSize(2);
        assertThat(journal.get(0).get("account_code")).isEqualTo("5301");
        assertThat((BigDecimal) journal.get(0).get("debit_amount")).isEqualByComparingTo("120");
        assertThat(journal.get(1).get("account_code")).isEqualTo("1401");
        assertThat((BigDecimal) journal.get(1).get("credit_amount")).isEqualByComparingTo("120");
        assertThat(journal).allSatisfy(l -> assertThat(l.get("cost_centre_id")).isEqualTo(costCentreId));

        MaintenanceCostDto cost = costs.cost(requestId);
        assertThat(cost.sparePartsCost()).isEqualByComparingTo("120");
        assertThat(cost.labourCostFinal()).isFalse();

        // 7 on hand is below the minimum of 8: flagged on the list, and the sweep raises the stock alert.
        assertThat(spareParts.get(part.id()).belowMinStock()).isTrue();
        // the sweep runs under a ShedLock that the scheduled run at start-up still holds for minutes; release it by
        // expiring the row (deleting it would stop ShedLock, which remembers it created the row, from locking at all)
        jdbc.update("UPDATE ims.shedlock SET lock_until = TIMESTAMP '2000-01-01 00:00:00' WHERE name = 'alert_sweep'");
        alertSweep.sweep();
        assertThat(alertRepo.findOpenByTypeAndEntity("LOW_STOCK", "MATERIAL", part.materialId())).isNotEmpty();
    }

    @Test
    void asking_for_more_than_is_in_stock_is_refused_and_leaves_no_trace() {
        SparePartDto part = newPart("0", "1");
        stock(part, "2");
        long requestId = newRequest("CORRECTIVE");

        assertThatThrownBy(() -> spareParts.issue(requestId,
                new IssueSparePartRequest(part.id(), WAREHOUSE_ID, new BigDecimal("5"), null)))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK));

        assertThat(spareParts.issuesOf(requestId)).isEmpty();
        assertThat(inventoryQuery.availableQtyByMaterial(List.of(part.materialId())).get(part.materialId()))
                .isEqualByComparingTo("2");
    }

    @Test
    void parts_cannot_be_issued_to_a_finished_request_or_from_an_inactive_part() {
        SparePartDto part = newPart("0", "1");
        stock(part, "5");
        long finished = newRequest("CORRECTIVE");
        maintenance.updateStatus(finished, "IN_PROGRESS", null);
        maintenance.updateStatus(finished, "COMPLETED", "done");
        assertThatThrownBy(() -> spareParts.issue(finished,
                new IssueSparePartRequest(part.id(), WAREHOUSE_ID, BigDecimal.ONE, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("COMPLETED");

        long open = newRequest("CORRECTIVE");
        spareParts.deactivate(part.id());
        assertThatThrownBy(() -> spareParts.issue(open,
                new IssueSparePartRequest(part.id(), WAREHOUSE_ID, BigDecimal.ONE, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("inactive");
    }

    // ── maintenance cost ────────────────────────────────────────────────────────────────────────────────────

    @Test
    void completing_a_request_fixes_the_labour_and_posts_it_once_to_the_machines_cost_centre() {
        long requestId = newRequest("CORRECTIVE");
        maintenance.updateStatus(requestId, "IN_PROGRESS", null);
        maintenance.addActivity(requestId, new RecordActivityRequest("Strip and inspect", 90, null, null, null));
        maintenance.addActivity(requestId, new RecordActivityRequest("Refit", 30, null, null, null));

        // before completion the labour is an estimate
        assertThat(costs.cost(requestId).labourCostFinal()).isFalse();
        assertThat(costs.cost(requestId).labourCost()).isEqualByComparingTo("600");

        maintenance.updateStatus(requestId, "COMPLETED", "fixed");

        MaintenanceRequest done = requestRepo.findById(requestId).orElseThrow();
        assertThat(done.getLabourHours()).isEqualByComparingTo("2.00");
        assertThat(done.getLabourCost()).isEqualByComparingTo("600");           // 120 min x 300 per hour / 60
        assertThat(done.getCostCentreId()).isEqualTo(costCentreId);
        assertThat(done.getCostPostedAt()).isNotNull();
        assertThat(done.getLabourJournalHeaderId()).isNotNull();

        List<Map<String, Object>> journal = journalLines("MAINTENANCE_REQUEST", requestId);
        assertThat(journal).hasSize(2);
        assertThat(journal.get(0).get("account_code")).isEqualTo("5301");
        assertThat((BigDecimal) journal.get(0).get("debit_amount")).isEqualByComparingTo("600");
        assertThat(journal.get(1).get("account_code")).isEqualTo("5302");
        assertThat((BigDecimal) journal.get(1).get("credit_amount")).isEqualByComparingTo("600");
        assertThat(journal).allSatisfy(l -> assertThat(l.get("cost_centre_id")).isEqualTo(costCentreId));

        // posting again is a no-op: still exactly one journal
        costs.postLabourCost(requestId);
        assertThat(journalLines("MAINTENANCE_REQUEST", requestId)).hasSize(2);

        MaintenanceCostDto cost = costs.cost(requestId);
        assertThat(cost.labourCostFinal()).isTrue();
        assertThat(cost.labourMinutes()).isEqualTo(120);
        assertThat(cost.totalCost()).isEqualByComparingTo("600");
    }

    @Test
    void work_booked_after_completion_is_refused_so_the_posted_cost_cannot_drift() {
        long requestId = newRequest("CORRECTIVE");
        maintenance.updateStatus(requestId, "IN_PROGRESS", null);
        maintenance.updateStatus(requestId, "COMPLETED", null);

        assertThatThrownBy(() -> maintenance.addActivity(requestId,
                new RecordActivityRequest("Too late", 60, null, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("COMPLETED");
    }

    @Test
    void a_request_with_no_labour_is_marked_posted_without_a_journal() {
        long requestId = newRequest("CORRECTIVE");
        maintenance.updateStatus(requestId, "IN_PROGRESS", null);
        maintenance.updateStatus(requestId, "COMPLETED", null);

        MaintenanceRequest done = requestRepo.findById(requestId).orElseThrow();
        assertThat(done.getLabourCost()).isEqualByComparingTo("0");
        assertThat(done.getCostPostedAt()).isNotNull();
        assertThat(done.getLabourJournalHeaderId()).isNull();
        assertThat(journalLines("MAINTENANCE_REQUEST", requestId)).isEmpty();
    }

    @Test
    void the_status_machine_is_enforced_on_a_real_request() {
        long requestId = newRequest("PREVENTIVE");

        assertThatThrownBy(() -> maintenance.updateStatus(requestId, "COMPLETED", null))
                .isInstanceOf(BusinessRuleException.class);
        maintenance.updateStatus(requestId, "CANCELLED", "not needed");
        assertThatThrownBy(() -> maintenance.updateStatus(requestId, "IN_PROGRESS", null))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(requestRepo.findById(requestId).orElseThrow().getStatus()).isEqualTo("CANCELLED");
    }

    private long completedRequestWithoutPostedCost(String labour, Long costCentre) {
        MaintenanceRequest r = new MaintenanceRequest();
        r.setRequestNumber("MR/IT/" + SEQ.incrementAndGet());
        r.setMachineId(machineId);
        r.setRequestType("CORRECTIVE");
        r.setDescription("IT completed without posting");
        r.setStatus("COMPLETED");
        r.setRequestedBy(userId);
        r.setCompletedAt(Instant.now());
        r.setLabourHours(new BigDecimal("0.50"));
        r.setLabourCost(new BigDecimal(labour));
        r.setCostCentreId(costCentre);
        return requestRepo.save(r).getId();
    }

    @Test
    void completed_requests_whose_cost_never_reached_the_gl_are_listed_and_reposted() {
        long id = completedRequestWithoutPostedCost("150.0000", costCentreId);

        assertThat(costs.unposted()).extracting(MaintenanceCostDto::requestId).contains(id);
        assertThat(poster.repostUnposted()).isGreaterThanOrEqualTo(1);

        assertThat(requestRepo.findById(id).orElseThrow().getCostPostedAt()).isNotNull();
        assertThat(costs.unposted()).extracting(MaintenanceCostDto::requestId).doesNotContain(id);
        assertThat(journalLines("MAINTENANCE_REQUEST", id)).hasSize(2);
    }

    @Test
    void a_posting_failure_raises_an_alert_and_a_successful_retry_resolves_it() {
        long id = completedRequestWithoutPostedCost("100.0000", null);   // no cost centre to charge

        assertThat(poster.postAndReport(id)).isFalse();
        assertThat(alertRepo.findOpenByTypeAndEntity("MAINTENANCE_COST_UNPOSTED", "MAINTENANCE_REQUEST", id))
                .hasSize(1);
        assertThat(requestRepo.findById(id).orElseThrow().getCostPostedAt()).isNull();

        MaintenanceRequest r = requestRepo.findById(id).orElseThrow();
        r.setCostCentreId(costCentreId);
        requestRepo.save(r);

        assertThat(poster.postAndReport(id)).isTrue();
        assertThat(alertRepo.findOpenByTypeAndEntity("MAINTENANCE_COST_UNPOSTED", "MAINTENANCE_REQUEST", id))
                .isEmpty();
        assertThat(requestRepo.findById(id).orElseThrow().getCostPostedAt()).isNotNull();
    }

    @Test
    void the_maintenance_cost_report_adds_labour_and_spare_parts_per_machine() {
        SparePartDto part = newPart("0", "1");
        stock(part, "10");
        long requestId = newRequest("CORRECTIVE");
        maintenance.updateStatus(requestId, "IN_PROGRESS", null);
        maintenance.addActivity(requestId, new RecordActivityRequest("Work", 60, null, null, null));
        spareParts.issue(requestId, new IssueSparePartRequest(part.id(), WAREHOUSE_ID, new BigDecimal("2"), null));
        maintenance.updateStatus(requestId, "COMPLETED", null);

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = costReport.runQuery(
                new ReportRequest(today.minusDays(1), today.plusDays(1), machineId, null, 0, 50));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(((Number) row.get("Requests")).longValue()).isEqualTo(1);
            assertThat((BigDecimal) row.get("Labour Cost")).isEqualByComparingTo("300");        // 60 min x 300 / 60
            assertThat((BigDecimal) row.get("Spare Parts Cost")).isEqualByComparingTo("80");    // 2 x 40
            assertThat((BigDecimal) row.get("Total Cost")).isEqualByComparingTo("380");
        });
        assertThat(costReport.requiredAuthority()).containsExactly("MAINTENANCE_COST_VIEW");
        assertThat(costs.cost(requestId).totalCost()).isEqualByComparingTo("380");
    }
}
