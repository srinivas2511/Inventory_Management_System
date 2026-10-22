package com.mfg.ims.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.engineering.bom.BomDto;
import com.mfg.ims.engineering.bom.BomItemRequest;
import com.mfg.ims.engineering.bom.BomService;
import com.mfg.ims.engineering.bom.CreateBomRequest;
import com.mfg.ims.engineering.routing.RoutingService;
import com.mfg.ims.engineering.routing.SaveRoutingStepRequest;
import com.mfg.ims.iam.domain.Role;
import com.mfg.ims.iam.domain.User;
import com.mfg.ims.iam.repository.RoleRepository;
import com.mfg.ims.iam.repository.UserRepository;
import com.mfg.ims.inventory.dto.OpeningBalanceLine;
import com.mfg.ims.inventory.service.OpeningBalanceService;
import com.mfg.ims.material.dto.MaterialDto;
import com.mfg.ims.material.dto.SaveMaterialRequest;
import com.mfg.ims.material.repository.MaterialRepository;
import com.mfg.ims.material.service.MaterialService;
import com.mfg.ims.partner.dto.SaveCustomerRequest;
import com.mfg.ims.partner.dto.SaveSupplierRequest;
import com.mfg.ims.partner.repository.CustomerRepository;
import com.mfg.ims.partner.repository.SupplierRepository;
import com.mfg.ims.partner.service.CustomerService;
import com.mfg.ims.partner.service.SupplierService;
import com.mfg.ims.procurement.dto.CreatePoRequest;
import com.mfg.ims.procurement.dto.PurchaseOrderDto;
import com.mfg.ims.procurement.repository.PurchaseOrderItemRepository;
import com.mfg.ims.procurement.service.PurchaseOrderService;
import com.mfg.ims.product.dto.ProductDto;
import com.mfg.ims.product.dto.SaveProductRequest;
import com.mfg.ims.product.repository.ProductRepository;
import com.mfg.ims.product.service.ProductService;
import com.mfg.ims.production.dto.CreateIssueRequest;
import com.mfg.ims.production.dto.CreateProductionOrderRequest;
import com.mfg.ims.production.dto.ProductionOrderDto;
import com.mfg.ims.production.dto.RecordOutputRequest;
import com.mfg.ims.production.repository.ProductionOrderRepository;
import com.mfg.ims.production.service.MaterialIssueService;
import com.mfg.ims.production.service.OperationExecutionService;
import com.mfg.ims.production.service.ProductionOrderService;
import com.mfg.ims.quality.dto.FinalInspectionDecisionRequest;
import com.mfg.ims.quality.dto.QualityInspectionDto;
import com.mfg.ims.insight.alert.AlertSweepJob;
import com.mfg.ims.quality.repository.QualityInspectionRepository;
import com.mfg.ims.quality.service.FinalInspectionService;
import com.mfg.ims.sales.dto.AddSalesOrderItemRequest;
import com.mfg.ims.sales.dto.CreateDispatchRequest;
import com.mfg.ims.sales.dto.CreateSalesOrderRequest;
import com.mfg.ims.sales.dto.ReserveRequest;
import com.mfg.ims.sales.dto.SalesOrderDto;
import com.mfg.ims.sales.service.DispatchService;
import com.mfg.ims.sales.service.ReservationService;
import com.mfg.ims.sales.service.SalesOrderService;

/**
 * Loads a complete end-to-end demo dataset on startup in the dev/demo profile.
 * Idempotent: each step checks for existing records before creating.
 * Calls real application services — no raw SQL.
 *
 * Note on procurement: PurchaseOrderService enforces approver ≠ submitter (segregation of duties),
 * so the demo creates and submits a PO but cannot approve it as the same user.
 * Raw-material stock for production is seeded via OpeningBalanceService instead.
 */
@Component
@Profile({"dev", "demo"})
@ConditionalOnProperty(name = "ims.demo.load-data", havingValue = "true")
public class DemoDataLoader {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    /** The demo administrator, remembered so a step that needs a second person can switch back to them. */
    private long adminUserId;

