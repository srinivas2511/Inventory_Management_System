-- R18 Advanced Manufacturing: spare parts, maintenance cost tracking, OEE and production scheduling.
-- Additive only. Every new table carries the DESIGN section 1.1 standard columns.
SET search_path = ims;

-- ── Machines: the cost centre that maintenance cost is charged to ──────────────────────────────────────────────
ALTER TABLE machines ADD COLUMN cost_centre_id BIGINT REFERENCES cost_centres(id);
UPDATE machines SET cost_centre_id = (SELECT id FROM cost_centres WHERE cost_centre_code = 'CC-PROD-01');

-- ── Maintenance activities get the standard update columns so they can be edited like any other record ─────────
ALTER TABLE maintenance_activities
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_by BIGINT;
CREATE INDEX ix_maint_act_request ON maintenance_activities(maintenance_request_id);

-- ── Maintenance requests: labour and the posting of the cost to the machine's cost centre ──────────────────────
ALTER TABLE maintenance_requests
    ADD COLUMN labour_hours              NUMERIC(10,2),
    ADD COLUMN labour_cost               NUMERIC(18,4),
    ADD COLUMN cost_centre_id            BIGINT REFERENCES cost_centres(id),
    ADD COLUMN labour_journal_header_id  BIGINT REFERENCES journal_headers(id),
    ADD COLUMN cost_posted_at            TIMESTAMPTZ;

-- ── Spare parts (DESIGN 24.2). A spare part is stocked as a material so that every movement goes through the
--    inventory ledger; the material is created and kept in step by SparePartService. ─────────────────────────────
CREATE TABLE spare_parts (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    spare_part_code          VARCHAR(20)   NOT NULL,
    name                     VARCHAR(150)  NOT NULL,
    description              VARCHAR(300),
    uom                      VARCHAR(10)   NOT NULL,
    material_id              BIGINT        NOT NULL REFERENCES materials(id),
    compatible_machine_types VARCHAR(200),
    min_stock                NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (min_stock >= 0),
    reorder_level            NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (reorder_level >= 0),
    standard_cost            NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK (standard_cost >= 0),
    active                   BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by               BIGINT,
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by               BIGINT,
    version                  BIGINT        NOT NULL DEFAULT 0,
    company_id               BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_spare_parts_code UNIQUE (spare_part_code),
    CONSTRAINT uq_spare_parts_material UNIQUE (material_id),
    CONSTRAINT chk_spare_parts_min_le_reorder CHECK (min_stock <= reorder_level)
);

CREATE TABLE spare_part_issue_lines (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    maintenance_request_id  BIGINT        NOT NULL REFERENCES maintenance_requests(id),
    spare_part_id           BIGINT        NOT NULL REFERENCES spare_parts(id),
    warehouse_id            BIGINT        NOT NULL REFERENCES warehouses(id),
    issued_qty              NUMERIC(18,3) NOT NULL CHECK (issued_qty > 0),
    unit_cost               NUMERIC(18,4),
    issued_by               BIGINT        NOT NULL REFERENCES users(id),
    issued_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    remarks                 VARCHAR(300),
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by              BIGINT,
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by              BIGINT,
    version                 BIGINT        NOT NULL DEFAULT 0,
    company_id              BIGINT        NOT NULL DEFAULT 1
);
CREATE INDEX ix_spare_issue_request ON spare_part_issue_lines(maintenance_request_id);
CREATE INDEX ix_spare_issue_part    ON spare_part_issue_lines(spare_part_id);

-- ── Downtime: a fixed set of loss categories so OEE losses can be ranked. NOT VALID keeps rows recorded before
--    this release untouched; the check applies to every new or changed row. ─────────────────────────────────────
ALTER TABLE production_downtime
    ADD CONSTRAINT chk_downtime_category CHECK (category IS NULL OR category IN (
        'BREAKDOWN','SETUP_CHANGEOVER','MATERIAL_SHORTAGE','NO_OPERATOR','MINOR_STOP','PLANNED_MAINTENANCE','OTHER'
    )) NOT VALID;
