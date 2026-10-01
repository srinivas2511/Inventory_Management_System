package com.springmfg.ims.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.springmfg.ims.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Task 1.1: V1-V3 apply to an empty database and give the schema DESIGN.md sections 2.1-2.3 describe. */
class SchemaMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void phase1TablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'ims'", String.class);
        assertThat(tables).contains(
                "uoms", "users", "roles", "permissions", "user_roles", "role_permissions", "user_warehouse_access",
                "refresh_tokens", "password_reset_tokens", "password_history", "system_settings",
                "number_sequences", "idempotency_keys", "audit_logs", "suppliers", "customers", "materials",
                "warehouses", "warehouse_locations", "machines", "operations", "rejection_reasons", "products",
                "spring_specifications", "spring_attribute_definitions", "customer_product_specs");
    }

    @Test
    void uomsAreSeeded() {
        List<String> codes = jdbc.queryForList("SELECT code FROM ims.uoms", String.class);
        assertThat(codes).contains("KG", "G", "PCS", "M", "MM", "CARTON");
    }

    @Test
    void auditLogIsPartitionedByMonthWithDefaultPartition() {
        Integer partitions = jdbc.queryForObject("""
                SELECT count(*) FROM pg_inherits i JOIN pg_class p ON p.oid = i.inhparent
                WHERE p.relname = 'audit_logs'""", Integer.class);
        assertThat(partitions).isGreaterThanOrEqualTo(14); // current + next 12 months + default
    }

    @Test
    void auditLogRowsCannotBeUpdatedOrDeleted() {
        jdbc.update("INSERT INTO ims.audit_logs (action, entity) VALUES ('TEST', 'schema-it')");
        assertThatThrownBy(() -> jdbc.update("UPDATE ims.audit_logs SET action = 'X' WHERE entity = 'schema-it'"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ims.audit_logs WHERE entity = 'schema-it'"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }

    @Test
    void usernameAndEmailAreCaseInsensitivelyUnique() {
        jdbc.update("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES ('CaseUser', 'Case User', 'Case@Example.com', 'x')""");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES ('caseuser', 'Other', 'other@example.com', 'x')""")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES ('other', 'Other', 'case@example.com', 'x')""")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void materialStockLevelsMustBeOrdered() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ims.materials (material_code, name, material_type, uom, min_stock, reorder_level)
                VALUES ('RM-BAD-1', 'Bad', 'ALLOY', 'KG', 50, 10)""")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void springTypeIsConstrainedToTheEnumeration() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO ims.products (product_code, name, spring_type) VALUES ('P-BAD', 'Bad', 'NOPE')"))
                .isInstanceOf(DataAccessException.class);
    }
}