    // ── seed codes (idempotency guard) ────────────────────────────────────────

    // documented in README.md; this class only exists in the dev/demo profiles, never production
    private static final String DEMO_ADMIN_USERNAME = "admin";
    private static final String DEMO_ADMIN_PASSWORD = "Admin@123";
    /** DESIGN section 11.2: the initial password of every demo role user; a change is forced at the first login. */
    private static final String DEMO_USER_PASSWORD = "Demo@12345!";
    /** DESIGN section 11.2 role users (plus finance1, which UAT Role 8 needs): username, role, full name. */
    private static final String[][] DEMO_USERS = {
            {"engineer1", "ENGINEER", "Demo Engineer"},
            {"prodmgr1", "PRODUCTION_MANAGER", "Demo Production Manager"},
            {"supervisor1", "SUPERVISOR", "Demo Supervisor"},
            {"operator1", "OPERATOR", "Demo Operator One"},
            {"operator2", "OPERATOR", "Demo Operator Two"},
            {"quality1", "QUALITY_MANAGER", "Demo Quality Manager"},
            {"storemgr1", "STORE_MANAGER", "Demo Store Manager"},
            {"storeop1", "STORE_OPERATOR", "Demo Store Operator"},
            {"purchase1", "PURCHASE_MANAGER", "Demo Purchase Manager"},
            {"sales1", "SALES", "Demo Sales"},
            {"dispatch1", "DISPATCH", "Demo Dispatch"},
            {"maint1", "MAINTENANCE", "Demo Maintenance"},
            {"mgmt1", "MANAGEMENT", "Demo Management"},
            {"finance1", "FINANCE_MANAGER", "Demo Finance Manager"},
    };

    private static final String SUPPLIER_CODE = "DEMO-KRISHNA";
    private static final String CUSTOMER_CODE  = "DEMO-AUTOINC";
    private static final String MATERIAL_CODE  = "DEMO-WR-65SI2";
    private static final String LOW_STOCK_MATERIAL_CODE = "DEMO-WR-LOWSTK";
    private static final String PRODUCT_CODE   = "DEMO-CS-25X3";

    // warehouse 1 / locations 1-8 are created by V2 migration (MAIN-WH)
    private static final long WAREHOUSE_ID = 1L;
    private static final long LOC_RM01     = 1L;  // RAW_MATERIAL
    private static final long LOC_FG01     = 4L;  // FINISHED

    // operations seeded by V2: 1=WIRE-DRAW, 2=COILING, 3=CUTTING, 4=GRINDING,
    // 5=HEAT-TREAT, 6=SHOT-PEEN, 7=SURFACE-TREAT, 8=INSPECTION, 9=PACKAGING
    private static final long OP_COILING    = 2L;
    private static final long OP_GRINDING   = 4L;
    private static final long OP_INSPECTION = 8L;

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final PasswordEncoder passwordEncoder;
    private final SupplierRepository supplierRepo;
    private final CustomerRepository customerRepo;
    private final MaterialRepository materialRepo;
    private final ProductRepository productRepo;
    private final PurchaseOrderItemRepository poItemRepo;
    private final ProductionOrderRepository productionOrderRepo;
    private final QualityInspectionRepository inspectionRepo;