CREATE INDEX ix_downtime_machine_start ON production_downtime(machine_id, started_at);

-- ── Production scheduling: the planned window of each routing operation ────────────────────────────────────────
ALTER TABLE production_operations
    ADD COLUMN scheduled_start TIMESTAMPTZ,
    ADD COLUMN scheduled_end   TIMESTAMPTZ,
    ADD CONSTRAINT chk_op_schedule_window CHECK (
        (scheduled_start IS NULL AND scheduled_end IS NULL)
        OR (scheduled_start IS NOT NULL AND scheduled_end IS NOT NULL AND scheduled_end > scheduled_start));
CREATE INDEX ix_prod_ops_machine_schedule ON production_operations(machine_id, scheduled_start)
    WHERE scheduled_start IS NOT NULL;

-- ── OEE daily aggregates ─────────────────────────────────────────────────────────────────────────────────────
-- The business day follows the hrm.timezone setting (default Asia/Kolkata), so a change of setting is picked up
-- by the next refresh. OeeService runs the identical query live; a test keeps the two in step.
CREATE FUNCTION business_tz() RETURNS text AS $$
    SELECT COALESCE((SELECT value FROM ims.system_settings WHERE key = 'hrm.timezone'), 'Asia/Kolkata')
$$ LANGUAGE sql STABLE;

CREATE MATERIALIZED VIEW mv_oee_daily AS
WITH output AS (
    SELECT COALESCE(po.machine_id, op.machine_id) AS machine_id,
           (COALESCE(po.end_time, po.created_at) AT TIME ZONE ims.business_tz())::date AS day,
           SUM(po.good_qty)::numeric(18,3) AS good_qty,
           SUM(po.rejected_qty + po.scrap_qty)::numeric(18,3) AS bad_qty,
           SUM((po.good_qty + po.rejected_qty + po.scrap_qty) * o.standard_time_min)::numeric(18,3) AS ideal_minutes,
           SUM((po.rejected_qty + po.scrap_qty) * o.standard_time_min)::numeric(18,3) AS bad_ideal_minutes
      FROM ims.production_outputs po
      JOIN ims.production_operations op ON op.id = po.production_operation_id
      JOIN ims.operations o ON o.id = op.operation_id
     WHERE COALESCE(po.machine_id, op.machine_id) IS NOT NULL
     GROUP BY 1, 2
), downtime AS (
    SELECT d.machine_id, g.day::date AS day,
           SUM(CASE WHEN d.category = 'PLANNED_MAINTENANCE' THEN
               EXTRACT(EPOCH FROM (LEAST(COALESCE(d.ended_at, now()), ((g.day::date + 1)::timestamp AT TIME ZONE ims.business_tz()))
                                 - GREATEST(d.started_at, (g.day::date::timestamp AT TIME ZONE ims.business_tz())))) / 60
               ELSE 0 END)::numeric(18,3) AS planned_min,
           SUM(CASE WHEN d.category IS DISTINCT FROM 'PLANNED_MAINTENANCE' THEN
               EXTRACT(EPOCH FROM (LEAST(COALESCE(d.ended_at, now()), ((g.day::date + 1)::timestamp AT TIME ZONE ims.business_tz()))
                                 - GREATEST(d.started_at, (g.day::date::timestamp AT TIME ZONE ims.business_tz())))) / 60
               ELSE 0 END)::numeric(18,3) AS unplanned_min
      FROM ims.production_downtime d
     CROSS JOIN LATERAL generate_series((d.started_at AT TIME ZONE ims.business_tz())::date::timestamp,
                                        (COALESCE(d.ended_at, now()) AT TIME ZONE ims.business_tz())::date::timestamp,
                                        interval '1 day') AS g(day)
     WHERE COALESCE(d.ended_at, now()) > d.started_at
     GROUP BY d.machine_id, g.day::date
), pm AS (
    SELECT r.machine_id, (a.performed_at AT TIME ZONE ims.business_tz())::date AS day,
           SUM(a.duration_minutes)::numeric(18,3) AS planned_min
      FROM ims.maintenance_activities a
      JOIN ims.maintenance_requests r ON r.id = a.maintenance_request_id
     WHERE r.request_type = 'PREVENTIVE' AND COALESCE(a.duration_minutes, 0) > 0
     GROUP BY 1, 2
)
SELECT machine_id, day,
       SUM(good_qty)::numeric(18,3) AS good_qty, SUM(bad_qty)::numeric(18,3) AS bad_qty,
       SUM(ideal_minutes)::numeric(18,3) AS ideal_minutes,
       SUM(bad_ideal_minutes)::numeric(18,3) AS bad_ideal_minutes,
       SUM(planned_min)::numeric(18,3) AS planned_downtime_min,
       SUM(unplanned_min)::numeric(18,3) AS unplanned_downtime_min
  FROM (
        SELECT machine_id, day, good_qty, bad_qty, ideal_minutes, bad_ideal_minutes,
               0::numeric AS planned_min, 0::numeric AS unplanned_min FROM output
        UNION ALL
        SELECT machine_id, day, 0, 0, 0, 0, planned_min, unplanned_min FROM downtime
        UNION ALL
        SELECT machine_id, day, 0, 0, 0, 0, planned_min, 0 FROM pm
       ) x
 GROUP BY machine_id, day;
