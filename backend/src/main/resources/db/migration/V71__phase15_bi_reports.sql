-- Phase 15 — BI views, registers and report builder (ERP-G-1..3)

SET search_path TO ims;

-- Saved reports
CREATE TABLE saved_reports (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    owner_id        BIGINT NOT NULL,
    report_code     VARCHAR(60),
    name            VARCHAR(120) NOT NULL,
    source_view     VARCHAR(50) NOT NULL,
    definition      JSONB NOT NULL DEFAULT '{}',
    share_scope     VARCHAR(10) NOT NULL DEFAULT 'PRIVATE'
                    CHECK (share_scope IN ('PRIVATE','TEAM','ROLE','PUBLIC')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0
);

-- Report view catalogue (whitelisted views and their columns for report builder)
CREATE TABLE report_view_catalogue (
    view_name       VARCHAR(50) NOT NULL,
    column_name     VARCHAR(60) NOT NULL,
    data_type       VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    label           VARCHAR(80),
    aggregations    VARCHAR(40),
    permission_code VARCHAR(60),
    PRIMARY KEY (view_name, column_name)
);

-- BI views (simple, read from existing tables)
CREATE OR REPLACE VIEW bi_sales AS
SELECT
    si.id AS so_id,
    si.doc_no,
    si.doc_date,
    si.status,
    si.company_id,
    si.customer_id,
    c.name AS customer_name,
    si.grand_total,
    si.currency,
    date_trunc('month', si.doc_date::timestamp) AS month
FROM sales_invoices si
LEFT JOIN customers c ON c.id = si.customer_id;

CREATE OR REPLACE VIEW bi_purchase AS
SELECT
    po.id AS po_id,
    po.po_number AS doc_no,
    po.po_date AS doc_date,
    po.status,
    po.company_id,
    po.supplier_id,
    s.name AS supplier_name,
    po.grand_total,
    date_trunc('month', po.po_date::timestamp) AS month
FROM purchase_orders po
LEFT JOIN suppliers s ON s.id = po.supplier_id;

CREATE OR REPLACE VIEW bi_inventory AS
SELECT
    ib.material_id,
    ib.product_id,
    ib.warehouse_id,
    ib.stock_status,
    ib.qty_on_hand AS quantity,
    ib.avg_unit_cost,
    ib.qty_on_hand * ib.avg_unit_cost AS stock_value,
    w.name AS warehouse_name
FROM inventory_balances ib
LEFT JOIN warehouses w ON w.id = ib.warehouse_id;

CREATE OR REPLACE VIEW bi_production AS
SELECT
    prod.id AS order_id,
    prod.order_number AS doc_no,
    prod.status,
    prod.company_id,
    prod.start_date AS planned_start,
    prod.expected_completion_date AS planned_end,
    prod.planned_qty,
    date_trunc('month', prod.start_date::timestamp) AS month
FROM production_orders prod;

-- Register views for common business registers
CREATE OR REPLACE VIEW reg_sales_invoice AS
SELECT
    i.id,
    i.doc_no,
    i.doc_date,
    i.status,
    i.company_id,
    i.customer_id,
    c.name AS customer_name,
    i.grand_total,
    i.balance_inr AS balance_due,
    i.currency
FROM sales_invoices i
LEFT JOIN customers c ON c.id = i.customer_id;

CREATE OR REPLACE VIEW reg_purchase_order AS
SELECT
    po.id,
    po.po_number AS doc_no,
    po.po_date AS doc_date,
    po.status,
    po.company_id,
    po.supplier_id,
    s.name AS supplier_name,
    po.grand_total
FROM purchase_orders po
LEFT JOIN suppliers s ON s.id = po.supplier_id;

CREATE OR REPLACE VIEW reg_payment AS
SELECT
    p.id,
    p.doc_no,
    p.doc_date AS payment_date,
    p.company_id,
    p.mode AS payment_mode,
    p.amount,
    p.currency,
    p.direction
FROM payments p;

-- Seed catalogue entries
INSERT INTO report_view_catalogue (view_name, column_name, data_type, label, aggregations, permission_code) VALUES
    ('bi_sales', 'doc_no',        'TEXT',    'Invoice No',    NULL,    'BI_SALES'),
    ('bi_sales', 'doc_date',      'DATE',    'Date',          NULL,    'BI_SALES'),
    ('bi_sales', 'customer_name', 'TEXT',    'Customer',      NULL,    'BI_SALES'),
    ('bi_sales', 'grand_total',   'NUMERIC', 'Amount',        'SUM',   'BI_SALES'),
    ('bi_sales', 'month',         'DATE',    'Month',         NULL,    'BI_SALES'),
    ('bi_purchase', 'doc_no',       'TEXT',    'PO Number',    NULL,   'BI_PURCHASE'),
    ('bi_purchase', 'doc_date',     'DATE',    'Date',         NULL,   'BI_PURCHASE'),
    ('bi_purchase', 'supplier_name','TEXT',    'Supplier',     NULL,   'BI_PURCHASE'),
    ('bi_purchase', 'grand_total',  'NUMERIC', 'Amount',       'SUM',  'BI_PURCHASE'),
    ('bi_inventory', 'warehouse_name','TEXT',  'Warehouse',    NULL,   'BI_INVENTORY'),
    ('bi_inventory', 'quantity',    'NUMERIC', 'Qty on Hand',  'SUM',  'BI_INVENTORY'),
    ('bi_inventory', 'stock_value', 'NUMERIC', 'Stock Value',  'SUM',  'BI_INVENTORY'),
    ('bi_production', 'doc_no',     'TEXT',    'Order No',     NULL,   'BI_PRODUCTION'),
    ('bi_production', 'status',     'TEXT',    'Status',       NULL,   'BI_PRODUCTION'),
    ('bi_production', 'planned_qty','NUMERIC', 'Planned Qty',  'SUM',  'BI_PRODUCTION')
ON CONFLICT (view_name, column_name) DO NOTHING;

-- Permissions
INSERT INTO permissions (code, module, description) VALUES
    ('BI_VIEW',       'ANALYTICS', 'View BI dashboards'),
    ('BI_SALES',      'ANALYTICS', 'BI sales area'),
    ('BI_PURCHASE',   'ANALYTICS', 'BI purchase area'),
    ('BI_INVENTORY',  'ANALYTICS', 'BI inventory area'),
    ('BI_PRODUCTION', 'ANALYTICS', 'BI production area'),
    ('REPORT_BUILD',  'ANALYTICS', 'Create and run custom reports'),
    ('REPORT_RUN',    'ANALYTICS', 'Run shared reports'),
    ('REGISTER_VIEW', 'ANALYTICS', 'View business registers')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('BI_VIEW','BI_SALES','BI_PURCHASE','BI_INVENTORY','BI_PRODUCTION',
                 'REPORT_BUILD','REPORT_RUN','REGISTER_VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('FINANCE_MANAGER','MANAGEMENT','CONTROLLER')
  AND p.code IN ('BI_VIEW','BI_SALES','BI_PURCHASE','BI_INVENTORY','BI_PRODUCTION',
                 'REPORT_BUILD','REPORT_RUN','REGISTER_VIEW')
ON CONFLICT DO NOTHING;