    private final SupplierService supplierService;
    private final CustomerService customerService;
    private final MaterialService materialService;
    private final ProductService productService;
    private final RoutingService routingService;
    private final BomService bomService;
    private final OpeningBalanceService openingBalanceService;
    private final PurchaseOrderService purchaseOrderService;
    private final ProductionOrderService productionOrderService;
    private final MaterialIssueService materialIssueService;
    private final OperationExecutionService opExecService;
    private final FinalInspectionService finalInspectionService;
    private final SalesOrderService salesOrderService;
    private final ReservationService reservationService;
    private final DispatchService dispatchService;
    private final AlertSweepJob alertSweepJob;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public DemoDataLoader(
            UserRepository userRepo,
            RoleRepository roleRepo,
            PasswordEncoder passwordEncoder,
            SupplierRepository supplierRepo,
            CustomerRepository customerRepo,
            MaterialRepository materialRepo,
            ProductRepository productRepo,
            PurchaseOrderItemRepository poItemRepo,
            ProductionOrderRepository productionOrderRepo,
            QualityInspectionRepository inspectionRepo,
            SupplierService supplierService,
            CustomerService customerService,
            MaterialService materialService,
            ProductService productService,
            RoutingService routingService,
            BomService bomService,
            OpeningBalanceService openingBalanceService,
            PurchaseOrderService purchaseOrderService,
            ProductionOrderService productionOrderService,
            MaterialIssueService materialIssueService,
            OperationExecutionService opExecService,
            FinalInspectionService finalInspectionService,
            SalesOrderService salesOrderService,
            ReservationService reservationService,
            DispatchService dispatchService,
            AlertSweepJob alertSweepJob,
            org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.passwordEncoder = passwordEncoder;
        this.supplierRepo = supplierRepo;
        this.customerRepo = customerRepo;
        this.materialRepo = materialRepo;
        this.productRepo = productRepo;
        this.poItemRepo = poItemRepo;
        this.productionOrderRepo = productionOrderRepo;
        this.inspectionRepo = inspectionRepo;
        this.supplierService = supplierService;
        this.customerService = customerService;
        this.materialService = materialService;
        this.productService = productService;
        this.routingService = routingService;
        this.bomService = bomService;
        this.openingBalanceService = openingBalanceService;
        this.purchaseOrderService = purchaseOrderService;
        this.productionOrderService = productionOrderService;
        this.materialIssueService = materialIssueService;
        this.opExecService = opExecService;
        this.finalInspectionService = finalInspectionService;
        this.salesOrderService = salesOrderService;
        this.reservationService = reservationService;
        this.dispatchService = dispatchService;
        this.alertSweepJob = alertSweepJob;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        log.info("DemoDataLoader: starting demo data load");
        try {
            installAdminContext(step0_adminUser());
            step0b_demoUsers();
            long supplierId = step1_supplier();
            long customerId = step2_customer();
            long materialId = step3_material(supplierId);
            long productId  = step4_product(materialId);
            step5_routing(productId);
            long bomId      = step6_bom(productId, materialId);
            if (!productionOrderRepo.findByProductId(productId).isEmpty()) {
                // stock, PO and production to dispatch are one flow; a second run must not repeat any of it
                log.info("DemoDataLoader: demo flow already loaded — nothing more to do");
                return;
            }
            step7_openingBalance(materialId);
            step8_purchaseOrderDraft(supplierId, materialId);
            long prodOrderId = step9_productionOrder(productId, bomId);
            step10_issueAndExecute(prodOrderId, materialId);
            long fgBatchId  = step11_finalInspection(prodOrderId);
            long soId       = step12_salesOrder(customerId, productId);
            step13_reserveAndDispatch(soId, productId, fgBatchId);
            step15_lateRunAndWaitingInspection(productId, bomId);
            step14_alertConditions(supplierId);
            log.info("DemoDataLoader: demo data load complete");
        } catch (Exception e) {
            log.error("DemoDataLoader: load failed — env may be partially seeded: {}", e.getMessage(), e);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ── Step 0: the documented demo administrator ───────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long step0_adminUser() {
        return userRepo.findByUsername(DEMO_ADMIN_USERNAME)
                .map(u -> { log.info("step0: demo admin exists id={}", u.getId()); return u.getId(); })
                .orElseGet(() -> {
                    Role adminRole = roleRepo.findByCode("ADMIN")
                            .orElseThrow(() -> new IllegalStateException("Role ADMIN is missing"));
                    User admin = new User();
                    admin.setUsername(DEMO_ADMIN_USERNAME);
                    admin.setFullName("Demo Administrator");
                    admin.setEmail("admin@demo.local");
                    admin.setPasswordHash(passwordEncoder.encode(DEMO_ADMIN_PASSWORD));
                    admin.setPasswordChangedAt(Instant.now());
                    admin.setMustChangePassword(false);
                    admin.setRoles(new HashSet<>(Set.of(adminRole)));
                    userRepo.save(admin);
                    log.info("step0: demo admin created (username={})", DEMO_ADMIN_USERNAME);
                    return admin.getId();
                });
    }

    /** DESIGN section 11.2: one user per role, created only when absent so a second run changes nothing. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int step0b_demoUsers() {
        int created = 0;
        for (String[] u : DEMO_USERS) {
            if (userRepo.findByUsername(u[0]).isPresent()) {
                continue;
            }
            Role role = roleRepo.findByCode(u[1]).orElse(null);
            if (role == null) {
                log.warn("step0b: role {} is missing, demo user {} skipped", u[1], u[0]);
                continue;
            }
            User user = new User();
            user.setUsername(u[0]);
            user.setFullName(u[2]);
            user.setEmail(u[0] + "@demo.local");
            user.setPasswordHash(passwordEncoder.encode(DEMO_USER_PASSWORD));
            user.setPasswordChangedAt(Instant.now());
            user.setMustChangePassword(true);
            user.setRoles(new HashSet<>(Set.of(role)));
            userRepo.save(user);
            created++;
        }
        log.info("step0b: {} demo role users created (initial password per DESIGN section 11.2)", created);
        return created;
    }

    // ── Step 1: Supplier ─────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long step1_supplier() {
        return supplierRepo.findBySupplierCode(SUPPLIER_CODE)
                .map(s -> { log.info("step1: supplier exists id={}", s.getId()); return s.getId(); })
                .orElseGet(() -> {
                    var dto = supplierService.create(new SaveSupplierRequest(
                            SUPPLIER_CODE, "Krishna Wire Products Pvt Ltd",
                            "Ravi Krishna", "+91-9876540001",
                            "procurement@krishnawires.example",
                            "Plot 42, MIDC Industrial Area, Pune 411026",
                            null, null, "Net 30", 7, null));
                    log.info("step1: supplier created id={}", dto.id());
                    return dto.id();
                });
    }

    // ── Step 2: Customer ─────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long step2_customer() {
        return customerRepo.findByCustomerCode(CUSTOMER_CODE)
                .map(c -> { log.info("step2: customer exists id={}", c.getId()); return c.getId(); })
                .orElseGet(() -> {
                    var dto = customerService.create(new SaveCustomerRequest(
                            CUSTOMER_CODE, "AutoIncorp Technologies Ltd",
                            "Priya Sharma", "+91-9876540002",
                            "purchase@autoincorp.example",
                            "Survey 18, Peenya Industrial Estate, Bengaluru 560058",
                            null, null, "Net 45", null));
                    log.info("step2: customer created id={}", dto.id());
                    return dto.id();
                });
    }

    // ── Step 3: Raw material ─────────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long step3_material(long supplierId) {
        return materialRepo.findByMaterialCode(MATERIAL_CODE)
                .map(m -> { log.info("step3: material exists id={}", m.getId()); return m.getId(); })
                .orElseGet(() -> {
                    MaterialDto dto = materialService.create(new SaveMaterialRequest(
                            MATERIAL_CODE, "65Si2 Spring Steel Wire Rod 3.0mm",
                            "WIRE_ROD", "65Si2", new BigDecimal("3.000"),
                            "KG", supplierId,
                            new BigDecimal("50"), new BigDecimal("100"),
                            new BigDecimal("2000"), new BigDecimal("85.0000"),
                            null, "High-carbon silicon-manganese spring steel wire rod, IS 4454", null));
                    log.info("step3: material created id={}", dto.id());
                    return dto.id();
                });
    }

    // ── Step 4: Finished-goods product ───────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long step4_product(long materialId) {
        return productRepo.findByProductCode(PRODUCT_CODE)
                .map(p -> { log.info("step4: product exists id={}", p.getId()); return p.getId(); })
                .orElseGet(() -> {
                    ProductDto dto = productService.create(new SaveProductRequest(
                            PRODUCT_CODE, "Compression Spring CS-25×3",
                            "COMPRESSION", materialId,
                            new BigDecimal("3.000"),   // wireDiameter
                            new BigDecimal("25.000"),  // outerDiameter
                            null,                      // innerDiameter
                            new BigDecimal("50.000"),  // freeLength
                            new BigDecimal("12"),      // numberOfCoils
                            new BigDecimal("10"),      // activeCoils
                            new BigDecimal("5.200"),   // springRate N/mm
                            new BigDecimal("120.000"), // maxLoad
                            new BigDecimal("40.000"),  // minLoad
                            new BigDecimal("38.000"),  // workingLength
                            new BigDecimal("36.000"),  // solidHeight
                            "CLOSED_GROUND", "ZINC_PHOSPHATE", "STRESS_RELIEF_260C",
                            "IS1895_CLASS2",
                            new BigDecimal("0.045"),   // unitWeightKg
                            "PCS", "DRG-CS25-001", "A",
                            null, null, new BigDecimal("12.5000"),
                            null, null));
                    log.info("step4: product created id={}", dto.id());
                    return dto.id();
                });
    }

    // ── Step 5: Product routing ───────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void step5_routing(long productId) {
        if (!routingService.getForProduct(productId).isEmpty()) {
            log.info("step5: routing exists for product {}", productId);
            return;
        }
        routingService.saveSteps(productId, List.of(
                new SaveRoutingStepRequest(10, OP_COILING,    null, new BigDecimal("15"), new BigDecimal("10"), false),
                new SaveRoutingStepRequest(20, OP_GRINDING,   null, new BigDecimal("20"), new BigDecimal("10"), false),
                new SaveRoutingStepRequest(30, OP_INSPECTION, null, new BigDecimal("20"), new BigDecimal("5"),  true)));
        log.info("step5: routing saved for product {}", productId);
    }

    // ── Step 6: BOM (DRAFT → REVIEW → APPROVED → ACTIVE) ────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long step6_bom(long productId, long materialId) {
        try {
            BomDto existing = bomService.findActiveBomForProduct(productId);
            log.info("step6: active BOM exists id={}", existing.id());
            return existing.id();
        } catch (Exception ignored) { /* no active BOM yet */ }

        BomDto bom = bomService.create(new CreateBomRequest(
                productId,
                BigDecimal.ONE,
                "Initial BOM for demo product",
                List.of(new BomItemRequest(
                        10, "MATERIAL", materialId, null,
                        new BigDecimal("0.045"), "KG",
                        new BigDecimal("3"), "65Si2 wire rod per piece"))));
        log.info("step6: BOM created id={} number={}", bom.id(), bom.bomNumber());

        bom = bomService.submitForReview(bom.id());
        bom = bomService.approve(bom.id());
        bom = bomService.activate(bom.id());
        log.info("step6: BOM activated id={}", bom.id());
        return bom.id();
    }

