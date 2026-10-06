-- R19 Analytics and Integration: webhook outbox, analytics views, read-only reporting schema, permissions.
-- Additive only. Every new table carries the DESIGN section 1.1 standard columns.
SET search_path = ims;

-- ── Webhooks (INT-002, DESIGN 25.3) ──────────────────────────────────────────────────────────────────────────
-- DESIGN lists secret_token_hash, but an HMAC signature needs the secret itself, so it is stored AES-GCM encrypted
-- (key from the environment) and never returned after creation. secret_hint is the last four characters.
CREATE TABLE webhook_subscriptions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    webhook_code    VARCHAR(40)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    target_url      VARCHAR(500) NOT NULL,
    secret_cipher   VARCHAR(600) NOT NULL,
    secret_hint     VARCHAR(8)   NOT NULL,
    events          VARCHAR(500) NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT       NOT NULL DEFAULT 0,
    company_id      BIGINT       NOT NULL DEFAULT 1,
    CONSTRAINT uq_webhook_code UNIQUE (webhook_code)
);

CREATE TABLE webhook_deliveries (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subscription_id  BIGINT       NOT NULL REFERENCES webhook_subscriptions(id),
    event_type       VARCHAR(60)  NOT NULL,
    event_key        VARCHAR(80)  NOT NULL,
    payload          JSONB        NOT NULL,
    status           VARCHAR(10)  NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING','RETRY','DELIVERED','FAILED')),
    retry_count      SMALLINT     NOT NULL DEFAULT 0,
    next_retry_at    TIMESTAMPTZ,
    attempted_at     TIMESTAMPTZ,
    delivered_at     TIMESTAMPTZ,
    response_status  INT,
    response_body    VARCHAR(2000),
    last_error       VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       BIGINT,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by       BIGINT,
    version          BIGINT       NOT NULL DEFAULT 0,
    company_id       BIGINT       NOT NULL DEFAULT 1,
    -- one delivery per event per subscription: enqueueing the same event twice is a no-op
    CONSTRAINT uq_webhook_delivery_event UNIQUE (subscription_id, event_key)
);
CREATE INDEX ix_webhook_delivery_due ON webhook_deliveries(next_retry_at) WHERE status IN ('PENDING','RETRY');
CREATE INDEX ix_webhook_delivery_sub ON webhook_deliveries(subscription_id, created_at DESC);

-- ── Analytics materialized views (DESIGN 25.1) ─────────────────────────────────────────────────────────────────
-- P&L per fiscal period from posted journals. A reversed journal keeps its lines and its reversal posts the mirror
-- image, so POSTED and REVERSED together net to zero.
CREATE MATERIALIZED VIEW mv_financial_summary_monthly AS
SELECT fp.id                                   AS period_id,
       fp.fiscal_year,
       fp.period_number,
       fp.period_name,
       fp.start_date                           AS month,
       COALESCE(SUM(CASE WHEN a.account_type = 'REVENUE'
                         THEN jl.credit_amount - jl.debit_amount END), 0)::numeric(18,4) AS revenue,
       COALESCE(SUM(CASE WHEN a.account_type = 'COGS'
                         THEN jl.debit_amount - jl.credit_amount END), 0)::numeric(18,4) AS cogs,
       COALESCE(SUM(CASE WHEN a.account_type = 'EXPENSE'
                         THEN jl.debit_amount - jl.credit_amount END), 0)::numeric(18,4) AS expenses
  FROM fiscal_periods fp
  LEFT JOIN journal_headers jh ON jh.period_id = fp.id AND jh.status IN ('POSTED','REVERSED')
  LEFT JOIN journal_lines jl   ON jl.journal_header_id = jh.id
  LEFT JOIN gl_accounts a      ON a.id = jl.gl_account_id
 GROUP BY fp.id, fp.fiscal_year, fp.period_number, fp.period_name, fp.start_date;
