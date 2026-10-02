-- Phase 7 — Insight: Dashboards, Reports, Alerts, Traceability, Maintenance
SET search_path TO ims;

-- ── Extend alerts table with status / resolved_by / resolution_note ─────────
ALTER TABLE alerts
  ADD COLUMN IF NOT EXISTS status          VARCHAR(15) NOT NULL DEFAULT 'OPEN',
  ADD COLUMN IF NOT EXISTS resolved_by     BIGINT,
  ADD COLUMN IF NOT EXISTS resolution_note VARCHAR(300);

-- Update existing resolved rows
UPDATE alerts SET status = 'RESOLVED' WHERE resolved_at IS NOT NULL;

-- Drop and recreate CHECK constraint to allow more alert types
ALTER TABLE alerts DROP CONSTRAINT IF EXISTS chk_alert_type;
ALTER TABLE alerts ADD CONSTRAINT chk_alert_type CHECK (alert_type IN (
  'LOW_STOCK','OVERSTOCK','PENDING_INSPECTION','PRODUCTION_DELAY',
  'MATERIAL_SHORTAGE','AGING_BATCH','PENDING_PO','LEDGER_MISMATCH',
  'OVERDUE_PO','OVERDUE_PM','LOW_MATERIAL_BATCH'
));

-- ── Maintenance tables ───────────────────────────────────────────────────────
CREATE TABLE maintenance_requests (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_number    VARCHAR(20)   NOT NULL UNIQUE,
  machine_id        BIGINT        NOT NULL,
  request_type      VARCHAR(20)   NOT NULL DEFAULT 'CORRECTIVE',
  priority          SMALLINT      NOT NULL DEFAULT 3,
  description       VARCHAR(500)  NOT NULL,
  status            VARCHAR(15)   NOT NULL DEFAULT 'OPEN',
  requested_by      BIGINT        NOT NULL,
  assigned_to       BIGINT,
  scheduled_date    DATE,
  completed_at      TIMESTAMPTZ,
  resolution_notes  VARCHAR(500),
  created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
  created_by        BIGINT,
  updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_by        BIGINT,
  version           BIGINT        NOT NULL DEFAULT 0,
  company_id        BIGINT        NOT NULL DEFAULT 1,
  CONSTRAINT chk_maint_type   CHECK (request_type IN ('CORRECTIVE','PREVENTIVE','PREDICTIVE')),
  CONSTRAINT chk_maint_status CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED'))
);
CREATE INDEX ix_maint_status ON maintenance_requests(status, machine_id);

CREATE TABLE maintenance_activities (
  id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  maintenance_request_id  BIGINT        NOT NULL REFERENCES maintenance_requests(id),
  description             VARCHAR(500)  NOT NULL,
  performed_by            BIGINT        NOT NULL,
  performed_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
  duration_minutes        INT,
  spare_parts_used        VARCHAR(300),
  created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
  created_by              BIGINT,
  version                 BIGINT        NOT NULL DEFAULT 0,
  company_id              BIGINT        NOT NULL DEFAULT 1
);

CREATE TABLE pm_schedules (
  id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  machine_id            BIGINT        NOT NULL,
  task_name             VARCHAR(100)  NOT NULL,
  frequency_days        INT           NOT NULL CHECK (frequency_days > 0),
  last_performed_date   DATE,
  next_due_date         DATE,
  assigned_to           BIGINT,
  active                BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
  created_by            BIGINT,
  updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_by            BIGINT,
  version               BIGINT        NOT NULL DEFAULT 0,
  company_id            BIGINT        NOT NULL DEFAULT 1
);
CREATE INDEX ix_pm_due ON pm_schedules(next_due_date) WHERE active = TRUE;

-- ── Materialized views ───────────────────────────────────────────────────────
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_production_monthly AS
SELECT
  date_trunc('month', po.created_at)::DATE             AS month,
  COUNT(*)                                              AS total_orders,
  COALESCE(SUM(po.planned_qty), 0)                      AS planned_qty,
  COALESCE(SUM(po.good_qty), 0)                         AS good_qty,
  COALESCE(SUM(po.rejected_qty), 0)                     AS rejected_qty,
  ROUND(COALESCE(SUM(po.good_qty), 0) * 100.0 /
        NULLIF(COALESCE(SUM(po.planned_qty), 0), 0), 2) AS yield_pct
FROM production_orders po
WHERE po.status NOT IN ('DRAFT','CANCELLED')
GROUP BY date_trunc('month', po.created_at)::DATE
WITH DATA;
CREATE UNIQUE INDEX IF NOT EXISTS ux_mv_prod_monthly ON mv_production_monthly (month);

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_inventory_value_monthly AS
SELECT
  date_trunc('month', it.transaction_date)::DATE                            AS month,
  it.item_type,
  COALESCE(SUM(CASE WHEN it.quantity > 0 THEN it.total_cost ELSE 0 END), 0) AS value_in,
  COALESCE(SUM(CASE WHEN it.quantity < 0 THEN ABS(it.total_cost) ELSE 0 END), 0) AS value_out
FROM inventory_transactions it
GROUP BY date_trunc('month', it.transaction_date)::DATE, it.item_type
WITH DATA;
CREATE UNIQUE INDEX IF NOT EXISTS ux_mv_inv_monthly ON mv_inventory_value_monthly (month, item_type);

-- ── System settings ──────────────────────────────────────────────────────────
INSERT INTO system_settings (key, value, description) VALUES
  ('alert.low_stock.enabled',          'true',   'Enable low stock alerts'),
  ('alert.pending_inspection.days',    '2',      'Alert if inspection pending for this many days'),
  ('report.export.max_rows',           '100000', 'Max rows for synchronous export'),
  ('production.target.monthly',        '10000',  'Monthly production target (pieces)'),
  ('report.retention.days',            '90',     'Days to keep generated report files'),
  ('dashboard.cache.ttl_seconds',      '300',    'Dashboard KPI cache TTL in seconds')
ON CONFLICT (key) DO NOTHING;