    // ── Step 7: Opening balance (seed 500 kg AVAILABLE raw-material stock) ───

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void step7_openingBalance(long materialId) {
        openingBalanceService.loadOpeningBalances(List.of(
                new OpeningBalanceLine(
                        "MATERIAL", materialId, null, null,
                        WAREHOUSE_ID, LOC_RM01, "AVAILABLE",
                        new BigDecimal("500.000"), "KG",
                        new BigDecimal("85.0000"),
                        "Demo opening balance — 65Si2 wire rod")));
        log.info("step7: opening balance posted for material {}", materialId);
    }

    // ── Step 8: PO (DRAFT → SUBMITTED) — demonstrates procurement entry ──────
    // Note: PO approval enforces approver ≠ submitter (segregation of duties).
    // In the demo this PO stays SUBMITTED; approval is done by a second user.

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void step8_purchaseOrderDraft(long supplierId, long materialId) {
        if (poItemRepo.countByMaterialId(materialId) > 0) {
            log.info("step8: PO already exists for material {}", materialId);
            return;
        }
        PurchaseOrderDto po = purchaseOrderService.create(new CreatePoRequest(
                supplierId,
                LocalDate.now().minusDays(5),
                LocalDate.now().plusDays(7),
                "Net 30", "Demo procurement order", null,
                List.of(new CreatePoRequest.PoLineRequest(
                        materialId, new BigDecimal("200.000"),
                        new BigDecimal("85.0000"), new BigDecimal("18")))));
        purchaseOrderService.submit(po.id());
        log.info("step8: PO created and submitted id={} number={}", po.id(), po.poNumber());
    }

