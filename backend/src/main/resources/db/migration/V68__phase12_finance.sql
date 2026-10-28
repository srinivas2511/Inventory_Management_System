-- Phase 12 — Finance: currencies, TDS, sales invoices, payments, open items, adjustment notes

SET search_path TO ims;

-- 1. Currencies master
CREATE TABLE currencies (
    code        VARCHAR(3) PRIMARY KEY,
    name        VARCHAR(40) NOT NULL,
    symbol      VARCHAR(5),
    decimals    INT NOT NULL DEFAULT 2,
    active      BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO currencies (code, name, symbol, decimals, active) VALUES
    ('INR', 'Indian Rupee',   '₹', 2, true),
    ('USD', 'US Dollar',      '$', 2, true),
    ('EUR', 'Euro',           '€', 2, true),
    ('GBP', 'British Pound',  '£', 2, true),
    ('AED', 'UAE Dirham',     NULL, 2, true)
ON CONFLICT DO NOTHING;

-- 2. TDS sections
CREATE TABLE tds_sections (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id          BIGINT NOT NULL DEFAULT 1,
    section             VARCHAR(10) NOT NULL,
    description         VARCHAR(150),
    rate                NUMERIC(9,4) NOT NULL,
    rate_no_pan         NUMERIC(9,4),
    annual_threshold    NUMERIC(18,4),
    single_threshold    NUMERIC(18,4),
    effective_from      DATE NOT NULL,
    effective_to        DATE,
    UNIQUE (company_id, section)
);
INSERT INTO tds_sections (section, description, rate, effective_from) VALUES
    ('194C', 'Payments to Contractors',          1.0000, '2024-04-01'),
    ('194H', 'Commission or Brokerage',           5.0000, '2024-04-01'),
    ('194I', 'Rent (Plant & Machinery)',           2.0000, '2024-04-01'),
    ('194J', 'Professional/Technical Services',  10.0000, '2024-04-01')
ON CONFLICT DO NOTHING;

-- 3. Sales invoices
CREATE TABLE sales_invoices (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id          BIGINT NOT NULL DEFAULT 1,
    doc_no              VARCHAR(40) NOT NULL,
    doc_date            DATE NOT NULL,
    status              VARCHAR(25) NOT NULL DEFAULT 'DRAFT',
    invoice_kind        VARCHAR(20) NOT NULL DEFAULT 'TAX'
                            CHECK (invoice_kind IN ('TAX','PROFORMA','EXPORT','BILL_OF_SUPPLY')),
    customer_id         BIGINT NOT NULL REFERENCES customers(id),
    place_of_supply     VARCHAR(2),
    supply_type         VARCHAR(15) NOT NULL DEFAULT 'B2B'
                            CHECK (supply_type IN ('B2B','B2C','EXPORT_LUT','EXPORT_IGST','SEZ','DEEMED')),
    reverse_charge      BOOLEAN NOT NULL DEFAULT FALSE,
    sales_order_id      BIGINT REFERENCES sales_orders(id),
    oc_id               BIGINT REFERENCES order_confirmations(id),
    dispatch_id         BIGINT REFERENCES dispatches(id),
    customer_po_no      VARCHAR(60),
    payment_terms_id    BIGINT,
    due_date            DATE,
    currency            VARCHAR(3) NOT NULL DEFAULT 'INR',
    exchange_rate       NUMERIC(18,8) NOT NULL DEFAULT 1,
    taxable_amount      NUMERIC(18,4) NOT NULL DEFAULT 0,
    tax_amount          NUMERIC(18,4) NOT NULL DEFAULT 0,
    tcs_amount          NUMERIC(18,4) NOT NULL DEFAULT 0,
    grand_total         NUMERIC(18,4) NOT NULL DEFAULT 0,
    balance_inr         NUMERIC(18,4),
    einvoice_status     VARCHAR(14) NOT NULL DEFAULT 'NOT_REQUIRED',
    irn                 VARCHAR(64),
    ack_no              VARCHAR(30),
    eway_bill_no        VARCHAR(20),
    cancelled_reason    VARCHAR(300),
    journal_entry_id    BIGINT,
    posted_at           TIMESTAMPTZ,
    posted_by           BIGINT,
    remarks             VARCHAR(1000),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          BIGINT NOT NULL,
    updated_at          TIMESTAMPTZ DEFAULT now(),
    updated_by          BIGINT,
    version             BIGINT NOT NULL DEFAULT 0,
    UNIQUE (company_id, doc_no)
);

CREATE TABLE sales_invoice_items (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id      BIGINT NOT NULL REFERENCES sales_invoices(id) ON DELETE CASCADE,
    line_no         INT NOT NULL,
    item_kind       VARCHAR(10) NOT NULL DEFAULT 'PRODUCT',
    material_id     BIGINT REFERENCES materials(id),
    product_id      BIGINT REFERENCES products(id),
    description     VARCHAR(300),
    qty             NUMERIC(18,3) NOT NULL,
    uom             VARCHAR(10),
    rate            NUMERIC(18,4),
    discount_pct    NUMERIC(9,4) DEFAULT 0,
    taxable_value   NUMERIC(18,4),
    tax_amount      NUMERIC(18,4) DEFAULT 0,
    line_total      NUMERIC(18,4),
    hsn_sac         VARCHAR(10),
    dispatch_item_id BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL DEFAULT 1,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (invoice_id, line_no)
);

-- 4. Open items (AR/AP ageing)
CREATE TABLE open_items (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    item_type       VARCHAR(25) NOT NULL,
    party_type      VARCHAR(10) NOT NULL CHECK (party_type IN ('CUSTOMER','SUPPLIER')),
    party_id        BIGINT NOT NULL,
    source_type     VARCHAR(40) NOT NULL,
    source_id       BIGINT NOT NULL,
    doc_no          VARCHAR(40),
    doc_date        DATE NOT NULL,
    due_date        DATE,
    currency        VARCHAR(3) NOT NULL DEFAULT 'INR',
    amount          NUMERIC(18,4) NOT NULL,
    amount_inr      NUMERIC(18,4) NOT NULL,
    balance         NUMERIC(18,4) NOT NULL,
    balance_inr     NUMERIC(18,4) NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0,
    CHECK (balance >= 0)
);
CREATE INDEX ix_open_items_party ON open_items(party_type, party_id) WHERE balance > 0;

-- 5. Payments
CREATE TABLE payments (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    doc_no          VARCHAR(40) NOT NULL,
    doc_date        DATE NOT NULL,
    status          VARCHAR(18) NOT NULL DEFAULT 'DRAFT',
    direction       VARCHAR(8) NOT NULL CHECK (direction IN ('RECEIPT','PAYMENT')),
    kind            VARCHAR(10) NOT NULL DEFAULT 'REGULAR'
                        CHECK (kind IN ('REGULAR','ADVANCE','ON_ACCOUNT')),
    party_type      VARCHAR(10) NOT NULL,
    party_id        BIGINT NOT NULL,
    mode            VARCHAR(12) NOT NULL
                        CHECK (mode IN ('CASH','CHEQUE','NEFT','RTGS','UPI','CARD','GATEWAY','ADJUSTMENT')),
    reference_no    VARCHAR(60),
    currency        VARCHAR(3) NOT NULL DEFAULT 'INR',
    exchange_rate   NUMERIC(18,8) NOT NULL DEFAULT 1,
    amount          NUMERIC(18,4) NOT NULL CHECK (amount > 0),
    amount_inr      NUMERIC(18,4) NOT NULL,
    tds_amount      NUMERIC(18,4) NOT NULL DEFAULT 0,
    bank_charges    NUMERIC(18,4) NOT NULL DEFAULT 0,
    unallocated_inr NUMERIC(18,4) NOT NULL DEFAULT 0,
    journal_entry_id BIGINT,
    remarks         VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (company_id, doc_no)
);

CREATE TABLE payment_allocations (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id      BIGINT NOT NULL REFERENCES payments(id),
    open_item_id    BIGINT NOT NULL REFERENCES open_items(id),
    allocated       NUMERIC(18,4) NOT NULL CHECK (allocated > 0),
    allocated_inr   NUMERIC(18,4) NOT NULL,
    forex_diff_inr  NUMERIC(18,4) NOT NULL DEFAULT 0,
    allocated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    allocated_by    BIGINT,
    unallocated_at  TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0
);

-- 6. Adjustment notes (credit/debit notes)
CREATE TABLE adjustment_notes (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id              BIGINT NOT NULL DEFAULT 1,
    doc_no                  VARCHAR(40) NOT NULL,
    doc_date                DATE NOT NULL,
    status                  VARCHAR(25) NOT NULL DEFAULT 'DRAFT',
    note_kind               VARCHAR(8) NOT NULL CHECK (note_kind IN ('CREDIT','DEBIT')),
    side                    VARCHAR(8) NOT NULL CHECK (side IN ('SALES','PURCHASE')),
    party_id                BIGINT NOT NULL,
    original_invoice_type   VARCHAR(25),
    original_invoice_id     BIGINT,
    reason_code             VARCHAR(20) NOT NULL,
    taxable_amount          NUMERIC(18,4) NOT NULL DEFAULT 0,
    tax_amount              NUMERIC(18,4) NOT NULL DEFAULT 0,
    grand_total             NUMERIC(18,4) NOT NULL DEFAULT 0,
    journal_entry_id        BIGINT,
    einvoice_status         VARCHAR(14) NOT NULL DEFAULT 'NOT_REQUIRED',
    irn                     VARCHAR(64),
    remarks                 VARCHAR(1000),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              BIGINT NOT NULL,
    updated_at              TIMESTAMPTZ DEFAULT now(),
    updated_by              BIGINT,
    version                 BIGINT NOT NULL DEFAULT 0,
    UNIQUE (company_id, doc_no)
);

CREATE TABLE adjustment_note_items (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    note_id             BIGINT NOT NULL REFERENCES adjustment_notes(id) ON DELETE CASCADE,
    line_no             INT NOT NULL,
    item_kind           VARCHAR(10) NOT NULL DEFAULT 'PRODUCT',
    material_id         BIGINT REFERENCES materials(id),
    product_id          BIGINT REFERENCES products(id),
    description         VARCHAR(300),
    qty                 NUMERIC(18,3) NOT NULL,
    uom                 VARCHAR(10),
    rate                NUMERIC(18,4),
    taxable_value       NUMERIC(18,4),
    tax_amount          NUMERIC(18,4) DEFAULT 0,
    line_total          NUMERIC(18,4),
    original_item_id    BIGINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          BIGINT NOT NULL DEFAULT 1,
    updated_at          TIMESTAMPTZ DEFAULT now(),
    updated_by          BIGINT,
    version             BIGINT NOT NULL DEFAULT 0,
    UNIQUE (note_id, line_no)
);

-- 7. TDS entries
CREATE TABLE tds_entries (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    direction       VARCHAR(10) CHECK (direction IN ('DEDUCTED','COLLECTED')),
    party_type      VARCHAR(10),
    party_id        BIGINT,
    section_id      BIGINT REFERENCES tds_sections(id),
    source_type     VARCHAR(40),
    source_id       BIGINT,
    base_amount     NUMERIC(18,4),
    rate            NUMERIC(9,4),
    tds_amount      NUMERIC(18,4),
    deduction_date  DATE,
    deposit_date    DATE,
    challan_no      VARCHAR(30),
    certificate_no  VARCHAR(30),
    fiscal_year     VARCHAR(9),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT
);

-- 8. Reminder policies and payment reminders
CREATE TABLE reminder_policies (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    name            VARCHAR(60) NOT NULL,
    days_after_due  INT NOT NULL,
    level           INT NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (company_id, level)
);
INSERT INTO reminder_policies (name, days_after_due, level) VALUES
    ('First Reminder',  7,  1),
    ('Second Reminder', 15, 2),
    ('Final Notice',    30, 3);

CREATE TABLE payment_reminders (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    open_item_id    BIGINT REFERENCES open_items(id),
    party_id        BIGINT,
    level           INT,
    mode            VARCHAR(8) CHECK (mode IN ('AUTO','MANUAL')),
    sent_at         TIMESTAMPTZ,
    channel         VARCHAR(8),
    sent_by         BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 9. Permissions
INSERT INTO permissions (code, module, description) VALUES
    ('SALES_INVOICE_VIEW',   'FINANCE', 'View sales invoices'),
    ('SALES_INVOICE_CREATE', 'FINANCE', 'Create sales invoices'),
    ('SALES_INVOICE_POST',   'FINANCE', 'Post sales invoices'),
    ('SALES_INVOICE_CANCEL', 'FINANCE', 'Cancel sales invoices'),
    ('ADJ_NOTE_VIEW',        'FINANCE', 'View adjustment notes'),
    ('ADJ_NOTE_CREATE',      'FINANCE', 'Create adjustment notes'),
    ('ADJ_NOTE_POST',        'FINANCE', 'Post adjustment notes'),
    ('PAYMENT_VIEW',         'FINANCE', 'View payments and receipts'),
    ('PAYMENT_CREATE',       'FINANCE', 'Create payments and receipts'),
    ('PAYMENT_POST',         'FINANCE', 'Post payments and receipts'),
    ('OPEN_ITEM_VIEW',       'FINANCE', 'View open items and ageing'),
    ('TDS_MANAGE',           'FINANCE', 'Manage TDS sections and entries'),
    ('REMINDER_MANAGE',      'FINANCE', 'Manage payment reminders')
ON CONFLICT (code) DO NOTHING;

-- Grant to ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN (
    'SALES_INVOICE_VIEW','SALES_INVOICE_CREATE','SALES_INVOICE_POST','SALES_INVOICE_CANCEL',
    'ADJ_NOTE_VIEW','ADJ_NOTE_CREATE','ADJ_NOTE_POST',
    'PAYMENT_VIEW','PAYMENT_CREATE','PAYMENT_POST',
    'OPEN_ITEM_VIEW','TDS_MANAGE','REMINDER_MANAGE'
  )
ON CONFLICT DO NOTHING;

-- Grant to FINANCE_MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'FINANCE_MANAGER'
  AND p.code IN (
    'SALES_INVOICE_VIEW','SALES_INVOICE_CREATE','SALES_INVOICE_POST','SALES_INVOICE_CANCEL',
    'ADJ_NOTE_VIEW','ADJ_NOTE_CREATE','ADJ_NOTE_POST',
    'PAYMENT_VIEW','PAYMENT_CREATE','PAYMENT_POST',
    'OPEN_ITEM_VIEW','TDS_MANAGE','REMINDER_MANAGE'
  )
ON CONFLICT DO NOTHING;
