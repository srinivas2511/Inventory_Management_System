-- E2E batch 5b (defects D-30, D-34): UAT Role 8 "Finance / Costing (read-only)" has the finance user view the inventory
-- valuation report (FIN-01), the production cost summary (FIN-02) and the valuation in a dashboard or export (FIN-04).
-- FINANCE_MANAGER held FINANCE_REPORT but none of the read permissions those screens and the report catalogue check,
-- so /insight/reports and /controlling/reports redirected or answered 403.
--
-- Read-only grants only (no new permission codes; ADMIN already holds every permission, V37):
--   REPORT_VIEW     the report catalogue (the generic /api/reports and the valuation / cost summary reports)
--   INVENTORY_VIEW  the stock screen the valuation report is compared with
--   VALUATION_VIEW  the stock valuation figures
--   DASHBOARD_VIEW  "view in dashboard" (FIN-04); the landing resolver sends a role without it elsewhere
-- ACCOUNTANT is left as it is: UAT Role 8 names the finance/costing user, not the day-to-day entry role.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permissions p ON (r.code, p.code) IN (
         ('FINANCE_MANAGER', 'REPORT_VIEW'),
         ('FINANCE_MANAGER', 'INVENTORY_VIEW'),
         ('FINANCE_MANAGER', 'VALUATION_VIEW'),
         ('FINANCE_MANAGER', 'DASHBOARD_VIEW'))
ON CONFLICT DO NOTHING;
