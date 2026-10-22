package com.mfg.ims.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mfg.ims.inventory.dto.OpeningBalanceLine;
import com.mfg.ims.inventory.service.OpeningBalanceService;
import com.mfg.ims.lookup.LookupDtos.MaterialStock;
import com.mfg.ims.lookup.LookupDtos.ReceivablePo;
import com.mfg.ims.material.domain.Material;
import com.mfg.ims.partner.domain.Customer;
import com.mfg.ims.partner.domain.Supplier;
import com.mfg.ims.product.domain.Product;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * Runs every lookup query against real PostgreSQL with data, because the create forms depend on them and a query
 * that names a missing column only fails when it is executed.
 */
class LookupIT extends AbstractIntegrationTest {

    @Autowired LookupService lookups;
    @Autowired OpeningBalanceService openingBalances;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;

    @Test
    @DisplayName("LOOK-1: master-data lookups find a record by code and expose id, code and name")
    void masterDataLookupsFindRecordsByCode() {
        Supplier supplier = td.supplier();
        Customer customer = td.customer();
        Material material = td.material();
        Product product = td.product();

        assertThat(lookups.suppliers(supplier.getSupplierCode()))
                .extracting(LookupDtos.Option::code).containsExactly(supplier.getSupplierCode());
        assertThat(lookups.customers(customer.getCustomerCode()))
                .extracting(LookupDtos.Option::code).containsExactly(customer.getCustomerCode());
        assertThat(lookups.materials(material.getMaterialCode()))
                .singleElement().satisfies(m -> {
                    assertThat(m.id()).isEqualTo(material.getId());
                    assertThat(m.uom()).isEqualTo("KG");
                });
        assertThat(lookups.products(product.getProductCode()))
                .extracting(LookupDtos.ItemOption::id).containsExactly(product.getId());
    }

    @Test
    @DisplayName("LOOK-2: a blank or missing search returns the list instead of failing on a null parameter")
    void blankSearchIsNullSafe() {
        td.supplier();
        assertThat(lookups.suppliers(null)).isNotEmpty();
        assertThat(lookups.suppliers("   ")).isNotEmpty();
        assertThat(lookups.materials(null)).isNotNull();
        assertThat(lookups.products("")).isNotNull();
        assertThat(lookups.customers(null)).isNotNull();
    }

    @Test
    @DisplayName("LOOK-3: warehouses and locations list the active ones, optionally for one warehouse")
    void warehousesAndLocations() {
        assertThat(lookups.warehouses(null)).isNotEmpty();
        Long warehouseId = lookups.warehouses(null).get(0).id();
        assertThat(lookups.locations(warehouseId)).isNotEmpty()
                .allSatisfy(l -> assertThat(l.warehouseId()).isEqualTo(warehouseId));
        assertThat(lookups.locations(null)).isNotEmpty();
    }

    @Test
    @DisplayName("LOOK-4: the active BOM of a product is returned only when one is active")
    void activeBomOnlyWhenActive() {
        Product product = td.product();
        assertThat(lookups.activeBom(product.getId())).isEmpty();

        jdbc.update("""
                INSERT INTO ims.bom (bom_number, revision, product_id, status, base_quantity, created_by)
                VALUES (?, 'A', ?, 'ACTIVE', 1, 1)
                """, td.code("BOMX"), product.getId());
        assertThat(lookups.activeBom(product.getId())).isPresent()
                .get().extracting(LookupDtos.ActiveBom::revision).isEqualTo("A");
    }

