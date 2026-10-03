-- R13 payroll: runs, slips, statutory settings (rates are data, never code) and the GL accounts the payroll
-- journals post to. Every table carries the DESIGN section 1.1 standard columns.
SET search_path = ims;

CREATE TABLE payroll_runs (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_number                 VARCHAR(20)   NOT NULL,
    period_month               SMALLINT      NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    period_year                SMALLINT      NOT NULL,
    status                     VARCHAR(10)   NOT NULL DEFAULT 'DRAFT'
                                   CHECK (status IN ('DRAFT','PROCESSING','COMPLETED','PAID')),
    initiated_by               BIGINT        NOT NULL,
    completed_at               TIMESTAMPTZ,
    approved_by                BIGINT,
    total_employees            INT,
    total_gross                NUMERIC(18,4),
    total_net                  NUMERIC(18,4),
    journal_header_id          BIGINT        REFERENCES journal_headers(id),
    paid_at                    TIMESTAMPTZ,
    paid_by                    BIGINT,
    payment_journal_header_id  BIGINT        REFERENCES journal_headers(id),
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by                 BIGINT,
    updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by                 BIGINT,
    version                    BIGINT        NOT NULL DEFAULT 0,
    company_id                 BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_payroll_runs_number UNIQUE (run_number),
    CONSTRAINT uq_payroll_runs_period UNIQUE (period_year, period_month)
);

CREATE TABLE payroll_slips (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payroll_run_id     BIGINT        NOT NULL REFERENCES payroll_runs(id),
    employee_id        BIGINT        NOT NULL REFERENCES employees(id),
    cost_centre_id     BIGINT        REFERENCES cost_centres(id),
    basic_pay          NUMERIC(18,4) NOT NULL,
    hra                NUMERIC(18,4) NOT NULL DEFAULT 0,
    da                 NUMERIC(18,4) NOT NULL DEFAULT 0,
    travel_allowance   NUMERIC(18,4) NOT NULL DEFAULT 0,
    other_allowances   NUMERIC(18,4) NOT NULL DEFAULT 0,
    gross_pay          NUMERIC(18,4) NOT NULL,
    pf_employee        NUMERIC(18,4) NOT NULL DEFAULT 0,
    pf_employer        NUMERIC(18,4) NOT NULL DEFAULT 0,
    esi_employee       NUMERIC(18,4) NOT NULL DEFAULT 0,
    esi_employer       NUMERIC(18,4) NOT NULL DEFAULT 0,
    professional_tax   NUMERIC(18,4) NOT NULL DEFAULT 0,
    tds                NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_deductions   NUMERIC(18,4) NOT NULL,
    net_pay            NUMERIC(18,4) NOT NULL,
    working_days       NUMERIC(4,1),
    paid_days          NUMERIC(4,1),
    lop_days           NUMERIC(4,1),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         BIGINT,
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by         BIGINT,
    version            BIGINT        NOT NULL DEFAULT 0,
    company_id         BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_payroll_slip UNIQUE (payroll_run_id, employee_id)
);
CREATE INDEX ix_payroll_slips_employee ON payroll_slips (employee_id);

-- Statutory rates and slabs. Defaults reflect the Indian new-regime figures at the time of writing; HR and
-- finance own them and must review them each financial year.
INSERT INTO system_settings (key, value, value_type, description) VALUES
('hrm.payroll.hra_pct',            '40',      'DECIMAL', 'HRA as a percentage of proportional basic pay'),
('hrm.payroll.da_pct',             '0',       'DECIMAL', 'Dearness allowance as a percentage of proportional basic pay'),
('hrm.payroll.pf_wage_ceiling',    '15000',   'DECIMAL', 'Monthly basic-pay ceiling for provident fund'),
('hrm.payroll.pf_employee_pct',    '12',      'DECIMAL', 'Employee provident fund contribution (%)'),
('hrm.payroll.pf_employer_pct',    '12',      'DECIMAL', 'Employer provident fund contribution (%)'),
('hrm.payroll.esi_gross_ceiling',  '21000',   'DECIMAL', 'ESI applies only when monthly gross does not exceed this'),
('hrm.payroll.esi_employee_pct',   '0.75',    'DECIMAL', 'Employee ESI contribution (%)'),
('hrm.payroll.esi_employer_pct',   '3.25',    'DECIMAL', 'Employer ESI contribution (%)'),
('hrm.payroll.pt_slabs',           '7500:0,10000:175,:200', 'STRING',
    'Professional tax slabs on monthly gross: "upTo:amount" pairs ascending, last slab without a bound'),
('hrm.payroll.tds_standard_deduction', '75000',   'DECIMAL', 'Annual standard deduction used for projected TDS'),
('hrm.payroll.tds_slabs',          '300000:0,700000:5,1000000:10,1200000:15,1500000:20,:30', 'STRING',
    'Annual income tax slabs: "upTo:ratePct" pairs ascending, last slab without a bound'),
('hrm.payroll.tds_rebate_limit',   '1200000', 'DECIMAL', 'Projected taxable income up to which no tax is deducted (rebate)'),
('hrm.payroll.tds_cess_pct',       '4',       'DECIMAL', 'Health and education cess on projected tax (%)')
ON CONFLICT (key) DO NOTHING;

-- GL accounts for payroll journals (5201 Salary Expense and 1201 Main Bank Account already exist).
INSERT INTO gl_accounts (account_code, name, account_group_id, account_type, normal_balance, company_id) VALUES
('5205', 'Employer PF Expense',       (SELECT id FROM gl_account_groups WHERE group_code = '52'), 'EXPENSE',   'DEBIT',  1),
('5206', 'Employer ESI Expense',      (SELECT id FROM gl_account_groups WHERE group_code = '52'), 'EXPENSE',   'DEBIT',  1),
('2501', 'Salary Payable',            (SELECT id FROM gl_account_groups WHERE group_code = '20'), 'LIABILITY', 'CREDIT', 1),
('2502', 'PF Payable',                (SELECT id FROM gl_account_groups WHERE group_code = '20'), 'LIABILITY', 'CREDIT', 1),
('2503', 'ESI Payable',               (SELECT id FROM gl_account_groups WHERE group_code = '20'), 'LIABILITY', 'CREDIT', 1),
('2504', 'Professional Tax Payable',  (SELECT id FROM gl_account_groups WHERE group_code = '20'), 'LIABILITY', 'CREDIT', 1),
('2505', 'TDS Payable',               (SELECT id FROM gl_account_groups WHERE group_code = '20'), 'LIABILITY', 'CREDIT', 1);
