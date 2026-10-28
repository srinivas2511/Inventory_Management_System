-- Phase 10: Advanced Purchase — RFQ, supplier quotes, PO extensions, purchase invoices, purchase returns
-- Migration V66 (maps to DESIGN-ERP V14)

-- ── RFQ ──────────────────────────────────────────────────────────────────────
CREATE TABLE ims.rfqs (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id         BIGINT        NOT NULL DEFAULT 1,
    doc_no             VARCHAR(40)   NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
                           CHECK (status IN ('DRAFT','SENT','CLOSED','CANCELLED')),
    requisition_id     BIGINT,
    due_date           DATE,
    delivery_location  VARCHAR(200),
    terms_id           BIGINT,
    notes              VARCHAR(500),
    created_by         BIGINT        NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by         BIGINT,
    version            BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (company_id, doc_no)
);

CREATE TABLE ims.rfq_items (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rfq_id          BIGINT        NOT NULL REFERENCES ims.rfqs ON DELETE CASCADE,
    line_no         INT           NOT NULL,
    material_id     BIGINT,
    product_id      BIGINT,
    description     VARCHAR(200),
    qty             NUMERIC(18,3) NOT NULL CHECK (qty > 0),
    uom_id          BIGINT,
    required_date   DATE,
    UNIQUE (rfq_id, line_no)
);

CREATE TABLE ims.rfq_suppliers (
    rfq_id          BIGINT        NOT NULL REFERENCES ims.rfqs ON DELETE CASCADE,
    supplier_id     BIGINT        NOT NULL,
    invited_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    quote_status    VARCHAR(12)   NOT NULL DEFAULT 'PENDING'
                        CHECK (quote_status IN ('PENDING','RECEIVED','DECLINED')),
    PRIMARY KEY (rfq_id, supplier_id)
);

-- ── Supplier Quotes ───────────────────────────────────────────────────────────
CREATE TABLE ims.supplier_quotes (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id       BIGINT        NOT NULL DEFAULT 1,
    doc_no           VARCHAR(40)   NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
                         CHECK (status IN ('DRAFT','SUBMITTED','AWARDED','REJECTED','CANCELLED')),
    rfq_id           BIGINT        NOT NULL REFERENCES ims.rfqs,
    supplier_id      BIGINT        NOT NULL,
    valid_until      DATE,
    lead_time_days   INT,
    freight          NUMERIC(18,4) NOT NULL DEFAULT 0,
    payment_terms_id BIGINT,
    notes            VARCHAR(500),
    awarded          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by       BIGINT        NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by       BIGINT,
    version          BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (rfq_id, supplier_id),
    UNIQUE (company_id, doc_no)
);

CREATE TABLE ims.supplier_quote_items (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quote_id      BIGINT        NOT NULL REFERENCES ims.supplier_quotes ON DELETE CASCADE,
    rfq_item_id   BIGINT        NOT NULL REFERENCES ims.rfq_items,
    line_no       INT           NOT NULL,
    unit_price    NUMERIC(18,4) NOT NULL DEFAULT 0,
    tax_pct       NUMERIC(9,4)  NOT NULL DEFAULT 0,
    moq           NUMERIC(18,3),
    awarded_qty   NUMERIC(18,3) NOT NULL DEFAULT 0,
    notes         VARCHAR(200),
    UNIQUE (quote_id, line_no)
);

-- ── PO extensions ─────────────────────────────────────────────────────────────
ALTER TABLE ims.purchase_orders
    ADD COLUMN IF NOT EXISTS po_kind           VARCHAR(10) NOT NULL DEFAULT 'STANDARD'
                                                   CHECK (po_kind IN ('STANDARD','FOREIGN','ADVANCE')),
    ADD COLUMN IF NOT EXISTS currency          VARCHAR(3)  NOT NULL DEFAULT 'INR',
    ADD COLUMN IF NOT EXISTS exchange_rate     NUMERIC(18,8) NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS rfq_id            BIGINT,
    ADD COLUMN IF NOT EXISTS branch_id         BIGINT,
    ADD COLUMN IF NOT EXISTS revision_no       INT         NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS root_document_id  BIGINT,
    ADD COLUMN IF NOT EXISTS payment_terms_id  BIGINT,
    ADD COLUMN IF NOT EXISTS advance_pct       NUMERIC(9,4),
    ADD COLUMN IF NOT EXISTS advance_amount    NUMERIC(18,4),
    ADD COLUMN IF NOT EXISTS incoterm          VARCHAR(10),
    ADD COLUMN IF NOT EXISTS custom            JSONB       NOT NULL DEFAULT '{}';

