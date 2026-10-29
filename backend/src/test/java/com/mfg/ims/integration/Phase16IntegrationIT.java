package com.mfg.ims.integration;

import com.mfg.ims.integration.calllog.IntegrationCallService;
import com.mfg.ims.integration.tally.CreateTallyLedgerMappingRequest;
import com.mfg.ims.integration.tally.TallyLedgerMappingService;
import com.mfg.ims.integration.tally.TallyVoucherQueueService;
import com.mfg.ims.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 16 — Integrations.
 *
 * <p>P16-1 Record and list integration calls; P16-2 Create and list Tally ledger mappings;
 * P16-3 Enqueue Tally voucher; P16-4 Approve and reject Tally vouchers.
 */
@Transactional
public class Phase16IntegrationIT extends AbstractIntegrationTest {

    static final Long TEST_ACTOR = 9016L;

    @Autowired IntegrationCallService callService;
    @Autowired TallyLedgerMappingService mappingService;
    @Autowired TallyVoucherQueueService voucherQueueService;

    @Test
    void p16_1_recordAndListIntegrationCalls() {
        callService.record("TALLY", "OUT", "POST /ledgers", 200, true, 45, "corr-001", null);
        callService.record("TALLY", "OUT", "POST /vouchers", 500, false, 120, "corr-002", "Timeout");

        var calls = callService.list("TALLY");
        assertThat(calls).hasSizeGreaterThanOrEqualTo(2);
        assertThat(calls).anyMatch(c -> "corr-001".equals(c.correlationId()) && Boolean.TRUE.equals(c.ok()));
        assertThat(calls).anyMatch(c -> "corr-002".equals(c.correlationId()) && Boolean.FALSE.equals(c.ok()));
    }

    @Test
    void p16_2_createAndListTallyLedgerMappings() {
        var req = new CreateTallyLedgerMappingRequest("1001", "Cash", "Primary cash account");
        var dto = mappingService.create(req, TEST_ACTOR);
        assertThat(dto.id()).isNotNull();
        assertThat(dto.glAccountCode()).isEqualTo("1001");
        assertThat(dto.tallyLedger()).isEqualTo("Cash");

        var list = mappingService.list();
        assertThat(list).anyMatch(m -> "1001".equals(m.glAccountCode()));
    }

    @Test
    void p16_3_enqueueTallyVoucher() {
        var dto = voucherQueueService.enqueue("JOURNAL", 100L, "Journal",
                "<TALLYMESSAGE/>", TEST_ACTOR);
        assertThat(dto.id()).isNotNull();
        assertThat(dto.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(dto.voucherType()).isEqualTo("Journal");

        var list = voucherQueueService.list("PENDING_APPROVAL");
        assertThat(list).anyMatch(v -> v.id().equals(dto.id()));
    }

    @Test
    void p16_4_approveAndRejectTallyVouchers() {
        var v1 = voucherQueueService.enqueue("JOURNAL", 101L, "Receipt", "<R/>", TEST_ACTOR);
        var v2 = voucherQueueService.enqueue("JOURNAL", 102L, "Payment", "<P/>", TEST_ACTOR);

        var approved = voucherQueueService.approve(v1.id(), TEST_ACTOR);
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.approvedBy()).isEqualTo(TEST_ACTOR);

        var rejected = voucherQueueService.reject(v2.id(), TEST_ACTOR);
        assertThat(rejected.status()).isEqualTo("REJECTED");
    }
}
