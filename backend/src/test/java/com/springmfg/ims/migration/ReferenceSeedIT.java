package com.springmfg.ims.migration;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.springmfg.ims.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Task 1.2: the reference seed must equal the permission catalogue in DESIGN.md section 7.1 and the role
 * matrix in ARCHITECTURE.md section 10.3 (expanded as recorded in docs/decisions.md).
 */
class ReferenceSeedIT extends AbstractIntegrationTest {

    /** Expected role -> permissions. Written out by hand so that a change to the seed or the matrix is a visible diff. */
    private static final Map<String, Set<String>> EXPECTED = Map.ofEntries(
        entry("ADMIN", Set.of("ADJUST_APPROVE", "ADJUST_APPROVE_ADMIN", "AUDIT_VIEW", "BOM_VIEW", "CUSTOMER_MANAGE", "DASHBOARD_VIEW", "DISPATCH_APPROVE", "DISPATCH_VIEW", "EXPORT_DATA", "INVENTORY_ADJUST", "INVENTORY_VIEW", "INVENTORY_VIEW_ALL", "LOCATION_MANAGE", "MACHINE_MANAGE", "MAINTENANCE_VIEW", "MASTERDATA_MANAGE", "MATERIAL_VIEW", "OPERATION_MANAGE", "PERMISSION_MANAGE", "PRODUCTION_VIEW", "PRODUCT_VIEW", "PURCHASE_APPROVE", "PURCHASE_VIEW", "QUALITY_VIEW", "REJECTION_REASON_MANAGE", "REPORT_VIEW", "ROLE_MANAGE", "SALES_VIEW", "SETTINGS_MANAGE", "SUPPLIER_MANAGE", "TRACEABILITY_VIEW", "USER_CREATE", "USER_DELETE", "USER_UPDATE", "USER_VIEW", "VALUATION_VIEW", "WAREHOUSE_MANAGE")),
        entry("ENGINEER", Set.of("BOM_APPROVE", "BOM_CREATE", "BOM_REVIEW", "BOM_UPDATE", "BOM_VIEW", "DRAWING_MANAGE", "INVENTORY_VIEW", "INVENTORY_VIEW_ALL", "MATERIAL_VIEW", "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_VIEW", "REPORT_VIEW", "ROUTING_MANAGE")),
        entry("PRODUCTION_MANAGER", Set.of("BOM_VIEW", "INVENTORY_VIEW", "INVENTORY_VIEW_ALL", "MATERIAL_VIEW", "PRODUCTION_APPROVE", "PRODUCTION_ASSIGN", "PRODUCTION_CLOSE", "PRODUCTION_CREATE", "PRODUCTION_RELEASE", "PRODUCTION_REOPEN", "PRODUCTION_SCHEDULE", "PRODUCTION_UPDATE", "PRODUCTION_VIEW", "PRODUCT_VIEW", "REPORT_VIEW")),
        entry("SUPERVISOR", Set.of("ADJUST_REVIEW", "DOWNTIME_RECORD", "MATERIAL_REQUEST", "PROBLEM_REPORT", "PRODUCTION_ASSIGN", "PRODUCTION_EXECUTE", "PRODUCT_VIEW")),
        entry("OPERATOR", Set.of("PROBLEM_REPORT", "PRODUCTION_EXECUTE")),
        entry("QUALITY_MANAGER", Set.of("MATERIAL_VIEW", "PRODUCT_VIEW", "QUALITY_APPROVE", "QUALITY_HOLD", "QUALITY_INSPECT", "QUALITY_REJECT", "QUALITY_VIEW", "REJECTION_REASON_MANAGE", "REPORT_VIEW", "TRACEABILITY_VIEW")),
        entry("STORE_MANAGER", Set.of("ADJUST_APPROVE", "INVENTORY_ADJUST", "INVENTORY_ISSUE", "INVENTORY_RECEIVE", "INVENTORY_RETURN", "INVENTORY_TRANSFER", "INVENTORY_VIEW", "INVENTORY_VIEW_ALL", "LOCATION_MANAGE", "MATERIAL_VIEW", "REPORT_VIEW", "STOCK_COUNT", "VALUATION_VIEW")),
        entry("PURCHASE_MANAGER", Set.of("MATERIAL_VIEW", "PURCHASE_CREATE", "PURCHASE_VIEW", "REQUISITION_CREATE", "SUPPLIER_MANAGE")),
        entry("SALES", Set.of("CUSTOMER_MANAGE", "DISPATCH_REQUEST", "DISPATCH_VIEW", "PRODUCT_VIEW", "RESERVE_FG", "SALES_CREATE", "SALES_UPDATE", "SALES_VIEW")),
        entry("DISPATCH", Set.of("DISPATCH_CREATE", "DISPATCH_VIEW", "INVENTORY_VIEW", "INVENTORY_VIEW_ALL", "SALES_VIEW")),
        entry("MAINTENANCE", Set.of("DOWNTIME_RECORD", "MACHINE_MANAGE", "MAINTENANCE_MANAGE", "MAINTENANCE_VIEW")),
        entry("STORE_OPERATOR", Set.of("INVENTORY_ISSUE", "INVENTORY_RECEIVE", "INVENTORY_RETURN", "INVENTORY_TRANSFER", "INVENTORY_VIEW", "STOCK_COUNT")),
        entry("MANAGEMENT", Set.of("BOM_VIEW", "DASHBOARD_VIEW", "DISPATCH_VIEW", "EXPORT_DATA", "INVENTORY_VIEW", "INVENTORY_VIEW_ALL", "MAINTENANCE_VIEW", "MATERIAL_VIEW", "PRODUCTION_VIEW", "PRODUCT_VIEW", "PURCHASE_VIEW", "QUALITY_VIEW", "REPORT_VIEW", "SALES_VIEW", "TRACEABILITY_VIEW", "VALUATION_VIEW")));

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void thirteenSystemRolesAreSeeded() {
        List<String> roles = jdbc.queryForList("SELECT code FROM ims.roles WHERE system_role", String.class);
        assertThat(roles).containsExactlyInAnyOrderElementsOf(EXPECTED.keySet()).hasSize(13);
    }

