-- Phase 2 — Adjustments, stock counts, approval requests, alerts (DESIGN.md §2.2, §2.5)
SET search_path TO ims;

-- ── Stock adjustments ─────────────────────────────────────────────────────────
CREATE TABLE stock_adjustments (
  id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  adjustment_number       VARCHAR(20)   NOT NULL,
  item_type               VARCHAR(10)   NOT NULL,
  material_id             BIGINT        REFERENCES materials(id),
  product_id              BIGINT        REFERENCES products(id),
  batch_id                BIGINT,
  warehouse_id            BIGINT        NOT NULL REFERENCES warehouses(id),
  location_id             BIGINT        NOT NULL REFERENCES warehouse_locations(id),
  stock_status            VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',
  system_qty              NUMERIC(18,3) NOT NULL,
  counted_qty             NUMERIC(18,3) NOT NULL,
  variance_qty            NUMERIC(18,3) GENERATED ALWAYS AS (counted_qty - system_qty) STORED,
  variance_value          NUMERIC(18,4),
  reason                  VARCHAR(500)  NOT NULL,
  status                  VARCHAR(25)   NOT NULL DEFAULT 'REQUESTED',
  requested_by            BIGINT        NOT NULL,
  posted_transaction_id   BIGINT        REFERENCES inventory_transactions(id),
  stock_count_id          BIGINT,
  created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
  created_by              BIGINT,
  updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_by              BIGINT,
  version                 BIGINT        NOT NULL DEFAULT 0,
  company_id              BIGINT        NOT NULL DEFAULT 1,
  CONSTRAINT uq_adj_number  UNIQUE (adjustment_number),
  CONSTRAINT chk_adj_status CHECK (status IN ('REQUESTED','SUPERVISOR_REVIEW','MANAGER_APPROVAL','ADMIN_APPROVAL','POSTED','REJECTED'))
);
CREATE INDEX ix_adj_status ON stock_adjustments(status);

-- ── Stock counts ──────────────────────────────────────────────────────────────
CREATE TABLE stock_counts (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  count_number VARCHAR(20)  NOT NULL,
  warehouse_id BIGINT       NOT NULL REFERENCES warehouses(id),
  status       VARCHAR(15)  NOT NULL DEFAULT 'OPEN',
  count_date   DATE         NOT NULL,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by   BIGINT,
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by   BIGINT,
  version      BIGINT       NOT NULL DEFAULT 0,
  company_id   BIGINT       NOT NULL DEFAULT 1,
  CONSTRAINT uq_stock_count_number UNIQUE (count_number)
);

CREATE TABLE stock_count_lines (
  id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  stock_count_id BIGINT        NOT NULL REFERENCES stock_counts(id) ON DELETE CASCADE,
  item_type      VARCHAR(10)   NOT NULL,
  material_id    BIGINT        REFERENCES materials(id),
  product_id     BIGINT        REFERENCES products(id),
  batch_id       BIGINT,
  location_id    BIGINT        NOT NULL REFERENCES warehouse_locations(id),
  system_qty     NUMERIC(18,3),
  counted_qty    NUMERIC(18,3),
  counted_by     BIGINT
);

-- ── Approval requests ─────────────────────────────────────────────────────────
CREATE TABLE approval_requests (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  document_type       VARCHAR(30)  NOT NULL,
  document_id         BIGINT       NOT NULL,
  step_no             INT          NOT NULL,
  required_permission VARCHAR(60)  NOT NULL,
  status              VARCHAR(15)  NOT NULL DEFAULT 'PENDING',
  requested_by        BIGINT       NOT NULL,
  decided_by          BIGINT,
  decided_at          TIMESTAMPTZ,
  comment             VARCHAR(500),
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT chk_ap_status CHECK (status IN ('PENDING','APPROVED','REJECTED'))
);
CREATE INDEX ix_ap_doc ON approval_requests(document_type, document_id);
CREATE INDEX ix_ap_status ON approval_requests(status);

-- ── Alerts ────────────────────────────────────────────────────────────────────
CREATE TABLE alerts (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  alert_type      VARCHAR(30)  NOT NULL,
  severity        VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
  entity_type     VARCHAR(40),
  entity_id       BIGINT,
  open_key        VARCHAR(120) NOT NULL,
  message         VARCHAR(300) NOT NULL,
  first_seen_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  last_seen_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  acknowledged_by BIGINT,
  acknowledged_at TIMESTAMPTZ,
  resolved_at     TIMESTAMPTZ,
  CONSTRAINT chk_alert_type     CHECK (alert_type IN ('LOW_STOCK','OVERSTOCK','PENDING_INSPECTION','PRODUCTION_DELAY','MATERIAL_SHORTAGE','AGING_BATCH','PENDING_PO','LEDGER_MISMATCH')),
  CONSTRAINT chk_alert_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL'))
);
CREATE UNIQUE INDEX ux_alert_open ON alerts(open_key) WHERE resolved_at IS NULL;
CREATE INDEX ix_alert_type ON alerts(alert_type) WHERE resolved_at IS NULL;