CREATE UNIQUE INDEX ux_mv_financial_summary ON mv_financial_summary_monthly(period_id);

CREATE MATERIALIZED VIEW mv_supplier_performance AS
WITH po_scope AS (
    SELECT po.id, po.supplier_id, date_trunc('month', po.po_date)::date AS month,
           po.grand_total, po.expected_delivery_date,
           (SELECT MIN(gr.receipt_date) FROM goods_receipts gr WHERE gr.purchase_order_id = po.id) AS first_receipt
      FROM purchase_orders po
     WHERE po.status NOT IN ('DRAFT','CANCELLED')
), po_agg AS (
    SELECT supplier_id, month,
           COUNT(*)                                                              AS po_count,
           COALESCE(SUM(grand_total), 0)::numeric(18,4)                          AS ordered_value,
           COUNT(*) FILTER (WHERE first_receipt IS NOT NULL)                     AS pos_received,
           COUNT(*) FILTER (WHERE first_receipt IS NOT NULL
                              AND expected_delivery_date IS NOT NULL
                              AND first_receipt <= expected_delivery_date)       AS pos_on_time
      FROM po_scope GROUP BY supplier_id, month
), item_agg AS (
    SELECT s.supplier_id, s.month,
           COALESCE(SUM(i.ordered_qty), 0)::numeric(18,3)  AS ordered_qty,
           COALESCE(SUM(i.received_qty), 0)::numeric(18,3) AS received_qty,
           COALESCE(SUM(i.accepted_qty), 0)::numeric(18,3) AS accepted_qty,
           COALESCE(SUM(i.rejected_qty), 0)::numeric(18,3) AS rejected_qty,
           AVG((i.rate - m.standard_cost) * 100.0 / NULLIF(m.standard_cost, 0))::numeric(18,2) AS price_variance_pct
      FROM po_scope s
      JOIN purchase_order_items i ON i.purchase_order_id = s.id
      JOIN materials m            ON m.id = i.material_id
     GROUP BY s.supplier_id, s.month
)
SELECT p.supplier_id, p.month, p.po_count, p.ordered_value, p.pos_received, p.pos_on_time,
       ROUND(p.pos_on_time * 100.0 / NULLIF(p.pos_received, 0), 2)                         AS on_time_pct,
       COALESCE(i.ordered_qty, 0)  AS ordered_qty,  COALESCE(i.received_qty, 0) AS received_qty,
       COALESCE(i.accepted_qty, 0) AS accepted_qty, COALESCE(i.rejected_qty, 0) AS rejected_qty,
       ROUND(COALESCE(i.accepted_qty, 0) * 100.0
             / NULLIF(COALESCE(i.accepted_qty, 0) + COALESCE(i.rejected_qty, 0), 0), 2)    AS quality_pass_pct,
       i.price_variance_pct
  FROM po_agg p
  LEFT JOIN item_agg i ON i.supplier_id = p.supplier_id AND i.month = p.month;
CREATE UNIQUE INDEX ux_mv_supplier_performance ON mv_supplier_performance(supplier_id, month);

