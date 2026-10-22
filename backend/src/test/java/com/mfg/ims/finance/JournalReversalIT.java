package com.mfg.ims.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.finance.gl.dto.JournalCreateRequest;
import com.mfg.ims.finance.gl.dto.JournalHeaderDto;
import com.mfg.ims.finance.gl.dto.JournalLineDto;
import com.mfg.ims.finance.gl.dto.JournalLineRequest;
import com.mfg.ims.finance.gl.service.GlAccountResolver;
import com.mfg.ims.finance.gl.service.JournalService;
import com.mfg.ims.support.AbstractIntegrationTest;

/**
 * Reversing a posted journal: the original has to leave POSTED, or the reversal button could be pressed again and
 * again, each time booking another mirror image. Not {@code @Transactional}: each service call commits on its own,
 * as it does for a user clicking through the journal screens.
 */
class JournalReversalIT extends AbstractIntegrationTest {

    @Autowired JournalService journals;
    @Autowired com.mfg.ims.support.TestData td;
    @Autowired GlAccountResolver glAccounts;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("JRN-REV-1: reversing a posted journal marks it REVERSED, mirrors its lines and cannot be repeated")
    void reversalMarksTheOriginalAndCannotBeRepeated() {
        JournalHeaderDto posted = postedManualJournal("1500.0000");

        JournalHeaderDto reversal = journals.reverse(posted.id(), "Entered against the wrong account");

        assertThat(reversal.reversalOfId()).isEqualTo(posted.id());
        assertThat(reversal.status()).isEqualTo("POSTED");
        assertThat(reversal.journalType()).isEqualTo("REVERSAL");
        assertThat(reversal.description()).contains("Entered against the wrong account");
        JournalLineDto bank = line(reversal, "1201");
        JournalLineDto revenue = line(reversal, "4001");
        assertThat(bank.debitAmount()).isEqualByComparingTo("0");
        assertThat(bank.creditAmount()).isEqualByComparingTo("1500");
        assertThat(revenue.debitAmount()).isEqualByComparingTo("1500");
        assertThat(revenue.creditAmount()).isEqualByComparingTo("0");
        assertThat(status(posted.id())).isEqualTo("REVERSED");

        assertThatThrownBy(() -> journals.reverse(posted.id(), "again"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ims.journal_headers WHERE reversal_of_id = ?", Integer.class, posted.id()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("JRN-REV-2: only a posted journal can be reversed")
    void onlyAPostedJournalCanBeReversed() {
        JournalHeaderDto draft = journals.createDraft(manual("700.0000"));

        assertThatThrownBy(() -> journals.reverse(draft.id(), "too early"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION);
        assertThat(status(draft.id())).isEqualTo("DRAFT");
    }

    private JournalHeaderDto postedManualJournal(String amount) {
        JournalHeaderDto draft = journals.createDraft(manual(amount));
        journals.submit(draft.id());
        td.runAsSecondUser(() -> journals.approve(draft.id()), "JOURNAL_APPROVE");
        return journals.post(draft.id());
    }

    private JournalCreateRequest manual(String amount) {
        Long bank = glAccounts.idOf("1201");
        Long revenue = glAccounts.idOf("4001");
        BigDecimal value = new BigDecimal(amount);
        return new JournalCreateRequest("GL", LocalDate.now(), "Manual journal " + System.nanoTime(), "INR",
                List.of(new JournalLineRequest(bank, value, BigDecimal.ZERO, "Dr bank"),
                        new JournalLineRequest(revenue, BigDecimal.ZERO, value, "Cr revenue")));
    }

    private static JournalLineDto line(JournalHeaderDto journal, String accountCode) {
        return journal.lines().stream().filter(l -> accountCode.equals(l.accountCode())).findFirst()
                .orElseThrow(() -> new AssertionError("no line on " + accountCode));
    }

    private String status(Long journalId) {
        return jdbc.queryForObject("SELECT status FROM ims.journal_headers WHERE id = ?", String.class, journalId);
    }
}
