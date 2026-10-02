-- R9-2.1: Accounts Payable — ap_invoices, ap_payments.
-- R9-2.3: Accounts Receivable — ar_invoices, ar_receipts.
-- R9-3.1: Bank statements — bank_statements, bank_statement_lines.
SET search_path = ims;

-- ─── Accounts Payable ────────────────────────────────────────────────────────
CREATE TABLE ap_invoices (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_number      VARCHAR(25)   NOT NULL,
    supplier_invoice_no VARCHAR(40),
    supplier_id         BIGINT        NOT NULL REFERENCES suppliers(id),
    invoice_date        DATE          NOT NULL,
    due_date            DATE          NOT NULL,
    goods_receipt_id    BIGINT        REFERENCES goods_receipts(id),
    subtotal            NUMERIC(18,4) NOT NULL,
    tax_total           NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_amount        NUMERIC(18,4) NOT NULL,
    paid_amount         NUMERIC(18,4) NOT NULL DEFAULT 0,
    balance_amount      NUMERIC(18,4) GENERATED ALWAYS AS (total_amount - paid_amount) STORED,
    status              VARCHAR(15)   NOT NULL DEFAULT 'DRAFT'
                            CHECK (status IN ('DRAFT','OPEN','PARTIALLY_PAID','PAID','CANCELLED')),
    currency_code       VARCHAR(3)       NOT NULL DEFAULT 'INR',
    period_id           BIGINT        NOT NULL REFERENCES fiscal_periods(id),
    journal_header_id   BIGINT        REFERENCES journal_headers(id),
    company_id          BIGINT        NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by          BIGINT,
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by          BIGINT,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_ap_invoices_number UNIQUE (invoice_number)
);

CREATE TABLE ap_payments (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_reference VARCHAR(25)  NOT NULL,
    ap_invoice_id    BIGINT        NOT NULL REFERENCES ap_invoices(id),
    payment_date     DATE          NOT NULL,
    amount           NUMERIC(18,4) NOT NULL CHECK (amount > 0),
    payment_method   VARCHAR(20)   NOT NULL CHECK (payment_method IN ('BANK_TRANSFER','CHEQUE','CASH')),
    bank_reference   VARCHAR(60),
    journal_header_id BIGINT       REFERENCES journal_headers(id),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by       BIGINT        NOT NULL,
    CONSTRAINT uq_ap_payments_ref UNIQUE (payment_reference)
);

-- ─── Accounts Receivable ─────────────────────────────────────────────────────
CREATE TABLE ar_invoices (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_number   VARCHAR(25)   NOT NULL,
    customer_id      BIGINT        NOT NULL REFERENCES customers(id),
    dispatch_id      BIGINT        REFERENCES dispatches(id),
    invoice_date     DATE          NOT NULL,
    due_date         DATE          NOT NULL,
    subtotal         NUMERIC(18,4) NOT NULL,
    tax_total        NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_amount     NUMERIC(18,4) NOT NULL,
    received_amount  NUMERIC(18,4) NOT NULL DEFAULT 0,
    balance_amount   NUMERIC(18,4) GENERATED ALWAYS AS (total_amount - received_amount) STORED,
    status           VARCHAR(15)   NOT NULL DEFAULT 'OPEN'
                         CHECK (status IN ('OPEN','PARTIALLY_PAID','PAID','CANCELLED','CREDIT_NOTE')),
    currency_code    VARCHAR(3)       NOT NULL DEFAULT 'INR',
    period_id        BIGINT        NOT NULL REFERENCES fiscal_periods(id),
    journal_header_id BIGINT       REFERENCES journal_headers(id),
    company_id       BIGINT        NOT NULL DEFAULT 1,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by       BIGINT,
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by       BIGINT,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_ar_invoices_number UNIQUE (invoice_number)
);

CREATE TABLE ar_receipts (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    receipt_reference VARCHAR(25)  NOT NULL,
    ar_invoice_id    BIGINT        NOT NULL REFERENCES ar_invoices(id),
    receipt_date     DATE          NOT NULL,
    amount           NUMERIC(18,4) NOT NULL CHECK (amount > 0),
    payment_method   VARCHAR(20)   NOT NULL CHECK (payment_method IN ('BANK_TRANSFER','CHEQUE','CASH','UPI')),
    bank_reference   VARCHAR(60),
    journal_header_id BIGINT       REFERENCES journal_headers(id),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by       BIGINT        NOT NULL,
    CONSTRAINT uq_ar_receipts_ref UNIQUE (receipt_reference)
);

-- ─── Bank Statements ─────────────────────────────────────────────────────────
CREATE TABLE bank_statements (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    statement_reference VARCHAR(30)   NOT NULL,
    bank_account_code   VARCHAR(20)   NOT NULL,
    gl_account_id       BIGINT        NOT NULL REFERENCES gl_accounts(id),
    period_id           BIGINT        NOT NULL REFERENCES fiscal_periods(id),
    statement_date      DATE          NOT NULL,
    opening_balance     NUMERIC(18,4) NOT NULL DEFAULT 0,
    closing_balance     NUMERIC(18,4) NOT NULL DEFAULT 0,
    status              VARCHAR(15)   NOT NULL DEFAULT 'IMPORTED'
                             CHECK (status IN ('IMPORTED','IN_PROGRESS','RECONCILED')),
    company_id          BIGINT        NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by          BIGINT,
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by          BIGINT,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_bank_statements_ref UNIQUE (statement_reference)
);

CREATE TABLE bank_statement_lines (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bank_statement_id     BIGINT        NOT NULL REFERENCES bank_statements(id) ON DELETE CASCADE,
    line_number           INT           NOT NULL,
    transaction_date      DATE          NOT NULL,
    value_date            DATE,
    description           VARCHAR(200)  NOT NULL,
    debit_amount          NUMERIC(18,4) NOT NULL DEFAULT 0,
    credit_amount         NUMERIC(18,4) NOT NULL DEFAULT 0,
    bank_reference        VARCHAR(60),
    matched_journal_line_id BIGINT      REFERENCES journal_lines(id),
    reconciled            BOOLEAN       NOT NULL DEFAULT FALSE,
    UNIQUE (bank_statement_id, line_number)
);

CREATE INDEX ix_ap_invoices_supplier   ON ap_invoices(supplier_id);
CREATE INDEX ix_ap_invoices_status     ON ap_invoices(status);
CREATE INDEX ix_ar_invoices_customer   ON ar_invoices(customer_id);
CREATE INDEX ix_ar_invoices_status     ON ar_invoices(status);
CREATE INDEX ix_ar_invoices_dispatch   ON ar_invoices(dispatch_id);
