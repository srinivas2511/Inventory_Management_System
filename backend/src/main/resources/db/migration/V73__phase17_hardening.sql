-- Phase 17 — ERP hardening: performance indexes, public system settings

SET search_path TO ims;

-- Performance indexes on high-cardinality foreign keys / date columns used in common queries
CREATE INDEX IF NOT EXISTS ix_journal_headers_period_status
    ON journal_headers(period_id, status);

CREATE INDEX IF NOT EXISTS ix_inventory_transactions_date
    ON inventory_transactions(transaction_date DESC);

CREATE INDEX IF NOT EXISTS ix_sales_invoices_customer_date
    ON sales_invoices(company_id, customer_id, doc_date DESC);

CREATE INDEX IF NOT EXISTS ix_purchase_orders_supplier_date
    ON purchase_orders(company_id, supplier_id, po_date DESC);

CREATE INDEX IF NOT EXISTS ix_ap_invoices_supplier_status
    ON ap_invoices(supplier_id, status);

CREATE INDEX IF NOT EXISTS ix_payments_company_date
    ON payments(company_id, doc_date DESC);

-- Public system settings (visible without authentication)
INSERT INTO system_settings (key, value, value_type, description)
VALUES
    ('system.app_name',    'ERP System',           'STRING', 'Application display name'),
    ('system.app_version', '17.0',                 'STRING', 'Application release version'),
    ('system.company_name','Manufacturing Company', 'STRING', 'Default company name')
ON CONFLICT (key) DO NOTHING;