    // ── Step 9: Production order (DRAFT → APPROVED → RELEASED) ──────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long step9_productionOrder(long productId, long bomId) {
        ProductionOrderDto order = productionOrderService.create(new CreateProductionOrderRequest(
                productId, bomId,
                new BigDecimal("100"),
                LocalDate.now(),
                LocalDate.now().plusDays(3),
                "LINE-A", null, null, null, (short) 2,
                "Demo production run — 100 pcs CS-25x3"));
        log.info("step9: production order created id={} number={}", order.id(), order.orderNumber());

        order = productionOrderService.approve(order.id());
        order = productionOrderService.release(order.id());
        log.info("step9: production order released id={}", order.id());
        return order.id();
    }

    // ── Step 10: Material issue + operations ─────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void step10_issueAndExecute(long prodOrderId, long materialId) {
        // Issue: 100 pcs × 0.045 kg × 1.03 scrap ≈ 4.635 kg
        materialIssueService.issue(new CreateIssueRequest(
                prodOrderId, WAREHOUSE_ID,
                List.of(new CreateIssueRequest.IssueLineRequest(
                        materialId, new BigDecimal("4.635"), null)),
                "Demo issue — 100 pcs × 0.045 kg + 3% scrap"));
        log.info("step10: material issued for order {}", prodOrderId);

        // Execute each operation (BOM snapshot was created on release)
        ProductionOrderDto order = productionOrderService.findById(prodOrderId);
        for (var op : order.operations()) {
            opExecService.startOperation(op.id(), null);
            opExecService.recordOutput(new RecordOutputRequest(
                    op.id(),
                    new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                    null, null, null, null, "A", "Demo run"));
            opExecService.completeOperation(op.id());
            log.info("step10: operation {} seq={} completed", op.id(), op.sequenceNo());
        }
        log.info("step10: all operations complete for order {}", prodOrderId);
    }

    // ── Step 11: Final inspection → APPROVED (100 pcs) ───────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long step11_finalInspection(long prodOrderId) {
        var pending = inspectionRepo.findByProductionOrderIdAndInspectionTypeAndStatus(
                prodOrderId, "FINAL", "PENDING");
        if (pending.isEmpty()) {
            log.warn("step11: no PENDING FINAL inspection for order {} — skipping", prodOrderId);
            return 0L;
        }
        long inspId = pending.get(0).getId();
        log.info("step11: completing final inspection id={}", inspId);

        QualityInspectionDto result = finalInspectionService.completeFinalInspection(
                inspId,
                new FinalInspectionDecisionRequest(
                        new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                        List.of(), List.of(),
                        "Demo QC pass — all 100 pieces accepted"));

        Long fgBatchId = result.fgBatchId();
        if (fgBatchId == null) {
            log.warn("step11: fgBatchId not set on completed inspection {}", inspId);
            return 0L;
        }
        log.info("step11: FG batch approved id={}", fgBatchId);
        return fgBatchId;
    }

    // ── Step 12: Sales order (DRAFT → confirm → approve) ─────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long step12_salesOrder(long customerId, long productId) {
        // Segregation of duties: the sales user creates and confirms the order, the administrator approves it.
        long salesUserId = userRepo.findByUsername("sales1").map(u -> u.getId())
                .orElseThrow(() -> new IllegalStateException("Demo user sales1 is missing"));
        installUserContext(salesUserId, "sales1", Set.of("SALES", "SALES_VIEW", "SALES_CREATE", "SALES_UPDATE"));
        SalesOrderDto so = salesOrderService.create(new CreateSalesOrderRequest(
                customerId,
                LocalDate.now(),
                LocalDate.now().plusDays(10),
                "AutoIncorp Technologies, Survey 18, Peenya, Bengaluru 560058",
                "Net 45",
                "Demo sales order — urgent delivery",
                List.of(new AddSalesOrderItemRequest(
                        productId, new BigDecimal("80"),
                        new BigDecimal("14.5000"), null, null))));
        log.info("step12: SO created id={} number={}", so.id(), so.orderNumber());

        so = salesOrderService.confirm(so.id());
        installAdminContext(adminUserId);
        so = salesOrderService.approve(so.id());
        log.info("step12: SO approved id={}", so.id());
        return so.id();
    }

    // ── Step 13: FG reservation + dispatch ────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void step13_reserveAndDispatch(long soId, long productId, long fgBatchId) {
        if (fgBatchId == 0L) {
            log.warn("step13: no valid FG batch — skipping reservation and dispatch");
            return;
        }

        SalesOrderDto so = salesOrderService.findById(soId);
        long soItemId = so.items().get(0).id();

        reservationService.reserve(new ReserveRequest(
                soItemId, fgBatchId, WAREHOUSE_ID, LOC_FG01,
                new BigDecimal("80")));
        log.info("step13: reserved 80 pcs from fg batch {} for SO item {}", fgBatchId, soItemId);

        dispatchService.create(new CreateDispatchRequest(
                soId,
                LocalDate.now(),
                "DEMO TRANSPORT CO", "MH-12-AB-1234", "LR-DEMO-001",
                "AutoIncorp Technologies, Survey 18, Peenya, Bengaluru 560058",
                "INV-DEMO-001",
                List.of(new CreateDispatchRequest.DispatchLineRequest(
                        soItemId, fgBatchId,
                        new BigDecimal("80"),
                        WAREHOUSE_ID, LOC_FG01,
                        new BigDecimal("14.5000"))),
                "Demo dispatch — full order fulfilled"));
        log.info("step13: dispatch created — demo workflow complete");
    }

    // ── Step 15: a late production run and an inspection nobody has picked up (DESIGN §11.4) ─────────────────

    /**
     * A second production order whose first operation is under way and whose expected completion date has passed
     * (the PRODUCTION_DELAY alert), with an in-process inspection raised on that operation that has waited longer
     * than the pending-inspection limit (the PENDING_INSPECTION alert). Everything goes through the real services;
     * the only direct writes are the two dates that cannot be set to the past by a service: the expected completion
     * date and the inspection's creation time. Demo profile only, and only on the first load.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void step15_lateRunAndWaitingInspection(long productId, long bomId) {
        try {
            ProductionOrderDto order = productionOrderService.create(new CreateProductionOrderRequest(
                    productId, bomId, new BigDecimal("50"), LocalDate.now(), LocalDate.now().plusDays(5),
                    "LINE-B", null, null, null, (short) 3, "Demo late run — 50 pcs, behind schedule"));
            order = productionOrderService.approve(order.id());
            order = productionOrderService.release(order.id());
            // the sequence rule: coiling and grinding are done, the inspection step (the routing's inspection point)
            // is the one under way, and a sample from it is waiting for a decision
            var operations = order.operations();
            for (int i = 0; i < operations.size() - 1; i++) {
                Long operationId = operations.get(i).id();
                opExecService.startOperation(operationId, null);
                opExecService.recordOutput(new RecordOutputRequest(
                        operationId,
                        new BigDecimal("50"), BigDecimal.ZERO, BigDecimal.ZERO,
                        null, null, null, null, "B", "Demo late run"));
                opExecService.completeOperation(operationId);
            }
            Long inspectionOperationId = operations.get(operations.size() - 1).id();
            opExecService.startOperation(inspectionOperationId, null);

            var inspection = finalInspectionService.createInProcess(
                    new com.mfg.ims.quality.dto.CreateInProcessInspectionRequest(
                            inspectionOperationId, new BigDecimal("10")));

            int delayedOrders = jdbc.update(
                    "UPDATE ims.production_orders SET expected_completion_date = ? WHERE id = ?",
                    java.sql.Date.valueOf(LocalDate.now().minusDays(1)), order.id());
            int agedInspections = jdbc.update(
                    "UPDATE ims.quality_inspections SET created_at = now() - interval '4 days' WHERE id = ?",
                    inspection.id());
            log.info("step15: late order {} ({} row) and waiting inspection {} ({} row) seeded",
                    order.orderNumber(), delayedOrders, inspection.id(), agedInspections);
        } catch (Exception e) {
            log.warn("step15: late run and waiting inspection not seeded: {}", e.getMessage());
        }
    }

    // ── Step 14: a visible alert (DESIGN §11.4) ───────────────────────────────

    /**
     * A second wire grade that is on the books with a minimum stock but none on hand, so the low-stock check has
     * something to find, then one alert sweep now that the demo data exists. The scheduled sweep ran once at startup,
     * before any of this existed, and would not run again for 15 minutes.
     */
    public void step14_alertConditions(long supplierId) {
        try {
            if (materialRepo.findByMaterialCode(LOW_STOCK_MATERIAL_CODE).isEmpty()) {
                materialService.create(new SaveMaterialRequest(
                        LOW_STOCK_MATERIAL_CODE, "Music Wire 2.0mm (low stock demo)",
                        "WIRE_ROD", "MW-A", new BigDecimal("2.000"),
                        "KG", supplierId,
                        new BigDecimal("100"), new BigDecimal("200"),
                        new BigDecimal("1000"), new BigDecimal("120.0000"),
                        null, "No stock on hand: raises the low-stock alert in the demo", null));
                log.info("step14: low-stock demo material created");
            }
            alertSweepJob.runSweep();
            log.info("step14: alert sweep run");
        } catch (Exception e) {
            log.warn("step14: alert conditions not seeded: {}", e.getMessage());
        }
    }

    // ── Security context helper ───────────────────────────────────────────────

    private void installUserContext(long userId, String username, Set<String> authorities) {
        CachedUser user = new CachedUser(userId, username, true, 0, Set.of(), authorities);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private void installAdminContext(long adminId) {
        this.adminUserId = adminId;
        CachedUser admin = new CachedUser(
                adminId, DEMO_ADMIN_USERNAME, true, 0,
                Set.of("ADMIN"),
                Set.of("ADMIN", "USER_VIEW", "USER_CREATE", "USER_UPDATE", "SETTINGS_MANAGE",
                        "MASTERDATA_MANAGE", "MATERIAL_VIEW", "SUPPLIER_MANAGE", "CUSTOMER_MANAGE",
                        "WAREHOUSE_MANAGE", "LOCATION_MANAGE", "MACHINE_MANAGE", "OPERATION_MANAGE",
                        "REJECTION_REASON_MANAGE", "PRODUCT_VIEW", "PRODUCT_CREATE", "PRODUCT_UPDATE",
                        "DRAWING_MANAGE", "BOM_VIEW", "BOM_CREATE", "BOM_UPDATE", "BOM_REVIEW",
                        "BOM_APPROVE", "ROUTING_MANAGE",
                        "INVENTORY_VIEW", "INVENTORY_VIEW_ALL", "INVENTORY_RECEIVE",
                        "INVENTORY_ISSUE", "INVENTORY_TRANSFER", "INVENTORY_RETURN",
                        "INVENTORY_ADJUST", "ADJUST_APPROVE", "ADJUST_APPROVE_ADMIN",
                        "PURCHASE_VIEW", "PURCHASE_CREATE", "PURCHASE_APPROVE",
                        "PRODUCTION_VIEW", "PRODUCTION_CREATE", "PRODUCTION_UPDATE",
                        "PRODUCTION_APPROVE", "PRODUCTION_RELEASE", "PRODUCTION_EXECUTE",
                        "PRODUCTION_CLOSE", "DOWNTIME_RECORD",
                        "QUALITY_VIEW", "QUALITY_INSPECT", "QUALITY_APPROVE", "QUALITY_REJECT",
                        "SALES_VIEW", "SALES_CREATE", "SALES_APPROVE",
                        "DISPATCH_VIEW", "DISPATCH_CREATE", "DISPATCH_APPROVE"));
        var auth = new UsernamePasswordAuthenticationToken(admin, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