CREATE MATERIALIZED VIEW mv_customer_sales_summary AS
WITH o AS (
    SELECT so.customer_id, date_trunc('month', so.order_date)::date AS month,
           COUNT(DISTINCT so.id)                                           AS orders,
           COALESCE(SUM(i.ordered_qty * COALESCE(i.unit_price, 0)), 0)::numeric(18,4) AS ordered_value
      FROM sales_orders so
      LEFT JOIN sales_order_items i ON i.sales_order_id = so.id
     WHERE so.status NOT IN ('DRAFT','CANCELLED')
     GROUP BY so.customer_id, date_trunc('month', so.order_date)::date
), d AS (
    SELECT dp.customer_id, date_trunc('month', dp.dispatch_date)::date AS month,
           COUNT(DISTINCT dp.id)                                           AS dispatches,
           COALESCE(SUM(di.quantity * COALESCE(di.unit_price, 0)), 0)::numeric(18,4) AS dispatched_value
      FROM dispatches dp
      LEFT JOIN dispatch_items di ON di.dispatch_id = dp.id
     GROUP BY dp.customer_id, date_trunc('month', dp.dispatch_date)::date
), inv AS (
    SELECT customer_id, date_trunc('month', invoice_date)::date AS month,
           COALESCE(SUM(total_amount), 0)::numeric(18,4)    AS invoiced_total,
           COALESCE(SUM(received_amount), 0)::numeric(18,4) AS received_total
      FROM ar_invoices
     WHERE status <> 'CANCELLED'
     GROUP BY customer_id, date_trunc('month', invoice_date)::date
), keys AS (
    SELECT customer_id, month FROM o UNION SELECT customer_id, month FROM d UNION SELECT customer_id, month FROM inv
)
SELECT k.customer_id, k.month,
       COALESCE(o.orders, 0) AS orders, COALESCE(o.ordered_value, 0) AS ordered_value,
       COALESCE(d.dispatches, 0) AS dispatches, COALESCE(d.dispatched_value, 0) AS dispatched_value,
       COALESCE(inv.invoiced_total, 0) AS invoiced_total, COALESCE(inv.received_total, 0) AS received_total
  FROM keys k
  LEFT JOIN o   ON o.customer_id = k.customer_id AND o.month = k.month
  LEFT JOIN d   ON d.customer_id = k.customer_id AND d.month = k.month
  LEFT JOIN inv ON inv.customer_id = k.customer_id AND inv.month = k.month;
CREATE UNIQUE INDEX ux_mv_customer_sales ON mv_customer_sales_summary(customer_id, month);

-- ── Alerts and notification templates for the new alert types ──────────────────────────────────────────────────
ALTER TABLE alerts DROP CONSTRAINT IF EXISTS chk_alert_type;
ALTER TABLE alerts ADD CONSTRAINT chk_alert_type CHECK (alert_type IN (
  'LOW_STOCK','OVERSTOCK','PENDING_INSPECTION','PRODUCTION_DELAY',
  'MATERIAL_SHORTAGE','AGING_BATCH','PENDING_PO','LEDGER_MISMATCH',
  'OVERDUE_PO','OVERDUE_PM','LOW_MATERIAL_BATCH','SPC_OUT_OF_CONTROL',
  'MISSING_AR_INVOICE','MISSING_JOURNAL','MAINTENANCE_COST_UNPOSTED',
  'WEBHOOK_DELIVERY_FAILED','ANOMALY_DETECTED'
));

INSERT INTO notification_templates (template_code, channel, description, subject_template, body_template) VALUES
('WEBHOOK_DELIVERY_FAILED', 'BOTH', 'Alert: a webhook delivery failed permanently after all retries',
    '[{{severity}}] Webhook delivery failed', '{{message}}'),
('ANOMALY_DETECTED', 'BOTH', 'Alert: the anomaly sweep found an unusual pattern',
    '[{{severity}}] Anomaly detected', '{{message}}')
ON CONFLICT (template_code) DO NOTHING;

-- ── Settings ─────────────────────────────────────────────────────────────────────────────────────────────────────
INSERT INTO system_settings (key, value, value_type, description) VALUES
('analytics.anomaly.z_threshold', '3', 'DECIMAL',
    'A weekly material consumption this many standard deviations above its history is flagged as an anomaly'),
('analytics.anomaly.min_history_weeks', '6', 'INTEGER',
    'Weeks of history a material needs before its consumption is checked for anomalies'),
('analytics.anomaly.supplier_quality_drop_pts', '10', 'DECIMAL',
    'A supplier whose incoming quality pass rate falls by this many percentage points against its recent average is flagged'),
('analytics.anomaly.yield_drop_pts', '5', 'DECIMAL',
    'A machine whose first-pass yield over the last 7 days falls by this many percentage points against the 28 days before is flagged'),
