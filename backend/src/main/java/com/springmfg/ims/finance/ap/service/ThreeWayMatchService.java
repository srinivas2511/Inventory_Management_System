package com.springmfg.ims.finance.ap.service;

import java.math.BigDecimal;

import jakarta.persistence.EntityManager;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.settings.SystemSettingService;

/**
 * Reads the goods receipt an AP invoice bills and applies {@link ThreeWayMatchCalculator} to it. The receipt row is
 * locked for the rest of the transaction, so two invoices for the same receipt cannot both claim its open value.
 *
 * <p>Procurement tables are read with SQL rather than through procurement classes: finance sits beside procurement,
 * not on top of it, and the match needs three numbers, not the aggregate.
 */
@Service
public class ThreeWayMatchService {

    private static final String PRICE_TOLERANCE_PCT = "ap.match.price_tolerance_pct";
    private static final String ABSOLUTE_TOLERANCE = "ap.match.absolute_tolerance";

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final SystemSettingService settings;

    public ThreeWayMatchService(JdbcTemplate jdbc, EntityManager entityManager, SystemSettingService settings) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.settings = settings;
    }

    /** What the receipt holds and what is already claimed against it. */
    public record ReceiptPosition(String grNumber, BigDecimal value, BigDecimal tax, BigDecimal invoiced) {
    }

    @Transactional
    public ReceiptPosition lockAndRead(Long goodsReceiptId, Long supplierId, Long excludeInvoiceId) {
        entityManager.flush();
        var header = jdbc.query("""
                SELECT gr.gr_number, gr.status, po.supplier_id
                  FROM ims.goods_receipts gr
                  JOIN ims.purchase_orders po ON po.id = gr.purchase_order_id
                 WHERE gr.id = ?
                   FOR UPDATE OF gr
                """, (rs, i) -> new Object[] {rs.getString(1), rs.getString(2), rs.getLong(3)}, goodsReceiptId);
        if (header.isEmpty()) {
            throw new NotFoundException("GoodsReceipt", goodsReceiptId);
        }
        String grNumber = (String) header.get(0)[0];
        if (!"POSTED".equals(header.get(0)[1])) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Goods receipt " + grNumber + " is " + header.get(0)[1] + " and cannot be invoiced");
        }
        if (!supplierId.equals(header.get(0)[2])) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Goods receipt " + grNumber + " was received from a different supplier than the one on this invoice");
        }

        // The same per-line rounding the goods receipt used when it credited GR/IR (quantity x PO rate, 4 dp).
        BigDecimal[] values = jdbc.queryForObject("""
                SELECT COALESCE(SUM(ROUND(gi.received_qty * pi.rate, 4)), 0),
                       COALESCE(SUM(ROUND(ROUND(gi.received_qty * pi.rate, 4) * pi.tax_pct / 100, 4)), 0)
                  FROM ims.goods_receipt_items gi
                  JOIN ims.purchase_order_items pi ON pi.id = gi.po_item_id
                 WHERE gi.goods_receipt_id = ?
                """, (rs, i) -> new BigDecimal[] {rs.getBigDecimal(1), rs.getBigDecimal(2)}, goodsReceiptId);

        BigDecimal invoiced = jdbc.queryForObject("""
                SELECT COALESCE(SUM(match_cleared_amount), 0)
                  FROM ims.ap_invoices
                 WHERE goods_receipt_id = ?
                   AND status <> 'CANCELLED'
                   AND match_status IN ('MATCHED', 'OVERRIDDEN', 'NOT_REQUIRED')
                   AND id <> ?
                """, BigDecimal.class, goodsReceiptId, excludeInvoiceId == null ? -1L : excludeInvoiceId);
        return new ReceiptPosition(grNumber, values[0], values[1], invoiced);
    }

    public ThreeWayMatchCalculator.Outcome evaluate(ReceiptPosition position, BigDecimal subtotal, BigDecimal tax) {
        return ThreeWayMatchCalculator.evaluate(subtotal, tax, position.value(), position.tax(), position.invoiced(),
                settings.getDecimal(PRICE_TOLERANCE_PCT, BigDecimal.ZERO),
                settings.getDecimal(ABSOLUTE_TOLERANCE, BigDecimal.ZERO));
    }
}
