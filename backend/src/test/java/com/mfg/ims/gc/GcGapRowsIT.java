package com.mfg.ims.gc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.inventory.domain.FgBatch;
import com.mfg.ims.inventory.dto.PostingCommand;
import com.mfg.ims.inventory.repository.FgBatchRepository;
import com.mfg.ims.inventory.repository.InventoryBalanceRepository;
import com.mfg.ims.inventory.repository.MaterialBatchRepository;
import com.mfg.ims.inventory.service.BatchService;
import com.mfg.ims.inventory.service.InventoryPostingService;
import com.mfg.ims.inventory.service.NegativeStockOverrideService;
import com.mfg.ims.inventory.dto.SetNegativeStockRequest;
import com.mfg.ims.material.dto.SavePurchasingInfoRequest;
import com.mfg.ims.material.dto.SaveUomConversionRequest;
import com.mfg.ims.material.service.MaterialPurchasingInfoService;
import com.mfg.ims.material.service.SupplierApprovedMaterialService;
import com.mfg.ims.material.service.UomConversionService;
import com.mfg.ims.procurement.dto.CreateGrRequest;
import com.mfg.ims.procurement.dto.CreatePoRequest;
import com.mfg.ims.procurement.service.GoodsReceiptService;
import com.mfg.ims.procurement.service.PurchaseOrderService;
import com.mfg.ims.quality.dto.CreateFinalInspectionRequest;
import com.mfg.ims.quality.dto.QualityInspectionDto;
import com.mfg.ims.quality.repository.QualityInspectionItemRepository;
import com.mfg.ims.quality.service.FinalInspectionService;
import com.mfg.ims.sales.dto.AddSalesOrderItemRequest;
import com.mfg.ims.sales.dto.CreateSalesOrderRequest;
import com.mfg.ims.sales.dto.SaveCustomerSpecOverrideRequest;
import com.mfg.ims.sales.dto.SaveCustomerProductSpecRequest;
import com.mfg.ims.sales.service.CustomerApprovedProductService;
import com.mfg.ims.sales.service.CustomerProductSpecService;
import com.mfg.ims.sales.service.CustomerSpecOverrideService;
import com.mfg.ims.sales.service.SalesOrderService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * GC-1 .. GC-5 (requirements-traceability gap rows): MDM-003 alternative units, MDM-004/020/021 purchasing info and
 * approved lists, INV-004 per-item negative stock, SAL-006 customer spec overrides. GC-4 (PDF documents) is covered by
 * DispatchDocumentRendererTest and GC-6 (CSP) by SecurityHeadersTest. Hand-worked numbers throughout.
 */
@Transactional
class GcGapRowsIT extends AbstractIntegrationTest {

    private static final Long WH = 1L;
    private static final Long LOC = 1L;

    @Autowired TestData td;
    @Autowired JdbcTemplate jdbc;
    @Autowired SystemSettingService settings;
    @Autowired UomConversionService uom;
    @Autowired MaterialPurchasingInfoService purchasing;
    @Autowired SupplierApprovedMaterialService approvedMaterials;
    @Autowired CustomerApprovedProductService approvedProducts;
    @Autowired NegativeStockOverrideService negativeStock;
    @Autowired InventoryPostingService posting;
    @Autowired InventoryBalanceRepository balanceRepo;
    @Autowired BatchService batchService;
    @Autowired MaterialBatchRepository materialBatchRepo;
    @Autowired PurchaseOrderService poService;
    @Autowired com.mfg.ims.procurement.repository.PurchaseOrderRepository poRepo;
    @Autowired GoodsReceiptService grService;
    @Autowired SalesOrderService salesOrderService;
    @Autowired CustomerProductSpecService specService;
    @Autowired CustomerSpecOverrideService overrideService;
    @Autowired FinalInspectionService finalService;
    @Autowired FgBatchRepository fgBatchRepo;
    @Autowired QualityInspectionItemRepository itemRepo;

