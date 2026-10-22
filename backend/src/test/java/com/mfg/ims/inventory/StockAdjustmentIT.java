package com.mfg.ims.inventory;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.inventory.dto.StockAdjustmentDto;
import com.mfg.ims.inventory.service.StockAdjustmentService;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.procurement.domain.PurchaseOrder;
import com.mfg.ims.procurement.dto.CreateGrRequest;
import com.mfg.ims.procurement.dto.CreatePoRequest;
import com.mfg.ims.procurement.repository.PurchaseOrderRepository;
import com.mfg.ims.procurement.service.GoodsReceiptService;
import com.mfg.ims.procurement.service.PurchaseOrderService;
import com.mfg.ims.quality.service.InspectionService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * D-10: a stock adjustment looked up a single batch-less balance row, but balances are kept per batch, so a count of
 * 190 kg against 200 kg on hand was recorded as a system quantity of 0, a variance of +190 and a value of 0 (which
 * slips under the Admin-approval threshold). Driven through the real services without a surrounding transaction.
 *
 * <p>Stock, worked by hand: batch 1 = 100 kg at 50 = 5,000; batch 2 = 100 kg at 150 = 15,000; so 200 kg worth
 * 20,000 at a weighted average of 20,000 / 200 = 100.0000 a kg. The threshold is 10,000 and the rule is "above".
 */
class StockAdjustmentIT extends AbstractIntegrationTest {

    private static final long WAREHOUSE_ID = 1L;
    private static final long LOCATION_ID = 1L;

    @Autowired StockAdjustmentService adjustmentService;
    @Autowired PurchaseOrderService poService;
    @Autowired PurchaseOrderRepository poRepo;
    @Autowired GoodsReceiptService grService;
    @Autowired InspectionService inspectionService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    private Long requesterId;

