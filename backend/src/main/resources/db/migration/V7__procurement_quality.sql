-- Phase 3: Procurement and Incoming Quality (DESIGN.md §2.6, §2.8)
-- purchase_requisitions, purchase_orders, goods_receipts,
-- inspection_plans, inspection_plan_parameters, quality_inspections,
-- quality_inspection_items, rejections

SET search_path = ims;

-- ─── Purchase Requisitions ────────────────────────────────────────────────────
CREATE TABLE purchase_requisitions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pr_number       VARCHAR(20)  NOT NULL UNIQUE,
    material_id     BIGINT       NOT NULL REFERENCES materials(id),
    requested_qty   NUMERIC(18,3) NOT NULL CHECK (requested_qty > 0),
    uom             VARCHAR(10)  NOT NULL,
    needed_by       DATE,
    reason          VARCHAR(500),
    status          VARCHAR(15)  NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN ('OPEN','CONVERTED','CANCELLED')),
    requested_by    BIGINT       NOT NULL,
    converted_po_id BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT       NOT NULL DEFAULT 0,
    company_id      BIGINT       NOT NULL DEFAULT 1
);

-- ─── Purchase Orders ──────────────────────────────────────────────────────────
CREATE TABLE purchase_orders (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    po_number               VARCHAR(20)  NOT NULL UNIQUE,
    supplier_id             BIGINT       NOT NULL REFERENCES suppliers(id),
    po_date                 DATE         NOT NULL,
    expected_delivery_date  DATE,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                                CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','SENT_TO_SUPPLIER',
                                                  'PARTIALLY_RECEIVED','RECEIVED','CANCELLED')),
    subtotal                NUMERIC(18,4) NOT NULL DEFAULT 0,
    tax_total               NUMERIC(18,4) NOT NULL DEFAULT 0,
    grand_total             NUMERIC(18,4) NOT NULL DEFAULT 0,
    currency                VARCHAR(3)      NOT NULL DEFAULT 'INR',
    payment_terms           VARCHAR(60),
    remarks                 VARCHAR(500),
    requisition_id          BIGINT       REFERENCES purchase_requisitions(id),
    submitted_by            BIGINT,
    approved_by             BIGINT,
    approved_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              BIGINT,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by              BIGINT,
    version                 BIGINT       NOT NULL DEFAULT 0,
    company_id              BIGINT       NOT NULL DEFAULT 1
);

CREATE TABLE purchase_order_items (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purchase_order_id   BIGINT       NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    line_no             INT          NOT NULL,
    material_id         BIGINT       NOT NULL REFERENCES materials(id),
    ordered_qty         NUMERIC(18,3) NOT NULL CHECK (ordered_qty > 0),
    received_qty        NUMERIC(18,3) NOT NULL DEFAULT 0,
    accepted_qty        NUMERIC(18,3) NOT NULL DEFAULT 0,
    rejected_qty        NUMERIC(18,3) NOT NULL DEFAULT 0,
    rate                NUMERIC(18,4) NOT NULL CHECK (rate >= 0),
    tax_pct             NUMERIC(5,2) NOT NULL DEFAULT 0,
    line_total          NUMERIC(18,4) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          BIGINT,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          BIGINT,
    version             BIGINT       NOT NULL DEFAULT 0,
    company_id          BIGINT       NOT NULL DEFAULT 1,
    UNIQUE (purchase_order_id, line_no)
);

-- ─── Goods Receipts ───────────────────────────────────────────────────────────
CREATE TABLE goods_receipts (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    gr_number           VARCHAR(20)  NOT NULL UNIQUE,
    purchase_order_id   BIGINT       NOT NULL REFERENCES purchase_orders(id),
    receipt_date        DATE         NOT NULL,
    supplier_challan_no VARCHAR(40),
    vehicle_no          VARCHAR(20),
    warehouse_id        BIGINT       NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'POSTED',
    received_by         BIGINT       NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          BIGINT,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          BIGINT,
    version             BIGINT       NOT NULL DEFAULT 0,
    company_id          BIGINT       NOT NULL DEFAULT 1
);

CREATE TABLE goods_receipt_items (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    goods_receipt_id    BIGINT       NOT NULL REFERENCES goods_receipts(id) ON DELETE CASCADE,
    po_item_id          BIGINT       NOT NULL REFERENCES purchase_order_items(id),
    material_id         BIGINT       NOT NULL,
    received_qty        NUMERIC(18,3) NOT NULL CHECK (received_qty > 0),
    location_id         BIGINT       NOT NULL,
    heat_number         VARCHAR(40),
    certificate_number  VARCHAR(40),
    manufacturing_date  DATE,
    material_batch_id   BIGINT       REFERENCES material_batches(id),
    inspection_required BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          BIGINT,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          BIGINT,
    version             BIGINT       NOT NULL DEFAULT 0,
    company_id          BIGINT       NOT NULL DEFAULT 1
);

-- ─── Inspection plans ─────────────────────────────────────────────────────────
CREATE TABLE inspection_plans (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_code       VARCHAR(20)  UNIQUE,
    inspection_type VARCHAR(12)  NOT NULL CHECK (inspection_type IN ('INCOMING','IN_PROCESS','FINAL')),
    product_id      BIGINT       REFERENCES products(id),
    material_id     BIGINT       REFERENCES materials(id),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT       NOT NULL DEFAULT 0,
    company_id      BIGINT       NOT NULL DEFAULT 1
);

