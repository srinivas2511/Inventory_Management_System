-- Phase 2 — Inventory core: batches, balances, ledger + immutability triggers (DESIGN.md §2.5)
SET search_path TO ims;

-- ── Material batches ──────────────────────────────────────────────────────────
CREATE TABLE material_batches (
  id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  batch_number         VARCHAR(30)   NOT NULL,
  material_id          BIGINT        NOT NULL REFERENCES materials(id),
  supplier_id          BIGINT        REFERENCES suppliers(id),
  purchase_order_id    BIGINT,
  goods_receipt_item_id BIGINT,
  manufacturing_date   DATE,
  received_date        DATE          NOT NULL,
  expiry_date          DATE,
  received_qty         NUMERIC(18,3) NOT NULL,
  remaining_qty        NUMERIC(18,3) NOT NULL,
  heat_number          VARCHAR(40),
  certificate_number   VARCHAR(40),
  unit_cost            NUMERIC(18,4),
  status               VARCHAR(15)   NOT NULL DEFAULT 'QUARANTINE',
  created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  created_by           BIGINT,
  updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_by           BIGINT,
  version              BIGINT        NOT NULL DEFAULT 0,
  company_id           BIGINT        NOT NULL DEFAULT 1,
  CONSTRAINT uq_material_batch_number UNIQUE (batch_number),
  CONSTRAINT chk_mb_received_qty      CHECK (received_qty > 0),
  CONSTRAINT chk_mb_remaining_qty     CHECK (remaining_qty >= 0),
  CONSTRAINT chk_mb_status            CHECK (status IN ('AVAILABLE','QUARANTINE','REJECTED','CONSUMED','EXPIRED','HOLD'))
);
CREATE INDEX ix_mb_material ON material_batches(material_id);
CREATE INDEX ix_mb_status   ON material_batches(status);

-- ── FG batches ────────────────────────────────────────────────────────────────
CREATE TABLE fg_batches (
  id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  batch_number         VARCHAR(30)   NOT NULL,
  product_id           BIGINT        NOT NULL REFERENCES products(id),
  production_order_id  BIGINT        NOT NULL,
  produced_qty         NUMERIC(18,3) NOT NULL,
  approved_qty         NUMERIC(18,3) NOT NULL DEFAULT 0,
  rejected_qty         NUMERIC(18,3) NOT NULL DEFAULT 0,
  remaining_qty        NUMERIC(18,3) NOT NULL DEFAULT 0,
  produced_on          DATE          NOT NULL,
  quality_status       VARCHAR(25)   NOT NULL DEFAULT 'PRODUCTION_COMPLETED',
  unit_cost            NUMERIC(18,4),
  created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  created_by           BIGINT,
  updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_by           BIGINT,
  version              BIGINT        NOT NULL DEFAULT 0,
  company_id           BIGINT        NOT NULL DEFAULT 1,
  CONSTRAINT uq_fg_batch_number  UNIQUE (batch_number),
  CONSTRAINT chk_fg_produced_qty CHECK (produced_qty > 0),
  CONSTRAINT chk_fg_qty_sum      CHECK (approved_qty + rejected_qty <= produced_qty)
);
CREATE INDEX ix_fgb_product ON fg_batches(product_id);
CREATE INDEX ix_fgb_quality  ON fg_batches(quality_status);

-- ── FG batch material source links (order-level traceability) ─────────────────
CREATE TABLE fg_batch_material_sources (
  fg_batch_id       BIGINT        NOT NULL REFERENCES fg_batches(id),
  material_batch_id BIGINT        NOT NULL REFERENCES material_batches(id),
  quantity          NUMERIC(18,3) NOT NULL,
  PRIMARY KEY (fg_batch_id, material_batch_id)
);