    @BeforeEach
    void login() {
        requesterId = newUserAndLogin("req", "INVENTORY_ADJUST", "INVENTORY_VIEW", "PURCHASE_CREATE",
                "PURCHASE_APPROVE", "INVENTORY_RECEIVE", "QUALITY_INSPECT", "QUALITY_VIEW", "QUALITY_APPROVE");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Long newUserAndLogin(String prefix, String... authorities) {
        String name = td.code(prefix);
        Long userId = jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'Adjustment IT', ?, 'x') RETURNING id
                """, Long.class, name, name + "@example.com");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CachedUser(userId, "u" + userId, true, 1, Set.of(), Set.copyOf(Arrays.asList(authorities))), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
        return userId;
    }

    private Long receiveMaterial(Material material, String qty, String price) {
        var po = poService.create(new CreatePoRequest(td.supplier().getId(), LocalDate.now(),
                LocalDate.now().plusDays(7), "NET30", null, null,
                List.of(new CreatePoRequest.PoLineRequest(material.getId(), new BigDecimal(qty),
                        new BigDecimal(price), new BigDecimal("0.00")))));
        po = poService.submit(po.id());
        PurchaseOrder entity = poRepo.findById(po.id()).orElseThrow();
        entity.setSubmittedBy(-99L);
        poRepo.save(entity);
        po = poService.approve(po.id());
        var gr = grService.receive(new CreateGrRequest(po.id(), LocalDate.now(), WAREHOUSE_ID,
                td.code("CH"), "MH12AB0001",
                List.of(new CreateGrRequest.GrLineRequest(po.items().get(0).id(), new BigDecimal(qty), LOCATION_ID,
                        td.code("HEAT"), td.code("CERT"), null, true))));
        Long batchId = gr.items().get(0).materialBatchId();
        Long inspectionId = jdbc.queryForObject("""
                SELECT id FROM ims.quality_inspections WHERE material_batch_id = ? AND inspection_type = 'INCOMING'
                """, Long.class, batchId);
        inspectionService.accept(inspectionId);
        return batchId;
    }

    private record Stock(Material material, Long batch1, Long batch2, Long locationId) {
    }

    private Stock twoBatches() {
        Material material = td.material();
        Long b1 = receiveMaterial(material, "100.000", "50.0000");
        Long b2 = receiveMaterial(material, "100.000", "150.0000");
        Long location = jdbc.queryForObject("""
                SELECT DISTINCT location_id FROM ims.inventory_balances
                 WHERE material_id = ? AND stock_status = 'AVAILABLE' AND qty_on_hand > 0
                """, Long.class, material.getId());
        return new Stock(material, b1, b2, location);
    }

    private StockAdjustmentDto count(Stock s, Long batchId, String counted) {
        return adjustmentService.request("MATERIAL", s.material().getId(), null, batchId, WAREHOUSE_ID,
                s.locationId(), "AVAILABLE", new BigDecimal(counted), "Physical stock verification");
    }

    private List<String> stepsOf(Long adjustmentId) {
        return jdbc.queryForList("""
                SELECT required_permission FROM ims.approval_requests
                 WHERE document_type = 'STOCK_ADJUSTMENT' AND document_id = ? ORDER BY step_no
                """, String.class, adjustmentId);
    }

    private BigDecimal onHand(Long batchId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(qty_on_hand), 0) FROM ims.inventory_balances
                 WHERE material_batch_id = ? AND stock_status = 'AVAILABLE'
                """, BigDecimal.class, batchId);
    }

    private void approveEveryStep(Long adjustmentId) {
        List<Long> requestIds = jdbc.queryForList("""
                SELECT id FROM ims.approval_requests
                 WHERE document_type = 'STOCK_ADJUSTMENT' AND document_id = ? ORDER BY step_no
                """, Long.class, adjustmentId);
        for (Long requestId : requestIds) {
            newUserAndLogin("appr", "ADJUST_REVIEW", "ADJUST_APPROVE", "ADJUST_APPROVE_ADMIN", "INVENTORY_VIEW");
            adjustmentService.approveStep(requestId, "ok");
        }
    }

    private long journalMark() {
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM ims.journal_headers", Long.class);
        return max == null ? 0 : max;
    }

    private Map<String, BigDecimal> netByAccount(long mark) {
        Map<String, BigDecimal> out = new HashMap<>();
        jdbc.query("""
                SELECT a.account_code, SUM(jl.debit_amount - jl.credit_amount) AS net
                  FROM ims.journal_lines jl
                  JOIN ims.journal_headers jh ON jh.id = jl.journal_header_id
                  JOIN ims.gl_accounts a ON a.id = jl.gl_account_id
                 WHERE jh.id > ? AND jh.status = 'POSTED'
                 GROUP BY a.account_code
                """, rs -> {
            out.put(rs.getString("account_code"), rs.getBigDecimal("net"));
        }, mark);
        return out;
    }

    // ---- scenarios ------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("ADJ-IT-1 (D-10): a count that names no batch compares against the stock of every batch, valued at their average")
    void systemQuantityAndValueComeFromEveryBatch() {
        Stock s = twoBatches();

        // 190 counted against 200 held: variance -10, worth 10 x 100.0000 = 1,000.
        StockAdjustmentDto loss = count(s, null, "190");

        assertThat(loss.systemQty()).as("system quantity is the stock held, not 0").isEqualByComparingTo("200.000");
        assertThat(loss.varianceQty()).as("a smaller count is a negative variance").isEqualByComparingTo("-10.000");
        assertThat(loss.varianceValue()).isEqualByComparingTo("1000.0000");
    }

    @Test
    @DisplayName("ADJ-IT-2 (D-10): the Admin step is added only when the variance value is above 10,000")
    void adminApprovalIsDecidedOnTheRealValue() {
        Stock s = twoBatches();

        // Variance x 100.0000: 10 -> 1,000; 99.9 -> 9,990; 100 -> 10,000 (at the threshold: not above); 100.1 -> 10,010.
        StockAdjustmentDto small = count(s, null, "190");
        StockAdjustmentDto justBelow = count(s, null, "100.1");
        StockAdjustmentDto atThreshold = count(s, null, "100");
        StockAdjustmentDto justAbove = count(s, null, "99.9");

        assertThat(justBelow.varianceValue()).isEqualByComparingTo("9990.0000");
        assertThat(atThreshold.varianceValue()).isEqualByComparingTo("10000.0000");
        assertThat(justAbove.varianceValue()).isEqualByComparingTo("10010.0000");
        assertThat(stepsOf(small.id())).containsExactly("ADJUST_REVIEW", "ADJUST_APPROVE");
        assertThat(stepsOf(justBelow.id())).containsExactly("ADJUST_REVIEW", "ADJUST_APPROVE");
        assertThat(stepsOf(atThreshold.id())).as("exactly 10,000 is not above the threshold")
                .containsExactly("ADJUST_REVIEW", "ADJUST_APPROVE");
        assertThat(stepsOf(justAbove.id())).containsExactly("ADJUST_REVIEW", "ADJUST_APPROVE", "ADJUST_APPROVE_ADMIN");
    }

    @Test
    @DisplayName("ADJ-IT-3 (D-10): stock can only be added to a named batch, and a gain is valued at that batch's cost")
    void aGainNeedsANamedBatch() {
        Stock s = twoBatches();

        assertThatThrownBy(() -> count(s, null, "205"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("select the batch");

        // Naming batch 2 (100 held at 150): counting 103 is +3 x 150.0000 = 450.
        StockAdjustmentDto gain = count(s, s.batch2(), "103");
        assertThat(gain.systemQty()).isEqualByComparingTo("100.000");
        assertThat(gain.varianceQty()).isEqualByComparingTo("3.000");
        assertThat(gain.varianceValue()).isEqualByComparingTo("450.0000");
    }

    @Test
    @DisplayName("ADJ-IT-4 (D-10): an approved loss is taken from the oldest batches and journalled Dr 5203 / Cr 1401 at their cost")
    void approvedLossIsPostedAcrossBatchesAndJournalled() {
        Stock s = twoBatches();
        // 80 counted against 200: -120, taken 100 from batch 1 (at 50) and 20 from batch 2 (at 150) = 5,000 + 3,000 = 8,000.
        StockAdjustmentDto loss = count(s, null, "80");
        assertThat(loss.varianceValue()).as("estimated at the average: 120 x 100").isEqualByComparingTo("12000.0000");
        assertThat(stepsOf(loss.id())).hasSize(3);

        long mark = journalMark();
        approveEveryStep(loss.id());

        assertThat(adjustmentService.findById(loss.id()).status()).isEqualTo("POSTED");
        assertThat(onHand(s.batch1())).isEqualByComparingTo("0");
        assertThat(onHand(s.batch2())).isEqualByComparingTo("80");
        Map<String, BigDecimal> gl = netByAccount(mark);
        assertThat(gl.get("5203")).as("inventory adjustment expense").isEqualByComparingTo("8000");
        assertThat(gl.get("1401")).as("raw material stock").isEqualByComparingTo("-8000");
    }

    @Test
    @DisplayName("ADJ-IT-5 (D-10): an approved gain on a named batch is journalled Dr 1401 / Cr 5203")
    void approvedGainIsJournalledInTheRightDirection() {
        Stock s = twoBatches();
        StockAdjustmentDto gain = count(s, s.batch2(), "103");
        long mark = journalMark();

        approveEveryStep(gain.id());

        assertThat(adjustmentService.findById(gain.id()).status()).isEqualTo("POSTED");
        assertThat(onHand(s.batch2())).isEqualByComparingTo("103");
        Map<String, BigDecimal> gl = netByAccount(mark);
        assertThat(gl.get("1401")).as("a gain debits the stock account: 3 x 150").isEqualByComparingTo("450");
        assertThat(gl.get("5203")).as("and credits the adjustment account").isEqualByComparingTo("-450");
    }
}