CREATE UNIQUE INDEX ux_mv_oee_daily ON mv_oee_daily(machine_id, day);

-- ── Alerts: a maintenance cost that could not be posted ────────────────────────────────────────────────────────
ALTER TABLE alerts DROP CONSTRAINT IF EXISTS chk_alert_type;
ALTER TABLE alerts ADD CONSTRAINT chk_alert_type CHECK (alert_type IN (
  'LOW_STOCK','OVERSTOCK','PENDING_INSPECTION','PRODUCTION_DELAY',
  'MATERIAL_SHORTAGE','AGING_BATCH','PENDING_PO','LEDGER_MISMATCH',
  'OVERDUE_PO','OVERDUE_PM','LOW_MATERIAL_BATCH','SPC_OUT_OF_CONTROL',
  'MISSING_AR_INVOICE','MISSING_JOURNAL','MAINTENANCE_COST_UNPOSTED'
));

-- The notification engine needs a template for every alert type (NotificationIT checks this); same shape as V32.
INSERT INTO notification_templates (template_code, channel, description, subject_template, body_template) VALUES
('MAINTENANCE_COST_UNPOSTED', 'BOTH', 'Alert: the labour cost of a completed maintenance request is not in the GL',
    '[{{severity}}] Maintenance cost not posted', '{{message}}')
ON CONFLICT (template_code) DO NOTHING;

-- ── Chart of accounts and the spare part issue assignment ────────────────────────────────────────────────────────
INSERT INTO gl_accounts (account_code, name, account_group_id, account_type, normal_balance, company_id) VALUES
('5301', 'Repairs and Maintenance Expense', (SELECT id FROM gl_account_groups WHERE group_code = '52'), 'EXPENSE', 'DEBIT',  1),
('5302', 'Maintenance Labour Absorbed',     (SELECT id FROM gl_account_groups WHERE group_code = '52'), 'EXPENSE', 'CREDIT', 1);

-- A spare part issued to a maintenance request leaves stock and becomes maintenance expense. The cost centre is
-- the issuing machine's; the one on this row is only the default when a machine has none.
INSERT INTO inventory_account_assignments
    (company_id, transaction_type, item_type, valuation_class, gl_account_debit_id, gl_account_credit_id, cost_centre_id)
