package com.mfg.ims.enterprise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import com.jayway.jsonpath.JsonPath;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.enterprise.dto.PlantDtos.SaveSettingRequest;
import com.mfg.ims.enterprise.scope.PlantAccessService;
import com.mfg.ims.enterprise.service.PlantSettingService;
import com.mfg.ims.iam.dto.CreateUserRequest;
import com.mfg.ims.iam.service.UserAdminService;
import com.mfg.ims.inventory.dto.TransferCommand;
import com.mfg.ims.inventory.service.InventoryPostingService;
import com.mfg.ims.inventory.service.InventoryQueryService;
import com.mfg.ims.lookup.LookupService;
import com.mfg.ims.procurement.dto.CreateGrRequest;
import com.mfg.ims.procurement.dto.CreatePoRequest;
import com.mfg.ims.procurement.dto.PurchaseOrderDto;
import com.mfg.ims.procurement.service.GoodsReceiptService;
import com.mfg.ims.procurement.service.PurchaseOrderService;
import com.mfg.ims.sales.dto.AddSalesOrderItemRequest;
import com.mfg.ims.sales.dto.CreateSalesOrderRequest;
import com.mfg.ims.sales.service.SalesOrderService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;
import com.mfg.ims.warehouse.service.WarehouseService;

/**
 * Plant scoping (ENT-002 .. ENT-005, R9-7.1 / R9-7.2). Deliberately NOT {@code @Transactional}: every call commits on
 * its own. Two plants exist (the seeded default plant and one created here). A user without assignments, and every
 * existing user, behaves as before; a user assigned to plants sees and creates only those plants' documents.
 */
class PlantScopeIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Plant#Scope-2026x";
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000L);
    private static final String[] ADMIN = {"PURCHASE_VIEW", "PURCHASE_CREATE", "PURCHASE_APPROVE", "SALES_VIEW",
            "SALES_CREATE", "SALES_APPROVE", "INVENTORY_VIEW", "INVENTORY_RECEIVE", "INVENTORY_ADJUST", "PLANT_MANAGE"};
    private static final long BUYER = 7_920_001L;
    private static final long APPROVER = 7_920_002L;

    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;
    @Autowired PurchaseOrderService pos;
    @Autowired GoodsReceiptService receipts;
    @Autowired SalesOrderService salesOrders;
    @Autowired InventoryQueryService inventory;
    @Autowired InventoryPostingService posting;
    @Autowired WarehouseService warehouses;
    @Autowired LookupService lookups;
    @Autowired PlantSettingService settings;
    @Autowired PlantAccessService access;
    @Autowired UserAdminService userAdminService;
    @Autowired com.mfg.ims.auth.service.UserPermissionCache permissionCache;

    private Long plantA;
    private Long plantB;
    private Long warehouseB;
    private Long locationB;

    @BeforeEach
    void plants() {
        plantA = jdbc.queryForObject("SELECT MIN(id) FROM plants WHERE active", Long.class);
        String code = "PB" + SEQ.incrementAndGet() % 100_000;
        plantB = jdbc.queryForObject(
                "INSERT INTO plants (plant_code, name, company_id) VALUES (?, 'Second plant', 1) RETURNING id",
                Long.class, code);
        warehouseB = jdbc.queryForObject("INSERT INTO warehouses (warehouse_code, name, plant_id) VALUES (?, 'Plant B store', ?) RETURNING id",
                Long.class, "WB" + SEQ.incrementAndGet() % 1_000_000, plantB);
        locationB = jdbc.queryForObject("INSERT INTO warehouse_locations (warehouse_id, location_code, name, location_type) "
                + "VALUES (?, 'RM-B', 'Raw B', 'RAW_MATERIAL') RETURNING id", Long.class, warehouseB);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private PurchaseOrderDto poIn(Long plantId) {
        return td.asPlantUser(BUYER, Set.of(), () -> pos.create(new CreatePoRequest(td.supplier().getId(),
                LocalDate.now(), LocalDate.now().plusDays(7), "NET30", null, null,
                List.of(new CreatePoRequest.PoLineRequest(td.material().getId(), new BigDecimal("10"),
                        new BigDecimal("50.0000"), BigDecimal.ZERO)), plantId)), ADMIN);
    }

    private List<Long> visiblePoIds(Set<Long> plants) {
        return td.asPlantUser(BUYER, plants, () -> pos.search(null, null, PageRequest.of(0, 500)).getContent().stream()
                .map(PurchaseOrderDto::id).toList(), ADMIN);
    }

    @Test
    @DisplayName("PLANT-1: a document created without a plant belongs to the default plant, and an unrestricted user sees every plant")
    void defaultPlantAndUnrestrictedView() {
        PurchaseOrderDto defaultPo = poIn(null);
        PurchaseOrderDto otherPo = poIn(plantB);

        assertThat(jdbc.queryForObject("SELECT plant_id FROM purchase_orders WHERE id = ?", Long.class, defaultPo.id()))
                .isEqualTo(plantA);
        assertThat(jdbc.queryForObject("SELECT plant_id FROM purchase_orders WHERE id = ?", Long.class, otherPo.id()))
                .isEqualTo(plantB);
        assertThat(visiblePoIds(Set.of())).contains(defaultPo.id(), otherPo.id());
    }

    @Test
    @DisplayName("PLANT-2: a user limited to one plant lists, reads and changes only that plant's purchase orders")
    void scopedUserSeesOnlyOwnPlant() {
        PurchaseOrderDto inA = poIn(plantA);
        PurchaseOrderDto inB = poIn(plantB);

        assertThat(visiblePoIds(Set.of(plantA))).contains(inA.id()).doesNotContain(inB.id());
        assertThat(visiblePoIds(Set.of(plantB))).contains(inB.id()).doesNotContain(inA.id());

        td.asPlantUser(BUYER, Set.of(plantA), () -> {
            assertThat(pos.findById(inA.id()).id()).isEqualTo(inA.id());
            assertThatThrownBy(() -> pos.findById(inB.id())).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> pos.submit(inB.id())).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> pos.cancel(inB.id(), "not mine")).isInstanceOf(NotFoundException.class);
            return null;
        }, ADMIN);
    }

    @Test
    @DisplayName("PLANT-3: creating a document: the only plant is used, another plant is refused, several plants need a choice")
    void createRules() {
        List<CreatePoRequest.PoLineRequest> lines = List.of(new CreatePoRequest.PoLineRequest(td.material().getId(),
                new BigDecimal("1"), new BigDecimal("10.0000"), BigDecimal.ZERO));
        Long supplier = td.supplier().getId();

        PurchaseOrderDto own = td.asPlantUser(BUYER, Set.of(plantB), () -> pos.create(new CreatePoRequest(supplier,
                LocalDate.now(), null, null, null, null, lines)), ADMIN);
        assertThat(jdbc.queryForObject("SELECT plant_id FROM purchase_orders WHERE id = ?", Long.class, own.id()))
                .as("a user limited to one plant creates in it").isEqualTo(plantB);

        assertThatThrownBy(() -> td.asPlantUser(BUYER, Set.of(plantB), () -> pos.create(new CreatePoRequest(supplier,
                LocalDate.now(), null, null, null, null, lines, plantA)), ADMIN))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo(ErrorCode.OUT_OF_SCOPE);

        assertThatThrownBy(() -> td.asPlantUser(BUYER, Set.of(plantA, plantB), () -> pos.create(new CreatePoRequest(
                supplier, LocalDate.now(), null, null, null, null, lines)), ADMIN))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION_FAILED);

        PurchaseOrderDto chosen = td.asPlantUser(BUYER, Set.of(plantA, plantB), () -> pos.create(new CreatePoRequest(
                supplier, LocalDate.now(), null, null, null, null, lines, plantA)), ADMIN);
        assertThat(jdbc.queryForObject("SELECT plant_id FROM purchase_orders WHERE id = ?", Long.class, chosen.id()))
                .isEqualTo(plantA);
    }

    @Test
    @DisplayName("PLANT-4: a goods receipt takes its purchase order's plant, is hidden from other plants, and cannot go to another plant's warehouse")
    void goodsReceiptFollowsThePurchaseOrder() {
        PurchaseOrderDto po = poIn(plantA);
        td.asPlantUser(BUYER, Set.of(), () -> pos.submit(po.id()), ADMIN);
        PurchaseOrderDto approved = td.asPlantUser(APPROVER, Set.of(), () -> pos.approve(po.id()), ADMIN);
        Long poItem = approved.items().get(0).id();

        // The default warehouse (id 1) belongs to the default plant; plant B's warehouse is refused for plant A's order.
        assertThatThrownBy(() -> td.asPlantUser(APPROVER, Set.of(), () -> receipts.receive(new CreateGrRequest(po.id(),
                LocalDate.now(), warehouseB, "CH-B", "MH01", List.of(new CreateGrRequest.GrLineRequest(poItem,
                new BigDecimal("4"), locationB, "HEAT-B", "CERT-B", null, true)))), "INVENTORY_RECEIVE"))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION_FAILED);

        var gr = td.asPlantUser(APPROVER, Set.of(plantA), () -> receipts.receive(new CreateGrRequest(po.id(),
                LocalDate.now(), 1L, "CH-A", "MH02", List.of(new CreateGrRequest.GrLineRequest(poItem,
                new BigDecimal("4"), 1L, "HEAT-A", "CERT-A", null, true)))), "INVENTORY_RECEIVE");
        assertThat(jdbc.queryForObject("SELECT plant_id FROM goods_receipts WHERE id = ?", Long.class, gr.id()))
                .isEqualTo(plantA);

        td.asPlantUser(APPROVER, Set.of(plantB), () -> {
            assertThatThrownBy(() -> receipts.findById(gr.id())).isInstanceOf(NotFoundException.class);
            assertThat(receipts.search(null, null, null, null, null, PageRequest.of(0, 500)).getContent())
                    .extracting("id").doesNotContain(gr.id());
            assertThatThrownBy(() -> receipts.findByPurchaseOrder(po.id(), PageRequest.of(0, 10)))
                    .isInstanceOf(NotFoundException.class);
            return null;
        }, "INVENTORY_RECEIVE", "INVENTORY_VIEW");
        td.asPlantUser(APPROVER, Set.of(plantA), () -> {
            assertThat(receipts.findById(gr.id()).id()).isEqualTo(gr.id());
            return null;
        }, "INVENTORY_RECEIVE", "INVENTORY_VIEW");
    }

    @Test
    @DisplayName("PLANT-5: sales orders are scoped the same way")
    void salesOrdersAreScoped() {
        Long customer = td.customer().getId();
        Long product = td.product().getId();
        var inA = td.asPlantUser(BUYER, Set.of(), () -> salesOrders.create(new CreateSalesOrderRequest(customer,
                LocalDate.now(), null, "Addr", "NET30", "A", List.of(new AddSalesOrderItemRequest(product,
                new BigDecimal("2"), new BigDecimal("100.0000"), null, null)), plantA)), ADMIN);
        var inB = td.asPlantUser(BUYER, Set.of(), () -> salesOrders.create(new CreateSalesOrderRequest(customer,
                LocalDate.now(), null, "Addr", "NET30", "B", List.of(new AddSalesOrderItemRequest(product,
                new BigDecimal("2"), new BigDecimal("100.0000"), null, null)), plantB)), ADMIN);

        td.asPlantUser(BUYER, Set.of(plantA), () -> {
            assertThat(salesOrders.search(null, null, null, null, PageRequest.of(0, 500)).getContent())
                    .extracting("id").contains(inA.id()).doesNotContain(inB.id());
            assertThatThrownBy(() -> salesOrders.findById(inB.id())).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> salesOrders.cancel(inB.id(), "x")).isInstanceOf(NotFoundException.class);
            return null;
        }, ADMIN);
    }

    @Test
    @DisplayName("PLANT-6: stock follows the warehouse's plant: transfers into another plant are refused, balances, the ledger and warehouse lists are filtered")
    void stockFollowsTheWarehouse() {
        TransferCommand toB = new TransferCommand("MATERIAL", td.material().getId(), null, null, null, 1L, 1L,
                "AVAILABLE", warehouseB, locationB, "AVAILABLE", BigDecimal.ONE, "KG", "TEST", 1L, "T", null);
        assertThatThrownBy(() -> td.asPlantUser(BUYER, Set.of(plantA), () -> posting.transfer(toB), ADMIN))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo(ErrorCode.OUT_OF_SCOPE);

        td.asPlantUser(BUYER, Set.of(plantB), () -> {
            assertThat(warehouses.search(null, null)).extracting("id").contains(warehouseB).doesNotContain(1L);
            assertThat(lookups.warehouses(null)).extracting("id").contains(warehouseB).doesNotContain(1L);
            assertThat(lookups.locations(null)).extracting("id").contains(locationB);
            assertThat(inventory.queryBalances(null, null, null, null))
                    .extracting("warehouseId").doesNotContain(1L);
            assertThat(inventory.ledger(null, null, null, null, null, null, PageRequest.of(0, 500)).getContent())
                    .extracting("warehouseId").doesNotContain(1L);
            assertThat(lookups.plants()).extracting("id").containsExactly(plantB);
            return null;
        }, ADMIN);
        td.asPlantUser(BUYER, Set.of(), () -> {
            assertThat(warehouses.search(null, null)).extracting("id").contains(warehouseB, 1L);
            assertThat(lookups.plants()).extracting("id").contains(plantA, plantB);
            return null;
        }, ADMIN);
    }

    @Test
    @DisplayName("PLANT-7: settings resolve plant, then company, then the system default")
    void settingsResolveInOrder() {
        assertThat(settings.resolve(plantB, "timezone").orElseThrow().source()).isEqualTo("SYSTEM");

        td.asPlantUser(BUYER, Set.of(), () -> {
            settings.set(new SaveSettingRequest(null, "timezone", "UTC"));
            return null;
        }, ADMIN);
        var company = settings.resolve(plantB, "timezone").orElseThrow();
        assertThat(company.source()).isEqualTo("COMPANY");
        assertThat(company.value()).isEqualTo("UTC");

        td.asPlantUser(BUYER, Set.of(), () -> {
            settings.set(new SaveSettingRequest(plantB, "timezone", "America/New_York"));
            return null;
        }, ADMIN);
        var plant = settings.resolve(plantB, "timezone").orElseThrow();
        assertThat(plant.source()).isEqualTo("PLANT");
        assertThat(plant.value()).isEqualTo("America/New_York");
        assertThat(settings.resolve(plantA, "timezone").orElseThrow().source()).as("another plant keeps the company value")
                .isEqualTo("COMPANY");

        td.asPlantUser(BUYER, Set.of(), () -> {
            settings.clear(plantB, "timezone");
            settings.clear(null, "timezone");
            return null;
        }, ADMIN);
        assertThat(settings.resolve(plantB, "timezone").orElseThrow().source()).isEqualTo("SYSTEM");

        assertThatThrownBy(() -> td.asPlantUser(BUYER, Set.of(), () -> {
            settings.set(new SaveSettingRequest(plantB, "costing_method", "FIFO"));
            return null;
        }, ADMIN)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> td.asPlantUser(BUYER, Set.of(), () -> {
            settings.set(new SaveSettingRequest(plantB, "default_warehouse_id", String.valueOf(1L)));
            return null;
        }, ADMIN)).as("a warehouse of another plant").isInstanceOf(BusinessRuleException.class);
        td.asPlantUser(BUYER, Set.of(), () -> {
            settings.set(new SaveSettingRequest(plantB, "default_warehouse_id", String.valueOf(warehouseB)));
            settings.clear(plantB, "default_warehouse_id");
            return null;
        }, ADMIN);
    }

    // ── through the real login, JWT filter, user cache and interceptor ────────────────────────────────

    private void role(String code, String... permissions) {
        jdbc.update("INSERT INTO roles (code, name) VALUES (?, ?)", code, code);
        for (String permission : permissions) {
            jdbc.update("INSERT INTO role_permissions (role_id, permission_id) "
                    + "SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = ? AND p.code = ?", code, permission);
        }
    }

    private record Login(long userId, String token) {}

    private Login login(String roleCode) throws Exception {
        String username = "plantscope-" + SEQ.incrementAndGet();
        var created = userAdminService.create(new CreateUserRequest(username, null, "Plant tester", username + "@plant.test",
                null, PASSWORD, Set.of(roleCode)));
        jdbc.update("UPDATE users SET must_change_password = FALSE WHERE username = ?", username);
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Login(created.id(), JsonPath.read(body, "$.accessToken"));
    }

    private void dropRolesAndUsers() {
        jdbc.update("DELETE FROM user_plant_access WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'plantscope-%')");
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'plantscope-%')");
        jdbc.update("DELETE FROM password_history WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'plantscope-%')");
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'plantscope-%')");
        jdbc.update("DELETE FROM users WHERE username LIKE 'plantscope-%'");
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code LIKE 'PSC_%')");
        jdbc.update("DELETE FROM roles WHERE code LIKE 'PSC_%'");
    }

    @Test
    @DisplayName("PLANT-8: through the real login a plant-scoped user is limited, is kept out of company-level functions, and an assignment change takes effect at once")
    void realLoginAssignmentAndGuard() throws Exception {
        dropRolesAndUsers();
        try {
            role("PSC_BUYER", "PURCHASE_VIEW", "PURCHASE_CREATE", "DASHBOARD_VIEW");
            role("PSC_PLANT_ADMIN", "PLANT_MANAGE");
            PurchaseOrderDto inA = poIn(plantA);
            PurchaseOrderDto inB = poIn(plantB);

            Login buyer = login("PSC_BUYER");
            Login admin = login("PSC_PLANT_ADMIN");

            // Unassigned: unrestricted, and the company-level dashboard is open to the holder of its permission.
            mockMvc.perform(get("/api/dashboard/kpis").header("Authorization", "Bearer " + buyer.token()))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/procurement/purchase-orders?size=500").header("Authorization", "Bearer " + buyer.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.id==" + inA.id() + ")]").isNotEmpty())
                    .andExpect(jsonPath("$.content[?(@.id==" + inB.id() + ")]").isNotEmpty());

            // The plant administrator assigns the buyer to plant B; the cached user is evicted, so it applies on the next call.
            mockMvc.perform(put("/api/plants/users/" + buyer.userId()).header("Authorization", "Bearer " + admin.token())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"plantIds\":[" + plantB + "]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plantIds[0]").value(plantB.intValue()))
                    .andExpect(jsonPath("$.allAccess").value(false));

            mockMvc.perform(get("/api/procurement/purchase-orders?size=500").header("Authorization", "Bearer " + buyer.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.id==" + inB.id() + ")]").isNotEmpty())
                    .andExpect(jsonPath("$.content[?(@.id==" + inA.id() + ")]").isEmpty());
            mockMvc.perform(get("/api/procurement/purchase-orders/" + inA.id()).header("Authorization", "Bearer " + buyer.token()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/dashboard/kpis").header("Authorization", "Bearer " + buyer.token()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PLANT_SCOPE_COMPANY_LEVEL"));

            // Removing the assignment lifts every restriction again.
            mockMvc.perform(put("/api/plants/users/" + buyer.userId()).header("Authorization", "Bearer " + admin.token())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"plantIds\":[]}"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/dashboard/kpis").header("Authorization", "Bearer " + buyer.token()))
                    .andExpect(status().isOk());

            // The buyer cannot assign plants: no PLANT_MANAGE.
            mockMvc.perform(put("/api/plants/users/" + buyer.userId()).header("Authorization", "Bearer " + buyer.token())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"plantIds\":[]}"))
                    .andExpect(status().isForbidden());
            assertThat(access.plantIdsOf(buyer.userId())).isEmpty();
        } finally {
            dropRolesAndUsers();
        }
    }

    @Test
    @DisplayName("PLANT-9: PLANT_ALL_ACCESS lifts the restriction whatever is assigned (cross-plant visibility per role)")
    void allAccessRoleSeesEveryPlant() throws Exception {
        dropRolesAndUsers();
        try {
            role("PSC_ALL", "PURCHASE_VIEW", "PLANT_ALL_ACCESS");
            PurchaseOrderDto inA = poIn(plantA);
            PurchaseOrderDto inB = poIn(plantB);
            Login user = login("PSC_ALL");
            access.replace(user.userId(), Set.of(plantB), null);
            permissionCache.evict(user.userId());

            mockMvc.perform(get("/api/procurement/purchase-orders?size=500").header("Authorization", "Bearer " + user.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.id==" + inA.id() + ")]").isNotEmpty())
                    .andExpect(jsonPath("$.content[?(@.id==" + inB.id() + ")]").isNotEmpty());
        } finally {
            dropRolesAndUsers();
        }
    }
}
