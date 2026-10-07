package com.springmfg.ims.lookup;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Read-only shapes for the dropdowns on the create forms. */
public final class LookupDtos {

    private LookupDtos() {}

    public record Option(Long id, String code, String name) {}

    public record ItemOption(Long id, String code, String name, String uom) {}

    public record LocationOption(Long id, Long warehouseId, String code, String name, String locationType) {}

    public record ReceivablePoItem(Long poItemId, Integer lineNo, Long materialId, String materialCode,
            String materialName, String uom, BigDecimal orderedQty, BigDecimal receivedQty, BigDecimal remainingQty) {}

    public record ReceivablePo(Long id, String poNumber, Long supplierId, String supplierName, String status,
            List<ReceivablePoItem> items) {}

    public record ActiveBom(Long id, String bomNumber, String revision, BigDecimal baseQuantity) {}

    public record FgStock(Long fgBatchId, String batchNumber, Long productId, Long warehouseId, Long locationId,
            String locationCode, BigDecimal onHand, BigDecimal reserved, BigDecimal available) {}

    public record DispatchableItem(Long salesOrderItemId, Integer lineNo, Long productId, String productCode,
            String productName, BigDecimal orderedQty, BigDecimal dispatchedQty, BigDecimal remainingQty,
            BigDecimal unitPrice) {}

    /** An approved sales order a pick wave can take in; no line detail, so the wave planner needs no sales-read rights. */
    public record PickableOrder(Long id, String orderNumber, String customerName) {}

    public record DispatchableOrder(Long id, String orderNumber, Long customerId, String customerName,
            String deliveryAddress, String status, List<DispatchableItem> items) {}
    /** A posted goods receipt that still has value open to invoice; what a supplier invoice can claim against it. */
    public record InvoiceableReceipt(Long id, String grNumber, LocalDate receiptDate, String poNumber, Long supplierId,
            BigDecimal receivedValue, BigDecimal receivedTax, BigDecimal invoicedValue, BigDecimal openValue) {}

    /** Raw material in one batch at one location, available to issue; listed first-expiry-first-out. */
    public record MaterialStock(Long batchId, String batchNumber, LocalDate expiryDate, LocalDate receivedDate,
            Long locationId, String locationCode, BigDecimal onHand, BigDecimal reserved, BigDecimal available) {}
}
