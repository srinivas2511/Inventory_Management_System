-- V83: GRN-invoice mismatch register, product ageing, new-product register, scrap dispose workflow
SET search_path TO ims;

-- ── GRN-Invoice Mismatch Register ─────────────────────────────────────────────
-- View: goods receipts that have no matching AP invoice (or a mismatch)
CREATE OR REPLACE VIEW reg_grn_invoice_mismatch AS
SELECT
    gr.id,
    gr.gr_number,
    gr.receipt_date,
    s.name        AS supplier_name,
    po.po_number,
    SUM(gri.received_qty * poi.rate)              AS gr_value,
    COALESCE(SUM(ai.total_amount), 0)             AS invoiced_value,
    SUM(gri.received_qty * poi.rate)
        - COALESCE(SUM(ai.total_amount), 0)       AS mismatch_amount,
    CASE
        WHEN ai.id IS NULL THEN 'NOT_INVOICED'
        WHEN SUM(gri.received_qty * poi.rate) > COALESCE(SUM(ai.total_amount), 0) + 0.01
            THEN 'UNDER_INVOICED'
        WHEN SUM(gri.received_qty * poi.rate) < COALESCE(SUM(ai.total_amount), 0) - 0.01
            THEN 'OVER_INVOICED'
        ELSE 'MATCHED'
    END AS mismatch_status
FROM goods_receipts gr
JOIN purchase_orders po ON po.id = gr.purchase_order_id
JOIN suppliers s ON s.id = po.supplier_id
JOIN goods_receipt_items gri ON gri.goods_receipt_id = gr.id
JOIN purchase_order_items poi ON poi.id = gri.po_item_id
LEFT JOIN ap_invoices ai ON ai.goods_receipt_id = gr.id AND ai.status NOT IN ('CANCELLED')
WHERE gr.status = 'POSTED'
GROUP BY gr.id, gr.gr_number, gr.receipt_date, s.name, po.po_number, ai.id
HAVING
    ai.id IS NULL
    OR ABS(SUM(gri.received_qty * poi.rate) - COALESCE(SUM(ai.total_amount), 0)) > 0.01;

-- ── Product Ageing Register ───────────────────────────────────────────────────
-- View: finished-goods batches with age (days since batch creation)
CREATE OR REPLACE VIEW reg_product_ageing AS
SELECT
    fb.id,
    fb.batch_number,
    p.product_code,
    p.name                                                      AS product_name,
    fb.produced_qty                                             AS quantity,
    fb.quality_status                                           AS status,
    fb.created_at::date                                         AS batch_date,
    CURRENT_DATE - fb.created_at::date                         AS age_days,
    CASE
        WHEN CURRENT_DATE - fb.created_at::date <= 30  THEN '0-30 days'
        WHEN CURRENT_DATE - fb.created_at::date <= 60  THEN '31-60 days'
        WHEN CURRENT_DATE - fb.created_at::date <= 90  THEN '61-90 days'
        ELSE 'Over 90 days'
    END                                                         AS ageing_bucket
FROM fg_batches fb
JOIN products p ON p.id = fb.product_id
WHERE fb.quality_status NOT IN ('DISPATCHED', 'SCRAPPED');

-- ── New Product Register ──────────────────────────────────────────────────────
-- View: products created in the last 90 days with BOM and routing status
CREATE OR REPLACE VIEW reg_new_products AS
SELECT
    p.id,
    p.product_code,
    p.name                                          AS product_name,
    p.spring_type                                   AS product_type,
    p.lifecycle_status,
    p.created_at::date                              AS created_date,
    CURRENT_DATE - p.created_at::date               AS days_since_creation,
    (SELECT COUNT(*) > 0 FROM bom b
     WHERE b.product_id = p.id AND b.status = 'ACTIVE')::boolean AS has_active_bom,
    (SELECT COUNT(*) > 0 FROM product_routing r
     WHERE r.product_id = p.id AND r.active = true)::boolean      AS has_active_routing
FROM products p
WHERE p.created_at >= NOW() - INTERVAL '90 days'
ORDER BY p.created_at DESC;

-- ── Scrap Dispose workflow ────────────────────────────────────────────────────
-- Scrap lots: track scrapped FG/RM batches that need physical disposal
CREATE TABLE IF NOT EXISTS scrap_lots (
    id              BIGSERIAL PRIMARY KEY,
    lot_number      VARCHAR(50)     NOT NULL UNIQUE,
    source_type     VARCHAR(20)     NOT NULL CHECK (source_type IN ('FG_BATCH','MATERIAL','ADJ')),
    source_id       BIGINT,
    item_name       VARCHAR(200),
    quantity        NUMERIC(18,3)   NOT NULL,
    uom             VARCHAR(30),
    scrap_reason    VARCHAR(500),
    status          VARCHAR(30)     NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','LISTED_FOR_SALE','SOLD','DISPOSED')),
    sale_price      NUMERIC(18,4),
    buyer_name      VARCHAR(200),
    disposed_at     TIMESTAMPTZ,
    disposed_by     BIGINT,
    notes           TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by      BIGINT
);

INSERT INTO permissions (code, module, description) VALUES
    ('SCRAP_VIEW',    'INVENTORY', 'View scrap lots'),
    ('SCRAP_MANAGE',  'INVENTORY', 'Create and update scrap lots (sell / dispose)')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('ADMIN','STORE_MANAGER','PRODUCTION_MANAGER')
  AND p.code IN ('SCRAP_VIEW','SCRAP_MANAGE')
ON CONFLICT DO NOTHING;

-- Register permission for mismatch/ageing/new-product (reuses REPORT_VIEW already granted)
INSERT INTO permissions (code, module, description) VALUES
    ('GRN_MISMATCH_VIEW', 'PROCUREMENT', 'View GRN-invoice mismatch register'),
    ('PRODUCT_AGEING_VIEW','INVENTORY',  'View product ageing register'),
    ('NEW_PRODUCT_VIEW',  'MASTER_DATA', 'View new product register')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('ADMIN','FINANCE_MANAGER','STORE_MANAGER','INVENTORY_MANAGER','PURCHASE_MANAGER')
  AND p.code IN ('GRN_MISMATCH_VIEW','PRODUCT_AGEING_VIEW','NEW_PRODUCT_VIEW')
ON CONFLICT DO NOTHING;
