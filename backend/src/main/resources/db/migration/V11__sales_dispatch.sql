-- V11: Sales Orders, Reservations, Dispatches, Customer Product Specs
-- Phase 6 — Sales and Dispatch

SET search_path = ims;

-- ─── Sales Orders ───────────────────────────────────────────────────────────

CREATE TABLE sales_orders (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_number     VARCHAR(20)  NOT NULL UNIQUE,
  customer_id      BIGINT       NOT NULL REFERENCES customers(id),
  order_date       DATE         NOT NULL DEFAULT CURRENT_DATE,
  required_date    DATE,
  delivery_address VARCHAR(400),
  payment_terms    VARCHAR(100),
  notes            VARCHAR(500),
  status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
  approved_by      BIGINT,
  approved_at      TIMESTAMPTZ,
  cancelled_by     BIGINT,
  cancelled_at     TIMESTAMPTZ,
  cancel_reason    VARCHAR(300),
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by       BIGINT,
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by       BIGINT,
  version          BIGINT       NOT NULL DEFAULT 0,
  company_id       BIGINT       NOT NULL DEFAULT 1
);

CREATE TABLE sales_order_items (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  sales_order_id   BIGINT          NOT NULL REFERENCES sales_orders(id) ON DELETE CASCADE,
  line_no          INT             NOT NULL,
  product_id       BIGINT          NOT NULL REFERENCES products(id),
  ordered_qty      NUMERIC(18,3)   NOT NULL CHECK (ordered_qty > 0),
  reserved_qty     NUMERIC(18,3)   NOT NULL DEFAULT 0,
  packed_qty       NUMERIC(18,3)   NOT NULL DEFAULT 0,
  dispatched_qty   NUMERIC(18,3)   NOT NULL DEFAULT 0,
  unit_price       NUMERIC(18,4),
  customer_spec_ref VARCHAR(100),
  notes            VARCHAR(300),
  created_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
  created_by       BIGINT,
  updated_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
  updated_by       BIGINT,
  version          BIGINT          NOT NULL DEFAULT 0,
  company_id       BIGINT          NOT NULL DEFAULT 1,
  UNIQUE (sales_order_id, line_no),
  CHECK (dispatched_qty <= ordered_qty)
);

-- ─── Reservations ───────────────────────────────────────────────────────────

CREATE TABLE sales_reservations (
  id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  sales_order_item_id  BIGINT          NOT NULL REFERENCES sales_order_items(id),
  fg_batch_id          BIGINT          NOT NULL REFERENCES fg_batches(id),
  warehouse_id         BIGINT          NOT NULL,
  location_id          BIGINT          NOT NULL,
  reserved_qty         NUMERIC(18,3)   NOT NULL CHECK (reserved_qty > 0),
  status               VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE',
  reserved_by          BIGINT,
  expires_at           TIMESTAMPTZ,
  created_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
  created_by           BIGINT,
  updated_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
  updated_by           BIGINT,
  version              BIGINT          NOT NULL DEFAULT 0,
  company_id           BIGINT          NOT NULL DEFAULT 1
);

CREATE INDEX ix_reservation_batch ON sales_reservations (fg_batch_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_reservation_soitem ON sales_reservations (sales_order_item_id) WHERE status = 'ACTIVE';

-- ─── Dispatches ─────────────────────────────────────────────────────────────

CREATE TABLE dispatches (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  dispatch_number   VARCHAR(20)  NOT NULL UNIQUE,
  sales_order_id    BIGINT       NOT NULL REFERENCES sales_orders(id),
  customer_id       BIGINT       NOT NULL REFERENCES customers(id),
  dispatch_date     DATE         NOT NULL DEFAULT CURRENT_DATE,
  transporter       VARCHAR(100),
  vehicle_no        VARCHAR(20),
  lr_number         VARCHAR(30),
  delivery_address  VARCHAR(400) NOT NULL,
  invoice_reference VARCHAR(40),
  status            VARCHAR(12)  NOT NULL DEFAULT 'DISPATCHED',
  dispatched_by     BIGINT       NOT NULL,
  notes             VARCHAR(300),
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by        BIGINT,
  updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by        BIGINT,
  version           BIGINT       NOT NULL DEFAULT 0,
  company_id        BIGINT       NOT NULL DEFAULT 1
);

CREATE TABLE dispatch_items (
  id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  dispatch_id          BIGINT          NOT NULL REFERENCES dispatches(id) ON DELETE CASCADE,
  sales_order_item_id  BIGINT          NOT NULL REFERENCES sales_order_items(id),
  product_id           BIGINT          NOT NULL REFERENCES products(id),
  fg_batch_id          BIGINT          NOT NULL REFERENCES fg_batches(id),
  quantity             NUMERIC(18,3)   NOT NULL CHECK (quantity > 0),
  warehouse_id         BIGINT,
  location_id          BIGINT,
  unit_price           NUMERIC(18,4),
  created_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
  created_by           BIGINT,
  updated_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
  updated_by           BIGINT,
  version              BIGINT          NOT NULL DEFAULT 0,
  company_id           BIGINT          NOT NULL DEFAULT 1
);

-- ─── Customer-specific product specs ────────────────────────────────────────

CREATE TABLE customer_product_specs (
  id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  customer_id          BIGINT       NOT NULL REFERENCES customers(id),
  product_id           BIGINT       NOT NULL REFERENCES products(id),
  spec_reference       VARCHAR(100),
  drawing_number       VARCHAR(50),
  customer_part_number VARCHAR(50),
  notes                VARCHAR(500),
  active               BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by           BIGINT,
  updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by           BIGINT,
  version              BIGINT       NOT NULL DEFAULT 0,
  company_id           BIGINT       NOT NULL DEFAULT 1,
  UNIQUE (customer_id, product_id)
);

-- ─── System settings ─────────────────────────────────────────────────────────

INSERT INTO system_settings (key, value, description) VALUES
  ('sales.reservation.max_days',       '30',    'Days before an unused reservation is auto-released'),
  ('sales.dispatch.require_reservation','false', 'If true, dispatch is only allowed against an active reservation')
ON CONFLICT (key) DO NOTHING;
