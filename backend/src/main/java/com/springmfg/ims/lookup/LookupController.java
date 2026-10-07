package com.springmfg.ims.lookup;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.lookup.LookupDtos.ActiveBom;
import com.springmfg.ims.lookup.LookupDtos.DispatchableOrder;
import com.springmfg.ims.lookup.LookupDtos.FgStock;
import com.springmfg.ims.lookup.LookupDtos.InvoiceableReceipt;
import com.springmfg.ims.lookup.LookupDtos.ItemOption;
import com.springmfg.ims.lookup.LookupDtos.LocationOption;
import com.springmfg.ims.lookup.LookupDtos.MaterialStock;
import com.springmfg.ims.lookup.LookupDtos.Option;
import com.springmfg.ims.lookup.LookupDtos.PickableOrder;
import com.springmfg.ims.lookup.LookupDtos.ReceivablePo;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/lookups")
@Tag(name = "Lookups")
public class LookupController {

    private final LookupService service;

    public LookupController(LookupService service) {
        this.service = service;
    }

    @GetMapping("/suppliers")
    @PreAuthorize("hasAnyAuthority('PURCHASE_CREATE','PURCHASE_VIEW','SUPPLIER_MANAGE','MASTERDATA_MANAGE','AP_MANAGE')")
    public List<Option> suppliers(@RequestParam(required = false) String q) {
        return service.suppliers(q);
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAnyAuthority('SALES_CREATE','SALES_VIEW','CUSTOMER_MANAGE','MASTERDATA_MANAGE','AR_MANAGE')")
    public List<Option> customers(@RequestParam(required = false) String q) {
        return service.customers(q);
    }

    @GetMapping("/warehouses")
    @PreAuthorize("hasAnyAuthority('INVENTORY_RECEIVE','INVENTORY_VIEW','INVENTORY_ISSUE','INVENTORY_ADJUST','DISPATCH_CREATE','RESERVE_FG','WAREHOUSE_MANAGE')")
    public List<Option> warehouses(@RequestParam(required = false) String q) {
        return service.warehouses(q);
    }

    @GetMapping("/locations")
    @PreAuthorize("hasAnyAuthority('INVENTORY_RECEIVE','INVENTORY_VIEW','INVENTORY_ADJUST','DISPATCH_CREATE','RESERVE_FG','WAREHOUSE_MANAGE')")
    public List<LocationOption> locations(@RequestParam(required = false) Long warehouseId) {
        return service.locations(warehouseId);
    }

    @GetMapping("/materials")
    @PreAuthorize("hasAnyAuthority('PURCHASE_CREATE','PURCHASE_VIEW','BOM_CREATE','BOM_UPDATE','MATERIAL_VIEW','MASTERDATA_MANAGE','INVENTORY_ADJUST')")
    public List<ItemOption> materials(@RequestParam(required = false) String q) {
        return service.materials(q);
    }

    @GetMapping("/products")
    @PreAuthorize("hasAnyAuthority('SALES_CREATE','SALES_VIEW','BOM_CREATE','BOM_UPDATE','PRODUCTION_CREATE','PRODUCT_VIEW','INVENTORY_ADJUST')")
    public List<ItemOption> products(@RequestParam(required = false) String q) {
        return service.products(q);
    }

    @GetMapping("/products/{productId}/active-bom")
    @PreAuthorize("hasAnyAuthority('PRODUCTION_CREATE','BOM_VIEW','BOM_CREATE')")
    public ResponseEntity<ActiveBom> activeBom(@PathVariable Long productId) {
        return service.activeBom(productId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/purchase-orders/receivable")
    @PreAuthorize("hasAnyAuthority('INVENTORY_RECEIVE','PURCHASE_VIEW')")
    public List<ReceivablePo> receivablePurchaseOrders() {
        return service.receivablePurchaseOrders();
    }

    @GetMapping("/goods-receipts/invoiceable")
    @PreAuthorize("hasAuthority('AP_MANAGE')")
    public List<InvoiceableReceipt> invoiceableReceipts(@RequestParam(required = false) Long supplierId) {
        return service.invoiceableReceipts(supplierId);
    }

    @GetMapping("/material-stock")
    @PreAuthorize("hasAuthority('INVENTORY_ISSUE')")
    public List<MaterialStock> materialStock(@RequestParam Long materialId, @RequestParam Long warehouseId) {
        return service.materialStock(materialId, warehouseId);
    }

    @GetMapping("/fg-stock")
    @PreAuthorize("hasAnyAuthority('DISPATCH_CREATE','RESERVE_FG','SALES_CREATE','SALES_VIEW')")
    public List<FgStock> finishedGoodsStock(@RequestParam(required = false) Long productId) {
        return service.finishedGoodsStock(productId);
    }

    @GetMapping("/sales-orders/dispatchable")
    @PreAuthorize("hasAnyAuthority('DISPATCH_CREATE','RESERVE_FG','SALES_VIEW')")
    public List<DispatchableOrder> dispatchableOrders() {
        return service.dispatchableOrders();
    }

    @GetMapping("/sales-orders/approved")
    @PreAuthorize("hasAuthority('WAVE_MANAGE')")
    public List<PickableOrder> pickableOrders() {
        return service.pickableOrders();
    }
}
