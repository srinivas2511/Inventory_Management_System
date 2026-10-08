-- R9-7.10: module activation. A customer adopts the modules it needs; a deactivated module is hidden from the navigation
-- and its API refuses requests with MODULE_INACTIVE. The core (inventory, procurement, sales, production, quality,
-- identity, settings) is always active. Every module starts active, so nothing changes until an administrator opts out.
--
-- The switch gates the API and the screens. It does not stop automatic postings other modules make (for example the
-- accounting bridge keeps journalling stock movements): the ledger of record must never silently lose an entry.
SET search_path = ims;

CREATE TABLE module_activation (
    module_code VARCHAR(20)  PRIMARY KEY,
    name        VARCHAR(60)  NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    changed_by  BIGINT,
    changed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO module_activation (module_code, name) VALUES
('FINANCE',           'Finance and accounting'),
('CONTROLLING',       'Controlling and product costing'),
('MRP',               'Material requirements planning'),
('CRM',               'Customer relationship management'),
('HRM',               'Human resources and payroll'),
('WMS',               'Warehouse management'),
('PLM',               'Product lifecycle management'),
('QUALITY_PLUS',      'Advanced quality (SPC, CAPA, complaints, first article)'),
('DOCUMENTS',         'Documents'),
('ADV_MANUFACTURING', 'Advanced manufacturing (OEE, scheduling, spare parts)'),
('ANALYTICS',         'Analytics'),
('INTEGRATION',       'Integration and data exchange');

INSERT INTO permissions (code, module, description) VALUES
('MODULE_MANAGE', 'IAM', 'Activate and deactivate optional modules')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = 'ADMIN' AND p.code = 'MODULE_MANAGE'
ON CONFLICT DO NOTHING;
