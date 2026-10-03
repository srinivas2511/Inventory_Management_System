package com.springmfg.ims.wms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.procurement.event.GoodsReceivedForInspectionEvent;
import com.springmfg.ims.procurement.repository.GoodsReceiptItemRepository;
import com.springmfg.ims.sales.event.SalesOrderApprovedEvent;

/**
 * Prepares put-away and picking work after a goods receipt is posted or a sales order is approved. Each runs after
 * the business transaction has committed, in its own transaction, and never fails the document that triggered it:
 * warehouse planning can always be redone from the transfer order screens.
 */
@Component
public class WmsEventListeners {

    private static final Logger log = LoggerFactory.getLogger(WmsEventListeners.class);

    private final PutAwayService putAway;
    private final PickingService picking;
    private final GoodsReceiptItemRepository receiptItems;
    private final SystemSettingService settings;
    private final TransactionTemplate requiresNew;

    public WmsEventListeners(PutAwayService putAway, PickingService picking, GoodsReceiptItemRepository receiptItems,
                             SystemSettingService settings, PlatformTransactionManager transactionManager) {
        this.putAway = putAway;
        this.picking = picking;
        this.receiptItems = receiptItems;
        this.settings = settings;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGoodsReceived(GoodsReceivedForInspectionEvent event) {
        if (!settings.getBoolean("wms.putaway.auto_create", true)) {
            return;
        }
        try {
            requiresNew.executeWithoutResult(s -> receiptItems.findById(event.goodsReceiptItemId())
                    .map(i -> i.getGoodsReceipt().getId())
                    .ifPresent(putAway::createPutAwayTasks));
        } catch (RuntimeException e) {
            log.warn("Put-away planning failed for goods receipt item {}: {}", event.goodsReceiptItemId(), e.toString());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSalesOrderApproved(SalesOrderApprovedEvent event) {
        if (!settings.getBoolean("wms.picking.auto_create", true)) {
            return;
        }
        try {
            requiresNew.executeWithoutResult(s -> picking.createPickingTasks(event.salesOrderId(), null));
        } catch (RuntimeException e) {
            log.warn("Picking planning failed for sales order {}: {}", event.salesOrderId(), e.toString());
        }
    }
}