    @Test
    @DisplayName("LOOK-5: a sent purchase order is receivable until every line is received")
    void receivablePurchaseOrders() {
        Supplier supplier = td.supplier();
        Material material = td.material();
        String poNumber = td.code("POX");
        Long poId = jdbc.queryForObject("""
                INSERT INTO ims.purchase_orders (po_number, supplier_id, po_date, status, created_by)
                VALUES (?, ?, CURRENT_DATE, 'SENT_TO_SUPPLIER', 1) RETURNING id
                """, Long.class, poNumber, supplier.getId());
        jdbc.update("""
                INSERT INTO ims.purchase_order_items (purchase_order_id, line_no, material_id, ordered_qty,
                                                      received_qty, rate, line_total, created_by)
                VALUES (?, 1, ?, 100, 40, 85, 8500, 1)
                """, poId, material.getId());

        List<ReceivablePo> receivable = lookups.receivablePurchaseOrders();
        assertThat(receivable).anySatisfy(po -> {
            assertThat(po.poNumber()).isEqualTo(poNumber);
            assertThat(po.supplierName()).isEqualTo("IT Supplier");
            assertThat(po.items()).singleElement().satisfies(i -> {
                assertThat(i.remainingQty()).isEqualByComparingTo(new BigDecimal("60"));
                assertThat(i.materialCode()).isEqualTo(material.getMaterialCode());
            });
        });

        jdbc.update("UPDATE ims.purchase_order_items SET received_qty = ordered_qty WHERE purchase_order_id = ?", poId);
        assertThat(lookups.receivablePurchaseOrders()).noneMatch(po -> po.poNumber().equals(poNumber));
    }

    @Test
    @DisplayName("LOOK-6: finished-goods stock and dispatchable orders execute and return no stock for an unknown product")
    void stockAndDispatchableOrdersExecute() {
        assertThat(lookups.finishedGoodsStock(td.product().getId())).isEmpty();
        assertThat(lookups.finishedGoodsStock(null)).isNotNull();
        assertThat(lookups.dispatchableOrders()).isNotNull();
    }

    @Test
    @DisplayName("LOOK-7: material stock lists available batches earliest expiry first, with reserved stock taken off and quarantine left out")
    void materialStockFollowsTheIssueOrder() {
        Material material = td.material();
        long warehouse = 1L;
        long location = 1L;
        for (String qty : List.of("10.000", "5.000")) {
            openingBalances.loadOpeningBalances(List.of(new OpeningBalanceLine("MATERIAL", material.getId(), null, null,
                    warehouse, location, "AVAILABLE", new BigDecimal(qty), "KG", new BigDecimal("80.0000"), "LOOK-7")));
        }
        openingBalances.loadOpeningBalances(List.of(new OpeningBalanceLine("MATERIAL", material.getId(), null, null,
                warehouse, location, "QUARANTINE", new BigDecimal("7.000"), "KG", new BigDecimal("80.0000"), "LOOK-7")));

        List<Long> batchIds = jdbc.queryForList("""
                SELECT DISTINCT mb.id FROM ims.material_batches mb
                  JOIN ims.inventory_balances b ON b.material_batch_id = mb.id
                 WHERE b.material_id = ? AND b.stock_status = 'AVAILABLE' ORDER BY mb.id
                """, Long.class, material.getId());
        assertThat(batchIds).hasSize(2);
        jdbc.update("UPDATE ims.material_batches SET expiry_date = ? WHERE id = ?",
                LocalDate.now().plusDays(400), batchIds.get(0));
        jdbc.update("UPDATE ims.material_batches SET expiry_date = ? WHERE id = ?",
                LocalDate.now().plusDays(30), batchIds.get(1));
        jdbc.update("UPDATE ims.inventory_balances SET qty_reserved = 2 WHERE material_batch_id = ? AND stock_status = 'AVAILABLE'",
                batchIds.get(1));

        List<MaterialStock> stock = lookups.materialStock(material.getId(), warehouse);

        assertThat(stock).extracting(MaterialStock::batchId).containsExactly(batchIds.get(1), batchIds.get(0));
        assertThat(stock.get(0).onHand()).isEqualByComparingTo("5.000");
        assertThat(stock.get(0).reserved()).isEqualByComparingTo("2.000");
        assertThat(stock.get(0).available()).isEqualByComparingTo("3.000");
        assertThat(stock.get(1).available()).isEqualByComparingTo("10.000");
        assertThat(stock.get(0).locationCode()).isNotBlank();
        assertThat(lookups.materialStock(material.getId(), 999999L)).isEmpty();
        assertThat(lookups.materialStock(999999L, warehouse)).isEmpty();
    }
}
