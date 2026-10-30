-- V95: GRN-QIR link, GRN pending report, GRN-invoice mismatch report (items 10.3)
SET search_path TO ims;

-- ── GRN–Quality Inspection link ──────────────────────────────────────────────
ALTER TABLE goods_receipt_items
    ADD COLUMN IF NOT EXISTS inspection_id BIGINT REFERENCES quality_inspections(id);

-- ── GRN Pending view ─────────────────────────────────────────────────────────
-- Shows GRNs that are not yet posted or cancelled (e.g. PENDING_APPROVAL)
CREATE OR REPLACE VIEW grn_pending AS
SELECT
    gr.id                       AS grn_id,
    gr.gr_number                AS grn_number,
    po.supplier_id,
    s.name                      AS supplier_name,
    gr.receipt_date,
    gr.status
FROM goods_receipts gr
JOIN purchase_orders  po ON po.id = gr.purchase_order_id
JOIN suppliers        s  ON s.id  = po.supplier_id
WHERE gr.status NOT IN ('CANCELLED', 'POSTED');

-- ── GRN–Invoice Mismatch view ─────────────────────────────────────────────────
-- Shows GRNs whose linked AP invoice value differs from the GR receipt value by > 0.01
CREATE OR REPLACE VIEW grn_invoice_mismatch AS
WITH gr_values AS (
    SELECT  gri.goods_receipt_id,
            COALESCE(SUM(gri.received_qty * poi.rate), 0) AS grn_value
    FROM    goods_receipt_items gri
    JOIN    purchase_order_items poi ON poi.id = gri.po_item_id
    GROUP BY gri.goods_receipt_id
)
SELECT
    gr.id                                       AS grn_id,
    gr.gr_number                                AS grn_number,
    ai.id                                       AS invoice_id,
    ai.invoice_number,
    ai.total_amount                             AS invoice_value,
    COALESCE(gv.grn_value, 0)                   AS grn_value,
    ai.total_amount - COALESCE(gv.grn_value, 0) AS mismatch_amount
FROM goods_receipts gr
JOIN ap_invoices ai ON ai.goods_receipt_id = gr.id
LEFT JOIN gr_values gv ON gv.goods_receipt_id = gr.id
WHERE ABS(ai.total_amount - COALESCE(gv.grn_value, 0)) > 0.01;
