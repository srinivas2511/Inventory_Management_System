-- 12.3: Sales invoice types (PROFORMA, DIRECT, AD_HOC, EXPORT)
SET search_path = ims;

ALTER TABLE sales_invoices ADD COLUMN IF NOT EXISTS invoice_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE sales_invoices ADD COLUMN IF NOT EXISTS lut_arn VARCHAR(50);

-- invoice_type values: STANDARD, PROFORMA, DIRECT, AD_HOC, EXPORT

INSERT INTO permissions (code, description)
VALUES ('SALES_INVOICE_VIEW', 'View sales invoices')
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code, description)
VALUES ('SALES_INVOICE_CREATE', 'Create sales invoices')
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code, description)
VALUES ('SALES_INVOICE_POST', 'Post sales invoices to GL')
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code, description)
VALUES ('SALES_INVOICE_CANCEL', 'Cancel sales invoices')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name IN ('ADMIN', 'SALES_MANAGER', 'FINANCE_MANAGER')
  AND p.code IN ('SALES_INVOICE_VIEW', 'SALES_INVOICE_CREATE', 'SALES_INVOICE_POST', 'SALES_INVOICE_CANCEL')
ON CONFLICT DO NOTHING;
