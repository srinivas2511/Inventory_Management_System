package com.mfg.ims.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.jayway.jsonpath.JsonPath;
import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.iam.dto.CreateUserRequest;
import com.mfg.ims.iam.service.UserAdminService;
import com.mfg.ims.support.AbstractIntegrationTest;

/**
 * Role-based access through the real path: a database user holding exactly one seeded role logs in at
 * {@code /api/auth/login}, and the JWT filter resolves the permissions the role was granted by the Flyway seed.
 * Nothing is supplied by hand, so an endpoint guarded by a code no role holds fails here.
 *
 * <p>Write endpoints are probed with a non-existent id and no body: a permitted caller reaches the service (the
 * declared 404 or 400), a forbidden caller is stopped by method security (403). Every probe must also resolve to a
 * real controller method, and a permitted caller must get 2xx unless the probe is declared in
 * {@link #DECLARED_STATUS}, so a mistyped path (404 for everybody) can no longer pass.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleAccessIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Rbac#Test-2026";
    private static final String MISSING = "999999";

    private static final List<String> ROLES = List.of("ADMIN", "ENGINEER", "PRODUCTION_MANAGER", "SUPERVISOR",
            "OPERATOR", "QUALITY_MANAGER", "STORE_MANAGER", "PURCHASE_MANAGER", "SALES", "DISPATCH", "MAINTENANCE",
            "STORE_OPERATOR", "MANAGEMENT", "FINANCE_MANAGER", "ACCOUNTANT", "PLANNER", "CONTROLLER",
            "SALES_MANAGER");

    private static final Set<String> DOCUMENT_READERS = Set.of("ADMIN", "ENGINEER", "PRODUCTION_MANAGER", "SUPERVISOR",
            "QUALITY_MANAGER", "STORE_MANAGER", "PURCHASE_MANAGER", "MAINTENANCE", "MANAGEMENT");
    private static final Set<String> DOCUMENT_NON_READERS = Set.of("OPERATOR", "SALES", "DISPATCH", "STORE_OPERATOR");
    private static final Set<String> DOCUMENT_UPLOADERS = Set.of("ADMIN", "ENGINEER", "PRODUCTION_MANAGER",
            "QUALITY_MANAGER", "PURCHASE_MANAGER", "MAINTENANCE");

    private static final Set<String> SPARE_PART_READERS = Set.of("ADMIN", "MAINTENANCE", "STORE_MANAGER",
            "PRODUCTION_MANAGER", "PURCHASE_MANAGER", "MANAGEMENT");
    private static final Set<String> SPARE_PART_NON_READERS = Set.of("OPERATOR", "SALES", "DISPATCH", "STORE_OPERATOR",
            "QUALITY_MANAGER", "SUPERVISOR", "FINANCE_MANAGER");
    private static final Set<String> SPARE_PART_MANAGERS = Set.of("ADMIN", "MAINTENANCE", "STORE_MANAGER");
    private static final String SPARE_PART_BODY =
            "{\"sparePartCode\":\"X1\",\"name\":\"N\",\"uom\":\"PCS\",\"minStock\":0,\"reorderLevel\":0,"
                    + "\"standardCost\":0}";
    private static final Set<String> MAINTENANCE_COST_READERS = Set.of("ADMIN", "MAINTENANCE", "MANAGEMENT",
            "CONTROLLER", "FINANCE_MANAGER");
    private static final Set<String> OEE_READERS = Set.of("ADMIN", "PRODUCTION_MANAGER", "SUPERVISOR", "MAINTENANCE",
            "MANAGEMENT");
    private static final Set<String> OEE_NON_READERS = Set.of("OPERATOR", "SALES", "STORE_MANAGER", "QUALITY_MANAGER",
            "DISPATCH");

    // analytics and integration (R19)
    private static final Set<String> ANALYTICS_READERS = Set.of("ADMIN", "MANAGEMENT", "CONTROLLER", "FINANCE_MANAGER",
            "PRODUCTION_MANAGER", "PURCHASE_MANAGER");
    private static final Set<String> ANALYTICS_NON_READERS = Set.of("ENGINEER", "SUPERVISOR", "OPERATOR",
            "QUALITY_MANAGER", "STORE_MANAGER", "SALES", "DISPATCH", "MAINTENANCE", "STORE_OPERATOR", "ACCOUNTANT",
            "PLANNER");
    private static final Set<String> ANALYTICS_FINANCE_READERS = Set.of("ADMIN", "MANAGEMENT", "CONTROLLER",
            "FINANCE_MANAGER");
    private static final Set<String> ANALYTICS_FINANCE_NON_READERS = Set.of("PRODUCTION_MANAGER", "PURCHASE_MANAGER",
            "ENGINEER", "SUPERVISOR", "OPERATOR", "SALES", "ACCOUNTANT", "PLANNER");
    private static final Set<String> ADMIN_ONLY = Set.of("ADMIN");
    private static final Set<String> NOT_ADMIN = Set.of("MANAGEMENT", "FINANCE_MANAGER", "CONTROLLER", "PRODUCTION_MANAGER",
            "PURCHASE_MANAGER", "ENGINEER", "SUPERVISOR", "OPERATOR", "SALES", "DISPATCH", "MAINTENANCE",
            "STORE_MANAGER", "STORE_OPERATOR", "ACCOUNTANT", "PLANNER", "QUALITY_MANAGER");
    private static final Set<String> FORECAST_PLANNERS = Set.of("ADMIN", "PLANNER");
    private static final Set<String> FORECAST_NON_PLANNERS = Set.of("MANAGEMENT", "PRODUCTION_MANAGER", "PURCHASE_MANAGER",
            "OPERATOR", "SALES", "FINANCE_MANAGER");
    private static final Set<String> TALLY_EXPORTERS = Set.of("ADMIN", "FINANCE_MANAGER");
    private static final Set<String> TALLY_NON_EXPORTERS = Set.of("MANAGEMENT", "CONTROLLER", "ACCOUNTANT",
            "PRODUCTION_MANAGER", "PURCHASE_MANAGER", "OPERATOR", "SALES");
    // Phase 9: managers who were granted COMMENT_ADD / GLOBAL_SEARCH in V64 (includes STORE_MANAGER)
    private static final Set<String> PHASE9_MGRS_ALLOWED = Set.of("ADMIN", "PURCHASE_MANAGER", "PRODUCTION_MANAGER",
            "FINANCE_MANAGER", "QUALITY_MANAGER", "STORE_MANAGER");
    private static final Set<String> PHASE9_MGRS_FORBIDDEN = Set.of("OPERATOR", "SALES", "DISPATCH", "ENGINEER",
            "SUPERVISOR", "ACCOUNTANT");
    // Phase 9: managers who were granted IMPORT_VIEW / APPROVAL_RULE_VIEW in V65 (STORE_MANAGER excluded)
    private static final Set<String> PHASE9_APPROVAL_ALLOWED = Set.of("ADMIN", "PURCHASE_MANAGER",
            "PRODUCTION_MANAGER", "FINANCE_MANAGER", "QUALITY_MANAGER");
    private static final Set<String> PHASE9_APPROVAL_FORBIDDEN = Set.of("OPERATOR", "SALES", "DISPATCH",
            "ENGINEER", "SUPERVISOR", "ACCOUNTANT", "STORE_MANAGER");
    // Phase 10: RFQ and supplier-quote endpoints — ADMIN + PURCHASE_MANAGER only
    private static final Set<String> PHASE10_PURCHASE_ALLOWED = Set.of("ADMIN", "PURCHASE_MANAGER");
    private static final Set<String> PHASE10_PURCHASE_FORBIDDEN = Set.of("OPERATOR", "SALES", "DISPATCH",
            "ENGINEER", "FINANCE_MANAGER", "PRODUCTION_MANAGER");
    // Phase 11: Quotations, OC, Delivery challans — ADMIN + SALES_MANAGER only
    private static final Set<String> PHASE11_SALES_ALLOWED = Set.of("ADMIN", "SALES_MANAGER");
    private static final Set<String> PHASE11_SALES_FORBIDDEN = Set.of("OPERATOR", "SALES", "DISPATCH",
            "ENGINEER", "FINANCE_MANAGER", "PURCHASE_MANAGER");
    // Phase 12: Sales invoices, payments, open items — ADMIN + FINANCE_MANAGER only
    private static final Set<String> PHASE12_FINANCE_ALLOWED = Set.of("ADMIN", "FINANCE_MANAGER");
    private static final Set<String> PHASE12_FINANCE_FORBIDDEN = Set.of("OPERATOR", "SALES", "DISPATCH",
            "ENGINEER", "PURCHASE_MANAGER", "SALES_MANAGER");
    /** Inactive on purpose: the permitted caller creates a subscription that never receives an event. */
    private static final String WEBHOOK_BODY =
            "{\"name\":\"rbac probe\",\"targetUrl\":\"https://example.com/hook\",\"events\":[\"StockPosted\"],\"active\":false}";

    /** One probed endpoint: who must get in, and who must be stopped with 403. */
    private record Probe(HttpMethod method, String path, String body, Set<String> allowed, Set<String> forbidden) {
        static Probe of(HttpMethod method, String path, Set<String> allowed, Set<String> forbidden) {
            return new Probe(method, path, null, allowed, forbidden);
        }

        /** Endpoints that bind a validated body need one, or argument binding answers 400 before method security. */
        static Probe withBody(HttpMethod method, String path, String body, Set<String> allowed,
                Set<String> forbidden) {
            return new Probe(method, path, body, allowed, forbidden);
        }
    }

    private static final List<Probe> PROBES = List.of(
            // master data
            Probe.of(HttpMethod.GET, "/api/customers", Set.of("ADMIN", "SALES"),
                    Set.of("OPERATOR", "QUALITY_MANAGER", "MANAGEMENT")),
            Probe.of(HttpMethod.GET, "/api/suppliers", Set.of("ADMIN", "PURCHASE_MANAGER"),
                    Set.of("SALES", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/materials",
                    Set.of("ADMIN", "ENGINEER", "STORE_MANAGER", "PURCHASE_MANAGER", "PRODUCTION_MANAGER",
                            "MANAGEMENT"),
                    Set.of("OPERATOR", "SALES", "DISPATCH")),
            Probe.of(HttpMethod.GET, "/api/products",
                    Set.of("ADMIN", "ENGINEER", "SALES", "MANAGEMENT", "PRODUCTION_MANAGER", "QUALITY_MANAGER"),
                    Set.of("OPERATOR", "STORE_OPERATOR", "DISPATCH")),
            Probe.of(HttpMethod.GET, "/api/warehouses",
                    Set.of("ADMIN", "STORE_MANAGER", "STORE_OPERATOR", "MANAGEMENT"), Set.of("OPERATOR")),
            // DESIGN §8.6: the roles whose landing screen is a dashboard can read it; OPERATOR (My Work), DISPATCH
            // (dispatch queue) and MAINTENANCE (machine status) land elsewhere; FINANCE_MANAGER got DASHBOARD_VIEW in V45 (UAT Role 8, FIN-04)
            Probe.of(HttpMethod.GET, "/api/dashboard/kpis",
                    Set.of("ADMIN", "ENGINEER", "PRODUCTION_MANAGER", "SUPERVISOR", "QUALITY_MANAGER", "STORE_MANAGER",
                            "STORE_OPERATOR", "PURCHASE_MANAGER", "SALES", "MANAGEMENT", "CONTROLLER", "PLANNER", "FINANCE_MANAGER"),
                    Set.of("OPERATOR", "DISPATCH", "MAINTENANCE", "ACCOUNTANT")),
            // the dashboard charts are fed by the trends endpoint under the same permission as the KPIs
            Probe.of(HttpMethod.GET, "/api/dashboard/trends",
                    Set.of("ADMIN", "ENGINEER", "PRODUCTION_MANAGER", "SUPERVISOR", "QUALITY_MANAGER", "STORE_MANAGER",
                            "STORE_OPERATOR", "PURCHASE_MANAGER", "SALES", "MANAGEMENT", "CONTROLLER", "PLANNER", "FINANCE_MANAGER"),
                    Set.of("OPERATOR", "DISPATCH", "MAINTENANCE", "ACCOUNTANT")),
            // DESIGN §12 steps 20-21: quality traces an FG batch back and a material batch forward (a missing id is 404)
            Probe.of(HttpMethod.GET, "/api/traceability/fg-batch/" + MISSING,
                    Set.of("ADMIN", "QUALITY_MANAGER", "MANAGEMENT"),
                    Set.of("ENGINEER", "PRODUCTION_MANAGER", "SUPERVISOR", "OPERATOR", "STORE_MANAGER",
                            "PURCHASE_MANAGER", "SALES", "DISPATCH", "MAINTENANCE", "STORE_OPERATOR",
                            "FINANCE_MANAGER", "ACCOUNTANT", "PLANNER", "CONTROLLER")),
            Probe.of(HttpMethod.GET, "/api/traceability/material-batch/" + MISSING,
                    Set.of("ADMIN", "QUALITY_MANAGER", "MANAGEMENT"),
                    Set.of("ENGINEER", "PRODUCTION_MANAGER", "SUPERVISOR", "OPERATOR", "STORE_MANAGER",
                            "PURCHASE_MANAGER", "SALES", "DISPATCH", "MAINTENANCE", "STORE_OPERATOR",
                            "FINANCE_MANAGER", "ACCOUNTANT", "PLANNER", "CONTROLLER")),
            // DESIGN §12 step 8: the store manager opens the production order screen and issues material to the order
            Probe.of(HttpMethod.GET, "/api/production-orders",
                    Set.of("ADMIN", "PRODUCTION_MANAGER", "SUPERVISOR", "STORE_MANAGER", "MANAGEMENT"),
                    Set.of("ENGINEER", "OPERATOR", "QUALITY_MANAGER", "PURCHASE_MANAGER", "SALES", "DISPATCH",
                            "MAINTENANCE", "STORE_OPERATOR", "FINANCE_MANAGER", "ACCOUNTANT", "PLANNER", "CONTROLLER")),
            Probe.withBody(HttpMethod.POST, "/api/production/issues",
                    "{\"productionOrderId\":" + MISSING + ",\"warehouseId\":1,"
                            + "\"lines\":[{\"materialId\":1,\"quantity\":1}]}",
                    Set.of("ADMIN", "STORE_MANAGER"),
                    Set.of("OPERATOR", "PRODUCTION_MANAGER", "SUPERVISOR", "SALES", "QUALITY_MANAGER")),
            // returning issued material to stock needs PRODUCTION_CLOSE; a missing order answers 404 to the permitted
            Probe.withBody(HttpMethod.POST, "/api/production-orders/999999999/material-returns",
                    "{\"materialId\":1,\"quantity\":1}",
                    Set.of("ADMIN", "PRODUCTION_MANAGER"), Set.of("OPERATOR", "SALES", "QUALITY_MANAGER")),
            // QCI-04/05: putting an incoming inspection on hold needs QUALITY_HOLD, re-inspecting needs QUALITY_INSPECT
            Probe.withBody(HttpMethod.POST, "/api/quality/inspections/" + MISSING + "/hold",
                    "{\"reason\":\"probe\"}",
                    Set.of("ADMIN", "QUALITY_MANAGER"), Set.of("OPERATOR", "SALES", "STORE_MANAGER", "DISPATCH")),
            Probe.of(HttpMethod.POST, "/api/quality/inspections/" + MISSING + "/reinspect",
                    Set.of("ADMIN", "QUALITY_MANAGER"), Set.of("OPERATOR", "SALES", "STORE_MANAGER", "DISPATCH")),
            // assigning operators to production work needs PRODUCTION_ASSIGN (managers and supervisors); a missing target is a 404
            Probe.withBody(HttpMethod.PUT, "/api/production/operations/" + MISSING + "/assignment",
                    "{\"operatorId\":null,\"reason\":null}",
                    Set.of("ADMIN", "PRODUCTION_MANAGER", "SUPERVISOR"), Set.of("OPERATOR", "SALES", "QUALITY_MANAGER")),
            Probe.withBody(HttpMethod.PUT, "/api/production-orders/" + MISSING + "/operator-assignment",
                    "{\"operatorId\":null,\"reason\":null}",
                    Set.of("ADMIN", "PRODUCTION_MANAGER", "SUPERVISOR"), Set.of("OPERATOR", "SALES", "QUALITY_MANAGER")),
            Probe.of(HttpMethod.GET, "/api/production/assignable-operators",
                    Set.of("ADMIN", "PRODUCTION_MANAGER", "SUPERVISOR"), Set.of("OPERATOR", "SALES", "QUALITY_MANAGER")),
            // the issue dialog's batch picker needs INVENTORY_ISSUE (store staff), not the production permissions
            Probe.of(HttpMethod.GET, "/api/lookups/material-stock?materialId=" + MISSING + "&warehouseId=" + MISSING,
                    Set.of("ADMIN", "STORE_MANAGER"), Set.of("OPERATOR", "PRODUCTION_MANAGER", "SALES")),
            // dropdown lookups for the create forms: readable with the transactional permission, not the master data one
            Probe.of(HttpMethod.GET, "/api/lookups/suppliers", Set.of("ADMIN", "PURCHASE_MANAGER", "ACCOUNTANT"),
                    Set.of("OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/lookups/customers", Set.of("ADMIN", "SALES", "ACCOUNTANT"), Set.of("OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/lookups/goods-receipts/invoiceable",
                    Set.of("ADMIN", "ACCOUNTANT", "FINANCE_MANAGER"), Set.of("OPERATOR", "SALES", "PURCHASE_MANAGER")),
            Probe.of(HttpMethod.GET, "/api/lookups/materials", Set.of("ADMIN", "PURCHASE_MANAGER", "ENGINEER"),
                    Set.of("OPERATOR")),
            // FINANCE_MANAGER holds TAX_MANAGE and picks the product whose GST slab it sets (R9-7.5)
            Probe.of(HttpMethod.GET, "/api/lookups/products", Set.of("ADMIN", "SALES", "PRODUCTION_MANAGER", "FINANCE_MANAGER"),
                    Set.of("OPERATOR")),
            // R9-7.5 GST tax engine: reading slabs and previewing a determination needs a finance permission
            Probe.of(HttpMethod.GET, "/api/finance/tax/slabs", Set.of("ADMIN", "ACCOUNTANT", "FINANCE_MANAGER"),
                    Set.of("OPERATOR", "DISPATCH")),
            Probe.of(HttpMethod.GET, "/api/finance/tax/determine?direction=SALES&slab=GST18&amount=1000",
                    Set.of("ADMIN", "ACCOUNTANT", "FINANCE_MANAGER"), Set.of("OPERATOR", "DISPATCH")),
            Probe.of(HttpMethod.GET, "/api/finance/tax/product-slabs/" + MISSING, Set.of("ADMIN", "FINANCE_MANAGER"),
                    Set.of("OPERATOR", "DISPATCH")),
            Probe.withBody(HttpMethod.PUT, "/api/finance/tax/product-slabs/" + MISSING, "{}",
                    Set.of("ADMIN", "FINANCE_MANAGER"), Set.of("OPERATOR", "ACCOUNTANT", "SALES")),
            // R9-7.6 exchange rates: readable with the ledger view, changed only by those who maintain them
            Probe.of(HttpMethod.GET, "/api/finance/exchange-rates", Set.of("ADMIN", "ACCOUNTANT", "FINANCE_MANAGER"),
                    Set.of("OPERATOR", "DISPATCH")),
            Probe.withBody(HttpMethod.PUT, "/api/finance/exchange-rates",
                    "{\"fromCurrency\":\"USD\",\"periodId\":" + MISSING + ",\"rate\":80}",
                    Set.of("ADMIN", "FINANCE_MANAGER"), Set.of("OPERATOR", "ACCOUNTANT", "SALES")),
            // R9-7.4 AR credit notes: raised with AR_MANAGE, approved only with CREDIT_NOTE_APPROVE
            Probe.of(HttpMethod.GET, "/api/ar/credit-notes", Set.of("ADMIN", "ACCOUNTANT", "FINANCE_MANAGER"),
                    Set.of("OPERATOR", "DISPATCH", "SALES")),
            Probe.of(HttpMethod.GET, "/api/ar/credit-notes/" + MISSING, Set.of("ADMIN", "ACCOUNTANT", "FINANCE_MANAGER"),
                    Set.of("OPERATOR", "DISPATCH", "SALES")),
            Probe.withBody(HttpMethod.POST, "/api/ar/credit-notes",
                    "{\"arInvoiceId\":" + MISSING + ",\"creditDate\":\"2012-01-15\",\"subtotal\":1,\"reason\":\"probe\"}",
                    Set.of("ADMIN", "ACCOUNTANT", "FINANCE_MANAGER"), Set.of("OPERATOR", "SALES", "DISPATCH")),
            Probe.of(HttpMethod.POST, "/api/ar/credit-notes/" + MISSING + "/approve", Set.of("ADMIN", "FINANCE_MANAGER"),
                    Set.of("OPERATOR", "ACCOUNTANT", "SALES")),
            Probe.of(HttpMethod.POST, "/api/ar/credit-notes/" + MISSING + "/cancel", Set.of("ADMIN", "ACCOUNTANT", "FINANCE_MANAGER"),
                    Set.of("OPERATOR", "SALES", "DISPATCH")),
            // Unrealised FX revaluation: run and list gated behind FX_REVALUATION_RUN (ADMIN + FINANCE_MANAGER only)
            Probe.of(HttpMethod.GET, "/api/finance/fx-revaluation", Set.of("ADMIN", "FINANCE_MANAGER"),
                    Set.of("OPERATOR", "DISPATCH", "SALES")),
            Probe.of(HttpMethod.POST, "/api/finance/fx-revaluation/run?periodId=" + MISSING,
                    Set.of("ADMIN", "FINANCE_MANAGER"), Set.of("OPERATOR", "ACCOUNTANT", "DISPATCH")),
            // R9-7.10 module activation: any signed-in user reads the list (the navigation needs it); only MODULE_MANAGE switches
            Probe.of(HttpMethod.GET, "/api/modules", Set.of("ADMIN", "OPERATOR", "SALES", "DISPATCH"), Set.of()),
            Probe.withBody(HttpMethod.PUT, "/api/modules/CRM", "{\"active\":true}", Set.of("ADMIN"),
                    Set.of("OPERATOR", "SALES", "FINANCE_MANAGER")),
            // R9-7.9 step-up authentication: any signed-in user can ask for the status or re-authenticate
            Probe.of(HttpMethod.GET, "/api/auth/step-up", Set.of("ADMIN", "OPERATOR", "SALES", "FINANCE_MANAGER"), Set.of()),
            Probe.withBody(HttpMethod.POST, "/api/auth/step-up", "{\"credential\":\"not-the-password\"}",
                    Set.of("ADMIN", "OPERATOR", "SALES", "FINANCE_MANAGER"), Set.of()),
            // R9-7.1 / R9-7.2 plants: PLANT_MANAGE maintains plants, plant settings and user assignments
            Probe.of(HttpMethod.GET, "/api/plants", Set.of("ADMIN"), Set.of("OPERATOR", "SALES", "FINANCE_MANAGER")),
            Probe.withBody(HttpMethod.POST, "/api/plants", "{\"plantCode\":\"P001\",\"name\":\"Probe\"}", Set.of("ADMIN"),
                    Set.of("OPERATOR", "SALES", "FINANCE_MANAGER")),
            Probe.withBody(HttpMethod.PUT, "/api/plants/" + MISSING, "{\"plantCode\":\"X1\",\"name\":\"X\"}",
                    Set.of("ADMIN"), Set.of("OPERATOR", "SALES", "FINANCE_MANAGER")),
            Probe.of(HttpMethod.GET, "/api/plants/" + MISSING + "/settings", Set.of("ADMIN"),
                    Set.of("OPERATOR", "SALES", "FINANCE_MANAGER")),
            Probe.of(HttpMethod.GET, "/api/plants/company-settings", Set.of("ADMIN"),
                    Set.of("OPERATOR", "SALES", "FINANCE_MANAGER")),
            Probe.withBody(HttpMethod.PUT, "/api/plants/settings", "{\"key\":\"bogus\",\"value\":\"x\"}",
                    Set.of("ADMIN"), Set.of("OPERATOR", "SALES", "FINANCE_MANAGER")),
            Probe.of(HttpMethod.DELETE, "/api/plants/settings?key=bogus", Set.of("ADMIN"),
                    Set.of("OPERATOR", "SALES", "FINANCE_MANAGER")),
            Probe.of(HttpMethod.GET, "/api/plants/users/" + MISSING, Set.of("ADMIN"),
                    Set.of("OPERATOR", "SALES", "FINANCE_MANAGER")),
            Probe.withBody(HttpMethod.PUT, "/api/plants/users/" + MISSING, "{\"plantIds\":[]}", Set.of("ADMIN"),
                    Set.of("OPERATOR", "SALES", "FINANCE_MANAGER")),
            Probe.of(HttpMethod.GET, "/api/lookups/plants", Set.of("ADMIN", "SALES", "PURCHASE_MANAGER"),
                    Set.of("OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/lookups/warehouses", Set.of("ADMIN", "STORE_MANAGER"), Set.of("OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/lookups/locations", Set.of("ADMIN", "STORE_MANAGER"), Set.of("OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/lookups/products/" + MISSING + "/active-bom",
                    Set.of("ADMIN", "PRODUCTION_MANAGER", "ENGINEER"), Set.of("OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/lookups/purchase-orders/receivable", Set.of("ADMIN"), Set.of("OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/lookups/fg-stock", Set.of("ADMIN", "SALES", "DISPATCH"), Set.of("OPERATOR")),
            // D-33: the pick wave planner reads approved orders through a lookup, not the sales list it may not read
            Probe.of(HttpMethod.GET, "/api/lookups/sales-orders/approved", Set.of("ADMIN", "STORE_MANAGER"),
                    Set.of("OPERATOR", "SALES", "STORE_OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/lookups/sales-orders/dispatchable", Set.of("ADMIN", "DISPATCH"),
                    Set.of("OPERATOR")),
            Probe.of(HttpMethod.DELETE, "/api/materials/" + MISSING, Set.of("ADMIN"),
                    Set.of("ENGINEER", "STORE_MANAGER")),
            Probe.of(HttpMethod.DELETE, "/api/products/" + MISSING, Set.of("ADMIN", "ENGINEER"),
                    Set.of("SALES", "MANAGEMENT")),
            // product types and attribute definitions as data (R20)
            Probe.of(HttpMethod.GET, "/api/product-types",
                    Set.of("ADMIN", "ENGINEER", "SALES", "MANAGEMENT", "PRODUCTION_MANAGER", "QUALITY_MANAGER"),
                    Set.of("OPERATOR", "STORE_OPERATOR", "DISPATCH")),
            Probe.of(HttpMethod.GET, "/api/admin/attribute-definitions", Set.of("ADMIN"),
                    Set.of("ENGINEER", "SALES", "MANAGEMENT", "PRODUCTION_MANAGER", "OPERATOR")),
            Probe.withBody(HttpMethod.POST, "/api/admin/attribute-definitions",
                    "{\"productType\":\"NO_SUCH_TYPE\",\"attributeCode\":\"probe_attr\",\"label\":\"Probe\","
                            + "\"dataType\":\"TEXT\",\"required\":false,\"displayOrder\":1}",
                    Set.of("ADMIN"), Set.of("ENGINEER", "SALES", "MANAGEMENT", "OPERATOR")),
            Probe.withBody(HttpMethod.PUT, "/api/admin/attribute-definitions/" + MISSING,
                    "{\"productType\":\"COMPRESSION\",\"attributeCode\":\"probe_attr\",\"label\":\"Probe\","
                            + "\"dataType\":\"TEXT\",\"required\":false,\"displayOrder\":1}",
                    Set.of("ADMIN"), Set.of("ENGINEER", "SALES", "MANAGEMENT", "OPERATOR")),
            Probe.of(HttpMethod.POST, "/api/admin/attribute-definitions/" + MISSING + "/deactivate",
                    Set.of("ADMIN"), Set.of("ENGINEER", "SALES", "MANAGEMENT", "OPERATOR")),
            Probe.of(HttpMethod.POST, "/api/admin/attribute-definitions/" + MISSING + "/activate",
                    Set.of("ADMIN"), Set.of("ENGINEER", "SALES", "MANAGEMENT", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/machines",
                    Set.of("ADMIN", "PRODUCTION_MANAGER", "SUPERVISOR", "MAINTENANCE"), Set.of("OPERATOR", "SALES")),
            Probe.of(HttpMethod.GET, "/api/operations", Set.of("ADMIN", "ENGINEER", "PRODUCTION_MANAGER"),
                    Set.of("OPERATOR", "SALES")),
            Probe.of(HttpMethod.DELETE, "/api/rejection-reasons/" + MISSING, Set.of("ADMIN", "QUALITY_MANAGER"),
                    Set.of("OPERATOR", "STORE_MANAGER")),
            // identity, settings, audit
            Probe.of(HttpMethod.GET, "/api/admin/users", Set.of("ADMIN", "MANAGEMENT"),
                    Set.of("ENGINEER", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/admin/roles", Set.of("ADMIN"), Set.of("MANAGEMENT", "OPERATOR")),
            // another user's password: a permission of its own, held by ADMIN only; unlock needs only USER_UPDATE
            Probe.withBody(HttpMethod.POST, "/api/admin/users/" + MISSING + "/reset-password",
                    "{\"reason\":\"probe\"}", Set.of("ADMIN"), Set.of("MANAGEMENT", "OPERATOR", "FINANCE_MANAGER")),
            Probe.of(HttpMethod.POST, "/api/admin/users/" + MISSING + "/unlock", Set.of("ADMIN"),
                    Set.of("MANAGEMENT", "OPERATOR", "FINANCE_MANAGER")),
            Probe.of(HttpMethod.GET, "/api/admin/settings", Set.of("ADMIN"), Set.of("MANAGEMENT")),
            Probe.of(HttpMethod.GET, "/api/settings", Set.of("ADMIN"), Set.of("MANAGEMENT")),
            Probe.of(HttpMethod.GET, "/api/audit-logs", Set.of("ADMIN", "MANAGEMENT"), Set.of("OPERATOR")),
            // alerts
            Probe.of(HttpMethod.GET, "/api/alerts",
                    Set.of("ADMIN", "PRODUCTION_MANAGER", "QUALITY_MANAGER", "STORE_MANAGER", "PURCHASE_MANAGER",
                            "MANAGEMENT", "FINANCE_MANAGER", "ACCOUNTANT"),
                    Set.of("OPERATOR", "SALES")),
            Probe.of(HttpMethod.PUT, "/api/alerts/" + MISSING + "/acknowledge",
                    Set.of("ADMIN", "STORE_MANAGER", "FINANCE_MANAGER"),
                    Set.of("MANAGEMENT", "OPERATOR", "ACCOUNTANT")),
            // AR reconciliation: any AR user may see the dispatches without an invoice, only finance managers retry
            Probe.of(HttpMethod.GET, "/api/ar/reconciliation/missing-invoices",
                    Set.of("ADMIN", "FINANCE_MANAGER", "ACCOUNTANT"), Set.of("OPERATOR", "SALES", "MANAGEMENT")),
            Probe.of(HttpMethod.POST, "/api/ar/reconciliation/missing-invoices/retry",
                    Set.of("ADMIN", "FINANCE_MANAGER"), Set.of("ACCOUNTANT", "OPERATOR", "SALES")),
            // AP three-way match: only AP_MATCH_OVERRIDE holders approve a variance, AP users may cancel a draft
            Probe.withBody(HttpMethod.POST, "/api/ap/invoices/" + MISSING + "/match-override", "{\"reason\":\"probe\"}",
                    Set.of("ADMIN", "FINANCE_MANAGER"), Set.of("ACCOUNTANT", "OPERATOR", "SALES", "MANAGEMENT")),
            Probe.of(HttpMethod.PUT, "/api/ap/invoices/" + MISSING + "/cancel",
                    Set.of("ADMIN", "FINANCE_MANAGER", "ACCOUNTANT"), Set.of("OPERATOR", "SALES", "MANAGEMENT")),
            // R9-7 screens: settlement history with the realised exchange difference, budget against actual, period reasons
            Probe.of(HttpMethod.GET, "/api/ap/invoices/" + MISSING + "/payments",
                    Set.of("ADMIN", "FINANCE_MANAGER", "ACCOUNTANT"), Set.of("OPERATOR", "SALES", "MANAGEMENT")),
            Probe.of(HttpMethod.GET, "/api/ar/invoices/" + MISSING + "/receipts",
                    Set.of("ADMIN", "FINANCE_MANAGER", "ACCOUNTANT"), Set.of("OPERATOR", "SALES", "DISPATCH")),
            Probe.of(HttpMethod.GET, "/api/config/budgets/vs-actual?fiscalYear=2026",
                    Set.of("ADMIN", "FINANCE_MANAGER", "ACCOUNTANT"), Set.of("OPERATOR", "SALES")),
            Probe.withBody(HttpMethod.POST, "/api/periods/" + MISSING + "/finalize-close", "{\"reason\":\"probe\"}",
                    Set.of("ADMIN", "FINANCE_MANAGER"), Set.of("OPERATOR", "SALES", "ACCOUNTANT")),
            Probe.withBody(HttpMethod.POST, "/api/periods/" + MISSING + "/reopen", "{\"reason\":\"probe\"}",
                    Set.of("ADMIN", "FINANCE_MANAGER"), Set.of("OPERATOR", "SALES", "ACCOUNTANT")),
            // goods receipt
            Probe.of(HttpMethod.GET, "/api/procurement/goods-receipts",
                    Set.of("ADMIN", "STORE_OPERATOR", "PURCHASE_MANAGER"), Set.of("OPERATOR", "SALES")),
            Probe.of(HttpMethod.GET, "/api/procurement/goods-receipts/" + MISSING,
                    Set.of("ADMIN", "STORE_OPERATOR", "PURCHASE_MANAGER"), Set.of("OPERATOR", "SALES")),
            // sales and dispatch
            Probe.of(HttpMethod.GET, "/api/sales/orders", Set.of("ADMIN", "SALES", "DISPATCH", "MANAGEMENT"),
                    Set.of("OPERATOR")),
            Probe.of(HttpMethod.POST, "/api/sales/orders/" + MISSING + "/approve", Set.of("ADMIN"),
                    Set.of("SALES", "DISPATCH")),
            Probe.withBody(HttpMethod.POST, "/api/sales/orders/" + MISSING + "/cancel", "{\"reason\":\"probe\"}",
                    Set.of("ADMIN", "SALES"), Set.of("DISPATCH", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/sales/dispatches", Set.of("ADMIN", "DISPATCH", "SALES", "MANAGEMENT"),
                    Set.of("OPERATOR")),
            // GC-4: printable dispatch documents (PDF delivery note and packing list)
            Probe.of(HttpMethod.GET, "/api/sales/dispatches/" + MISSING + "/delivery-note.pdf",
                    Set.of("ADMIN", "DISPATCH"), Set.of("OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/sales/dispatches/" + MISSING + "/packing-list.pdf",
                    Set.of("ADMIN", "DISPATCH"), Set.of("OPERATOR")),
            // GC-3: per-item negative-stock override (administrator only)
            Probe.of(HttpMethod.GET, "/api/inventory/negative-stock-overrides", Set.of("ADMIN"),
                    Set.of("STORE_MANAGER", "MANAGEMENT")),
            Probe.withBody(HttpMethod.PUT, "/api/inventory/negative-stock-overrides/materials/" + MISSING,
                    "{\"allowed\":true,\"reason\":\"probe\"}", Set.of("ADMIN"), Set.of("STORE_MANAGER", "ENGINEER")),
            Probe.withBody(HttpMethod.PUT, "/api/inventory/negative-stock-overrides/products/" + MISSING,
                    "{\"allowed\":true,\"reason\":\"probe\"}", Set.of("ADMIN"), Set.of("STORE_MANAGER", "ENGINEER")),
            // GC-1: alternative units of measure, and balances shown in one
            Probe.of(HttpMethod.GET, "/api/materials/" + MISSING + "/uom-conversions",
                    Set.of("ADMIN", "ENGINEER", "STORE_MANAGER", "PURCHASE_MANAGER"), Set.of("OPERATOR", "SALES")),
            Probe.withBody(HttpMethod.PUT, "/api/materials/" + MISSING + "/uom-conversions",
                    "{\"altUom\":\"PCS\",\"factorToBase\":1}", Set.of("ADMIN"), Set.of("ENGINEER", "STORE_MANAGER")),
            Probe.of(HttpMethod.DELETE, "/api/materials/" + MISSING + "/uom-conversions/PCS", Set.of("ADMIN"),
                    Set.of("ENGINEER", "STORE_MANAGER")),
            Probe.of(HttpMethod.GET, "/api/inventory/balances/in-uom?materialId=" + MISSING + "&uom=PCS",
                    Set.of("ADMIN", "STORE_MANAGER"), Set.of("OPERATOR")),
            // GC-2: purchasing info, supplier approved materials, customer approved products
            Probe.of(HttpMethod.GET, "/api/materials/" + MISSING + "/purchasing-info",
                    Set.of("ADMIN", "ENGINEER", "STORE_MANAGER", "PURCHASE_MANAGER"), Set.of("OPERATOR", "SALES")),
            Probe.withBody(HttpMethod.PUT, "/api/materials/" + MISSING + "/purchasing-info", "{}", Set.of("ADMIN"),
                    Set.of("ENGINEER", "STORE_MANAGER")),
            Probe.of(HttpMethod.GET, "/api/suppliers/" + MISSING + "/approved-materials",
                    Set.of("ADMIN", "PURCHASE_MANAGER"), Set.of("SALES", "OPERATOR")),
            Probe.withBody(HttpMethod.PUT, "/api/suppliers/" + MISSING + "/approved-materials", "{\"ids\":[]}",
                    Set.of("ADMIN", "PURCHASE_MANAGER"), Set.of("SALES", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/customers/" + MISSING + "/approved-products", Set.of("ADMIN", "SALES"),
                    Set.of("OPERATOR", "QUALITY_MANAGER")),
            Probe.withBody(HttpMethod.PUT, "/api/customers/" + MISSING + "/approved-products", "{\"ids\":[]}",
                    Set.of("ADMIN", "SALES"), Set.of("OPERATOR", "QUALITY_MANAGER")),
            // GC-5: customer tolerance overrides
            Probe.of(HttpMethod.GET, "/api/customers/" + MISSING + "/product-specs/" + MISSING + "/overrides",
                    Set.of("ADMIN", "SALES"), Set.of("OPERATOR")),
            Probe.withBody(HttpMethod.PUT, "/api/customers/" + MISSING + "/product-specs/" + MISSING + "/overrides",
                    "{\"parameterName\":\"P\",\"lowerLimit\":1,\"upperLimit\":2}", Set.of("ADMIN", "SALES"),
                    Set.of("OPERATOR", "DISPATCH")),
            Probe.of(HttpMethod.DELETE,
                    "/api/customers/" + MISSING + "/product-specs/" + MISSING + "/overrides?parameterName=P",
                    Set.of("ADMIN", "SALES"), Set.of("OPERATOR")),
            // maintenance
            Probe.of(HttpMethod.GET, "/api/maintenance/requests", Set.of("ADMIN", "MAINTENANCE", "MANAGEMENT"),
                    Set.of("OPERATOR")),
            Probe.of(HttpMethod.POST, "/api/maintenance/pm-schedules/" + MISSING + "/generate-request",
                    Set.of("ADMIN", "MAINTENANCE"), Set.of("MANAGEMENT", "PURCHASE_MANAGER")),
            // advanced quality (R16): SPC, CAPA, complaints, first article inspection, complaint report
            Probe.of(HttpMethod.GET, "/api/spc/charts", Set.of("ADMIN", "QUALITY_MANAGER", "MANAGEMENT"),
                    Set.of("OPERATOR", "SALES", "ENGINEER")),
            Probe.of(HttpMethod.GET, "/api/spc/charts/" + MISSING, Set.of("ADMIN", "QUALITY_MANAGER", "MANAGEMENT"),
                    Set.of("OPERATOR", "SALES")),
            Probe.withBody(HttpMethod.POST, "/api/spc/charts",
                    "{\"productId\":" + MISSING + ",\"parameterName\":\"probe\",\"chartType\":\"XBAR_R\"}",
                    Set.of("ADMIN", "QUALITY_MANAGER"), Set.of("MANAGEMENT", "OPERATOR", "ENGINEER")),
            Probe.withBody(HttpMethod.POST, "/api/spc/charts/" + MISSING + "/points", "{\"values\":[1]}",
                    Set.of("ADMIN", "QUALITY_MANAGER"), Set.of("MANAGEMENT", "OPERATOR")),
            Probe.of(HttpMethod.POST, "/api/spc/charts/" + MISSING + "/recalculate-limits",
                    Set.of("ADMIN", "QUALITY_MANAGER"), Set.of("MANAGEMENT", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/capa", Set.of("ADMIN", "QUALITY_MANAGER", "MANAGEMENT"),
                    Set.of("OPERATOR", "SALES")),
            Probe.withBody(HttpMethod.POST, "/api/capa/" + MISSING + "/close", "{\"verificationNotes\":\"probe\"}",
                    Set.of("ADMIN", "QUALITY_MANAGER"), Set.of("MANAGEMENT", "OPERATOR")),
            Probe.of(HttpMethod.POST, "/api/capa/from-rejection/" + MISSING, Set.of("ADMIN", "QUALITY_MANAGER"),
                    Set.of("MANAGEMENT", "SALES")),
            Probe.of(HttpMethod.GET, "/api/complaints", Set.of("ADMIN", "QUALITY_MANAGER", "MANAGEMENT"),
                    Set.of("SALES", "DISPATCH", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/complaints/lookups/customers", Set.of("ADMIN", "QUALITY_MANAGER"),
                    Set.of("SALES", "MANAGEMENT", "OPERATOR")),
            Probe.withBody(HttpMethod.POST, "/api/complaints",
                    "{\"customerId\":" + MISSING + ",\"complaintDate\":\"2026-10-04\",\"category\":\"OTHER\","
                            + "\"description\":\"probe\"}",
                    Set.of("ADMIN", "QUALITY_MANAGER"), Set.of("SALES", "MANAGEMENT")),
            Probe.of(HttpMethod.GET, "/api/fai", Set.of("ADMIN", "QUALITY_MANAGER", "MANAGEMENT"),
                    Set.of("OPERATOR", "SALES")),
            Probe.withBody(HttpMethod.POST, "/api/fai", "{\"productId\":" + MISSING + "}",
                    Set.of("ADMIN", "QUALITY_MANAGER"), Set.of("MANAGEMENT", "OPERATOR")),
            Probe.withBody(HttpMethod.POST, "/api/fai/" + MISSING + "/results",
                    "{\"values\":[{\"itemId\":1,\"actualValue\":1.5}]}", Set.of("ADMIN", "QUALITY_MANAGER"),
                    Set.of("MANAGEMENT", "OPERATOR")),
            Probe.of(HttpMethod.POST, "/api/fai/" + MISSING + "/complete", Set.of("ADMIN", "QUALITY_MANAGER"),
                    Set.of("MANAGEMENT", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/reports/QUAL_COMPLAINTS",
                    Set.of("ADMIN", "QUALITY_MANAGER", "MANAGEMENT"), Set.of("OPERATOR", "SALES")),

            // documents (R17): reading is DOCUMENT_VIEW, uploading/linking/submitting DOCUMENT_UPLOAD, approval
            // DOCUMENT_APPROVE, categories DOCUMENT_MANAGE. Multipart uploads are probed in DocumentIT.
            Probe.of(HttpMethod.GET, "/api/documents", DOCUMENT_READERS, DOCUMENT_NON_READERS),
            Probe.of(HttpMethod.GET, "/api/documents/" + MISSING, DOCUMENT_READERS, DOCUMENT_NON_READERS),
            Probe.of(HttpMethod.GET, "/api/documents/" + MISSING + "/download", DOCUMENT_READERS,
                    DOCUMENT_NON_READERS),
            Probe.of(HttpMethod.GET, "/api/document-categories", DOCUMENT_READERS, DOCUMENT_NON_READERS),
            Probe.withBody(HttpMethod.POST, "/api/documents/" + MISSING + "/links",
                    "{\"entityType\":\"PRODUCT\",\"entityId\":1}", DOCUMENT_UPLOADERS,
                    Set.of("SUPERVISOR", "STORE_MANAGER", "MANAGEMENT", "OPERATOR", "SALES")),
            Probe.of(HttpMethod.DELETE, "/api/documents/" + MISSING + "/links/" + MISSING, DOCUMENT_UPLOADERS,
                    Set.of("SUPERVISOR", "STORE_MANAGER", "MANAGEMENT", "OPERATOR")),
            Probe.of(HttpMethod.POST, "/api/documents/" + MISSING + "/submit", DOCUMENT_UPLOADERS,
                    Set.of("SUPERVISOR", "STORE_MANAGER", "MANAGEMENT", "OPERATOR")),
            Probe.of(HttpMethod.POST, "/api/documents/" + MISSING + "/approve",
                    Set.of("ADMIN", "PRODUCTION_MANAGER", "QUALITY_MANAGER"),
                    Set.of("ENGINEER", "MAINTENANCE", "PURCHASE_MANAGER", "MANAGEMENT", "OPERATOR")),
            Probe.withBody(HttpMethod.POST, "/api/documents/" + MISSING + "/reject", "{\"reason\":\"probe\"}",
                    Set.of("ADMIN", "PRODUCTION_MANAGER", "QUALITY_MANAGER"),
                    Set.of("ENGINEER", "MAINTENANCE", "MANAGEMENT", "OPERATOR")),
            Probe.withBody(HttpMethod.POST, "/api/documents/" + MISSING + "/obsolete", "{\"reason\":\"probe\"}",
                    Set.of("ADMIN", "PRODUCTION_MANAGER", "QUALITY_MANAGER"),
                    Set.of("ENGINEER", "MAINTENANCE", "MANAGEMENT", "OPERATOR")),
            Probe.withBody(HttpMethod.PUT, "/api/document-categories/" + MISSING,
                    "{\"categoryCode\":\"PROBE\",\"name\":\"Probe\"}", Set.of("ADMIN"),
                    Set.of("QUALITY_MANAGER", "ENGINEER", "PRODUCTION_MANAGER", "MANAGEMENT")),

            // notifications (R17): a user's own inbox and preferences need only a signed-in user; templates and the
            // delivery log are NOTIFICATION_MANAGE (administrators)
            Probe.of(HttpMethod.GET, "/api/notifications", Set.copyOf(ROLES), Set.of()),
            Probe.of(HttpMethod.GET, "/api/notifications/unread-count", Set.copyOf(ROLES), Set.of()),
            Probe.of(HttpMethod.GET, "/api/notification-preferences", Set.copyOf(ROLES), Set.of()),
            Probe.of(HttpMethod.POST, "/api/notifications/read-all", Set.copyOf(ROLES), Set.of()),
            Probe.of(HttpMethod.POST, "/api/notifications/" + MISSING + "/read", Set.copyOf(ROLES), Set.of()),
            Probe.of(HttpMethod.GET, "/api/notification-templates", Set.of("ADMIN"),
                    Set.of("QUALITY_MANAGER", "MANAGEMENT", "ENGINEER", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/notification-log", Set.of("ADMIN"),
                    Set.of("QUALITY_MANAGER", "MANAGEMENT", "ENGINEER", "OPERATOR")),
            Probe.withBody(HttpMethod.PUT, "/api/notification-templates/" + MISSING,
                    "{\"channel\":\"BOTH\",\"bodyTemplate\":\"x\"}", Set.of("ADMIN"),
                    Set.of("QUALITY_MANAGER", "MANAGEMENT", "ENGINEER")),
            // advanced manufacturing (R18): spare parts, maintenance cost, OEE, scheduling
            Probe.of(HttpMethod.GET, "/api/spare-parts", SPARE_PART_READERS, SPARE_PART_NON_READERS),
            Probe.of(HttpMethod.GET, "/api/spare-parts/" + MISSING, SPARE_PART_READERS, SPARE_PART_NON_READERS),
            Probe.withBody(HttpMethod.PUT, "/api/spare-parts/" + MISSING, SPARE_PART_BODY, SPARE_PART_MANAGERS,
                    Set.of("PRODUCTION_MANAGER", "PURCHASE_MANAGER", "MANAGEMENT", "OPERATOR")),
            Probe.of(HttpMethod.DELETE, "/api/spare-parts/" + MISSING, SPARE_PART_MANAGERS,
                    Set.of("PRODUCTION_MANAGER", "PURCHASE_MANAGER", "MANAGEMENT")),
            Probe.of(HttpMethod.GET, "/api/maintenance/requests/" + MISSING + "/spare-parts",
                    Set.of("ADMIN", "MAINTENANCE", "MANAGEMENT", "STORE_MANAGER", "PURCHASE_MANAGER",
                            "PRODUCTION_MANAGER"),
                    Set.of("OPERATOR", "SALES", "DISPATCH")),
            Probe.withBody(HttpMethod.POST, "/api/maintenance/requests/" + MISSING + "/spare-parts",
                    "{\"sparePartId\":1,\"warehouseId\":1,\"quantity\":1}", SPARE_PART_MANAGERS,
                    Set.of("PRODUCTION_MANAGER", "SUPERVISOR", "MANAGEMENT", "PURCHASE_MANAGER")),
            Probe.of(HttpMethod.GET, "/api/maintenance/requests/" + MISSING + "/activities",
                    Set.of("ADMIN", "MAINTENANCE", "MANAGEMENT"), Set.of("OPERATOR", "SALES")),
            Probe.withBody(HttpMethod.POST, "/api/maintenance/requests/" + MISSING + "/activities",
                    "{\"description\":\"x\",\"durationMinutes\":10}", Set.of("ADMIN", "MAINTENANCE"),
                    Set.of("MANAGEMENT", "OPERATOR", "STORE_MANAGER")),
            Probe.of(HttpMethod.GET, "/api/maintenance/requests/" + MISSING + "/cost", MAINTENANCE_COST_READERS,
                    Set.of("STORE_MANAGER", "PRODUCTION_MANAGER", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/maintenance/costs/unposted", MAINTENANCE_COST_READERS,
                    Set.of("STORE_MANAGER", "PRODUCTION_MANAGER", "OPERATOR", "SALES")),
            Probe.of(HttpMethod.POST, "/api/maintenance/requests/" + MISSING + "/post-cost",
                    Set.of("ADMIN", "FINANCE_MANAGER"), Set.of("MAINTENANCE", "MANAGEMENT", "CONTROLLER")),
            Probe.of(HttpMethod.POST, "/api/maintenance/costs/repost-unposted", Set.of("ADMIN", "FINANCE_MANAGER"),
                    Set.of("MAINTENANCE", "MANAGEMENT", "CONTROLLER")),
            Probe.of(HttpMethod.GET, "/api/oee/summary", OEE_READERS, OEE_NON_READERS),
            Probe.of(HttpMethod.GET, "/api/oee/machines", OEE_READERS, OEE_NON_READERS),
            Probe.of(HttpMethod.GET, "/api/oee/machines/" + MISSING, OEE_READERS, OEE_NON_READERS),
            Probe.of(HttpMethod.GET, "/api/oee/trend", OEE_READERS, OEE_NON_READERS),
            Probe.of(HttpMethod.GET, "/api/oee/losses", OEE_READERS, OEE_NON_READERS),
            Probe.of(HttpMethod.GET, "/api/oee/downtime-reasons", OEE_READERS, OEE_NON_READERS),
            Probe.of(HttpMethod.GET, "/api/scheduling/gantt",
                    Set.of("ADMIN", "PRODUCTION_MANAGER", "SUPERVISOR", "PLANNER", "MANAGEMENT"),
                    Set.of("OPERATOR", "MAINTENANCE", "SALES", "STORE_MANAGER")),
            Probe.of(HttpMethod.POST, "/api/scheduling/run?dryRun=true",
                    Set.of("ADMIN", "PRODUCTION_MANAGER", "PLANNER"),
                    Set.of("SUPERVISOR", "MANAGEMENT", "OPERATOR", "MAINTENANCE")),
            Probe.withBody(HttpMethod.PUT, "/api/scheduling/operations/" + MISSING + "/schedule",
                    "{\"machineId\":1,\"start\":\"2035-01-01T09:00:00Z\"}",
                    Set.of("ADMIN", "PRODUCTION_MANAGER", "PLANNER"),
                    Set.of("SUPERVISOR", "MANAGEMENT", "OPERATOR")),
            Probe.of(HttpMethod.DELETE, "/api/scheduling/operations/" + MISSING + "/schedule",
                    Set.of("ADMIN", "PRODUCTION_MANAGER", "PLANNER"),
                    Set.of("SUPERVISOR", "MANAGEMENT", "OPERATOR")),
            // analytics: the query endpoint, with finance queries needing the second permission
            Probe.of(HttpMethod.GET, "/api/analytics/catalogue", ANALYTICS_READERS, ANALYTICS_NON_READERS),
            Probe.of(HttpMethod.GET, "/api/analytics/query?key=PRODUCTION_MONTHLY", ANALYTICS_READERS, ANALYTICS_NON_READERS),
            Probe.of(HttpMethod.GET, "/api/analytics/query?key=SUPPLIER_SCORECARD", ANALYTICS_READERS, ANALYTICS_NON_READERS),
            Probe.of(HttpMethod.GET, "/api/analytics/query?key=FIN_SUMMARY_MONTHLY", ANALYTICS_FINANCE_READERS,
                    ANALYTICS_FINANCE_NON_READERS),
            Probe.of(HttpMethod.GET, "/api/analytics/query?key=COST_VARIANCE", ANALYTICS_FINANCE_READERS,
                    ANALYTICS_FINANCE_NON_READERS),
            Probe.of(HttpMethod.POST, "/api/analytics/refresh", ADMIN_ONLY, NOT_ADMIN),
            Probe.of(HttpMethod.POST, "/api/analytics/anomalies/run", ADMIN_ONLY, NOT_ADMIN),
            Probe.of(HttpMethod.GET, "/api/analytics/forecast/provider", FORECAST_PLANNERS, FORECAST_NON_PLANNERS),
            Probe.of(HttpMethod.POST, "/api/analytics/forecast/generate", FORECAST_PLANNERS, FORECAST_NON_PLANNERS),
            // webhooks
            Probe.of(HttpMethod.GET, "/api/integration/webhooks", ADMIN_ONLY, NOT_ADMIN),
            Probe.of(HttpMethod.GET, "/api/integration/webhooks/event-types", ADMIN_ONLY, NOT_ADMIN),
            Probe.of(HttpMethod.GET, "/api/integration/webhooks/" + MISSING, ADMIN_ONLY, NOT_ADMIN),
            Probe.of(HttpMethod.GET, "/api/integration/webhook-deliveries", ADMIN_ONLY, NOT_ADMIN),
            Probe.withBody(HttpMethod.POST, "/api/integration/webhooks", WEBHOOK_BODY, ADMIN_ONLY, NOT_ADMIN),
            Probe.withBody(HttpMethod.PUT, "/api/integration/webhooks/" + MISSING, WEBHOOK_BODY, ADMIN_ONLY, NOT_ADMIN),
            Probe.of(HttpMethod.POST, "/api/integration/webhooks/" + MISSING + "/rotate-secret", ADMIN_ONLY, NOT_ADMIN),
            Probe.of(HttpMethod.POST, "/api/integration/webhooks/" + MISSING + "/test", ADMIN_ONLY, NOT_ADMIN),
            Probe.of(HttpMethod.POST, "/api/integration/webhook-deliveries/" + MISSING + "/retry", ADMIN_ONLY, NOT_ADMIN),
            // bulk import (the upload itself is covered by ImportIT: argument binding precedes method security there)
            Probe.of(HttpMethod.GET, "/api/integration/import", ADMIN_ONLY, NOT_ADMIN),
            Probe.of(HttpMethod.GET, "/api/integration/import/customers/template", ADMIN_ONLY, NOT_ADMIN),
            // export and Tally
            Probe.of(HttpMethod.GET, "/api/integration/export", ADMIN_ONLY, NOT_ADMIN),
            Probe.of(HttpMethod.GET, "/api/integration/export/customers", ADMIN_ONLY, NOT_ADMIN),
            Probe.of(HttpMethod.GET, "/api/integration/tally/ledgers", TALLY_EXPORTERS, TALLY_NON_EXPORTERS),
            Probe.of(HttpMethod.GET, "/api/integration/tally/vouchers", TALLY_EXPORTERS, TALLY_NON_EXPORTERS),

            // Phase 9: admin master-data settings (each guarded by its own permission + SETTINGS_MANAGE — ADMIN only)
            Probe.of(HttpMethod.GET, "/api/admin/item-categories", ADMIN_ONLY,
                    Set.of("ENGINEER", "PURCHASE_MANAGER", "PRODUCTION_MANAGER", "MANAGEMENT")),
            Probe.of(HttpMethod.GET, "/api/admin/payment-terms", ADMIN_ONLY,
                    Set.of("ENGINEER", "FINANCE_MANAGER", "PRODUCTION_MANAGER", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/admin/transporters", ADMIN_ONLY,
                    Set.of("ENGINEER", "PURCHASE_MANAGER", "DISPATCH", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/admin/document-series", ADMIN_ONLY,
                    Set.of("ENGINEER", "FINANCE_MANAGER", "PRODUCTION_MANAGER", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/admin/teams", ADMIN_ONLY,
                    Set.of("ENGINEER", "FINANCE_MANAGER", "PRODUCTION_MANAGER", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/admin/custom-fields", ADMIN_ONLY,
                    Set.of("ENGINEER", "FINANCE_MANAGER", "PRODUCTION_MANAGER", "OPERATOR")),
            Probe.of(HttpMethod.GET, "/api/admin/company-profile", ADMIN_ONLY,
                    Set.of("ENGINEER", "FINANCE_MANAGER", "PRODUCTION_MANAGER", "OPERATOR")),
            // approval rules: list visible to managers who have APPROVAL_RULE_VIEW (V65) — STORE_MANAGER excluded
            Probe.of(HttpMethod.GET, "/api/admin/approval-rules", PHASE9_APPROVAL_ALLOWED,
                    PHASE9_APPROVAL_FORBIDDEN),
            // Phase 9: lookups accessible to any authenticated user (isAuthenticated())
            Probe.of(HttpMethod.GET, "/api/lookups/payment-terms", Set.copyOf(ROLES), Set.of()),
            Probe.of(HttpMethod.GET, "/api/lookups/transporters", Set.copyOf(ROLES), Set.of()),
            // Phase 9: tags list — authenticated; global search and comments require GLOBAL_SEARCH / COMMENT_ADD
            Probe.of(HttpMethod.GET, "/api/tags", Set.copyOf(ROLES), Set.of()),
            Probe.of(HttpMethod.GET, "/api/search?q=test", PHASE9_MGRS_ALLOWED, PHASE9_MGRS_FORBIDDEN),
            Probe.of(HttpMethod.GET, "/api/comments?entityType=PRODUCT&entityId=1",
                    PHASE9_MGRS_ALLOWED, PHASE9_MGRS_FORBIDDEN),
            // Phase 9: import jobs list (IMPORT_VIEW or IMPORT_RUN — ADMIN + managers, not STORE_MANAGER)
            Probe.of(HttpMethod.GET, "/api/import", PHASE9_APPROVAL_ALLOWED, PHASE9_APPROVAL_FORBIDDEN),
            // Phase 10: RFQ — ADMIN + PURCHASE_MANAGER (RFQ_VIEW)
            Probe.of(HttpMethod.GET, "/api/rfqs", PHASE10_PURCHASE_ALLOWED, PHASE10_PURCHASE_FORBIDDEN),
            // Phase 10: Purchase invoices — ADMIN + PURCHASE_MANAGER (PURCHASE_INVOICE_VIEW)
            Probe.of(HttpMethod.GET, "/api/purchase-invoices", PHASE10_PURCHASE_ALLOWED, PHASE10_PURCHASE_FORBIDDEN),
            // Phase 10: Purchase returns — ADMIN + PURCHASE_MANAGER (PURCHASE_RETURN_VIEW)
            Probe.of(HttpMethod.GET, "/api/purchase-returns", PHASE10_PURCHASE_ALLOWED, PHASE10_PURCHASE_FORBIDDEN),
            // Phase 10: Supplier quotes — ADMIN + PURCHASE_MANAGER (RFQ_VIEW)
            Probe.of(HttpMethod.GET, "/api/supplier-quotes", PHASE10_PURCHASE_ALLOWED, PHASE10_PURCHASE_FORBIDDEN),
            // Phase 11: Quotations — ADMIN + SALES_MANAGER (QUOTATION_VIEW)
            Probe.of(HttpMethod.GET, "/api/sales/quotations", PHASE11_SALES_ALLOWED, PHASE11_SALES_FORBIDDEN),
            Probe.of(HttpMethod.GET, "/api/sales/quotations/" + MISSING, PHASE11_SALES_ALLOWED, PHASE11_SALES_FORBIDDEN),
            // Phase 11: Order confirmations — ADMIN + SALES_MANAGER (OC_VIEW)
            Probe.of(HttpMethod.GET, "/api/sales/order-confirmations", PHASE11_SALES_ALLOWED, PHASE11_SALES_FORBIDDEN),
            Probe.of(HttpMethod.GET, "/api/sales/order-confirmations/" + MISSING, PHASE11_SALES_ALLOWED, PHASE11_SALES_FORBIDDEN),
            // Phase 11: Delivery challans — ADMIN + SALES_MANAGER (CHALLAN_VIEW)
            Probe.of(HttpMethod.GET, "/api/sales/delivery-challans", PHASE11_SALES_ALLOWED, PHASE11_SALES_FORBIDDEN),
            Probe.of(HttpMethod.GET, "/api/sales/delivery-challans/" + MISSING, PHASE11_SALES_ALLOWED, PHASE11_SALES_FORBIDDEN),
            // Phase 12: Sales invoices — ADMIN + FINANCE_MANAGER (SALES_INVOICE_VIEW)
            Probe.of(HttpMethod.GET, "/api/finance/sales-invoices", PHASE12_FINANCE_ALLOWED, PHASE12_FINANCE_FORBIDDEN),
            Probe.of(HttpMethod.GET, "/api/finance/sales-invoices/" + MISSING, PHASE12_FINANCE_ALLOWED, PHASE12_FINANCE_FORBIDDEN),
            // Phase 12: Payments — ADMIN + FINANCE_MANAGER (PAYMENT_VIEW)
            Probe.of(HttpMethod.GET, "/api/finance/payments", PHASE12_FINANCE_ALLOWED, PHASE12_FINANCE_FORBIDDEN),
            Probe.of(HttpMethod.GET, "/api/finance/payments/" + MISSING, PHASE12_FINANCE_ALLOWED, PHASE12_FINANCE_FORBIDDEN),
            // Phase 12: Open items / ageing — ADMIN + FINANCE_MANAGER (OPEN_ITEM_VIEW)
            Probe.of(HttpMethod.GET, "/api/finance/open-items", PHASE12_FINANCE_ALLOWED, PHASE12_FINANCE_FORBIDDEN));

    /**
     * What a permitted caller gets when it is not a 2xx. Every other permitted probe must answer 2xx. These probes
     * reach the service (method security let them in) and are stopped there: a missing id answers 404, a create that
     * names records which do not exist answers 404, and a create without a body answers 400. A permitted caller that
     * gets any other status, including an undeclared 404, fails the test.
     */
    private static final Map<String, Integer> DECLARED_STATUS = Map.ofEntries(
            Map.entry("POST /api/auth/step-up", 400), // the probe users hold no real password, so the credential is wrong
            Map.entry("POST /api/plants", 409), // a valid body reaches method security; the seeded code P001 makes it a duplicate, so nothing is created
            Map.entry("PUT /api/plants/999999", 404),
            Map.entry("GET /api/plants/999999/settings", 404),
            Map.entry("PUT /api/plants/settings", 400), // an unknown setting key is refused, so no override is written
            Map.entry("DELETE /api/plants/settings?key=bogus", 400),
            Map.entry("GET /api/plants/users/999999", 404),
            Map.entry("PUT /api/plants/users/999999", 404),
            Map.entry("GET /api/ar/credit-notes/999999", 404),
            Map.entry("POST /api/ar/credit-notes", 404),
            Map.entry("POST /api/ar/credit-notes/999999/approve", 404),
            Map.entry("POST /api/ar/credit-notes/999999/cancel", 404),
            Map.entry("POST /api/finance/fx-revaluation/run?periodId=999999", 404),
            Map.entry("PUT /api/finance/exchange-rates", 404),
            Map.entry("PUT /api/finance/tax/product-slabs/999999", 404),
            Map.entry("GET /api/traceability/fg-batch/999999", 404),
            Map.entry("GET /api/traceability/material-batch/999999", 404),
            Map.entry("POST /api/production/issues", 404),
            Map.entry("DELETE /api/documents/999999/links/999999", 404),
            Map.entry("DELETE /api/materials/999999", 404),
            Map.entry("GET /api/sales/dispatches/999999/delivery-note.pdf", 404),
            Map.entry("GET /api/sales/dispatches/999999/packing-list.pdf", 404),
            Map.entry("PUT /api/inventory/negative-stock-overrides/materials/999999", 404),
            Map.entry("PUT /api/inventory/negative-stock-overrides/products/999999", 404),
            Map.entry("GET /api/materials/999999/uom-conversions", 404),
            Map.entry("PUT /api/materials/999999/uom-conversions", 404),
            Map.entry("DELETE /api/materials/999999/uom-conversions/PCS", 404),
            Map.entry("GET /api/inventory/balances/in-uom?materialId=999999&uom=PCS", 404),
            Map.entry("GET /api/materials/999999/purchasing-info", 404),
            Map.entry("PUT /api/materials/999999/purchasing-info", 404),
            Map.entry("GET /api/suppliers/999999/approved-materials", 404),
            Map.entry("PUT /api/suppliers/999999/approved-materials", 404),
            Map.entry("GET /api/customers/999999/approved-products", 404),
            Map.entry("PUT /api/customers/999999/approved-products", 404),
            Map.entry("GET /api/customers/999999/product-specs/999999/overrides", 404),
            Map.entry("PUT /api/customers/999999/product-specs/999999/overrides", 404),
            Map.entry("DELETE /api/customers/999999/product-specs/999999/overrides?parameterName=P", 404),
            Map.entry("DELETE /api/products/999999", 404),
            Map.entry("DELETE /api/rejection-reasons/999999", 404),
            Map.entry("DELETE /api/scheduling/operations/999999/schedule", 404),
            Map.entry("DELETE /api/spare-parts/999999", 404),
            Map.entry("GET /api/documents/999999", 404),
            Map.entry("GET /api/documents/999999/download", 404),
            Map.entry("GET /api/integration/webhooks/999999", 404),
            Map.entry("GET /api/maintenance/requests/999999/activities", 404),
            Map.entry("GET /api/maintenance/requests/999999/cost", 404),
            Map.entry("GET /api/maintenance/requests/999999/spare-parts", 404),
            Map.entry("GET /api/oee/machines/999999", 404),
            Map.entry("GET /api/procurement/goods-receipts/999999", 404),
            Map.entry("GET /api/spare-parts/999999", 404),
            Map.entry("GET /api/spc/charts/999999", 404),
            Map.entry("POST /api/ap/invoices/999999/match-override", 404),
            Map.entry("POST /api/quality/inspections/999999/hold", 404),
            Map.entry("POST /api/quality/inspections/999999/reinspect", 404),
            Map.entry("POST /api/admin/attribute-definitions", 400), // no body: validation answers 400 after method security
            Map.entry("POST /api/admin/attribute-definitions/999999/activate", 404),
            Map.entry("POST /api/admin/attribute-definitions/999999/deactivate", 404),
            Map.entry("POST /api/admin/users/999999/reset-password", 404),
            Map.entry("POST /api/admin/users/999999/unlock", 404),
            Map.entry("POST /api/capa/999999/close", 404),
            Map.entry("POST /api/capa/from-rejection/999999", 404),
            Map.entry("POST /api/complaints", 404), // the body names a customer and batch that do not exist
            Map.entry("POST /api/documents/999999/approve", 404),
            Map.entry("POST /api/documents/999999/links", 404),
            Map.entry("POST /api/documents/999999/obsolete", 404),
            Map.entry("POST /api/documents/999999/reject", 404),
            Map.entry("POST /api/documents/999999/submit", 404),
            Map.entry("POST /api/fai", 404), // the body names a product that does not exist
            Map.entry("POST /api/fai/999999/complete", 404),
            Map.entry("POST /api/fai/999999/results", 404),
            Map.entry("POST /api/integration/webhook-deliveries/999999/retry", 404),
            Map.entry("POST /api/integration/webhooks/999999/rotate-secret", 404),
            Map.entry("POST /api/integration/webhooks/999999/test", 404),
            Map.entry("POST /api/maintenance/pm-schedules/999999/generate-request", 404),
            Map.entry("POST /api/maintenance/requests/999999/activities", 404),
            Map.entry("POST /api/maintenance/requests/999999/post-cost", 404),
            Map.entry("POST /api/maintenance/requests/999999/spare-parts", 404),
            Map.entry("POST /api/notifications/999999/read", 404),
            Map.entry("POST /api/production-orders/999999999/material-returns", 404),
            Map.entry("POST /api/sales/orders/999999/approve", 404),
            Map.entry("POST /api/sales/orders/999999/cancel", 404),
            Map.entry("POST /api/spc/charts", 404), // the body names a parameter that does not exist
            Map.entry("POST /api/spc/charts/999999/points", 404),
            Map.entry("POST /api/spc/charts/999999/recalculate-limits", 404),
            Map.entry("PUT /api/admin/attribute-definitions/999999", 404),
            Map.entry("PUT /api/alerts/999999/acknowledge", 404),
            Map.entry("PUT /api/ap/invoices/999999/cancel", 404),
            Map.entry("GET /api/ap/invoices/999999/payments", 404),
            Map.entry("GET /api/ar/invoices/999999/receipts", 404),
            Map.entry("POST /api/periods/999999/finalize-close", 404),
            Map.entry("POST /api/periods/999999/reopen", 404),
            Map.entry("PUT /api/document-categories/999999", 404),
            Map.entry("PUT /api/integration/webhooks/999999", 404),
            Map.entry("PUT /api/notification-templates/999999", 404),
            Map.entry("PUT /api/production-orders/999999/operator-assignment", 404),
            Map.entry("PUT /api/production/operations/999999/assignment", 404),
            Map.entry("PUT /api/scheduling/operations/999999/schedule", 404),
            Map.entry("PUT /api/spare-parts/999999", 404),
            // Phase 11: GET by missing id answers 404
            Map.entry("GET /api/sales/quotations/999999", 404),
            Map.entry("GET /api/sales/order-confirmations/999999", 404),
            Map.entry("GET /api/sales/delivery-challans/999999", 404),
            // Phase 12: GET by missing id answers 404
            Map.entry("GET /api/finance/sales-invoices/999999", 404),
            Map.entry("GET /api/finance/payments/999999", 404));

    @Autowired UserAdminService userAdminService;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;
    @Autowired JdbcTemplate jdbc;

    private final Map<String, String> tokenByRole = new LinkedHashMap<>();
    private final String runId = UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    void createOneUserPerRoleAndLogIn() throws Exception {
        // user administration records an audit row, which needs an authenticated actor
        var actor = new CachedUser(9001L, "rbac-setup", true, 1, Set.of(), Set.of("USER_CREATE"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null,
                List.of(new SimpleGrantedAuthority("USER_CREATE"))));
        try {
            for (String role : ROLES) {
                String username = username(role);
                userAdminService.create(new CreateUserRequest(username, null, "RBAC " + role,
                        username + "@rbac.test", null, PASSWORD, Set.of(role)));
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
        // admin-created users start with a temporary password; these tests are about the endpoints, not that step
        jdbc.update("UPDATE users SET must_change_password = FALSE WHERE email LIKE ?", "%@rbac.test");
        for (String role : ROLES) {
            MvcResult login = mockMvc.perform(request(HttpMethod.POST, "/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username(role) + "\",\"password\":\"" + PASSWORD + "\"}"))
                    .andReturn();
            assertThat(login.getResponse().getStatus()).as("login as %s", role).isEqualTo(200);
            tokenByRole.put(role, JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken"));
        }
    }

    @AfterAll
    void removeTheUsers() {
        String like = "rbac_%_" + runId;
        // an alert raised by a probe (the anomaly sweep) notifies the users who hold the matching role
        jdbc.update("DELETE FROM notifications WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM password_history WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)",
                like);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM users WHERE username LIKE ?", like);
    }

    private String username(String role) {
        return "rbac_" + role.toLowerCase() + "_" + runId;
    }

    /** Permitted callers must never provoke a server error; forbidden callers are stopped with 403 before any query. */
    private int status(String role, Probe probe) throws Exception {
        var req = request(probe.method(), probe.path()).header("Authorization", "Bearer " + tokenByRole.get(role));
        if (probe.body() != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(probe.body());
        }
        return mockMvc.perform(req).andReturn().getResponse().getStatus();
    }

    @TestFactory
    Stream<DynamicTest> seededRolesReachExactlyTheEndpointsTheyShould() {
        return PROBES.stream().flatMap(probe -> Stream.concat(
                probe.allowed().stream().map(role -> DynamicTest.dynamicTest(
                        role + " may " + probe.method() + " " + probe.path(), () -> {
                            int status = status(role, probe);
                            assertThat(status).as("%s %s as %s", probe.method(), probe.path(), role)
                                    .isNotIn(401, 403).isLessThan(500);
                            Integer declared = DECLARED_STATUS.get(probe.method() + " " + probe.path());
                            if (declared != null) {
                                assertThat(status).as("%s %s as %s (declared)", probe.method(), probe.path(), role)
                                        .isEqualTo(declared);
                            } else {
                                assertThat(status).as("%s %s as %s must succeed", probe.method(), probe.path(), role)
                                        .isBetween(200, 299);
                            }
                        })),
                probe.forbidden().stream().map(role -> DynamicTest.dynamicTest(
                        role + " must not " + probe.method() + " " + probe.path(), () -> {
                            assertThat(status(role, probe)).as("%s %s as %s", probe.method(), probe.path(), role)
                                    .isEqualTo(403);
                        }))));
    }

    @TestFactory
    Stream<DynamicTest> everyEndpointRejectsAnonymousCallers() {
        return PROBES.stream().map(probe -> DynamicTest.dynamicTest(
                "anonymous " + probe.method() + " " + probe.path(), () -> {
                    int status = mockMvc.perform(request(probe.method(), probe.path())).andReturn().getResponse()
                            .getStatus();
                    assertThat(status).isEqualTo(401);
                }));
    }

    /** A mistyped path passes every status check by answering 404 for all roles, so each probe must hit a real handler. */
    @TestFactory
    Stream<DynamicTest> everyProbeResolvesToARealHandler() {
        return PROBES.stream().map(probe -> DynamicTest.dynamicTest(
                probe.method() + " " + probe.path(), () -> {
                    String path = probe.path();
                    int q = path.indexOf('?');
                    var req = new MockHttpServletRequest(probe.method().name(), q < 0 ? path : path.substring(0, q));
                    if (q >= 0) {
                        req.setQueryString(path.substring(q + 1));
                    }
                    Object handler = null;
                    try {
                        var chain = handlerMapping.getHandler(req);
                        handler = chain == null ? null : chain.getHandler();
                    } catch (Exception e) {
                        throw new AssertionError(probe.method() + " " + probe.path() + " does not resolve: " + e, e);
                    }
                    assertThat(handler).as("%s %s must map to a controller method", probe.method(), probe.path())
                            .isNotNull();
                }));
    }

    @TestFactory
    Stream<DynamicTest> everyDeclaredStatusBelongsToAProbe() {
        List<String> probeKeys = new ArrayList<>();
        PROBES.forEach(p -> probeKeys.add(p.method() + " " + p.path()));
        return DECLARED_STATUS.keySet().stream().sorted().map(key -> DynamicTest.dynamicTest(key, () ->
                assertThat(probeKeys).as("declared status without a probe: %s", key).contains(key)));
    }
}
