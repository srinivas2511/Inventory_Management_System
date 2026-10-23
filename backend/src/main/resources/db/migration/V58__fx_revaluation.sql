-- R9-7.6: unrealised FX revaluation at period end.
-- Invoices open in a foreign currency are revalued at the period closing rate; the movement is
-- posted as an unrealised gain (4902) or loss (5209).  Re-running the process for the same period
-- posts only the incremental change, and the fx_revaluations table tracks the net position.
SET search_path = ims;

INSERT INTO gl_accounts (account_code, name, account_group_id, account_type, normal_balance, company_id) VALUES
('4902', 'Unrealised FX Gain', (SELECT id FROM gl_account_groups WHERE group_code = '40'), 'REVENUE', 'CREDIT', 1),
('5209', 'Unrealised FX Loss', (SELECT id FROM gl_account_groups WHERE group_code = '52'), 'EXPENSE', 'DEBIT',  1)
ON CONFLICT (account_code) DO NOTHING;

CREATE TABLE IF NOT EXISTS fx_revaluations (
    id                BIGSERIAL PRIMARY KEY,
    period_id         BIGINT        NOT NULL REFERENCES fiscal_periods(id),
    document_type     VARCHAR(15)   NOT NULL,   -- AP_INVOICE or AR_INVOICE
    document_id       BIGINT        NOT NULL,
    currency_code     VARCHAR(3)    NOT NULL,
    remaining_foreign NUMERIC(18,4) NOT NULL,   -- open balance in the invoice currency
    booking_rate      NUMERIC(12,6) NOT NULL,   -- rate at invoice date
    closing_rate      NUMERIC(12,6) NOT NULL,   -- period-end rate used for this run
    difference_inr    NUMERIC(18,4) NOT NULL,   -- cumulative unrealised P&L for this (document, period)
    journal_header_id BIGINT        REFERENCES journal_headers(id),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        BIGINT,
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by        BIGINT,
    version           BIGINT        NOT NULL DEFAULT 0,
    company_id        BIGINT        NOT NULL DEFAULT 1,
    UNIQUE (document_type, document_id, period_id)
);

INSERT INTO permissions (code, module, description) VALUES
('FX_REVALUATION_RUN', 'FINANCE', 'Run unrealised FX revaluation of open foreign-currency invoices at period end')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
 WHERE r.code IN ('ADMIN', 'FINANCE_MANAGER') AND p.code = 'FX_REVALUATION_RUN'
ON CONFLICT DO NOTHING;