ALTER TABLE ims.purchase_order_items
    ADD COLUMN IF NOT EXISTS hsn_sac        VARCHAR(10),
    ADD COLUMN IF NOT EXISTS tax_code_id    BIGINT,
    ADD COLUMN IF NOT EXISTS discount_pct   NUMERIC(9,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cgst_rate      NUMERIC(9,4),
    ADD COLUMN IF NOT EXISTS sgst_rate      NUMERIC(9,4),
    ADD COLUMN IF NOT EXISTS igst_rate      NUMERIC(9,4),
    ADD COLUMN IF NOT EXISTS rfq_item_id    BIGINT;

CREATE TABLE ims.po_revisions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    po_id        BIGINT        NOT NULL REFERENCES ims.purchase_orders,
    revision_no  INT           NOT NULL,
    snapshot     JSONB         NOT NULL,
    reason       VARCHAR(500)  NOT NULL,
    created_by   BIGINT,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (po_id, revision_no)
);

-- ── GRN extensions ────────────────────────────────────────────────────────────
ALTER TABLE ims.goods_receipts
    ADD COLUMN IF NOT EXISTS inward_kind      VARCHAR(12) NOT NULL DEFAULT 'PO'
                                                  CHECK (inward_kind IN ('PO','NO_PO','JOB_WORK','SERVICE','RETURN')),
    ADD COLUMN IF NOT EXISTS supplier_dc_no   VARCHAR(40),
    ADD COLUMN IF NOT EXISTS supplier_dc_date DATE,
    ADD COLUMN IF NOT EXISTS vehicle_no       VARCHAR(20),
    ADD COLUMN IF NOT EXISTS transporter_id   BIGINT;

-- ── Purchase Invoices ─────────────────────────────────────────────────────────
CREATE TABLE ims.purchase_invoices (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id            BIGINT        NOT NULL DEFAULT 1,
    doc_no                VARCHAR(40)   NOT NULL,
    status                VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
                              CHECK (status IN ('DRAFT','OPEN','PARTIALLY_PAID','PAID','CANCELLED')),
    supplier_id           BIGINT        NOT NULL,
    supplier_invoice_no   VARCHAR(40)   NOT NULL,
    supplier_invoice_date DATE          NOT NULL,
    po_id                 BIGINT        REFERENCES ims.purchase_orders,
    place_of_supply       VARCHAR(2),
    reverse_charge        BOOLEAN       NOT NULL DEFAULT FALSE,
    tds_amount            NUMERIC(18,4) NOT NULL DEFAULT 0,
    due_date              DATE,
    match_status          VARCHAR(12)   NOT NULL DEFAULT 'MATCHED'
                              CHECK (match_status IN ('MATCHED','EXCEPTION','OVERRIDE')),
    journal_entry_id      BIGINT,
    ap_balance            NUMERIC(18,4),
    subtotal              NUMERIC(18,4) NOT NULL DEFAULT 0,
    tax_total             NUMERIC(18,4) NOT NULL DEFAULT 0,
    total                 NUMERIC(18,4) NOT NULL DEFAULT 0,
    paid_amount           NUMERIC(18,4) NOT NULL DEFAULT 0,
    created_by            BIGINT        NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by            BIGINT,
    version               BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (company_id, supplier_id, supplier_invoice_no)
);

CREATE TABLE ims.purchase_invoice_items (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id      BIGINT        NOT NULL REFERENCES ims.purchase_invoices ON DELETE CASCADE,
    line_no         INT           NOT NULL,
    po_item_id      BIGINT,
    grn_item_id     BIGINT,
    material_id     BIGINT,
    product_id      BIGINT,
    description     VARCHAR(200),
    qty             NUMERIC(18,3) NOT NULL,
    unit_price      NUMERIC(18,4) NOT NULL,
    discount_pct    NUMERIC(9,4)  NOT NULL DEFAULT 0,
    cgst_rate       NUMERIC(9,4)  NOT NULL DEFAULT 0,
    sgst_rate       NUMERIC(9,4)  NOT NULL DEFAULT 0,
    igst_rate       NUMERIC(9,4)  NOT NULL DEFAULT 0,
    line_total      NUMERIC(18,4) NOT NULL,
    match_status    VARCHAR(12),
    qty_variance    NUMERIC(18,3),
    price_variance  NUMERIC(18,4),
    UNIQUE (invoice_id, line_no)
);