    /** Makes the PO look as if someone else submitted it, so approving it does not trip segregation of duties. */
    private void notMine(Long poId) {
        var entity = poRepo.findById(poId).orElseThrow();
        entity.setSubmittedBy(-99L);
        poRepo.save(entity);
    }

    private String altUom() {
        return jdbc.queryForObject("SELECT code FROM uoms WHERE code <> 'KG' ORDER BY code LIMIT 1", String.class);
    }

    // ── GC-1 / MDM-003 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GC-1: 1 alt unit = 250 KG converts 2 alt = 500.000 KG and back, and rounds HALF_UP to three decimals")
    void conversionMathIsExactAndRounded() {
        var m = td.material();
        String alt = altUom();
        uom.save(m.getId(), new SaveUomConversionRequest(alt, new BigDecimal("250")));

        assertThat(uom.toBase(m.getId(), new BigDecimal("2"), alt)).isEqualByComparingTo("500.000");
        assertThat(uom.fromBase(m.getId(), new BigDecimal("500"), alt)).isEqualByComparingTo("2.000");
        assertThat(uom.toBase(m.getId(), new BigDecimal("7.5"), "KG")).isEqualByComparingTo("7.5");
        assertThat(uom.toBase(m.getId(), new BigDecimal("7.5"), null)).isEqualByComparingTo("7.5");

        uom.save(m.getId(), new SaveUomConversionRequest(alt, new BigDecimal("0.333333")));
        assertThat(uom.toBase(m.getId(), new BigDecimal("10"), alt)).isEqualByComparingTo("3.333");   // 3.33333 -> 3.333
        assertThat(uom.toBase(m.getId(), new BigDecimal("3"), alt)).isEqualByComparingTo("1.000");    // 0.999999 -> 1.000
        assertThat(uom.list(m.getId())).hasSize(1);
    }

