package com.springmfg.ims.finance;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.finance.ap.dto.ApInvoiceCreateRequest;
import com.springmfg.ims.finance.ap.dto.ApInvoiceDto;
import com.springmfg.ims.finance.ap.service.ApService;
import com.springmfg.ims.finance.gl.domain.FiscalPeriod;
import com.springmfg.ims.finance.gl.domain.JournalHeader;
import com.springmfg.ims.finance.gl.dto.AutoJournalRequest;
import com.springmfg.ims.finance.gl.dto.JournalCreateRequest;
import com.springmfg.ims.finance.gl.dto.JournalHeaderDto;
import com.springmfg.ims.finance.gl.dto.JournalLineRequest;
import com.springmfg.ims.finance.gl.repository.FiscalPeriodRepository;
import com.springmfg.ims.finance.gl.repository.GlAccountRepository;
import com.springmfg.ims.finance.gl.repository.JournalHeaderRepository;
import com.springmfg.ims.finance.gl.service.FiscalPeriodService;
import com.springmfg.ims.finance.gl.service.JournalService;
import com.springmfg.ims.partner.domain.Supplier;
import com.springmfg.ims.partner.repository.SupplierRepository;
import com.springmfg.ims.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for R9 Finance & Accounting (tasks R9-5.1–R9-5.4).
 * Business rules 16–19: DR=CR balance; period gate; AP invoice creation; AR auto-creation.
 */
class FinanceIT extends AbstractIntegrationTest {

    @Autowired JournalService journalService;
    @Autowired FiscalPeriodService periodService;
    @Autowired FiscalPeriodRepository periodRepo;
    @Autowired JournalHeaderRepository journalHeaderRepo;
    @Autowired ApService apService;
    @Autowired GlAccountRepository glAccountRepo;
    @Autowired SupplierRepository supplierRepo;

    private Long acct(String code) {
        return glAccountRepo.findByAccountCode(code).orElseThrow(
                () -> new AssertionError("GL account " + code + " missing from seed")).getId();
    }

