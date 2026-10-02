-- Phase 4 — Production tables (DESIGN.md §4, PLAN tasks 4.3–4.8)
SET search_path TO ims;

-- ── Production orders ─────────────────────────────────────────────────────────
CREATE TABLE production_orders (
  id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_number             VARCHAR(20)  NOT NULL UNIQUE,
  product_id               BIGINT       NOT NULL REFERENCES products(id),
  bom_id                   BIGINT       NOT NULL REFERENCES bom(id),
  planned_qty              NUMERIC(18,3) NOT NULL CHECK (planned_qty > 0),
  good_qty                 NUMERIC(18,3) NOT NULL DEFAULT 0,
  rejected_qty             NUMERIC(18,3) NOT NULL DEFAULT 0,
  scrap_qty                NUMERIC(18,3) NOT NULL DEFAULT 0,
  start_date               DATE,
  expected_completion_date DATE,
  actual_start_at          TIMESTAMPTZ,
  actual_completion_at     TIMESTAMPTZ,
  production_line          VARCHAR(40),
  primary_machine_id       BIGINT       REFERENCES machines(id),
  supervisor_id            BIGINT       REFERENCES users(id),
  sales_order_id           BIGINT,
  priority                 SMALLINT     NOT NULL DEFAULT 3,
  status                   VARCHAR(15)  NOT NULL DEFAULT 'DRAFT',
  released_by              BIGINT,
  released_at              TIMESTAMPTZ,
  closed_by                BIGINT,
  closed_at                TIMESTAMPTZ,
  remarks                  VARCHAR(500),
  created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by               BIGINT,
  updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by               BIGINT,
  version                  BIGINT       NOT NULL DEFAULT 0,
  company_id               BIGINT       NOT NULL DEFAULT 1
);
CREATE INDEX ix_prod_orders_status     ON production_orders(status);
CREATE INDEX ix_prod_orders_product    ON production_orders(product_id);
CREATE INDEX ix_prod_orders_supervisor ON production_orders(supervisor_id);

-- ── Production order materials (BOM snapshot) ─────────────────────────────────
CREATE TABLE production_order_materials (
  id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  production_order_id  BIGINT        NOT NULL REFERENCES production_orders(id) ON DELETE CASCADE,
  material_id          BIGINT        NOT NULL REFERENCES materials(id),
  planned_qty          NUMERIC(18,3) NOT NULL,
  issued_qty           NUMERIC(18,3) NOT NULL DEFAULT 0,
  consumed_qty         NUMERIC(18,3) NOT NULL DEFAULT 0,
  returned_qty         NUMERIC(18,3) NOT NULL DEFAULT 0,
  scrap_qty            NUMERIC(18,3) NOT NULL DEFAULT 0,
  remaining_qty        NUMERIC(18,3) GENERATED ALWAYS AS (issued_qty - consumed_qty - returned_qty - scrap_qty) STORED,
  scrap_pct_applied    NUMERIC(5,2),
  uom                  VARCHAR(10)   NOT NULL,
  created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  created_by           BIGINT,
  updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_by           BIGINT,
  version              BIGINT        NOT NULL DEFAULT 0,
  company_id           BIGINT        NOT NULL DEFAULT 1,
  UNIQUE (production_order_id, material_id),
  CONSTRAINT ck_not_overconsumed CHECK (consumed_qty + returned_qty + scrap_qty <= issued_qty)
);

-- ── Production operations (routing snapshot) ──────────────────────────────────
CREATE TABLE production_operations (
  id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  production_order_id  BIGINT        NOT NULL REFERENCES production_orders(id) ON DELETE CASCADE,
  sequence_no          INT           NOT NULL,
  operation_id         BIGINT        NOT NULL REFERENCES operations(id),
  machine_id           BIGINT        REFERENCES machines(id),
  assigned_operator_id BIGINT        REFERENCES users(id),
  inspection_point     BOOLEAN       NOT NULL DEFAULT FALSE,
  status               VARCHAR(15)   NOT NULL DEFAULT 'PENDING',
  input_qty            NUMERIC(18,3) NOT NULL DEFAULT 0,
  good_qty             NUMERIC(18,3) NOT NULL DEFAULT 0,
  rejected_qty         NUMERIC(18,3) NOT NULL DEFAULT 0,
  scrap_qty            NUMERIC(18,3) NOT NULL DEFAULT 0,
  started_at           TIMESTAMPTZ,
  completed_at         TIMESTAMPTZ,
  created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  created_by           BIGINT,
  updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_by           BIGINT,
  version              BIGINT        NOT NULL DEFAULT 0,
  company_id           BIGINT        NOT NULL DEFAULT 1,
  UNIQUE (production_order_id, sequence_no),
  CONSTRAINT ck_op_qty CHECK (good_qty + rejected_qty + scrap_qty <= input_qty)
);
CREATE INDEX ix_prod_ops_order    ON production_operations(production_order_id);
CREATE INDEX ix_prod_ops_operator ON production_operations(assigned_operator_id);

