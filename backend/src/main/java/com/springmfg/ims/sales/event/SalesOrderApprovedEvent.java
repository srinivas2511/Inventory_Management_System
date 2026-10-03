package com.springmfg.ims.sales.event;

/** Published when a sales order is approved, so warehouse picking can be prepared without sales knowing about it. */
public record SalesOrderApprovedEvent(Long salesOrderId) {
}