('analytics.anomaly.downtime_factor', '2', 'DECIMAL',
    'A machine whose daily unplanned downtime over the last 7 days is this many times the 28 days before is flagged'),
('analytics.anomaly.min_downtime_min', '30', 'INTEGER',
    'Minimum average daily unplanned downtime (minutes) before a downtime increase is flagged'),
('integration.import.max_rows', '5000', 'INTEGER', 'Largest number of data rows accepted by one bulk import'),
('integration.import.max_file_mb', '5', 'INTEGER', 'Largest file accepted by a bulk import, in megabytes'),
('integration.webhook.timeout_seconds', '10', 'INTEGER', 'Request timeout for a webhook delivery'),
('integration.export.max_rows', '1000000', 'INTEGER', 'Largest number of rows one entity export streams before it stops')
ON CONFLICT (key) DO NOTHING;

-- ── Permissions and role grants ────────────────────────────────────────────────────────────────────────────────
INSERT INTO permissions (code, module, description) VALUES
('ANALYTICS_VIEW',    'ANALYTICS',   'Run the operational analytics queries and view the analytical dashboards'),
('ANALYTICS_FINANCE', 'ANALYTICS',   'Run the analytics queries that expose margins and financial results'),
('ANALYTICS_MANAGE',  'ANALYTICS',   'Refresh the analytics views and run the anomaly sweep on demand'),
('WEBHOOK_VIEW',      'INTEGRATION', 'View webhook subscriptions and their delivery history'),
('WEBHOOK_MANAGE',    'INTEGRATION', 'Create, change and rotate webhook subscriptions and retry deliveries'),
('DATA_IMPORT',       'INTEGRATION', 'Bulk-import master data from CSV or Excel files'),
('DATA_EXPORT',       'INTEGRATION', 'Export whole entities for data warehousing'),
('TALLY_EXPORT',      'INTEGRATION', 'Export ledgers and vouchers in Tally import format')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE (r.code, p.code) IN (
    ('ADMIN','ANALYTICS_VIEW'),('ADMIN','ANALYTICS_FINANCE'),('ADMIN','ANALYTICS_MANAGE'),
    ('ADMIN','WEBHOOK_VIEW'),('ADMIN','WEBHOOK_MANAGE'),('ADMIN','DATA_IMPORT'),('ADMIN','DATA_EXPORT'),
    ('ADMIN','TALLY_EXPORT'),
    ('MANAGEMENT','ANALYTICS_VIEW'),('MANAGEMENT','ANALYTICS_FINANCE'),
    ('CONTROLLER','ANALYTICS_VIEW'),('CONTROLLER','ANALYTICS_FINANCE'),
    ('FINANCE_MANAGER','ANALYTICS_VIEW'),('FINANCE_MANAGER','ANALYTICS_FINANCE'),('FINANCE_MANAGER','TALLY_EXPORT'),
    ('PRODUCTION_MANAGER','ANALYTICS_VIEW'),
    ('PURCHASE_MANAGER','ANALYTICS_VIEW')
)
ON CONFLICT DO NOTHING;

-- ── Read-only reporting schema for external BI tools (INT-005) ─────────────────────────────────────────────────
-- Views only; nothing sensitive (no users, salaries, bank details or audit data). The role cannot log in: a DBA
-- creates a login role that is a member of ims_reporting. The role is skipped (with a notice) if the migrating
-- database user may not create roles.
CREATE SCHEMA IF NOT EXISTS reporting;

CREATE VIEW reporting.materials AS
    SELECT id, material_code, name, material_type, grade, diameter_mm, uom, preferred_supplier_id,
           min_stock, reorder_level, max_stock, standard_cost, shelf_life_days, active FROM materials;
CREATE VIEW reporting.products AS
    SELECT id, product_code, name, spring_type, primary_material_id, uom, customer_id, status,
           reorder_level, standard_cost, active FROM products;