CREATE TABLE inspection_plan_parameters (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id         BIGINT       NOT NULL REFERENCES inspection_plans(id) ON DELETE CASCADE,
    seq             INT          NOT NULL,
    parameter_name  VARCHAR(60)  NOT NULL,
    specification   VARCHAR(80),
    nominal         NUMERIC(14,4),
    lower_limit     NUMERIC(14,4),
    upper_limit     NUMERIC(14,4),
    unit            VARCHAR(10),
    is_attribute    BOOLEAN      NOT NULL DEFAULT FALSE,
    mandatory       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT       NOT NULL DEFAULT 0,
    company_id      BIGINT       NOT NULL DEFAULT 1
);

-- ─── Quality Inspections ──────────────────────────────────────────────────────
CREATE TABLE quality_inspections (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inspection_number       VARCHAR(20)  NOT NULL UNIQUE,
    inspection_type         VARCHAR(12)  NOT NULL CHECK (inspection_type IN ('INCOMING','IN_PROCESS','FINAL')),
    status                  VARCHAR(12)  NOT NULL DEFAULT 'PENDING'
                                CHECK (status IN ('PENDING','IN_PROGRESS','APPROVED','REJECTED','HOLD')),
    goods_receipt_item_id   BIGINT       REFERENCES goods_receipt_items(id),
    material_id             BIGINT       REFERENCES materials(id),
    material_batch_id       BIGINT       REFERENCES material_batches(id),
    production_order_id     BIGINT,
    production_operation_id BIGINT,
    fg_batch_id             BIGINT,
    inspected_qty           NUMERIC(18,3) NOT NULL,
    sample_size             INT,
    accepted_qty            NUMERIC(18,3) NOT NULL DEFAULT 0,
    rejected_qty            NUMERIC(18,3) NOT NULL DEFAULT 0,
    hold_qty                NUMERIC(18,3) NOT NULL DEFAULT 0,
    inspector_id            BIGINT,
    inspected_at            TIMESTAMPTZ,
    decision_by             BIGINT,
    decision_at             TIMESTAMPTZ,
    remarks                 VARCHAR(500),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              BIGINT,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by              BIGINT,
    version                 BIGINT       NOT NULL DEFAULT 0,
    company_id              BIGINT       NOT NULL DEFAULT 1,
    CHECK (accepted_qty + rejected_qty + hold_qty <= inspected_qty)
);

CREATE TABLE quality_inspection_items (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inspection_id   BIGINT       NOT NULL REFERENCES quality_inspections(id) ON DELETE CASCADE,
    seq             INT          NOT NULL,
    parameter_name  VARCHAR(60)  NOT NULL,
    specification   VARCHAR(80),
    lower_limit     NUMERIC(14,4),
    upper_limit     NUMERIC(14,4),
    actual_value    NUMERIC(14,4),
    actual_text     VARCHAR(80),
    unit            VARCHAR(10),
    result          VARCHAR(12)  NOT NULL DEFAULT 'NOT_MEASURED'
                        CHECK (result IN ('PASS','FAIL','NOT_MEASURED')),
    deviation_note  VARCHAR(300),
    mandatory       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT       NOT NULL DEFAULT 0,
    company_id      BIGINT       NOT NULL DEFAULT 1,
    UNIQUE (inspection_id, seq)
);

-- ─── Rejections ───────────────────────────────────────────────────────────────
CREATE TABLE rejections (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inspection_id           BIGINT       REFERENCES quality_inspections(id),
    production_output_id    BIGINT,
    rejection_reason_id     BIGINT       NOT NULL REFERENCES rejection_reasons(id),
    item_type               VARCHAR(10),
    material_id             BIGINT,
    product_id              BIGINT,
    batch_id                BIGINT,
    quantity                NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
    disposition             VARCHAR(15)  NOT NULL DEFAULT 'SCRAP'
                                CHECK (disposition IN ('SCRAP','REWORK','RETURN_TO_SUPPLIER')),
    inventory_transaction_id BIGINT,
    recorded_by             BIGINT       NOT NULL,
    recorded_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              BIGINT,
    version                 BIGINT       NOT NULL DEFAULT 0,
    company_id              BIGINT       NOT NULL DEFAULT 1
);

-- ─── Indexes ──────────────────────────────────────────────────────────────────
CREATE INDEX ix_po_supplier  ON purchase_orders (supplier_id, status);
CREATE INDEX ix_po_date      ON purchase_orders (po_date);
CREATE INDEX ix_poi_material ON purchase_order_items (material_id);
CREATE INDEX ix_gr_po        ON goods_receipts (purchase_order_id);
CREATE INDEX ix_qi_type      ON quality_inspections (inspection_type, status);
CREATE INDEX ix_qi_batch     ON quality_inspections (material_batch_id);
CREATE INDEX ix_qi_gr_item   ON quality_inspections (goods_receipt_item_id);
CREATE INDEX ix_rej_reason   ON rejections (rejection_reason_id);

-- ─── Number sequence seeds ────────────────────────────────────────────────────
INSERT INTO number_sequences (prefix, seq_year, last_value) VALUES
    ('PR', EXTRACT(YEAR FROM now())::INT, 0),
    ('PUR', EXTRACT(YEAR FROM now())::INT, 0),
    ('GR', EXTRACT(YEAR FROM now())::INT, 0),
    ('QI', EXTRACT(YEAR FROM now())::INT, 0)
ON CONFLICT DO NOTHING;
