-- Phase 9 task 9.6 (part B) — Configurable approval engine: execution mode, quorum, APPROVAL_DECIDE
-- Adds execution_mode to approval_rules, quorum to approval_rule_steps, and seeds APPROVAL_DECIDE permission.
SET search_path TO ims;

-- execution_mode: AUTO auto-approves when condition is met; MANUAL always requires a human decision
ALTER TABLE approval_rules
  ADD COLUMN IF NOT EXISTS execution_mode VARCHAR(10) NOT NULL DEFAULT 'MANUAL'
    CHECK (execution_mode IN ('AUTO','MANUAL'));

-- quorum: ANY = first approver in the group passes; ALL = every approver in the group must approve
ALTER TABLE approval_rule_steps
  ADD COLUMN IF NOT EXISTS quorum VARCHAR(5) NOT NULL DEFAULT 'ANY'
    CHECK (quorum IN ('ANY','ALL'));

-- APPROVAL_DECIDE: required to approve, reject, or withdraw via the generic approval inbox
INSERT INTO permissions (code, module, description) VALUES
  ('APPROVAL_DECIDE', 'SETTINGS', 'Decide (approve/reject/withdraw) approval requests via inbox')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code IN ('ADMIN','PURCHASE_MANAGER','SALES_MANAGER','PRODUCTION_MANAGER',
                 'FINANCE_MANAGER','QUALITY_MANAGER','HR_MANAGER')
  AND p.code = 'APPROVAL_DECIDE'
ON CONFLICT DO NOTHING;

-- V5 constraint only allowed PENDING/APPROVED/REJECTED; extend to include WITHDRAWN
-- Use explicit schema to avoid any search_path ambiguity at migration run time.
ALTER TABLE ims.approval_requests DROP CONSTRAINT IF EXISTS chk_ap_status;
ALTER TABLE ims.approval_requests
  ADD CONSTRAINT chk_ap_status CHECK (status IN ('PENDING','APPROVED','REJECTED','WITHDRAWN'));