CREATE VIEW reporting.customers AS
    SELECT id, customer_code, name, payment_terms, status FROM customers;
CREATE VIEW reporting.suppliers AS
    SELECT id, supplier_code, name, payment_terms, lead_time_days, status FROM suppliers;
CREATE VIEW reporting.inventory_balances AS
    SELECT id, warehouse_id, location_id, item_type, material_id, product_id, material_batch_id, fg_batch_id,
           stock_status, qty_on_hand, qty_reserved, avg_unit_cost FROM inventory_balances;
CREATE VIEW reporting.inventory_transactions AS
    SELECT id, transaction_number, transaction_date, transaction_type, item_type, material_id, product_id,
           warehouse_id, location_id, stock_status, quantity, uom, unit_cost, total_cost,
           reference_type, reference_id, reference_number FROM inventory_transactions;
CREATE VIEW reporting.purchase_orders AS
    SELECT id, po_number, supplier_id, po_date, expected_delivery_date, status, subtotal, tax_total, grand_total,
           currency FROM purchase_orders;
CREATE VIEW reporting.sales_orders AS
    SELECT id, order_number, customer_id, order_date, required_date, status FROM sales_orders;
CREATE VIEW reporting.sales_order_items AS
    SELECT id, sales_order_id, line_no, product_id, ordered_qty, dispatched_qty, unit_price FROM sales_order_items;
CREATE VIEW reporting.dispatches AS
    SELECT id, dispatch_number, sales_order_id, customer_id, dispatch_date, status FROM dispatches;
CREATE VIEW reporting.production_orders AS
    SELECT id, order_number, product_id, good_qty, rejected_qty, scrap_qty, start_date, expected_completion_date,
           actual_completion_at, status FROM production_orders;
CREATE VIEW reporting.ar_invoices AS
    SELECT id, invoice_number, customer_id, dispatch_id, invoice_date, due_date, subtotal, tax_total, total_amount,
           received_amount, balance_amount, status, currency_code FROM ar_invoices;
CREATE VIEW reporting.ap_invoices AS
    SELECT id, invoice_number, supplier_id, invoice_date, due_date, subtotal, tax_total, total_amount,
           paid_amount, balance_amount, status, currency_code FROM ap_invoices;
CREATE VIEW reporting.journal_lines AS
    SELECT jl.id, jh.journal_number, jh.posting_date, jh.period_id, jh.source_module, jh.source_document_type,
           a.account_code, a.name AS account_name, a.account_type, jl.debit_amount, jl.credit_amount
      FROM journal_lines jl
      JOIN journal_headers jh ON jh.id = jl.journal_header_id AND jh.status IN ('POSTED','REVERSED')
      JOIN gl_accounts a      ON a.id = jl.gl_account_id;
CREATE VIEW reporting.financial_summary_monthly AS SELECT * FROM mv_financial_summary_monthly;
CREATE VIEW reporting.supplier_performance      AS SELECT * FROM mv_supplier_performance;
CREATE VIEW reporting.customer_sales_summary    AS SELECT * FROM mv_customer_sales_summary;
CREATE VIEW reporting.oee_daily                 AS SELECT * FROM mv_oee_daily;
CREATE VIEW reporting.production_monthly        AS SELECT * FROM mv_production_monthly;
CREATE VIEW reporting.inventory_value_monthly   AS SELECT * FROM mv_inventory_value_monthly;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ims_reporting') THEN
        CREATE ROLE ims_reporting NOLOGIN;
    END IF;
EXCEPTION WHEN insufficient_privilege THEN
    RAISE NOTICE 'Role ims_reporting not created: the migrating user lacks CREATEROLE. Create it and GRANT USAGE/SELECT on schema reporting.';
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ims_reporting') THEN
        EXECUTE 'GRANT USAGE ON SCHEMA reporting TO ims_reporting';
        EXECUTE 'GRANT SELECT ON ALL TABLES IN SCHEMA reporting TO ims_reporting';
    END IF;
END $$;
