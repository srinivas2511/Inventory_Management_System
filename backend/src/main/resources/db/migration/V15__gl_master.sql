-- R9-1.2: GL master data — gl_account_groups, gl_accounts, fiscal_periods.
-- Also seeds permissions and roles for finance module.
SET search_path = ims;

CREATE TABLE gl_account_groups (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_code   VARCHAR(10)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    account_type VARCHAR(20)  NOT NULL CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE','COGS')),
    company_id   BIGINT       NOT NULL DEFAULT 1,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   BIGINT,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by   BIGINT,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_gl_account_groups_code UNIQUE (group_code)
);

CREATE TABLE gl_accounts (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_code             VARCHAR(15)  NOT NULL,
    name                     VARCHAR(150) NOT NULL,
    account_group_id         BIGINT       NOT NULL REFERENCES gl_account_groups(id),
    account_type             VARCHAR(20)  NOT NULL,
    normal_balance           VARCHAR(6)   NOT NULL CHECK (normal_balance IN ('DEBIT','CREDIT')),
    allow_direct_posting     BOOLEAN      NOT NULL DEFAULT TRUE,
    is_reconciliation_account BOOLEAN     NOT NULL DEFAULT FALSE,
    is_tax_account           BOOLEAN      NOT NULL DEFAULT FALSE,
    tax_code                 VARCHAR(10),
    active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    company_id               BIGINT       NOT NULL DEFAULT 1,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               BIGINT,
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by               BIGINT,
    version                  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_gl_accounts_code UNIQUE (account_code)
);

CREATE TABLE fiscal_periods (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    period_name   VARCHAR(20)  NOT NULL,
    fiscal_year   INT          NOT NULL,
    period_number SMALLINT     NOT NULL,
    start_date    DATE         NOT NULL,
    end_date      DATE         NOT NULL,
    status        VARCHAR(10)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSING','CLOSED','LOCKED')),
    closed_by     BIGINT,
    closed_at     TIMESTAMPTZ,
    company_id    BIGINT       NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    BIGINT,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by    BIGINT,
    version       BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (company_id, fiscal_year, period_number)
);

-- ─── Seed GL account groups ───────────────────────────────────────────────────
INSERT INTO gl_account_groups (group_code, name, account_type, company_id) VALUES
('14', 'Inventory - Raw Materials',  'ASSET',     1),
('15', 'Inventory - WIP & FG',       'ASSET',     1),
('11', 'Trade Receivables',          'ASSET',     1),
('12', 'Bank & Cash',                'ASSET',     1),
('20', 'Current Liabilities',        'LIABILITY', 1),
('24', 'Trade Payables',             'LIABILITY', 1),
('30', 'Equity',                     'EQUITY',    1),
('40', 'Revenue',                    'REVENUE',   1),
('50', 'Cost of Sales',              'COGS',      1),
('52', 'Expenses',                   'EXPENSE',   1);

-- ─── Seed GL accounts ─────────────────────────────────────────────────────────
INSERT INTO gl_accounts (account_code, name, account_group_id, account_type, normal_balance, company_id) VALUES
('1401', 'Raw Material Stock',          (SELECT id FROM gl_account_groups WHERE group_code='14'), 'ASSET',     'DEBIT',  1),
('1501', 'WIP Stock',                   (SELECT id FROM gl_account_groups WHERE group_code='15'), 'ASSET',     'DEBIT',  1),
('1410', 'Finished Goods Stock',        (SELECT id FROM gl_account_groups WHERE group_code='15'), 'ASSET',     'DEBIT',  1),
('1101', 'Accounts Receivable Control', (SELECT id FROM gl_account_groups WHERE group_code='11'), 'ASSET',     'DEBIT',  1),
('1201', 'Main Bank Account',           (SELECT id FROM gl_account_groups WHERE group_code='12'), 'ASSET',     'DEBIT',  1),
('2401', 'GR/IR Clearing',              (SELECT id FROM gl_account_groups WHERE group_code='24'), 'LIABILITY', 'CREDIT', 1),
('2402', 'Accounts Payable Control',    (SELECT id FROM gl_account_groups WHERE group_code='24'), 'LIABILITY', 'CREDIT', 1),
('4001', 'Sales Revenue',               (SELECT id FROM gl_account_groups WHERE group_code='40'), 'REVENUE',   'CREDIT', 1),
('5001', 'Cost of Goods Sold',          (SELECT id FROM gl_account_groups WHERE group_code='50'), 'COGS',      'DEBIT',  1),
('5201', 'Salary Expense',              (SELECT id FROM gl_account_groups WHERE group_code='52'), 'EXPENSE',   'DEBIT',  1),
('5202', 'Scrap Loss',                  (SELECT id FROM gl_account_groups WHERE group_code='52'), 'EXPENSE',   'DEBIT',  1),
('5203', 'Inventory Adjustment',        (SELECT id FROM gl_account_groups WHERE group_code='52'), 'EXPENSE',   'DEBIT',  1);