    @Test
    @DisplayName("GC-1: a unit with no conversion, the base unit as an alternative, and an unknown unit are all refused")
    void unusableUnitsAreRefused() {
        var m = td.material();
        assertThatThrownBy(() -> uom.toBase(m.getId(), BigDecimal.TEN, altUom()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("No conversion");
        assertThatThrownBy(() -> uom.save(m.getId(), new SaveUomConversionRequest("KG", BigDecimal.ONE)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already the base unit");
        assertThatThrownBy(() -> uom.save(m.getId(), new SaveUomConversionRequest("NOT-A-UNIT", BigDecimal.ONE)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Unknown unit");
    }

    @Test
    @WithMockUser(authorities = {"PURCHASE_CREATE", "PURCHASE_APPROVE", "INVENTORY_RECEIVE"})
    @DisplayName("GC-1: a goods receipt entered in an alternative unit books the converted base quantity (2 x 25 = 50 KG)")
    void goodsReceiptInAlternativeUnitPostsBaseQuantity() {
        var supplier = td.supplier();
        var m = td.material();
        String alt = altUom();
        uom.save(m.getId(), new SaveUomConversionRequest(alt, new BigDecimal("25")));
        var po = poService.create(new CreatePoRequest(supplier.getId(), LocalDate.now(), LocalDate.now().plusDays(7),
                "NET30", null, null, List.of(new CreatePoRequest.PoLineRequest(m.getId(), new BigDecimal("100.000"),
                        new BigDecimal("50.0000"), new BigDecimal("18.00")))));
        po = poService.submit(po.id());
        notMine(po.id());
        po = poService.approve(po.id());

        var gr = grService.receive(new CreateGrRequest(po.id(), LocalDate.now(), WH, "CH-GC1", "MH01AB0001",
                List.of(new CreateGrRequest.GrLineRequest(po.items().get(0).id(), new BigDecimal("2"), LOC,
                        "HEAT-GC1", "CERT-GC1", null, true, alt))));

        var batch = materialBatchRepo.findById(gr.items().get(0).materialBatchId()).orElseThrow();
        assertThat(batch.getReceivedQty()).isEqualByComparingTo("50.000");

        // a unit without a conversion is refused, and nothing is booked
        var m2 = td.material();
        var po2 = poService.create(new CreatePoRequest(supplier.getId(), LocalDate.now(), LocalDate.now().plusDays(7),
                "NET30", null, null, List.of(new CreatePoRequest.PoLineRequest(m2.getId(), new BigDecimal("10.000"),
                        new BigDecimal("50.0000"), BigDecimal.ZERO))));
        po2 = poService.submit(po2.id());
        notMine(po2.id());
        var approved2 = poService.approve(po2.id());
        assertThatThrownBy(() -> grService.receive(new CreateGrRequest(approved2.id(), LocalDate.now(), WH, "CH-GC1b",
                "MH01AB0002", List.of(new CreateGrRequest.GrLineRequest(approved2.items().get(0).id(),
                        new BigDecimal("1"), LOC, "H", "C", null, true, alt)))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("No conversion");
    }

    // ── GC-2 / MDM-004, MDM-020, MDM-021 ──────────────────────────────────────

    @Test
    @DisplayName("GC-2: purchasing info keeps the preferred supplier, tax code and a purchase unit that has a conversion")
    void purchasingInfoNeedsAConversionForANonBaseUnit() {
        var m = td.material();
        var s = td.supplier();
        String alt = altUom();
        assertThatThrownBy(() -> purchasing.save(m.getId(), new SavePurchasingInfoRequest(s.getId(), alt, "IGST18")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Define the conversion");

        uom.save(m.getId(), new SaveUomConversionRequest(alt, new BigDecimal("25")));
        var saved = purchasing.save(m.getId(), new SavePurchasingInfoRequest(s.getId(), alt, "IGST18"));
        assertThat(saved.preferredSupplierId()).isEqualTo(s.getId());
        assertThat(saved.purchaseUom()).isEqualTo(alt);
        assertThat(saved.purchaseTaxCode()).isEqualTo("IGST18");
        assertThat(purchasing.get(m.getId()).baseUom()).isEqualTo("KG");

        var cleared = purchasing.save(m.getId(), new SavePurchasingInfoRequest(null, "", " "));
        assertThat(cleared.preferredSupplierId()).isNull();
        assertThat(cleared.purchaseUom()).isNull();
        assertThat(cleared.purchaseTaxCode()).isNull();
    }

    @Test
    @WithMockUser(authorities = {"PURCHASE_CREATE"})
    @DisplayName("GC-2: the approved-materials list is held to only when the setting is on, and a replace is exact")
    void supplierApprovedMaterialsAreEnforcedBySetting() {
        var s = td.supplier();
        var approved = td.material();
        var other = td.material();
        var list = approvedMaterials.replace(s.getId(), List.of(approved.getId()));
        assertThat(list).extracting(i -> i.id()).containsExactly(approved.getId());

        // setting off (default): any material is accepted
        approvedMaterials.assertMaySupply(s.getId(), other.getId());

        settings.update(SupplierApprovedMaterialService.ENFORCE_SETTING, "true");
        try {
            approvedMaterials.assertMaySupply(s.getId(), approved.getId());
            assertThatThrownBy(() -> approvedMaterials.assertMaySupply(s.getId(), other.getId()))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("approved materials list");
            // through the purchase order itself
            assertThatThrownBy(() -> poService.create(new CreatePoRequest(s.getId(), LocalDate.now(), null, null, null,
                    null, List.of(new CreatePoRequest.PoLineRequest(other.getId(), BigDecimal.TEN,
                            new BigDecimal("5.0000"), BigDecimal.ZERO)))))
                    .isInstanceOf(BusinessRuleException.class);
            var po = poService.create(new CreatePoRequest(s.getId(), LocalDate.now(), null, null, null, null,
                    List.of(new CreatePoRequest.PoLineRequest(approved.getId(), BigDecimal.TEN,
                            new BigDecimal("5.0000"), BigDecimal.ZERO))));
            assertThat(po.status()).isEqualTo("DRAFT");
        } finally {
            settings.update(SupplierApprovedMaterialService.ENFORCE_SETTING, "false");
        }

        // replacing with another set removes the old entry and adds the new one
        approvedMaterials.replace(s.getId(), List.of(other.getId()));
        assertThat(approvedMaterials.list(s.getId())).extracting(i -> i.id()).containsExactly(other.getId());
    }

    @Test
    @WithMockUser(authorities = {"SALES_CREATE"})
    @DisplayName("GC-2: the approved-products list is held to only when the setting is on")
    void customerApprovedProductsAreEnforcedBySetting() {
        var c = td.customer();
        var approved = td.product();
        var other = td.product();
        approvedProducts.replace(c.getId(), List.of(approved.getId()));
        approvedProducts.assertMayBuy(c.getId(), other.getId()); // setting off

        settings.update(CustomerApprovedProductService.ENFORCE_SETTING, "true");
        try {
            approvedProducts.assertMayBuy(c.getId(), approved.getId());
            assertThatThrownBy(() -> approvedProducts.assertMayBuy(c.getId(), other.getId()))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("approved products list");
            assertThatThrownBy(() -> salesOrderService.create(new CreateSalesOrderRequest(c.getId(), LocalDate.now(),
                    null, null, null, null, List.of(new AddSalesOrderItemRequest(other.getId(), BigDecimal.TEN,
                            new BigDecimal("9.0000"), null, null)))))
                    .isInstanceOf(BusinessRuleException.class);
            var so = salesOrderService.create(new CreateSalesOrderRequest(c.getId(), LocalDate.now(), null, null,
                    null, null, List.of(new AddSalesOrderItemRequest(approved.getId(), BigDecimal.TEN,
                            new BigDecimal("9.0000"), null, null))));
            assertThat(so.items()).hasSize(1);
        } finally {
            settings.update(CustomerApprovedProductService.ENFORCE_SETTING, "false");
        }
    }

    // ── GC-3 / INV-004 ────────────────────────────────────────────────────────

    private Long batchWith(Long materialId, String qty) {
        return batchService.createMaterialBatch(materialId, null, null, null, null, null, null,
                new BigDecimal(qty), null, null, new BigDecimal("10.0000"), "AVAILABLE").getId();
    }

    private void receive(Long materialId, Long batchId, String qty) {
        posting.post(new PostingCommand("GOODS_RECEIPT", "MATERIAL", materialId, null, batchId, null, WH, LOC,
                "AVAILABLE", new BigDecimal(qty), "KG", new BigDecimal("10.0000"), "GOODS_RECEIPT", 1L,
                "GC3-" + System.nanoTime(), "receipt"));
    }

    private void issue(Long materialId, Long batchId, String qty) {
        posting.post(new PostingCommand("MATERIAL_ISSUE", "MATERIAL", materialId, null, batchId, null, WH, LOC,
                "AVAILABLE", new BigDecimal(qty), "KG", null, "MATERIAL_ISSUE", 1L, "GC3-" + System.nanoTime(),
                "issue"));
    }

    @Test
    @DisplayName("GC-3: stock cannot go negative by default; an administrator override lets ONE item do so, audited")
    void perItemNegativeStockOverride() {
        var m = td.material();
        var other = td.material();
        Long b1 = batchWith(m.getId(), "1");
        Long b2 = batchWith(other.getId(), "1");
        receive(m.getId(), b1, "10.000");
        receive(other.getId(), b2, "10.000");

        // default: 10 on hand, issue 30 -> refused, balance unchanged
        assertThatThrownBy(() -> issue(m.getId(), b1, "30.000")).isInstanceOf(BusinessRuleException.class);
        assertThat(onHand(m.getId(), b1)).isEqualByComparingTo("10.000");

        var dto = negativeStock.setForMaterial(m.getId(), true, "Wire arrives after the shift; line must not stop");
        assertThat(dto.allowed()).isTrue();
        assertThat(negativeStock.list()).extracting(i -> i.code()).contains(m.getMaterialCode())
                .doesNotContain(other.getMaterialCode());

        issue(m.getId(), b1, "30.000");                                  // 10 - 30 = -20
        assertThat(onHand(m.getId(), b1)).isEqualByComparingTo("-20.000");
        assertThat(audit("NEGATIVE_STOCK_OVERRIDE", "MATERIAL", String.valueOf(m.getId()))).isEqualTo(1);
        assertThat(audit("NEGATIVE_STOCK_ALLOWED", "INVENTORY_BALANCE", "MATERIAL:" + m.getId())).isEqualTo(1);

        // the override is per item: the other material is still refused
        assertThatThrownBy(() -> issue(other.getId(), b2, "30.000")).isInstanceOf(BusinessRuleException.class);

        // switching it off again restores the rule and is audited too
        negativeStock.setForMaterial(m.getId(), false, "Supply normalised");
        assertThat(audit("NEGATIVE_STOCK_OVERRIDE", "MATERIAL", String.valueOf(m.getId()))).isEqualTo(2);
        assertThatThrownBy(() -> issue(m.getId(), b1, "1.000")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("GC-3: an override needs a reason and an explicit decision")
    void overrideRequestNeedsAReason() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var v = factory.getValidator();
            assertThat(v.validate(new SetNegativeStockRequest(true, " "))).isNotEmpty();
            assertThat(v.validate(new SetNegativeStockRequest(null, "reason"))).isNotEmpty();
            assertThat(v.validate(new SetNegativeStockRequest(true, "reason"))).isEmpty();
        }
    }

    private BigDecimal onHand(Long materialId, Long batchId) {
        return balanceRepo.findBalance(WH, LOC, "MATERIAL", materialId, null, batchId, null, "AVAILABLE")
                .orElseThrow().getQtyOnHand();
    }

    private int audit(String action, String entity, String entityId) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = ? AND entity = ? AND entity_id = ?",
                Integer.class, action, entity, entityId);
    }

    // ── GC-5 / SAL-006 ────────────────────────────────────────────────────────

    private Long finalPlan(Long productId, String parameter) {
        Long planId = jdbc.queryForObject("INSERT INTO inspection_plans (plan_code, inspection_type, product_id) "
                + "VALUES (?, 'FINAL', ?) RETURNING id", Long.class, td.code("FP"), productId);
        // standard limits 2.90 .. 3.10
        jdbc.update("INSERT INTO inspection_plan_parameters (plan_id, seq, parameter_name, nominal, lower_limit, "
                + "upper_limit, unit) VALUES (?, 1, ?, 3.0000, 2.9000, 3.1000, 'mm')", planId, parameter);
        return planId;
    }

    private Long productionOrder(Long productId, Long salesOrderId) {
        var bom = td.draftBom(productId);
        return jdbc.queryForObject("INSERT INTO production_orders (order_number, product_id, bom_id, planned_qty, "
                + "sales_order_id) VALUES (?, ?, ?, 100, ?) RETURNING id", Long.class, td.code("PO"), productId,
                bom.getId(), salesOrderId);
    }

    @Test
    @WithMockUser(authorities = {"SALES_CREATE", "SALES_UPDATE", "QUALITY_INSPECT"})
    @DisplayName("GC-5: the customer's own limits judge a 3.05 reading in an order made for them; standard limits pass it")
    void customerOverrideReplacesStandardLimitsDuringFinalInspection() {
        var customer = td.customer();
        var product = td.product();
        String param = "Wire diameter";
        finalPlan(product.getId(), param);
        specService.save(customer.getId(), product.getId(), new SaveCustomerProductSpecRequest("SPEC-9", "DRG-9", "CP-9", null));
        overrideService.save(customer.getId(), product.getId(),
                new SaveCustomerSpecOverrideRequest(param, new BigDecimal("2.98"), new BigDecimal("3.02"), new BigDecimal("3.00")));

        var so = salesOrderService.create(new CreateSalesOrderRequest(customer.getId(), LocalDate.now(), null, null,
                null, null, List.of(new AddSalesOrderItemRequest(product.getId(), BigDecimal.TEN,
                        new BigDecimal("9.0000"), null, null))));
        Long customerOrder = productionOrder(product.getId(), so.id());
        Long stockOrder = productionOrder(product.getId(), null);

        QualityInspectionDto forCustomer = finalService.createFinal(
                new CreateFinalInspectionRequest(batchOf(product, customerOrder).getId(), new BigDecimal("100"), null));
        QualityInspectionDto forStock = finalService.createFinal(
                new CreateFinalInspectionRequest(batchOf(product, stockOrder).getId(), new BigDecimal("100"), null));

        var customerItem = forCustomer.items().get(0);
        assertThat(customerItem.lowerLimit()).isEqualByComparingTo("2.98");
        assertThat(customerItem.upperLimit()).isEqualByComparingTo("3.02");
        assertThat(customerItem.specSource()).isEqualTo("CUSTOMER_SPEC");
        var stockItem = forStock.items().get(0);
        assertThat(stockItem.lowerLimit()).isEqualByComparingTo("2.90");
        assertThat(stockItem.upperLimit()).isEqualByComparingTo("3.10");
        assertThat(stockItem.specSource()).isEqualTo("STANDARD");

        // the same 3.05 reading: outside the customer's 2.98..3.02, inside the standard 2.90..3.10
        var ci = itemRepo.findById(customerItem.id()).orElseThrow();
        ci.setActualValue(new BigDecimal("3.05"));
        ci.computeResult();
        assertThat(ci.getResult()).isEqualTo("FAIL");
        var si = itemRepo.findById(stockItem.id()).orElseThrow();
        si.setActualValue(new BigDecimal("3.05"));
        si.computeResult();
        assertThat(si.getResult()).isEqualTo("PASS");
        assertThat(audit("CUSTOMER_SPEC_APPLIED", "QUALITY_INSPECTION", String.valueOf(forCustomer.id()))).isEqualTo(1);
        assertThat(audit("CUSTOMER_SPEC_APPLIED", "QUALITY_INSPECTION", String.valueOf(forStock.id()))).isZero();

        // removing the override puts the standard limits back for the next inspection
        overrideService.deactivate(customer.getId(), product.getId(), param);
        assertThat(overrideService.list(customer.getId(), product.getId())).isEmpty();
    }

    @Test
    @DisplayName("GC-5: an override needs at least one limit, in order, and an existing customer spec")
    void overrideValidation() {
        var customer = td.customer();
        var product = td.product();
        assertThatThrownBy(() -> overrideService.save(customer.getId(), product.getId(),
                new SaveCustomerSpecOverrideRequest("Wire diameter", new BigDecimal("1"), new BigDecimal("2"), null)))
                .hasMessageContaining("CustomerProductSpec");
        specService.save(customer.getId(), product.getId(), new SaveCustomerProductSpecRequest("S", "D", "P", null));
        assertThatThrownBy(() -> overrideService.save(customer.getId(), product.getId(),
                new SaveCustomerSpecOverrideRequest("Wire diameter", null, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("lower limit, an upper limit");
        assertThatThrownBy(() -> overrideService.save(customer.getId(), product.getId(),
                new SaveCustomerSpecOverrideRequest("Wire diameter", new BigDecimal("3.1"), new BigDecimal("2.9"), null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cannot be above");
    }

    private FgBatch batchOf(com.mfg.ims.product.domain.Product product, Long productionOrderId) {
        FgBatch b = new FgBatch();
        b.setBatchNumber(td.code("FGB"));
        b.setProduct(product);
        b.setProductionOrderId(productionOrderId);
        b.setProducedQty(new BigDecimal("100.000"));
        b.setProducedOn(LocalDate.now());
        b.setQualityStatus("QUALITY_PENDING");
        b.setUnitCost(new BigDecimal("50.0000"));
        return fgBatchRepo.save(b);
    }
}
