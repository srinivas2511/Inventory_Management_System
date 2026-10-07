-- E2E batch 2 (defects D-04, D-05, D-11): grants the documented role matrix requires but the seeds never made.
-- Additive and idempotent. ADMIN already holds every permission (V37), and no new permission codes are created here.
--
-- D-04  DESIGN §8.6 gives these roles a role dashboard as their landing screen ("Engineering dashboard",
--       "Supervisor dashboard", "Purchase dashboard", "Store dashboard"), but they held no DASHBOARD_VIEW, so
--       landing on it produced a 403. OPERATOR (My Work), DISPATCH (dispatch queue) and MAINTENANCE (machine status)
--       land elsewhere in §8.6 and deliberately do NOT get it.
-- D-11  DESIGN §8.6 lists Traceability among the Quality role's groups, and §12 steps 20-21 have quality1 trace an FG
--       batch and a raw-material batch (QMS-006).
-- D-05  DESIGN §12 step 8: storemgr1 issues material to a production order. The issue itself needs INVENTORY_ISSUE
--       (STORE_MANAGER has it); opening the order screen and reading the order's materials needs PRODUCTION_VIEW.
--       This is the minimal read-only grant that makes the documented step work. Left ungranted on purpose
--       (least privilege, documented step names storemgr1 or supervisor1): STORE_OPERATOR and INVENTORY_ISSUE for
--       SUPERVISOR.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permissions p ON (r.code, p.code) IN (
         ('ENGINEER',        'DASHBOARD_VIEW'),
         ('SUPERVISOR',      'DASHBOARD_VIEW'),
         ('PURCHASE_MANAGER','DASHBOARD_VIEW'),
         ('STORE_OPERATOR',  'DASHBOARD_VIEW'),
         ('QUALITY_MANAGER', 'TRACEABILITY_VIEW'),
         ('STORE_MANAGER',   'PRODUCTION_VIEW'))
ON CONFLICT DO NOTHING;
