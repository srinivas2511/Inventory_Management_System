-- R9-4.2: Budgets, exchange rates, and GST tax codes.
SET search_path = ims;

CREATE TABLE exchange_rates (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    from_currency CHAR(3)       NOT NULL,
    to_currency   CHAR(3)       NOT NULL DEFAULT 'INR',
    period_id     BIGINT        NOT NULL REFERENCES fiscal_periods(id),
    rate          NUMERIC(12,6) NOT NULL CHECK (rate > 0),
    company_id    BIGINT        NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by    BIGINT,
    UNIQUE (company_id, from_currency, to_currency, period_id)
);

CREATE TABLE tax_codes (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tax_code    VARCHAR(15)   NOT NULL,
    description VARCHAR(100)  NOT NULL,
    tax_type    VARCHAR(10)   NOT NULL CHECK (tax_type IN ('CGST','SGST','IGST','UTGST','EXEMPT')),
    rate_pct    NUMERIC(5,2)  NOT NULL CHECK (rate_pct >= 0),
    gl_account_id BIGINT      REFERENCES gl_accounts(id),
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    company_id  BIGINT        NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by  BIGINT,
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by  BIGINT,
    version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_tax_codes UNIQUE (tax_code)
);

CREATE TABLE budgets (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    budget_name     VARCHAR(100)  NOT NULL,
    fiscal_year     INT           NOT NULL,
    gl_account_id   BIGINT        NOT NULL REFERENCES gl_accounts(id),
    period_id       BIGINT        NOT NULL REFERENCES fiscal_periods(id),
    budgeted_amount NUMERIC(18,4) NOT NULL,
    company_id      BIGINT        NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT        NOT NULL DEFAULT 0,
    UNIQUE (company_id, fiscal_year, gl_account_id, period_id)
);

-- Seed GST tax codes
INSERT INTO tax_codes (tax_code, description, tax_type, rate_pct, company_id) VALUES
('CGST9',  'CGST @ 9%',   'CGST',   9.00, 1),
('SGST9',  'SGST @ 9%',   'SGST',   9.00, 1),
('CGST6',  'CGST @ 6%',   'CGST',   6.00, 1),
('SGST6',  'SGST @ 6%',   'SGST',   6.00, 1),
('IGST18', 'IGST @ 18%',  'IGST',  18.00, 1),
('IGST12', 'IGST @ 12%',  'IGST',  12.00, 1),
('IGST5',  'IGST @ 5%',   'IGST',   5.00, 1),
('EXEMPT', 'GST Exempt',  'EXEMPT',  0.00, 1);
