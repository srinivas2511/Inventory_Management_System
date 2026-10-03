package com.springmfg.ims.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.finance.gl.dto.JournalCreateRequest;
import com.springmfg.ims.finance.gl.dto.JournalHeaderDto;
import com.springmfg.ims.finance.gl.dto.JournalLineRequest;
import com.springmfg.ims.finance.gl.repository.FiscalPeriodRepository;
import com.springmfg.ims.finance.gl.repository.GlAccountRepository;
import com.springmfg.ims.finance.gl.service.FiscalPeriodService;
import com.springmfg.ims.finance.gl.service.JournalService;
import com.springmfg.ims.support.AbstractIntegrationTest;

/** Documents are attributed to the authenticated user, never to the -1 system fallback. */
class UserAttributionIT extends AbstractIntegrationTest {

    private static final long PREPARER = 7001L;
    private static final long APPROVER = 7002L;
    private static final long POSTER = 7003L;

    @Autowired JournalService journalService;
    @Autowired FiscalPeriodService periodService;
    @Autowired FiscalPeriodRepository periodRepo;
    @Autowired GlAccountRepository glAccountRepo;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void as(long userId, String... authorities) {
        var token = new UsernamePasswordAuthenticationToken(
                new CachedUser(userId, "u" + userId, true, 1, Set.of(), Set.copyOf(Arrays.asList(authorities))), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private Long acct(String code) {
        return glAccountRepo.findByAccountCode(code).orElseThrow().getId();
    }

    @Test
    @DisplayName("Manual journal records the real preparer, approver and poster")
    @Transactional
    void journalIsAttributedToTheAuthenticatedUsers() {
        BigDecimal amount = new BigDecimal("1200.0000");
        var req = new JournalCreateRequest("MANUAL", LocalDate.of(2026, 4, 15), "Attribution test", "INR",
                List.of(new JournalLineRequest(acct("1401"), amount, BigDecimal.ZERO, "Dr"),
                        new JournalLineRequest(acct("2401"), BigDecimal.ZERO, amount, "Cr")));

        as(PREPARER, "GL_POST");
        JournalHeaderDto draft = journalService.createDraft(req);
        assertThat(draft.preparedBy()).isEqualTo(PREPARER);
        journalService.submit(draft.id());

        as(APPROVER, "JOURNAL_APPROVE");
        assertThat(journalService.approve(draft.id()).approvedBy()).isEqualTo(APPROVER);

        as(POSTER, "GL_POST");
        JournalHeaderDto posted = journalService.post(draft.id());

        assertThat(posted.preparedBy()).isEqualTo(PREPARER);
        assertThat(posted.approvedBy()).isEqualTo(APPROVER);
        assertThat(posted.postedBy()).isEqualTo(POSTER);
    }

    @Test
    @DisplayName("Period close records the real user")
    @Transactional
    void periodCloseIsAttributedToTheAuthenticatedUser() {
        Long periodId = periodRepo.findOpenPeriodForDate(LocalDate.of(2026, 4, 15)).orElseThrow().getId();

        as(APPROVER, "PERIOD_CLOSE");
        periodService.initiatePeriodClose(periodId);
        periodService.finalizePeriodClose(periodId);

        assertThat(periodRepo.findById(periodId).orElseThrow().getClosedBy()).isEqualTo(APPROVER);
    }
}
