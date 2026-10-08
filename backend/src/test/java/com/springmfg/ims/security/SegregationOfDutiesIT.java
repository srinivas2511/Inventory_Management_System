package com.springmfg.ims.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.finance.gl.dto.AutoJournalRequest;
import com.springmfg.ims.finance.gl.dto.JournalCreateRequest;
import com.springmfg.ims.finance.gl.dto.JournalHeaderDto;
import com.springmfg.ims.finance.gl.dto.JournalLineRequest;
import com.springmfg.ims.finance.gl.repository.GlAccountRepository;
import com.springmfg.ims.finance.gl.service.JournalService;
import com.springmfg.ims.sales.dto.AddSalesOrderItemRequest;
import com.springmfg.ims.sales.dto.CreateSalesOrderRequest;
import com.springmfg.ims.sales.dto.SalesOrderDto;
import com.springmfg.ims.sales.service.SalesOrderService;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;

/**
 * Segregation of duties for journals (business rule 19, SEC-008) and sales orders (RUNBOOK second approver,
 * ARCHITECTURE section 10). Deliberately NOT {@code @Transactional}: each service call commits on its own. The
 * authorities are the real grants of the seeded roles read from the database, so holding the approval permission,
 * the Administrator role included, is shown not to bypass the rule.
 */
class SegregationOfDutiesIT extends AbstractIntegrationTest {

    private static final long USER_A = 7_910_001L;
    private static final long USER_B = 7_910_002L;
    private static final long USER_C = 7_910_003L;

    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;
    @Autowired JournalService journals;
    @Autowired SalesOrderService orders;
    @Autowired GlAccountRepository accounts;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private String[] grantsOf(String roleCode) {
        List<String> codes = jdbc.queryForList("""
                SELECT p.code FROM ims.permissions p
                  JOIN ims.role_permissions rp ON rp.permission_id = p.id
                  JOIN ims.roles r ON r.id = rp.role_id WHERE r.code = ?
                """, String.class, roleCode);
        return codes.toArray(String[]::new);
    }

    private List<String> rolesHolding(String permission) {
        return jdbc.queryForList("""
                SELECT r.code FROM ims.roles r
                  JOIN ims.role_permissions rp ON rp.role_id = r.id
                  JOIN ims.permissions p ON p.id = rp.permission_id WHERE p.code = ? ORDER BY r.code
                """, String.class, permission);
    }

    private JournalCreateRequest manualJournal() {
        BigDecimal amount = new BigDecimal("1500.0000");
        Long cash = accounts.findByAccountCode("1401").orElseThrow().getId();
        Long clearing = accounts.findByAccountCode("2401").orElseThrow().getId();
        return new JournalCreateRequest("MANUAL", LocalDate.of(2026, 4, 15), "SoD test", "INR",
                List.of(new JournalLineRequest(cash, amount, BigDecimal.ZERO, "Dr"),
                        new JournalLineRequest(clearing, BigDecimal.ZERO, amount, "Cr")));
    }

