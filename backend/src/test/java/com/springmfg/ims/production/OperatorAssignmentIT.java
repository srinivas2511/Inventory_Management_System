package com.springmfg.ims.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
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

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.engineering.bom.BomDto;
import com.springmfg.ims.engineering.bom.BomItemRequest;
import com.springmfg.ims.engineering.bom.BomService;
import com.springmfg.ims.engineering.bom.CreateBomRequest;
import com.springmfg.ims.engineering.routing.RoutingService;
import com.springmfg.ims.engineering.routing.SaveRoutingStepRequest;
import com.springmfg.ims.material.domain.Material;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.production.dto.AssignableOperatorDto;
import com.springmfg.ims.production.dto.CreateProductionOrderRequest;
import com.springmfg.ims.production.dto.ProductionOperationDto;
import com.springmfg.ims.production.dto.ProductionOrderDto;
import com.springmfg.ims.production.dto.RecordOutputRequest;
import com.springmfg.ims.production.service.OperationAssignmentService;
import com.springmfg.ims.production.service.OperationExecutionService;
import com.springmfg.ims.production.service.OperatorDirectory;
import com.springmfg.ims.production.service.ProductionOrderService;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;

/**
 * Operators and supervisors only see the work assigned to them, so assigning is what puts an operation on the shop
 * floor. Deliberately not transactional: it drives the real services and reads My Work the way the tablet does.
 */
class OperatorAssignmentIT extends AbstractIntegrationTest {

