-- R9-1.3: Journal engine — journal_headers, journal_lines, immutability trigger.
-- R9-1.4: inventory_account_assignments seed.
SET search_path = ims;

CREATE TABLE journal_headers (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    journal_number       VARCHAR(20)  NOT NULL,
    journal_type         VARCHAR(20)  NOT NULL,
    source_module        VARCHAR(20),
    source_document_type VARCHAR(30),
    source_document_id   BIGINT,
    posting_date         DATE         NOT NULL,
    period_id            BIGINT       NOT NULL REFERENCES fiscal_periods(id),
    description          VARCHAR(300) NOT NULL,
    currency_code        VARCHAR(3)      NOT NULL DEFAULT 'INR',
    exchange_rate        NUMERIC(12,6) NOT NULL DEFAULT 1,
    status               VARCHAR(10)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','POSTED','REVERSED')),
    reversal_of_id       BIGINT       REFERENCES journal_headers(id),
    prepared_by          BIGINT       NOT NULL,
    approved_by          BIGINT,
    approved_at          TIMESTAMPTZ,
    posted_by            BIGINT,
    posted_at            TIMESTAMPTZ,
    company_id           BIGINT       NOT NULL DEFAULT 1,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           BIGINT,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by           BIGINT,
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_journal_headers_number UNIQUE (journal_number)
);

CREATE TABLE journal_lines (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    journal_header_id  BIGINT        NOT NULL REFERENCES journal_headers(id) ON DELETE CASCADE,
    line_number        INT           NOT NULL,
    gl_account_id      BIGINT        NOT NULL REFERENCES gl_accounts(id),
    debit_amount       NUMERIC(18,4) NOT NULL DEFAULT 0,
    credit_amount      NUMERIC(18,4) NOT NULL DEFAULT 0,
    description        VARCHAR(200),
    CONSTRAINT chk_jl_amounts_non_negative CHECK (debit_amount >= 0 AND credit_amount >= 0),
    CONSTRAINT chk_jl_one_side_only CHECK (
        (debit_amount > 0 AND credit_amount = 0) OR
        (credit_amount > 0 AND debit_amount = 0)
    ),
    UNIQUE (journal_header_id, line_number)
);

-- Immutability trigger: once a journal_header is POSTED, its lines cannot be modified or deleted
CREATE OR REPLACE FUNCTION ims.prevent_posted_journal_modification()
RETURNS TRIGGER AS $$
DECLARE
    hdr_status VARCHAR(10);
BEGIN
    SELECT status INTO hdr_status
    FROM ims.journal_headers
    WHERE id = COALESCE(OLD.journal_header_id, NEW.journal_header_id);

    IF hdr_status = 'POSTED' THEN
        RAISE EXCEPTION 'Cannot modify lines of a posted journal (header id=%)',
            COALESCE(OLD.journal_header_id, NEW.journal_header_id);
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_journal_lines_immutability
    BEFORE UPDATE OR DELETE ON journal_lines
    FOR EACH ROW EXECUTE FUNCTION ims.prevent_posted_journal_modification();

-- ─── Inventory account assignments ───────────────────────────────────────────
CREATE TABLE inventory_account_assignments (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id            BIGINT       NOT NULL DEFAULT 1,
    transaction_type      VARCHAR(25)  NOT NULL,
    item_type             VARCHAR(10),
    valuation_class       VARCHAR(20),
    gl_account_debit_id   BIGINT       NOT NULL REFERENCES gl_accounts(id),
    gl_account_credit_id  BIGINT       NOT NULL REFERENCES gl_accounts(id)
);
CREATE UNIQUE INDEX ux_inv_acct_assign
    ON inventory_account_assignments (company_id, transaction_type, COALESCE(item_type,''), COALESCE(valuation_class,''));

-- Seed account assignments
INSERT INTO inventory_account_assignments
    (company_id, transaction_type, item_type, valuation_class, gl_account_debit_id, gl_account_credit_id)
VALUES
(1, 'PURCHASE_RECEIPT',     'MATERIAL', NULL,
    (SELECT id FROM gl_accounts WHERE account_code='1401'),
    (SELECT id FROM gl_accounts WHERE account_code='2401')),
(1, 'MATERIAL_ISSUE',       'MATERIAL', NULL,
    (SELECT id FROM gl_accounts WHERE account_code='1501'),
    (SELECT id FROM gl_accounts WHERE account_code='1401')),
(1, 'PRODUCTION_RECEIPT',   'PRODUCT',  NULL,
    (SELECT id FROM gl_accounts WHERE account_code='1410'),
    (SELECT id FROM gl_accounts WHERE account_code='1501')),
(1, 'PRODUCTION_REJECTION', 'PRODUCT',  NULL,
    (SELECT id FROM gl_accounts WHERE account_code='5202'),
    (SELECT id FROM gl_accounts WHERE account_code='1501')),
(1, 'SALES_DISPATCH',       'PRODUCT',  NULL,
    (SELECT id FROM gl_accounts WHERE account_code='5001'),
    (SELECT id FROM gl_accounts WHERE account_code='1410')),
(1, 'STOCK_ADJUSTMENT',     NULL,       NULL,
    (SELECT id FROM gl_accounts WHERE account_code='5203'),
    (SELECT id FROM gl_accounts WHERE account_code='1401'));

CREATE INDEX ix_journal_headers_period   ON journal_headers(period_id);
CREATE INDEX ix_journal_headers_status   ON journal_headers(status);
CREATE INDEX ix_journal_headers_src_doc  ON journal_headers(source_module, source_document_type, source_document_id);
CREATE INDEX ix_journal_lines_account    ON journal_lines(gl_account_id);
