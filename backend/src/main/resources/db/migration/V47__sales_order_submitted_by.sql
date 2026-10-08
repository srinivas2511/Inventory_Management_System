-- Segregation of duties for sales orders (REQUIREMENTS SEC-008 principle, ARCHITECTURE section 10, RUNBOOK
-- "second approver"): the person who confirms (submits) an order cannot approve it. The confirmer was never
-- recorded, only audited, so approve() could not compare against it. Existing rows keep NULL: only their creator
-- (created_by) is checked.
ALTER TABLE ims.sales_orders ADD COLUMN submitted_by BIGINT;