-- ─── Seed fiscal periods: Apr 2026 – Mar 2027 ────────────────────────────────
INSERT INTO fiscal_periods (period_name, fiscal_year, period_number, start_date, end_date, status, company_id) VALUES
('APR-2026', 2026,  1, '2026-04-01', '2026-04-30', 'OPEN', 1),
('MAY-2026', 2026,  2, '2026-05-01', '2026-05-31', 'OPEN', 1),
('JUN-2026', 2026,  3, '2026-06-01', '2026-06-30', 'OPEN', 1),
('JUL-2026', 2026,  4, '2026-07-01', '2026-07-31', 'OPEN', 1),
('AUG-2026', 2026,  5, '2026-08-01', '2026-08-31', 'OPEN', 1),
('SEP-2026', 2026,  6, '2026-09-01', '2026-09-30', 'OPEN', 1),
('OCT-2026', 2026,  7, '2026-10-01', '2026-10-31', 'OPEN', 1),
('NOV-2026', 2026,  8, '2026-11-01', '2026-11-30', 'OPEN', 1),
('DEC-2026', 2026,  9, '2026-12-01', '2026-12-31', 'OPEN', 1),
('JAN-2027', 2026, 10, '2027-01-01', '2027-01-31', 'OPEN', 1),
('FEB-2027', 2026, 11, '2027-02-01', '2027-02-28', 'OPEN', 1),
('MAR-2027', 2026, 12, '2027-03-01', '2027-03-31', 'OPEN', 1);

-- ─── Finance permissions ──────────────────────────────────────────────────────
INSERT INTO permissions (code, module, description) VALUES
('GL_VIEW',        'FINANCE', 'View GL accounts and journals'),
('GL_POST',        'FINANCE', 'Post journal entries to the General Ledger'),
('JOURNAL_APPROVE','FINANCE', 'Approve journal entries before posting'),
('PERIOD_CLOSE',   'FINANCE', 'Initiate and finalize accounting period close'),
('AP_MANAGE',      'FINANCE', 'Manage accounts payable invoices and payments'),
('AR_MANAGE',      'FINANCE', 'Manage accounts receivable invoices and receipts'),
('BANK_RECONCILE', 'FINANCE', 'Import bank statements and reconcile'),
('FINANCE_REPORT', 'FINANCE', 'View financial reports (Trial Balance, P&L, Balance Sheet)'),
('BUDGET_MANAGE',  'FINANCE', 'Manage budgets and exchange rates');

-- Finance Manager role
INSERT INTO roles (code, name, description, system_role)
VALUES ('FINANCE_MANAGER', 'Finance Manager', 'Full access to all finance functions', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'FINANCE_MANAGER'
  AND p.code IN ('GL_VIEW','GL_POST','JOURNAL_APPROVE','PERIOD_CLOSE','AP_MANAGE','AR_MANAGE','BANK_RECONCILE','FINANCE_REPORT','BUDGET_MANAGE')
ON CONFLICT DO NOTHING;

-- Accountant role
INSERT INTO roles (code, name, description, system_role)
VALUES ('ACCOUNTANT', 'Accountant', 'Day-to-day finance entry: journals, AP/AR', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ACCOUNTANT'
  AND p.code IN ('GL_VIEW','GL_POST','AP_MANAGE','AR_MANAGE','FINANCE_REPORT')
ON CONFLICT DO NOTHING;