-- ── Material issues ───────────────────────────────────────────────────────────
CREATE TABLE material_issues (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  issue_number        VARCHAR(20)  NOT NULL UNIQUE,
  production_order_id BIGINT       NOT NULL REFERENCES production_orders(id),
  issue_date          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  from_warehouse_id   BIGINT,
  issued_by           BIGINT       NOT NULL,
  received_by         BIGINT,
  remarks             VARCHAR(300),
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by          BIGINT,
  updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by          BIGINT,
  version             BIGINT       NOT NULL DEFAULT 0,
  company_id          BIGINT       NOT NULL DEFAULT 1
);

CREATE TABLE material_issue_lines (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  material_issue_id BIGINT        NOT NULL REFERENCES material_issues(id),
  material_id       BIGINT        NOT NULL,
  material_batch_id BIGINT        NOT NULL REFERENCES material_batches(id),
  from_location_id  BIGINT        NOT NULL,
  to_location_id    BIGINT        NOT NULL,
  quantity          NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
  unit_cost         NUMERIC(18,4) NOT NULL DEFAULT 0,
  created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
  created_by        BIGINT,
  updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_by        BIGINT,
  version           BIGINT        NOT NULL DEFAULT 0,
  company_id        BIGINT        NOT NULL DEFAULT 1
);

-- ── Production outputs ────────────────────────────────────────────────────────
CREATE TABLE production_outputs (
  id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  production_operation_id BIGINT        NOT NULL REFERENCES production_operations(id),
  production_order_id     BIGINT        NOT NULL REFERENCES production_orders(id),
  entry_no                INT           NOT NULL,
  input_qty               NUMERIC(18,3) NOT NULL,
  good_qty                NUMERIC(18,3) NOT NULL CHECK (good_qty >= 0),
  rejected_qty            NUMERIC(18,3) NOT NULL DEFAULT 0,
  scrap_qty               NUMERIC(18,3) NOT NULL DEFAULT 0,
  rejection_reason_id     BIGINT,
  operator_id             BIGINT        NOT NULL,
  machine_id              BIGINT,
  start_time              TIMESTAMPTZ,
  end_time                TIMESTAMPTZ,
  shift                   VARCHAR(10),
  remarks                 VARCHAR(300),
  correction_of_id        BIGINT        REFERENCES production_outputs(id),
  created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
  created_by              BIGINT,
  updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_by              BIGINT,
  version                 BIGINT        NOT NULL DEFAULT 0,
  company_id              BIGINT        NOT NULL DEFAULT 1
);

-- ── Production downtime ───────────────────────────────────────────────────────
CREATE TABLE production_downtime (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  machine_id          BIGINT       NOT NULL,
  production_order_id BIGINT,
  reason              VARCHAR(100) NOT NULL,
  category            VARCHAR(20),
  started_at          TIMESTAMPTZ  NOT NULL,
  ended_at            TIMESTAMPTZ,
  reported_by         BIGINT       NOT NULL,
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by          BIGINT,
  updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by          BIGINT,
  version             BIGINT       NOT NULL DEFAULT 0,
  company_id          BIGINT       NOT NULL DEFAULT 1
);

-- ── Material requests ─────────────────────────────────────────────────────────
CREATE TABLE material_requests (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  production_order_id BIGINT,
  material_id         BIGINT,
  requested_qty       NUMERIC(18,3),
  requested_by        BIGINT,
  status              VARCHAR(15)  NOT NULL DEFAULT 'OPEN',
  fulfilled_issue_id  BIGINT,
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by          BIGINT,
  updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by          BIGINT,
  version             BIGINT       NOT NULL DEFAULT 0,
  company_id          BIGINT       NOT NULL DEFAULT 1
);

-- ── System settings: production ───────────────────────────────────────────────
INSERT INTO system_settings (key, value, value_type, description)
VALUES
  ('production.issue.tolerance_pct',      '10',   'DECIMAL', 'Max % over-issue allowed vs planned qty'),
  ('production.release.require_material', 'false','BOOLEAN', 'Block order release if material shortage'),
  ('production.auto_backflush',           'false','BOOLEAN', 'Auto-consume materials on first op output'),
  ('quality.trace_granularity',           'ORDER','STRING',  'Traceability level: ORDER or BATCH')
ON CONFLICT (key) DO NOTHING;
