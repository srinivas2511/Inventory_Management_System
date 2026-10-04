-- Dispatch -> AR invoice reliability. A dispatch whose AR invoice could not be created used to be logged and forgotten
-- (the invoice value was a zero stub, so every invoice failed chk_jl_one_side_only). The invoice is now created after
-- the dispatch commits, a failure raises an alert, and finance can find and retry the missing invoices.
--   * two alert types for accounting steps that did not complete
--   * one AR invoice per dispatch, enforced by the database, so a retry can never double-bill
--   * AR_RECONCILE - run the retry; FINANCE_MANAGER and ADMIN. AR_MANAGE holders may list the dispatches without an invoice.
--   * the finance roles can see the alerts that concern them (ALERT_MANAGE for the manager only). Review before go-live.
SET search_path = ims;

ALTER TABLE alerts DROP CONSTRAINT IF EXISTS chk_alert_type;
ALTER TABLE alerts ADD CONSTRAINT chk_alert_type CHECK (alert_type IN (
  'LOW_STOCK','OVERSTOCK','PENDING_INSPECTION','PRODUCTION_DELAY',
  'MATERIAL_SHORTAGE','AGING_BATCH','PENDING_PO','LEDGER_MISMATCH',
  'OVERDUE_PO','OVERDUE_PM','LOW_MATERIAL_BATCH','SPC_OUT_OF_CONTROL',
  'MISSING_AR_INVOICE','MISSING_JOURNAL'
));

CREATE UNIQUE INDEX IF NOT EXISTS ux_ar_invoices_dispatch ON ar_invoices (dispatch_id) WHERE dispatch_id IS NOT NULL;

INSERT INTO permissions (code, module, description) VALUES
('AR_RECONCILE', 'FINANCE', 'Retry AR invoice creation for dispatches that have no invoice')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('ADMIN','FINANCE_MANAGER') AND p.code = 'AR_RECONCILE'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('FINANCE_MANAGER','ACCOUNTANT') AND p.code = 'ALERT_VIEW'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'FINANCE_MANAGER' AND p.code = 'ALERT_MANAGE'
ON CONFLICT DO NOTHING;

-- Every alert type needs its own notification template (NotificationIT enforces it).
INSERT INTO notification_templates (template_code, channel, description, subject_template, body_template) VALUES
('MISSING_AR_INVOICE', 'BOTH', 'Alert: a dispatch has no AR invoice',           '[{{severity}}] Dispatch without AR invoice', '{{message}}'),
('MISSING_JOURNAL',    'BOTH', 'Alert: an inventory movement has no GL journal', '[{{severity}}] Movement without GL journal', '{{message}}')
ON CONFLICT (template_code) DO NOTHING;