VALUES (1, 'MAINTENANCE_ISSUE', 'MATERIAL', NULL,
        (SELECT id FROM gl_accounts WHERE account_code = '5301'),
        (SELECT id FROM gl_accounts WHERE account_code = '1401'),
        (SELECT id FROM cost_centres WHERE cost_centre_code = 'CC-PROD-01'));

-- ── Settings ─────────────────────────────────────────────────────────────────────────────────────────────────────
INSERT INTO system_settings (key, value, value_type, description) VALUES
('mfg.calendar.shift_codes', 'GENERAL', 'STRING',
    'Comma-separated shift codes whose hours make up a machine''s planned production time (OEE) and the schedulable window (scheduling)'),
('mfg.schedule.default_horizon_days', '14', 'INTEGER',
    'Planning horizon, in days, of an auto-schedule run when none is given')
ON CONFLICT (key) DO NOTHING;

-- ── Permissions and role grants ────────────────────────────────────────────────────────────────────────────────
INSERT INTO permissions (code, module, description) VALUES
('SPARE_PART_VIEW',        'MAINTENANCE', 'View the spare part master and stock levels'),
('SPARE_PART_MANAGE',      'MAINTENANCE', 'Create and change spare parts'),
('SPARE_PART_ISSUE',       'MAINTENANCE', 'Issue spare parts against a maintenance request'),
('MAINTENANCE_COST_VIEW',  'MAINTENANCE', 'View maintenance cost per request and per machine'),
('MAINTENANCE_COST_POST',  'MAINTENANCE', 'Post or re-post the labour cost of a completed maintenance request to the GL'),
('OEE_VIEW',               'PRODUCTION',  'View OEE, loss breakdowns and downtime rankings'),
('SCHEDULE_VIEW',          'PRODUCTION',  'View the production schedule (Gantt)'),
('SCHEDULE_MANAGE',        'PRODUCTION',  'Run the scheduler and move scheduled operations')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE (r.code, p.code) IN (
    ('ADMIN','SPARE_PART_VIEW'),('ADMIN','SPARE_PART_MANAGE'),('ADMIN','SPARE_PART_ISSUE'),
    ('ADMIN','MAINTENANCE_COST_VIEW'),('ADMIN','MAINTENANCE_COST_POST'),('ADMIN','OEE_VIEW'),
    ('ADMIN','SCHEDULE_VIEW'),('ADMIN','SCHEDULE_MANAGE'),
    ('MAINTENANCE','SPARE_PART_VIEW'),('MAINTENANCE','SPARE_PART_MANAGE'),('MAINTENANCE','SPARE_PART_ISSUE'),
    ('MAINTENANCE','MAINTENANCE_COST_VIEW'),('MAINTENANCE','OEE_VIEW'),
    ('STORE_MANAGER','SPARE_PART_VIEW'),('STORE_MANAGER','SPARE_PART_MANAGE'),('STORE_MANAGER','SPARE_PART_ISSUE'),
    ('PURCHASE_MANAGER','SPARE_PART_VIEW'),
    ('PRODUCTION_MANAGER','SPARE_PART_VIEW'),('PRODUCTION_MANAGER','OEE_VIEW'),
    ('PRODUCTION_MANAGER','SCHEDULE_VIEW'),('PRODUCTION_MANAGER','SCHEDULE_MANAGE'),
    ('SUPERVISOR','OEE_VIEW'),('SUPERVISOR','SCHEDULE_VIEW'),
    ('PLANNER','SCHEDULE_VIEW'),('PLANNER','SCHEDULE_MANAGE'),
    ('MANAGEMENT','SPARE_PART_VIEW'),('MANAGEMENT','MAINTENANCE_COST_VIEW'),('MANAGEMENT','OEE_VIEW'),
    ('MANAGEMENT','SCHEDULE_VIEW'),
    ('CONTROLLER','MAINTENANCE_COST_VIEW'),
    ('FINANCE_MANAGER','MAINTENANCE_COST_VIEW'),('FINANCE_MANAGER','MAINTENANCE_COST_POST')
)
ON CONFLICT DO NOTHING;
