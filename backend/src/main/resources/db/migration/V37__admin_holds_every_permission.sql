-- The Administrator role holds every permission. Earlier release migrations granted ADMIN permission by permission and
-- missed the finance codes, PRODUCTION_EXECUTE and a few more (133 of 147 at the time of the UI walkthrough).
-- Idempotent: safe to run on a database that already has some or all of the grants.
-- Each release that seeds new permission codes must also grant them to ADMIN (docs/decisions.md);
-- AdminHoldsEveryPermissionIT fails the build otherwise. Code-level segregation-of-duties rules (for example
-- "requester cannot approve their own purchase order") are unaffected by holding a permission.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
 CROSS JOIN permissions p
 WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;