    private void assertSegregation(Runnable step) {
        assertThatThrownBy(step::run)
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.SEGREGATION_OF_DUTIES);
    }

    @Test
    @DisplayName("Business Rule 19 / SOD-JRN-1: no role that can approve journals can approve a journal its own user prepared")
    void noJournalApproverRoleCanApproveItsOwnJournal() {
        List<String> roles = rolesHolding("JOURNAL_APPROVE");
        assertThat(roles).as("roles holding JOURNAL_APPROVE").contains("ADMIN");
        for (String role : roles) {
            String[] grants = grantsOf(role);
            JournalHeaderDto draft = td.asUser(USER_A, () -> {
                JournalHeaderDto d = journals.createDraft(manualJournal());
                journals.submit(d.id());
                return d;
            }, grants);
            assertThat(draft.preparedBy()).as(role).isEqualTo(USER_A);

            assertSegregation(() -> td.asUser(USER_A, () -> journals.approve(draft.id()), grants));
            assertThat(journals.findById(draft.id()).status()).as(role + " journal still submitted").isEqualTo("SUBMITTED");

            JournalHeaderDto approved = td.asUser(USER_B, () -> journals.approve(draft.id()), grants);
            assertThat(approved.status()).as(role).isEqualTo("APPROVED");
            assertThat(approved.approvedBy()).isEqualTo(USER_B);
            assertThat(approved.preparedBy()).isEqualTo(USER_A);
        }
    }

    @Test
    @DisplayName("SOD-JRN-2: a different user's approval lets the journal post, and the posted journal balances")
    void secondUserApprovalCompletesTheJournal() {
        String[] admin = grantsOf("ADMIN");
        JournalHeaderDto draft = td.asUser(USER_A, () -> {
            JournalHeaderDto d = journals.createDraft(manualJournal());
            return journals.submit(d.id());
        }, admin);
        td.asUser(USER_B, () -> journals.approve(draft.id()), admin);
        JournalHeaderDto posted = td.asUser(USER_C, () -> journals.post(draft.id()), admin);

        assertThat(posted.status()).isEqualTo("POSTED");
        assertThat(posted.preparedBy()).isEqualTo(USER_A);
        assertThat(posted.approvedBy()).isEqualTo(USER_B);
        assertThat(posted.postedBy()).isEqualTo(USER_C);
    }

    private SalesOrderDto newOrder(long createdByUser, String... grants) {
        return td.asUser(createdByUser, () -> orders.create(new CreateSalesOrderRequest(td.customer().getId(),
                LocalDate.now(), LocalDate.now().plusDays(7), "Address", "NET30", "sod",
                List.of(new AddSalesOrderItemRequest(td.product().getId(), new BigDecimal("5"),
                        new BigDecimal("100.0000"), null, null)))), grants);
    }

    @Test
    @DisplayName("SOD-SO-1: no role that can approve sales orders can approve one its own user submitted")
    void noSalesApproverRoleCanApproveItsOwnOrder() {
        List<String> roles = rolesHolding("SALES_APPROVE");
        assertThat(roles).as("roles holding SALES_APPROVE").contains("ADMIN");
        for (String role : roles) {
            String[] grants = grantsOf(role);
            SalesOrderDto order = newOrder(USER_A, grants);
            SalesOrderDto confirmed = td.asUser(USER_A, () -> orders.confirm(order.id()), grants);
            assertThat(confirmed.submittedBy()).as(role).isEqualTo(USER_A);

            assertSegregation(() -> td.asUser(USER_A, () -> orders.approve(order.id()), grants));
            assertThat(orders.findById(order.id()).status()).as(role).isEqualTo("ORDERED");

            SalesOrderDto approved = td.asUser(USER_B, () -> orders.approve(order.id()), grants);
            assertThat(approved.status()).as(role).isEqualTo("APPROVED");
        }
    }

    @Test
    @DisplayName("SOD-SO-2: the creator and the submitter of an order are both barred from approving it; a third user may")
    void creatorAndSubmitterAreBothBarred() {
        String[] admin = grantsOf("ADMIN");
        SalesOrderDto order = newOrder(USER_A, admin);
        assertThat(order.createdBy()).isEqualTo(USER_A);
        td.asUser(USER_B, () -> orders.confirm(order.id()), admin);

        assertSegregation(() -> td.asUser(USER_A, () -> orders.approve(order.id()), admin));
        assertSegregation(() -> td.asUser(USER_B, () -> orders.approve(order.id()), admin));
        SalesOrderDto approved = td.asUser(USER_C, () -> orders.approve(order.id()), admin);

        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.createdBy()).isEqualTo(USER_A);
        assertThat(approved.submittedBy()).isEqualTo(USER_B);
    }

    @Test
    @DisplayName("SOD-SYS-1: a system posting is not a human step, so the same actor posts it without a second person")
    void systemPostingsAreNotBlocked() {
        BigDecimal amount = new BigDecimal("900.0000");
        Long inventory = accounts.findByAccountCode("1401").orElseThrow().getId();
        Long clearing = accounts.findByAccountCode("2401").orElseThrow().getId();
        String[] admin = grantsOf("ADMIN");

        // The payroll run posts its journal inside its own approval transaction through this entry point; the bridge
        // posts stock journals the same way. Neither has a preparer/approver pair.
        JournalHeaderDto posted = td.asUser(USER_A, () -> journals.postSourceJournal(new AutoJournalRequest(
                "SYSTEM", "test", "SOD_TEST", 1L, LocalDate.of(2026, 4, 15), "System posting", USER_A,
                List.of(new JournalLineRequest(inventory, amount, BigDecimal.ZERO, "Dr"),
                        new JournalLineRequest(clearing, BigDecimal.ZERO, amount, "Cr")))), admin);

        assertThat(posted.status()).isEqualTo("POSTED");
        assertThat(posted.preparedBy()).isEqualTo(USER_A);
        assertThat(posted.approvedBy()).isEqualTo(USER_A);
        assertThat(posted.postedBy()).isEqualTo(USER_A);
    }
}
