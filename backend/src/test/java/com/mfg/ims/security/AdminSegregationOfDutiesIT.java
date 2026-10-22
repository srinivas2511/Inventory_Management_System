package com.mfg.ims.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

import com.mfg.ims.approval.service.ApprovalService;
import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.procurement.dto.CreatePoRequest;
import com.mfg.ims.procurement.dto.PurchaseOrderDto;
import com.mfg.ims.procurement.service.PurchaseOrderService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * Holding every permission does not let the Administrator approve their own submission. The authorities here are
 * exactly the ADMIN role's grants read from the database, not hand-picked ones.
 */
class AdminSegregationOfDutiesIT extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;
    @Autowired PurchaseOrderService poService;
    @Autowired ApprovalService approvalService;

    private Long adminUserId;
    private Long otherAdminUserId;

    @BeforeEach
    void actAsAdmin() {
        adminUserId = newUser("adm");
        otherAdminUserId = newUser("adm2");
        loginAsAdmin(adminUserId);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Long newUser(String prefix) {
        return jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'Administrator', ?, 'x') RETURNING id
                """, Long.class, td.code(prefix), td.code(prefix) + "@example.com");
    }

    private void loginAsAdmin(Long userId) {
        List<String> codes = jdbc.queryForList("""
                SELECT p.code FROM ims.permissions p
                  JOIN ims.role_permissions rp ON rp.permission_id = p.id
                  JOIN ims.roles r ON r.id = rp.role_id WHERE r.code = 'ADMIN'
                """, String.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CachedUser(userId, "u" + userId, true, 1, Set.of("ADMIN"), Set.copyOf(codes)), null,
                codes.stream().map(SimpleGrantedAuthority::new).toList()));
    }

    @Test
    @DisplayName("SOD-ADM-1: an administrator who submitted a purchase order cannot approve it, a different one can")
    void administratorCannotApproveTheirOwnPurchaseOrder() {
        PurchaseOrderDto po = poService.create(new CreatePoRequest(td.supplier().getId(), LocalDate.now(), null, null,
                null, null, List.of(new CreatePoRequest.PoLineRequest(td.material().getId(), new BigDecimal("10"),
                        new BigDecimal("50.0000"), BigDecimal.ZERO))));
        poService.submit(po.id());

        assertThatThrownBy(() -> poService.approve(po.id()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.SEGREGATION_OF_DUTIES);

        loginAsAdmin(otherAdminUserId);
        assertThat(poService.approve(po.id()).status()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("SOD-ADM-2: an administrator who requested an approval cannot approve that request")
    void administratorCannotApproveTheirOwnApprovalRequest() {
        var requests = approvalService.requestApproval("ADMIN_SOD_TEST", System.nanoTime() % 1_000_000, adminUserId,
                List.of(new ApprovalService.StepDefinition(1, "ADJUST_APPROVE")));

        assertThatThrownBy(() -> approvalService.approve(requests.get(0).getId(), "mine"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo(ErrorCode.SEGREGATION_OF_DUTIES);

        loginAsAdmin(otherAdminUserId);
        assertThat(approvalService.approve(requests.get(0).getId(), "ok").getStatus()).isEqualTo("APPROVED");
    }
}
