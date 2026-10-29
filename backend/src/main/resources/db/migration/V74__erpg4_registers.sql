-- ERP-G-4 Additional registers (quotations, PO/SO items, last price, production summary)

SET search_path TO ims;

-- Quotations register view
CREATE OR REPLACE VIEW reg_quotation AS
SELECT
    q.id,
    q.doc_no,
    q.doc_date,
    q.status,
    q.company_id,
    q.customer_id,
    c.name  AS customer_name,
    q.currency,
    q.grand_total,
    q.valid_until
FROM quotations q
LEFT JOIN customers c ON c.id = q.customer_id;

-- PO line items register view
-- purchase_order_items uses ordered_qty, rate, line_total; material_id only (no product_id)
CREATE OR REPLACE VIEW reg_po_items AS
SELECT
    poi.id,
    po.po_number    AS po_no,
    po.po_date      AS doc_date,
    po.status,
    po.company_id,
    po.supplier_id,
    s.name          AS supplier_name,
    m.name          AS material_name,
    poi.ordered_qty AS ordered_qty,
    poi.rate        AS rate,
    poi.line_total
FROM purchase_order_items poi
JOIN purchase_orders po ON po.id = poi.purchase_order_id
LEFT JOIN suppliers s   ON s.id = po.supplier_id
LEFT JOIN materials m   ON m.id = poi.material_id;

-- SO line items register view
-- sales_order_items uses product_id, ordered_qty, unit_price; no material_id, no line_total
CREATE OR REPLACE VIEW reg_so_items AS
SELECT
    soi.id,
    so.order_number  AS so_no,
    so.order_date    AS doc_date,
    so.status,
    so.company_id,
    so.customer_id,
    c.name           AS customer_name,
    p.name           AS product_name,
    soi.ordered_qty  AS ordered_qty,
    soi.unit_price   AS rate
FROM sales_order_items soi
JOIN sales_orders so ON so.id = soi.sales_order_id
LEFT JOIN customers c ON c.id = so.customer_id
LEFT JOIN products p  ON p.id = soi.product_id;

-- Sales invoice items register view
CREATE OR REPLACE VIEW reg_invoice_items AS
SELECT
    sii.id,
    si.doc_no,
    si.doc_date,
    si.status,
    si.company_id,
    si.customer_id,
    c.name         AS customer_name,
    p.name         AS product_name,
    sii.qty,
    sii.rate,
    sii.taxable_value,
    sii.tax_amount,
    sii.line_total
FROM sales_invoice_items sii
JOIN sales_invoices si  ON si.id = sii.invoice_id
LEFT JOIN customers c   ON c.id = si.customer_id
LEFT JOIN products p    ON p.id = sii.product_id;

-- Adjustment notes register view
CREATE OR REPLACE VIEW reg_adjustment_notes AS
SELECT
    an.id,
    an.doc_no,
    an.doc_date,
    an.status,
    an.note_kind,
    an.side,
    an.company_id,
    an.reason_code,
    an.taxable_amount,
    an.tax_amount,
    an.grand_total
FROM adjustment_notes an;

-- Last purchase price per material (no product in PO items)
-- goods_receipt_items uses po_item_id and received_qty
CREATE OR REPLACE VIEW reg_last_price AS
SELECT DISTINCT ON (poi.material_id)
    poi.material_id,
    m.name            AS material_name,
    s.name            AS supplier_name,
    po.supplier_id,
    po.po_date        AS last_purchase_date,
    poi.rate          AS last_price,
    po.po_number      AS last_po_no
FROM purchase_order_items poi
JOIN purchase_orders po ON po.id = poi.purchase_order_id AND po.status NOT IN ('CANCELLED','DRAFT')
LEFT JOIN materials m   ON m.id = poi.material_id
LEFT JOIN suppliers s   ON s.id = po.supplier_id
ORDER BY poi.material_id, po.po_date DESC;

-- Production summary register
CREATE OR REPLACE VIEW reg_production_summary AS
SELECT
    prod.id          AS order_id,
    prod.order_number AS doc_no,
    prod.status,
    prod.company_id,
    prod.start_date,
    prod.expected_completion_date,
    prod.planned_qty,
    prod.good_qty    AS produced_qty,
    p.name           AS product_name
FROM production_orders prod
LEFT JOIN products p ON p.id = prod.product_id;

-- PO balance (ordered vs received) — goods_receipt_items uses po_item_id and received_qty
CREATE OR REPLACE VIEW reg_po_balance AS
SELECT
    poi.id          AS po_item_id,
    po.po_number    AS po_no,
    po.po_date,
    po.status,
    po.company_id,
    po.supplier_id,
    s.name          AS supplier_name,
    poi.material_id,
    m.name          AS material_name,
    poi.ordered_qty AS ordered_qty,
    COALESCE(
        (SELECT SUM(gri.received_qty)
         FROM goods_receipt_items gri
         JOIN goods_receipts gr ON gr.id = gri.goods_receipt_id
         WHERE gri.po_item_id = poi.id
           AND gr.status = 'POSTED'),
        0
    )               AS received_qty,
    poi.ordered_qty - COALESCE(
        (SELECT SUM(gri.received_qty)
         FROM goods_receipt_items gri
         JOIN goods_receipts gr ON gr.id = gri.goods_receipt_id
         WHERE gri.po_item_id = poi.id
           AND gr.status = 'POSTED'),
        0
    )               AS balance_qty,
    poi.rate        AS rate,
    (poi.ordered_qty - COALESCE(
        (SELECT SUM(gri.received_qty)
         FROM goods_receipt_items gri
         JOIN goods_receipts gr ON gr.id = gri.goods_receipt_id
         WHERE gri.po_item_id = poi.id
           AND gr.status = 'POSTED'),
        0
    )) * poi.rate   AS balance_value
FROM purchase_order_items poi
JOIN purchase_orders po ON po.id = poi.purchase_order_id
LEFT JOIN suppliers s   ON s.id = po.supplier_id
LEFT JOIN materials m   ON m.id = poi.material_id
WHERE po.status NOT IN ('CANCELLED');

-- Permissions
INSERT INTO permissions (code, module, description) VALUES
  ('QUOTATION_REGISTER_VIEW', 'REPORTS', 'View quotations register'),
  ('PO_REGISTER_VIEW',        'REPORTS', 'View purchase order registers'),
  ('SO_REGISTER_VIEW',        'REPORTS', 'View sales order registers'),
  ('INVOICE_REGISTER_VIEW',   'REPORTS', 'View invoice registers'),
  ('PRODUCTION_REPORT_VIEW',  'REPORTS', 'View production reports')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('QUOTATION_REGISTER_VIEW','PO_REGISTER_VIEW','SO_REGISTER_VIEW',
                 'INVOICE_REGISTER_VIEW','PRODUCTION_REPORT_VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('FINANCE_MANAGER','MANAGEMENT','SALES_MANAGER','PURCHASE_MANAGER','PRODUCTION_MANAGER')
  AND p.code IN ('QUOTATION_REGISTER_VIEW','PO_REGISTER_VIEW','SO_REGISTER_VIEW',
                 'INVOICE_REGISTER_VIEW','PRODUCTION_REPORT_VIEW')
ON CONFLICT DO NOTHING;
