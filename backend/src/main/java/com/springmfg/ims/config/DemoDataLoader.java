package com.springmfg.ims.config;

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

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.engineering.bom.BomDto;
import com.springmfg.ims.engineering.bom.BomItemRequest;
import com.springmfg.ims.engineering.bom.BomService;
import com.springmfg.ims.engineering.bom.CreateBomRequest;
import com.springmfg.ims.engineering.routing.RoutingService;
import com.springmfg.ims.engineering.routing.SaveRoutingStepRequest;
import com.springmfg.ims.iam.domain.Role;
import com.springmfg.ims.iam.domain.User;
import com.springmfg.ims.iam.repository.RoleRepository;
import com.springmfg.ims.iam.repository.UserRepository;
import com.springmfg.ims.inventory.dto.OpeningBalanceLine;
import com.springmfg.ims.inventory.service.OpeningBalanceService;
import com.springmfg.ims.material.dto.MaterialDto;
import com.springmfg.ims.material.dto.SaveMaterialRequest;
import com.springmfg.ims.material.repository.MaterialRepository;
import com.springmfg.ims.material.service.MaterialService;
import com.springmfg.ims.partner.dto.SaveCustomerRequest;
import com.springmfg.ims.partner.dto.SaveSupplierRequest;
import com.springmfg.ims.partner.repository.CustomerRepository;
import com.springmfg.ims.partner.repository.SupplierRepository;
import com.springmfg.ims.partner.service.CustomerService;
import com.springmfg.ims.partner.service.SupplierService;
import com.springmfg.ims.procurement.dto.CreatePoRequest;
import com.springmfg.ims.procurement.dto.PurchaseOrderDto;
import com.springmfg.ims.procurement.repository.PurchaseOrderItemRepository;
import com.springmfg.ims.procurement.service.PurchaseOrderService;
import com.springmfg.ims.product.dto.ProductDto;
import com.springmfg.ims.product.dto.SaveProductRequest;
import com.springmfg.ims.product.repository.ProductRepository;
import com.springmfg.ims.product.service.ProductService;
import com.springmfg.ims.production.dto.CreateIssueRequest;
import com.springmfg.ims.production.dto.CreateProductionOrderRequest;
import com.springmfg.ims.production.dto.ProductionOrderDto;
import com.springmfg.ims.production.dto.RecordOutputRequest;
import com.springmfg.ims.production.repository.ProductionOrderRepository;
import com.springmfg.ims.production.service.MaterialIssueService;
import com.springmfg.ims.production.service.OperationExecutionService;
import com.springmfg.ims.production.service.ProductionOrderService;
import com.springmfg.ims.quality.dto.FinalInspectionDecisionRequest;
import com.springmfg.ims.quality.dto.QualityInspectionDto;
import com.springmfg.ims.insight.alert.AlertSweepJob;
import com.springmfg.ims.quality.repository.QualityInspectionRepository;
import com.springmfg.ims.quality.service.FinalInspectionService;
import com.springmfg.ims.sales.dto.AddSalesOrderItemRequest;
import com.springmfg.ims.sales.dto.CreateDispatchRequest;
import com.springmfg.ims.sales.dto.CreateSalesOrderRequest;
import com.springmfg.ims.sales.dto.ReserveRequest;
import com.springmfg.ims.sales.dto.SalesOrderDto;
import com.springmfg.ims.sales.service.DispatchService;
import com.springmfg.ims.sales.service.ReservationService;
import com.springmfg.ims.sales.service.SalesOrderService;

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

    // ── seed codes (idempotency guard) ────────────────────────────────────────

    // documented in README.md; this class only exists in the dev/demo profiles, never production
    private static final String DEMO_ADMIN_USERNAME = "admin";
    private static final String DEMO_ADMIN_PASSWORD = "Admin@123";

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
            AlertSweepJob alertSweepJob) {
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
                            null, "Net 30", 7, null));
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
                            null, "Net 45", null));
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

    private void installAdminContext(long adminId) {
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
