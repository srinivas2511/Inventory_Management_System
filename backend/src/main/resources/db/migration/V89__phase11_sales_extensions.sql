-- Phase 11 extensions: OC submit/approve workflow + OC-to-WorkOrder link

-- Add submitted_by to order_confirmations for SoD on approve
ALTER TABLE ims.order_confirmations ADD COLUMN IF NOT EXISTS submitted_by BIGINT;

-- Add oc_id FK to production_orders so a work order can reference its source OC
ALTER TABLE ims.production_orders ADD COLUMN IF NOT EXISTS oc_id BIGINT REFERENCES ims.order_confirmations(id);

-- New permissions for the extended OC workflow
INSERT INTO ims.permissions (code, module, description) VALUES
    ('OC_SUBMIT',          'SALES', 'Submit an order confirmation for approval'),
    ('OC_APPROVE',         'SALES', 'Approve a submitted order confirmation')
ON CONFLICT (code) DO NOTHING;

-- Grant to ADMIN
INSERT INTO ims.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM ims.roles r, ims.permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('OC_SUBMIT', 'OC_APPROVE')
ON CONFLICT DO NOTHING;

-- Grant to SALES_MANAGER
INSERT INTO ims.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM ims.roles r, ims.permissions p
WHERE r.code = 'SALES_MANAGER'
  AND p.code IN ('OC_SUBMIT', 'OC_APPROVE')
ON CONFLICT DO NOTHING;
