package com.mfg.ims.integration.webhook.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.mfg.ims.common.event.InvoicePaidEvent;
import com.mfg.ims.inventory.domain.InventoryTransaction;
import com.mfg.ims.inventory.event.StockPostedEvent;
import com.mfg.ims.production.event.ProductionOrderClosedEvent;

/**
 * Turns domain events into webhook outbox rows once the originating transaction has committed. A failure here is
 * logged and never reaches the publisher: integration problems must not undo or block a stock movement, an invoice
 * receipt or a production close (PLAN R19-7.1). The trade-off, recorded in docs/decisions.md, is that an event can be
 * lost if the process dies between the commit and the enqueue.
 */
@Component
public class WebhookEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventListener.class);

    private final WebhookEnqueueService enqueue;

    public WebhookEventListener(WebhookEnqueueService enqueue) {
        this.enqueue = enqueue;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockPosted(StockPostedEvent event) {
        InventoryTransaction t = event.transaction();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("transactionId", t.getId());
        data.put("transactionNumber", t.getTransactionNumber());
        data.put("transactionDate", String.valueOf(t.getTransactionDate()));
        data.put("transactionType", t.getTransactionType());
        data.put("itemType", t.getItemType());
        data.put("materialId", t.getMaterialId());
        data.put("productId", t.getProductId());
        data.put("warehouseId", t.getWarehouseId());
        data.put("locationId", t.getLocationId());
        data.put("stockStatus", t.getStockStatus());
        data.put("quantity", t.getQuantity());
        data.put("uom", t.getUom());
        data.put("unitCost", t.getUnitCost());
        data.put("referenceType", t.getReferenceType());
        data.put("referenceId", t.getReferenceId());
        data.put("referenceNumber", t.getReferenceNumber());
        safely(WebhookEventTypes.STOCK_POSTED, WebhookEventTypes.STOCK_POSTED + ":" + t.getId(), data);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductionOrderClosed(ProductionOrderClosedEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderType", "PRODUCTION");
        data.put("orderId", event.productionOrderId());
        safely(WebhookEventTypes.ORDER_COMPLETED,
                WebhookEventTypes.ORDER_COMPLETED + ":PRODUCTION:" + event.productionOrderId(), data);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoicePaid(InvoicePaidEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceId", event.invoiceId());
        data.put("invoiceNumber", event.invoiceNumber());
        data.put("customerId", event.customerId());
        data.put("totalAmount", event.totalAmount());
        data.put("currency", event.currencyCode());
        safely(WebhookEventTypes.INVOICE_PAID, WebhookEventTypes.INVOICE_PAID + ":" + event.invoiceId(), data);
    }

    private void safely(String type, String key, Map<String, Object> data) {
        try {
            enqueue.enqueue(type, key, data);
        } catch (RuntimeException e) {
            log.error("Could not queue webhook event {}: {}", key, e.toString());
        }
    }
}
