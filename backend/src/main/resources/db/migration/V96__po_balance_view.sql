-- V96: PO balance view (item 10.7)
SET search_path TO ims;

-- ── PO Balance view ───────────────────────────────────────────────────────────
-- Shows ordered qty, received qty, and remaining balance per PO line
CREATE OR REPLACE VIEW po_balance AS
SELECT
    pol.id                                                  AS line_id,
    po.id                                                   AS po_id,
    po.po_number,
    pol.material_id,
    m.name                                                  AS material_name,
    pol.ordered_qty,
    COALESCE(SUM(grl.received_qty), 0)                      AS received_qty,
    pol.ordered_qty - COALESCE(SUM(grl.received_qty), 0)    AS balance_qty,
    pol.rate                                                AS unit_price
FROM purchase_order_items  pol
JOIN purchase_orders       po  ON po.id  = pol.purchase_order_id
JOIN materials             m   ON m.id   = pol.material_id
LEFT JOIN goods_receipt_items grl ON grl.po_item_id = pol.id
WHERE po.status NOT IN ('CANCELLED')
GROUP BY pol.id, po.id, po.po_number, pol.material_id, m.name, pol.ordered_qty, pol.rate;