-- ── Inventory balances ────────────────────────────────────────────────────────
CREATE TABLE inventory_balances (
  id                  BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  warehouse_id        BIGINT        NOT NULL REFERENCES warehouses(id),
  location_id         BIGINT        NOT NULL REFERENCES warehouse_locations(id),
  item_type           VARCHAR(10)   NOT NULL,
  material_id         BIGINT        REFERENCES materials(id),
  product_id          BIGINT        REFERENCES products(id),
  material_batch_id   BIGINT        REFERENCES material_batches(id),
  fg_batch_id         BIGINT        REFERENCES fg_batches(id),
  stock_status        VARCHAR(20)   NOT NULL,
  qty_on_hand         NUMERIC(18,3) NOT NULL DEFAULT 0,
  qty_reserved        NUMERIC(18,3) NOT NULL DEFAULT 0,
  avg_unit_cost       NUMERIC(18,4) NOT NULL DEFAULT 0,
  updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
  version             BIGINT        NOT NULL DEFAULT 0,
  company_id          BIGINT        NOT NULL DEFAULT 1,
  CONSTRAINT chk_ib_item_type CHECK (
    (item_type = 'MATERIAL' AND material_id IS NOT NULL AND product_id IS NULL) OR
    (item_type = 'PRODUCT'  AND product_id  IS NOT NULL AND material_id IS NULL)
  ),
  CONSTRAINT chk_ib_reserved CHECK (qty_reserved >= 0 AND qty_reserved <= GREATEST(qty_on_hand, 0))
);
CREATE UNIQUE INDEX ux_balance_key ON inventory_balances
  (warehouse_id, location_id, item_type,
   COALESCE(material_id, 0), COALESCE(product_id, 0),
   COALESCE(material_batch_id, 0), COALESCE(fg_batch_id, 0),
   stock_status);
CREATE INDEX ix_ib_material ON inventory_balances(material_id) WHERE material_id IS NOT NULL;
CREATE INDEX ix_ib_product  ON inventory_balances(product_id)  WHERE product_id IS NOT NULL;
CREATE INDEX ix_ib_warehouse ON inventory_balances(warehouse_id);

-- ── Inventory transactions (ledger) ──────────────────────────────────────────
CREATE TABLE inventory_transactions (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  transaction_number  VARCHAR(25)   NOT NULL,
  transaction_date    TIMESTAMPTZ   NOT NULL DEFAULT now(),
  transaction_type    VARCHAR(25)   NOT NULL,
  item_type           VARCHAR(10)   NOT NULL,
  material_id         BIGINT,
  product_id          BIGINT,
  material_batch_id   BIGINT,
  fg_batch_id         BIGINT,
  warehouse_id        BIGINT        NOT NULL,
  location_id         BIGINT        NOT NULL,
  stock_status        VARCHAR(20)   NOT NULL,
  quantity            NUMERIC(18,3) NOT NULL,
  uom                 VARCHAR(10)   NOT NULL,
  unit_cost           NUMERIC(18,4) NOT NULL DEFAULT 0,
  total_cost          NUMERIC(18,4) NOT NULL DEFAULT 0,
  reference_type      VARCHAR(30)   NOT NULL,
  reference_id        BIGINT        NOT NULL,
  reference_number    VARCHAR(30),
  transfer_group_id   BIGINT,
  reversal_of_id      BIGINT        REFERENCES inventory_transactions(id),
  balance_after       NUMERIC(18,3) NOT NULL,
  remarks             VARCHAR(500),
  created_by          BIGINT        NOT NULL,
  created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
  CONSTRAINT uq_tx_number   UNIQUE (transaction_number),
  CONSTRAINT chk_tx_qty     CHECK (quantity <> 0),
  CONSTRAINT chk_tx_item    CHECK (
    (item_type = 'MATERIAL' AND material_id IS NOT NULL AND product_id IS NULL) OR
    (item_type = 'PRODUCT'  AND product_id  IS NOT NULL AND material_id IS NULL)
  )
);
CREATE INDEX ix_tx_item   ON inventory_transactions(item_type, material_id, product_id, transaction_date);
CREATE INDEX ix_tx_batch  ON inventory_transactions(material_batch_id, fg_batch_id);
CREATE INDEX ix_tx_ref    ON inventory_transactions(reference_type, reference_id);
CREATE INDEX ix_tx_date_brin ON inventory_transactions USING BRIN (transaction_date);

-- ── Immutability trigger: blocks UPDATE / DELETE on the ledger ────────────────
CREATE OR REPLACE FUNCTION ims.fn_ledger_immutable()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Inventory transactions are immutable: use a reversal entry (INV-05)';
END;
$$;

CREATE TRIGGER trg_tx_immutable
  BEFORE UPDATE OR DELETE ON inventory_transactions
  FOR EACH ROW EXECUTE FUNCTION ims.fn_ledger_immutable();

-- ── Immutability trigger: blocks UPDATE / DELETE on inventory_balances directly
-- (only the posting service uses ON CONFLICT / FOR UPDATE, never direct UPDATE from outside)
-- We protect by convention + ArchUnit rule, not a DB trigger on balances (they legitimately update).