CREATE TABLE ims.match_exceptions (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id       BIGINT        NOT NULL REFERENCES ims.purchase_invoices,
    invoice_item_id  BIGINT,
    kind             VARCHAR(12)   NOT NULL CHECK (kind IN ('QTY','PRICE','MISSING_GRN')),
    detail           VARCHAR(300),
    resolved_by      BIGINT,
    resolved_at      TIMESTAMPTZ,
    resolution_reason VARCHAR(500)
);

-- ── Purchase Returns ──────────────────────────────────────────────────────────
CREATE TABLE ims.purchase_returns (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT        NOT NULL DEFAULT 1,
    doc_no          VARCHAR(40)   NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT','POSTED','CANCELLED')),
    supplier_id     BIGINT        NOT NULL,
    grn_id          BIGINT        REFERENCES ims.goods_receipts,
    invoice_id      BIGINT        REFERENCES ims.purchase_invoices,
    reason_code     VARCHAR(20),
    notes           VARCHAR(500),
    debit_note_id   BIGINT,
    created_by      BIGINT        NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (company_id, doc_no)
);

CREATE TABLE ims.purchase_return_items (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    return_id       BIGINT        NOT NULL REFERENCES ims.purchase_returns ON DELETE CASCADE,
    line_no         INT           NOT NULL,
    material_id     BIGINT,
    product_id      BIGINT,
    description     VARCHAR(200),
    qty             NUMERIC(18,3) NOT NULL CHECK (qty > 0),
    uom_id          BIGINT,
    unit_price      NUMERIC(18,4) NOT NULL DEFAULT 0,
    batch_id        BIGINT,
    location_id     BIGINT        NOT NULL,
    ledger_txn_id   BIGINT,
    UNIQUE (return_id, line_no)
);

-- ── Permissions ───────────────────────────────────────────────────────────────
INSERT INTO ims.permissions (code, module, description) VALUES
    ('RFQ_VIEW',                  'PROCUREMENT', 'View RFQs'),
    ('RFQ_CREATE',                'PROCUREMENT', 'Create RFQs'),
    ('RFQ_UPDATE',                'PROCUREMENT', 'Update RFQs'),
    ('RFQ_SEND',                  'PROCUREMENT', 'Send RFQ to suppliers'),
    ('RFQ_AWARD',                 'PROCUREMENT', 'Award RFQ — create PO drafts'),
    ('PURCHASE_AMEND',            'PROCUREMENT', 'Amend purchase orders (revision)'),
    ('PURCHASE_INVOICE_VIEW',     'FINANCE',     'View purchase invoices'),
    ('PURCHASE_INVOICE_CREATE',   'FINANCE',     'Create purchase invoices'),
    ('PURCHASE_INVOICE_UPDATE',   'FINANCE',     'Update purchase invoices'),
    ('PURCHASE_INVOICE_POST',     'FINANCE',     'Post purchase invoices'),
    ('PURCHASE_INVOICE_OVERRIDE', 'FINANCE',     'Override three-way match exception'),
    ('PURCHASE_RETURN_VIEW',      'PROCUREMENT', 'View purchase returns'),
    ('PURCHASE_RETURN_CREATE',    'PROCUREMENT', 'Create purchase returns'),
    ('PURCHASE_RETURN_POST',      'PROCUREMENT', 'Post purchase returns')
ON CONFLICT (code) DO NOTHING;

INSERT INTO ims.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ims.roles r, ims.permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN (
    'RFQ_VIEW','RFQ_CREATE','RFQ_UPDATE','RFQ_SEND','RFQ_AWARD',
    'PURCHASE_AMEND',
    'PURCHASE_INVOICE_VIEW','PURCHASE_INVOICE_CREATE','PURCHASE_INVOICE_UPDATE',
    'PURCHASE_INVOICE_POST','PURCHASE_INVOICE_OVERRIDE',
    'PURCHASE_RETURN_VIEW','PURCHASE_RETURN_CREATE','PURCHASE_RETURN_POST'
  )
ON CONFLICT DO NOTHING;

-- Grant to PURCHASE_MANAGER role (if it exists)
INSERT INTO ims.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ims.roles r, ims.permissions p
WHERE r.code = 'PURCHASE_MANAGER'
  AND p.code IN (
    'RFQ_VIEW','RFQ_CREATE','RFQ_UPDATE','RFQ_SEND','RFQ_AWARD',
    'PURCHASE_AMEND',
    'PURCHASE_INVOICE_VIEW','PURCHASE_INVOICE_CREATE','PURCHASE_INVOICE_UPDATE',
    'PURCHASE_INVOICE_POST',
    'PURCHASE_RETURN_VIEW','PURCHASE_RETURN_CREATE','PURCHASE_RETURN_POST'
  )
ON CONFLICT DO NOTHING;
