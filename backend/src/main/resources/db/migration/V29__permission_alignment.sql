-- Permission alignment. Controllers used authority codes that no migration ever seeded (MASTER_*_VIEW/MANAGE,
-- IAM_*, GOODS_RECEIVE, SALES_MANAGE, MAINTENANCE_CREATE, ...), so no role - ADMIN included - could call those
-- endpoints. Most of them were renamed in code to the canonical DESIGN section 7.1 codes. The three below have no
-- canonical equivalent, so they are seeded here with the narrowest sensible grants. Review before go-live:
--   ALERT_VIEW     - see alerts and the alert count; granted to ADMIN and the managers whose domains raise alerts
--   ALERT_MANAGE   - acknowledge / resolve alerts; same managers, no MANAGEMENT (read-only role)
--   SALES_APPROVE  - approve sales orders; ADMIN only until a sales-manager role is defined
SET search_path = ims;

INSERT INTO permissions (code, module, description) VALUES
('ALERT_VIEW',    'INSIGHT', 'View alerts and the open-alert count'),
('ALERT_MANAGE',  'INSIGHT', 'Acknowledge and resolve alerts'),
('SALES_APPROVE', 'SALES',   'Approve sales orders and cancel approved orders')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('ADMIN','PRODUCTION_MANAGER','QUALITY_MANAGER','STORE_MANAGER','PURCHASE_MANAGER','MANAGEMENT')
  AND p.code = 'ALERT_VIEW'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('ADMIN','PRODUCTION_MANAGER','QUALITY_MANAGER','STORE_MANAGER','PURCHASE_MANAGER')
  AND p.code = 'ALERT_MANAGE'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND p.code = 'SALES_APPROVE'
ON CONFLICT DO NOTHING;