    @Test
    void permissionCatalogueEqualsDesignSection71() throws IOException {
        Path design = Path.of("..", "docs", "DESIGN.md");
        Assumptions.assumeTrue(Files.exists(design), "docs/DESIGN.md not available in this build context");
        String doc = Files.readString(design);
        String section = doc.substring(doc.indexOf("### 7.1 Permission catalogue"), doc.indexOf("### 7.2 JWT"));
        Set<String> documented = new TreeSet<>();
        for (String row : section.split("\n")) {
            if (row.startsWith("| ") && !row.startsWith("| Module") && !row.startsWith("|---")) {
                String codes = row.substring(row.indexOf('`'));
                Matcher m = Pattern.compile("[A-Z][A-Z_]+").matcher(codes);
                while (m.find()) {
                    documented.add(m.group());
                }
            }
        }
        Set<String> seeded = new TreeSet<>(jdbc.queryForList("SELECT code FROM ims.permissions", String.class));
        assertThat(documented).hasSize(75);
        assertThat(seeded).isEqualTo(documented);
    }

    @Test
    void roleToPermissionMapEqualsTheDocumentedMatrix() {
        Map<String, Set<String>> actual = new java.util.HashMap<>();
        jdbc.query("""
                SELECT r.code AS role, p.code AS permission FROM ims.role_permissions rp
                JOIN ims.roles r ON r.id = rp.role_id JOIN ims.permissions p ON p.id = rp.permission_id
                WHERE r.system_role""",
                rs -> {
                    actual.computeIfAbsent(rs.getString("role"), k -> new HashSet<>()).add(rs.getString("permission"));
                });
        assertThat(actual.keySet()).isEqualTo(EXPECTED.keySet());
        EXPECTED.forEach((role, perms) -> assertThat(actual.get(role)).as(role).containsExactlyInAnyOrderElementsOf(perms));
    }

