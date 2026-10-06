package com.springmfg.ims.integration.webhook.service;

import java.util.List;

/** The domain events a webhook can subscribe to (INT-002). {@code Ping} is only sent by the "send test" action. */
public final class WebhookEventTypes {

    public static final String STOCK_POSTED = "StockPosted";
    public static final String ORDER_COMPLETED = "OrderCompleted";
    public static final String INVOICE_PAID = "InvoicePaid";
    public static final String PING = "Ping";
    public static final String ALL = "*";

    public static final List<String> SUBSCRIBABLE = List.of(STOCK_POSTED, ORDER_COMPLETED, INVOICE_PAID);

    private WebhookEventTypes() {
    }

    /** True if a subscription with the stored {@code events} value wants {@code eventType}. */
    public static boolean matches(String events, String eventType) {
        if (events == null) {
            return false;
        }
        for (String e : events.split(",")) {
            String t = e.trim();
            if (t.equals(ALL) || t.equals(eventType)) {
                return true;
            }
        }
        return false;
    }
}
