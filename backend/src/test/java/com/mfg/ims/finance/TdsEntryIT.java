package com.mfg.ims.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.finance.tds.TdsEntryDto;
import com.mfg.ims.finance.tds.TdsEntryService;
import com.mfg.ims.finance.tds.TdsSectionRepository;
import com.mfg.ims.support.AbstractIntegrationTest;

/**
 * TDS-1: create TDS entry; TDS-2: list by vendor/party.
 */
@Transactional
class TdsEntryIT extends AbstractIntegrationTest {

    @Autowired TdsEntryService tdsEntryService;
    @Autowired TdsSectionRepository sectionRepo;

    private static final Long TEST_USER_ID = 19001L;
    private static final Long PARTY_ID_A = 1001L;
    private static final Long PARTY_ID_B = 1002L;

    @BeforeEach
    void setUpSecurity() {
        var actor = new CachedUser(TEST_USER_ID, "tds-test-user", true, 1,
                Set.of("ADMIN"),
                Set.of("TDS_MANAGE"));
        var auth = new UsernamePasswordAuthenticationToken(actor, null,
                List.of(new SimpleGrantedAuthority("TDS_MANAGE")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tds_1_create_entry() {
        // Use section 1 (194C seeded by V68)
        var req = TdsEntryDto.request(
                "DEDUCTED", "SUPPLIER", PARTY_ID_A,
                1L,                                      // section_id = 194C
                new BigDecimal("100000.00"),
                new BigDecimal("1.0"),
                new BigDecimal("1000.00"),
                LocalDate.of(2026, 10, 1),
                "CHL-001",
                "2026-27");

        TdsEntryDto created = tdsEntryService.create(req, TEST_USER_ID);

        assertThat(created.id()).isNotNull();
        assertThat(created.direction()).isEqualTo("DEDUCTED");
        assertThat(created.partyType()).isEqualTo("SUPPLIER");
        assertThat(created.partyId()).isEqualTo(PARTY_ID_A);
        assertThat(created.tdsSection()).isEqualTo("194C");
        assertThat(created.baseAmount()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(created.tdsAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(created.challanNo()).isEqualTo("CHL-001");
        assertThat(created.fiscalYear()).isEqualTo("2026-27");
    }

    @Test
    void tds_2_list_filtered_by_party() {
        // Create entries for two different parties
        var reqA = TdsEntryDto.request("DEDUCTED", "SUPPLIER", PARTY_ID_A,
                1L, new BigDecimal("50000.00"), new BigDecimal("1.0"),
                new BigDecimal("500.00"), LocalDate.of(2026, 9, 1), null, "2026-27");
        var reqB = TdsEntryDto.request("DEDUCTED", "SUPPLIER", PARTY_ID_B,
                2L, new BigDecimal("80000.00"), new BigDecimal("5.0"),
                new BigDecimal("4000.00"), LocalDate.of(2026, 9, 15), null, "2026-27");

        tdsEntryService.create(reqA, TEST_USER_ID);
        tdsEntryService.create(reqB, TEST_USER_ID);

        List<TdsEntryDto> forA = tdsEntryService.list(PARTY_ID_A);
        List<TdsEntryDto> forB = tdsEntryService.list(PARTY_ID_B);

        assertThat(forA).anySatisfy(e -> assertThat(e.partyId()).isEqualTo(PARTY_ID_A));
        assertThat(forA).noneMatch(e -> PARTY_ID_B.equals(e.partyId()));
        assertThat(forB).anySatisfy(e -> assertThat(e.partyId()).isEqualTo(PARTY_ID_B));
        assertThat(forB).noneMatch(e -> PARTY_ID_A.equals(e.partyId()));
    }
}