    @Test
    void everyPermissionIsHeldByAtLeastOneRole() {
        List<String> orphans = jdbc.queryForList("""
                SELECT p.code FROM ims.permissions p
                WHERE NOT EXISTS (SELECT 1 FROM ims.role_permissions rp WHERE rp.permission_id = p.id)""", String.class);
        assertThat(orphans).isEmpty();
    }

    @Test
    void operatorCannotModifyProductsOrInventory() {
        Set<String> operator = EXPECTED.get("OPERATOR");
        assertThat(operator).containsExactlyInAnyOrder("PRODUCTION_EXECUTE", "PROBLEM_REPORT");
        assertThat(actualPermissions("OPERATOR")).doesNotContain("PRODUCT_UPDATE", "PRODUCT_CREATE", "INVENTORY_ADJUST");
    }

    @Test
    void managementHasNoWritePermissions() {
        Set<String> writes = actualPermissions("MANAGEMENT").stream()
                .filter(c -> !c.endsWith("_VIEW") && !c.equals("INVENTORY_VIEW_ALL") && !c.equals("EXPORT_DATA"))
                .collect(Collectors.toSet());
        assertThat(writes).isEmpty();
    }

    @Test
    void onlyAdminCanApproveLargeAdjustmentsAndStoreRolesCannot() {
        List<String> holders = jdbc.queryForList("""
                SELECT r.code FROM ims.role_permissions rp JOIN ims.roles r ON r.id = rp.role_id
                JOIN ims.permissions p ON p.id = rp.permission_id WHERE p.code = 'ADJUST_APPROVE_ADMIN'""", String.class);
        assertThat(holders).containsExactly("ADMIN");
    }

    @Test
    void rejectionReasonsOperationsAndSettingsAreSeeded() {
        assertThat(jdbc.queryForList("SELECT name FROM ims.rejection_reasons", String.class)).containsExactlyInAnyOrder(
                "Incorrect Dimension", "Wrong Wire Diameter", "Surface Defect", "Crack", "Incorrect Spring Rate",
                "Heat Treatment Failure", "Coiling Defect", "Grinding Defect", "Customer Specification Failure");
        assertThat(jdbc.queryForList("SELECT name FROM ims.operations", String.class)).containsExactlyInAnyOrder(
                "Wire Drawing", "Coiling", "Cutting", "Grinding", "Heat Treatment", "Shot Peening",
                "Surface Treatment", "Inspection", "Packaging");
        assertThat(jdbc.queryForObject("SELECT is_inspection FROM ims.operations WHERE operation_code = 'INSPECTION'",
                Boolean.class)).isTrue();
        Map<String, String> settings = jdbc.query("SELECT key, value FROM ims.system_settings", rs -> {
            Map<String, String> m = new java.util.HashMap<>();
            while (rs.next()) {
                m.put(rs.getString(1), rs.getString(2));
            }
            return m;
        });
        assertThat(settings).hasSize(16)
                .containsEntry("security.password.min_length", "12")
                .containsEntry("security.lockout.attempts", "5")
                .containsEntry("security.lockout.minutes", "15")
                .containsEntry("inventory.adjustment.admin_threshold", "10000.00")
                .containsEntry("inventory.allow_negative_stock", "false")
                .containsEntry("quality.trace_granularity", "ORDER");
    }

    private Set<String> actualPermissions(String role) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT p.code FROM ims.role_permissions rp JOIN ims.roles r ON r.id = rp.role_id
                JOIN ims.permissions p ON p.id = rp.permission_id WHERE r.code = ?""", String.class, role));
    }
}
