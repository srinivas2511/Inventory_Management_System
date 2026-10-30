-- 12.5: Advance receipt vouchers
SET search_path = ims;

-- Seed GL account 2403 Customer Advance (liability)
INSERT INTO gl_accounts (account_code, name, account_group_id, account_type, normal_balance, company_id)
SELECT '2403', 'Customer Advances', (SELECT id FROM gl_account_groups WHERE group_code = '24'), 'LIABILITY', 'CREDIT', 1
WHERE NOT EXISTS (SELECT 1 FROM gl_accounts WHERE account_code = '2403');

CREATE TABLE advance_receipts (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    receipt_date    DATE NOT NULL,
    amount          NUMERIC(18,4) NOT NULL,
    currency_code   CHAR(3) NOT NULL DEFAULT 'INR',
    reference       VARCHAR(100),
    notes           TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    applied_amount  NUMERIC(18,4) NOT NULL DEFAULT 0,
    journal_id      BIGINT REFERENCES journal_headers(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0
);

INSERT INTO permissions (code, description)
VALUES ('ADVANCE_RECEIPT_MANAGE', 'Manage advance receipts')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name IN ('ADMIN', 'FINANCE_MANAGER')
  AND p.code = 'ADVANCE_RECEIPT_MANAGE'
ON CONFLICT DO NOTHING;
