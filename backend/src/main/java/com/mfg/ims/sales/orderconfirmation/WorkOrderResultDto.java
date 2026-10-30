package com.mfg.ims.sales.orderconfirmation;

/** Returned by POST /api/sales/order-confirmations/{id}/create-work-order */
public record WorkOrderResultDto(Long workOrderId, String workOrderNumber) {}