    @Autowired BomService bomService;
    @Autowired RoutingService routingService;
    @Autowired ProductionOrderService orderService;
    @Autowired OperationExecutionService executionService;
    @Autowired OperationAssignmentService assignmentService;
    @Autowired OperatorDirectory operatorDirectory;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    @BeforeEach
    void login() {
        Long planner = newUser("plan", true, null);
        String[] authorities = { "PRODUCTION_VIEW", "PRODUCTION_CREATE", "PRODUCTION_ASSIGN", "PRODUCTION_EXECUTE" };
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CachedUser(planner, "u" + planner, true, 1, Set.of(), Set.copyOf(Arrays.asList(authorities))), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** A user; with a role code it holds that role's permissions, without one it holds none. */
    private Long newUser(String prefix, boolean active, String roleCode) {
        String name = td.code(prefix);
        Long id = jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash, active)
                VALUES (?, ?, ?, 'x', ?) RETURNING id
                """, Long.class, name, "Person " + name, name + "@example.com", active);
        if (roleCode != null) {
            jdbc.update("INSERT INTO ims.user_roles (user_id, role_id) SELECT ?, id FROM ims.roles WHERE code = ?",
                    id, roleCode);
        }
        return id;
    }

    private ProductionOrderDto releasedOrder() {
        Product product = td.product();
        Material material = td.material();
        List<Long> masterOps = jdbc.queryForList("SELECT id FROM ims.operations ORDER BY id LIMIT 3", Long.class);
        routingService.saveSteps(product.getId(), List.of(
                new SaveRoutingStepRequest(10, masterOps.get(0), null, new BigDecimal("15"), new BigDecimal("10"), false),
                new SaveRoutingStepRequest(20, masterOps.get(1), null, new BigDecimal("20"), new BigDecimal("10"), false),
                new SaveRoutingStepRequest(30, masterOps.get(2), null, new BigDecimal("20"), new BigDecimal("5"), true)));
        BomDto bom = bomService.create(new CreateBomRequest(product.getId(), BigDecimal.ONE, "Assignment IT BOM",
                List.of(new BomItemRequest(10, "MATERIAL", material.getId(), null,
                        new BigDecimal("0.045"), "KG", new BigDecimal("3"), "wire"))));
        bom = bomService.submitForReview(bom.id());
        bom = bomService.approve(bom.id());
        bom = bomService.activate(bom.id());
        ProductionOrderDto order = orderService.create(new CreateProductionOrderRequest(
                product.getId(), bom.id(), new BigDecimal("100"), LocalDate.now(), LocalDate.now().plusDays(3),
                "LINE-A", null, null, null, (short) 2, "Assignment IT"));
        order = orderService.approve(order.id());
        return orderService.release(order.id());
    }

    private List<ProductionOperationDto> operations(Long orderId) {
        return orderService.findById(orderId).operations().stream()
                .sorted(Comparator.comparing(ProductionOperationDto::sequenceNo)).toList();
    }

    private long auditRows(Long operationId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ims.audit_logs WHERE action = 'OP_ASSIGN' AND entity_id = ?",
                Long.class, String.valueOf(operationId));
    }

    @Test
    @DisplayName("ASG-1: an assigned operator sees the operation in My Work, an unassigned user does not, and the DTO names the operator")
    void assignedOperatorsSeeTheirWork() {
        ProductionOrderDto order = releasedOrder();
        Long first = operations(order.id()).get(0).id();
        Long operator = newUser("op", true, "OPERATOR");
        Long other = newUser("op", true, "OPERATOR");

        ProductionOperationDto dto = assignmentService.assign(first, operator, "Night shift");

        assertThat(dto.assignedOperatorId()).isEqualTo(operator);
        assertThat(dto.assignedOperatorName()).startsWith("Person ");
        assertThat(executionService.getMyWork(operator)).extracting(ProductionOperationDto::id).contains(first);
        assertThat(executionService.getMyWork(other)).extracting(ProductionOperationDto::id).doesNotContain(first);
        assertThat(operations(order.id()).get(0).assignedOperatorName()).isEqualTo(dto.assignedOperatorName());
    }

    @Test
    @DisplayName("ASG-2: assigning the same operator again changes and audits nothing; reassigning and unassigning are audited with old and new")
    void assignmentIsIdempotentAndAudited() {
        ProductionOrderDto order = releasedOrder();
        Long first = operations(order.id()).get(0).id();
        Long a = newUser("op", true, "OPERATOR");
        Long b = newUser("sup", true, "SUPERVISOR");

        assignmentService.assign(first, a, "first");
        assertThat(auditRows(first)).isEqualTo(1);
        assignmentService.assign(first, a, "again");
        assertThat(auditRows(first)).isEqualTo(1);

        assignmentService.assign(first, b, "swap");
        assertThat(auditRows(first)).isEqualTo(2);
        String oldNew = jdbc.queryForObject("""
                SELECT old_value::text || '>' || new_value::text FROM ims.audit_logs
                 WHERE action = 'OP_ASSIGN' AND entity_id = ? ORDER BY id DESC LIMIT 1
                """, String.class, String.valueOf(first));
        assertThat(oldNew).isEqualTo(a + ">" + b);

        ProductionOperationDto cleared = assignmentService.assign(first, null, "no longer needed");
        assertThat(cleared.assignedOperatorId()).isNull();
        assertThat(cleared.assignedOperatorName()).isNull();
        assertThat(executionService.getMyWork(b)).extracting(ProductionOperationDto::id).doesNotContain(first);
        assertThat(auditRows(first)).isEqualTo(3);
    }

    @Test
    @DisplayName("ASG-3: only active, unlocked users whose roles can execute production work are assignable")
    void onlyExecutingUsersAreAssignable() {
        ProductionOrderDto order = releasedOrder();
        Long first = operations(order.id()).get(0).id();
        Long noRole = newUser("norole", true, null);
        Long sales = newUser("sales", true, "SALES");
        Long inactive = newUser("gone", false, "OPERATOR");
        Long locked = newUser("lock", true, "OPERATOR");
        jdbc.update("UPDATE ims.users SET locked_until = now() + interval '1 hour' WHERE id = ?", locked);

        for (Long candidate : List.of(noRole, sales, inactive, locked, 987654321L)) {
            assertThatThrownBy(() -> assignmentService.assign(first, candidate, null))
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
        assertThat(operations(order.id()).get(0).assignedOperatorId()).isNull();
    }

    @Test
    @DisplayName("ASG-4: a finished operation cannot be assigned, and neither can an operation of an order that is not running")
    void stateRulesAreEnforced() {
        ProductionOrderDto order = releasedOrder();
        List<ProductionOperationDto> ops = operations(order.id());
        Long operator = newUser("op", true, "OPERATOR");
        Long first = ops.get(0).id();

        executionService.startOperation(first, null);
        executionService.recordOutput(new RecordOutputRequest(first, new BigDecimal("100"), BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, null, null, "A", "asg"));
        executionService.completeOperation(first);
        assertThatThrownBy(() -> assignmentService.assign(first, operator, null))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));

        ProductionOrderDto draft = orderService.create(new CreateProductionOrderRequest(
                order.productId(), order.bomId(), new BigDecimal("10"), LocalDate.now(), LocalDate.now().plusDays(1),
                "LINE-A", null, null, null, (short) 3, "draft"));
        assertThatThrownBy(() -> assignmentService.assignAll(draft.id(), operator, null))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));
    }

    @Test
    @DisplayName("ASG-5: assigning a whole order covers every unfinished operation, skips finished ones and can be undone")
    void assignAllSkipsFinishedOperations() {
        ProductionOrderDto order = releasedOrder();
        List<ProductionOperationDto> ops = operations(order.id());
        Long operator = newUser("op", true, "OPERATOR");
        Long first = ops.get(0).id();
        executionService.startOperation(first, null);
        executionService.recordOutput(new RecordOutputRequest(first, new BigDecimal("100"), BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, null, null, "A", "asg"));
        executionService.completeOperation(first);

        List<ProductionOperationDto> result = assignmentService.assignAll(order.id(), operator, "whole order");

        assertThat(result).hasSize(3);
        assertThat(result.get(0).assignedOperatorId()).isNull();
        assertThat(result.get(1).assignedOperatorId()).isEqualTo(operator);
        assertThat(result.get(2).assignedOperatorId()).isEqualTo(operator);
        assertThat(executionService.getMyWork(operator)).extracting(ProductionOperationDto::sequenceNo)
                .containsExactlyInAnyOrder(20, 30);

        assignmentService.assignAll(order.id(), null, "undo");
        assertThat(executionService.getMyWork(operator)).isEmpty();
    }

    @Test
    @DisplayName("ASG-6: the operator picker lists assignable users, filters by name and treats a blank search as no filter")
    void operatorPickerSearch() {
        Long operator = newUser("pickme", true, "OPERATOR");
        Long noRole = newUser("pickme", true, null);
        String name = jdbc.queryForObject("SELECT username FROM ims.users WHERE id = ?", String.class, operator);

        assertThat(operatorDirectory.assignable(name)).extracting(AssignableOperatorDto::id)
                .contains(operator).doesNotContain(noRole);
        assertThat(operatorDirectory.assignable(null)).extracting(AssignableOperatorDto::id).contains(operator);
        assertThat(operatorDirectory.assignable("   ")).extracting(AssignableOperatorDto::id).contains(operator);
        assertThat(operatorDirectory.assignable("zz-no-such-person")).isEmpty();
        assertThat(operatorDirectory.names(List.of(operator, noRole))).containsOnlyKeys(operator, noRole);
        assertThat(operatorDirectory.names(List.of())).isEmpty();
    }
}
