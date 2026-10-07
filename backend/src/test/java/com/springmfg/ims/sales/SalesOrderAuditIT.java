package com.springmfg.ims.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
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

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.sales.dto.AddSalesOrderItemRequest;
import com.springmfg.ims.sales.dto.CreateSalesOrderRequest;
import com.springmfg.ims.sales.dto.SalesOrderDto;
import com.springmfg.ims.sales.service.SalesOrderService;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;

/** D-26: every sales-order transition writes its audit row with the order id, old state, new state and reason in the right columns. */
class SalesOrderAuditIT extends AbstractIntegrationTest {

    @Autowired SalesOrderService salesOrderService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    @BeforeEach
    void login() {
        Long userId = jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'Sales audit IT', ?, 'x') RETURNING id
                """, Long.class, td.code("sa"), td.code("sa") + "@example.com");
        String[] authorities = { "SALES_CREATE", "SALES_APPROVE", "SALES_VIEW" };
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CachedUser(userId, "u" + userId, true, 1, Set.of(), Set.copyOf(Arrays.asList(authorities))), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private SalesOrderDto newOrder() {
        return salesOrderService.create(new CreateSalesOrderRequest(td.customer().getId(), LocalDate.now(),
                LocalDate.now().plusDays(7), "Address", "NET30", "audit",
                List.of(new AddSalesOrderItemRequest(td.product().getId(), new BigDecimal("5"),
                        new BigDecimal("100.0000"), null, null))));
    }

    private Map<String, Object> auditRow(String action, long orderId) {
        return jdbc.queryForMap("""
                SELECT entity, entity_id, old_value::text AS old_value, new_value::text AS new_value, reason
                  FROM ims.audit_logs WHERE action = ? AND entity_id = ? ORDER BY id DESC LIMIT 1
                """, action, String.valueOf(orderId));
    }

    @Test
    @DisplayName("SO-AUD-1 (D-26): create, confirm, approve and cancel each audit the order id, the old and new state and the reason")
    void everyTransitionIsAuditedWithItsValuesInTheRightColumns() {
        SalesOrderDto order = newOrder();
        Map<String, Object> created = auditRow("SO_CREATED", order.id());
        assertThat(created.get("entity")).isEqualTo("SALES_ORDER");
        assertThat(created.get("old_value")).isNull();
        assertThat((String) created.get("new_value")).contains("DRAFT");

        salesOrderService.confirm(order.id());
        Map<String, Object> confirmed = auditRow("SO_CONFIRMED", order.id());
        assertThat((String) confirmed.get("old_value")).contains("DRAFT");
        assertThat((String) confirmed.get("new_value")).contains("ORDERED");

        salesOrderService.approve(order.id());
        Map<String, Object> approved = auditRow("SO_APPROVED", order.id());
        assertThat((String) approved.get("old_value")).contains("ORDERED");
        assertThat((String) approved.get("new_value")).contains("APPROVED");

        salesOrderService.cancel(order.id(), "customer withdrew");
        Map<String, Object> cancelled = auditRow("SO_CANCELLED", order.id());
        assertThat((String) cancelled.get("old_value")).contains("APPROVED");
        assertThat((String) cancelled.get("new_value")).contains("CANCELLED");
        assertThat(cancelled.get("reason")).isEqualTo("customer withdrew");
    }
}