    @Test
    @DisplayName("Business Rule 16: Journal entry must balance (Dr = Cr)")
    @Transactional
    void journalMustBalance() {
        // Unbalanced request should throw JOURNAL_NOT_BALANCED
        var req = new JournalCreateRequest(
                "MANUAL",
                LocalDate.of(2026, 4, 15),
                "Unbalanced test entry",
                "INR",
                List.of(
                        new JournalLineRequest(acct("1401"), new BigDecimal("1000.00"), BigDecimal.ZERO, "Dr"),
                        new JournalLineRequest(acct("2401"), BigDecimal.ZERO, new BigDecimal("500.00"), "Cr")
                )
        );

        assertThatThrownBy(() -> journalService.createDraft(req))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo(ErrorCode.JOURNAL_NOT_BALANCED));
    }

    @Test
    @DisplayName("Business Rule 17: Balanced journal creates DRAFT with lines summing to zero net")
    @Transactional
    void balancedJournalCreatesDraft() {
        BigDecimal amount = new BigDecimal("5000.0000");
        var req = new JournalCreateRequest(
                "MANUAL",
                LocalDate.of(2026, 4, 15),
                "Test balanced entry",
                "INR",
                List.of(
                        new JournalLineRequest(acct("1401"), amount, BigDecimal.ZERO, "Dr RM Stock"),
                        new JournalLineRequest(acct("2401"), BigDecimal.ZERO, amount, "Cr AP Control")
                )
        );

        JournalHeaderDto dto = journalService.createDraft(req);

        assertThat(dto.status()).isEqualTo("DRAFT");
        assertThat(dto.lines()).hasSize(2);
        BigDecimal totalDr = dto.lines().stream().map(l -> l.debitAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCr = dto.lines().stream().map(l -> l.creditAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalDr).isEqualByComparingTo(totalCr);
    }

    @Test
    @DisplayName("Business Rule 18: Closed period blocks posting")
    @Transactional
    void closedPeriodBlocksPosting() {
        // Find and close a period
        FiscalPeriod period = periodRepo.findOpenPeriodForDate(LocalDate.of(2026, 4, 15))
                .orElseThrow(() -> new AssertionError("Seeded period APR-2026 must be OPEN"));

        Long periodId = period.getId();
        periodService.initiatePeriodClose(periodId);
        periodService.finalizePeriodClose(periodId);

        // Attempt to create a draft journal for this date should fail with PERIOD_CLOSED
        var req = new JournalCreateRequest(
                "MANUAL",
                LocalDate.of(2026, 4, 15),
                "Post to closed period",
                "INR",
                List.of(
                        new JournalLineRequest(acct("1401"), new BigDecimal("100.0000"), BigDecimal.ZERO, "Dr"),
                        new JournalLineRequest(acct("2401"), BigDecimal.ZERO, new BigDecimal("100.0000"), "Cr")
                )
        );

        assertThatThrownBy(() -> journalService.createDraft(req))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode())
                        .isEqualTo(ErrorCode.PERIOD_CLOSED));
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("Journal lines: a machine-built journal drops zero-amount lines and keeps the rest balanced")
    void autoJournalDropsZeroLines() {
        var req = new AutoJournalRequest("AUTO", "TEST", "ZERO_LINES", 1L, LocalDate.of(2026, 4, 15),
                "Zero line dropped", null,
                List.of(new JournalLineRequest(acct("1401"), bd("100.00"), BigDecimal.ZERO, "Dr"),
                        new JournalLineRequest(acct("2401"), BigDecimal.ZERO, BigDecimal.ZERO, "Nothing to account"),
                        new JournalLineRequest(acct("2401"), BigDecimal.ZERO, bd("100.00"), "Cr")));

        JournalHeaderDto j = journalService.postAutoJournal(req);

        assertThat(j.status()).isEqualTo("POSTED");
        assertThat(j.lines()).hasSize(2);
    }

    @Test
    @DisplayName("Journal lines: a machine-built journal with fewer than two non-zero lines is rejected clearly")
    void autoJournalWithOneNonZeroLineIsRejected() {
        var req = new AutoJournalRequest("AUTO", "TEST", "ZERO_LINES", 2L, LocalDate.of(2026, 4, 15),
                "All zero", null,
                List.of(new JournalLineRequest(acct("1401"), BigDecimal.ZERO, BigDecimal.ZERO, "Dr"),
                        new JournalLineRequest(acct("2401"), BigDecimal.ZERO, BigDecimal.ZERO, "Cr")));

        assertThatThrownBy(() -> journalService.postAutoJournal(req))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("Journal lines: a hand-entered line with neither debit nor credit is a validation error, not a 500")
    @Transactional
    void manualJournalWithEmptyLineIsRejected() {
        var req = new JournalCreateRequest("MANUAL", LocalDate.of(2026, 4, 15), "Empty line", "INR",
                List.of(new JournalLineRequest(acct("1401"), bd("100.00"), BigDecimal.ZERO, "Dr"),
                        new JournalLineRequest(acct("2401"), BigDecimal.ZERO, bd("100.00"), "Cr"),
                        new JournalLineRequest(acct("2401"), BigDecimal.ZERO, BigDecimal.ZERO, "Blank")));

        assertThatThrownBy(() -> journalService.createDraft(req))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("Journal lines: a line carrying both a debit and a credit is a validation error")
    @Transactional
    void manualJournalWithTwoSidedLineIsRejected() {
        var req = new JournalCreateRequest("MANUAL", LocalDate.of(2026, 4, 15), "Two-sided line", "INR",
                List.of(new JournalLineRequest(acct("1401"), bd("100.00"), bd("100.00"), "Both"),
                        new JournalLineRequest(acct("2401"), BigDecimal.ZERO, bd("100.00"), "Cr")));

        assertThatThrownBy(() -> journalService.createDraft(req))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
