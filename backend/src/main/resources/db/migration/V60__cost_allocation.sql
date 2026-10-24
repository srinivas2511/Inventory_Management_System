-- R10-6.3 Period-end overhead allocation cycle
-- cost_allocation_rules: which cost centres to allocate and by what base
-- cost_allocation_runs:  one run per period
-- cost_allocation_lines: per-product breakdown of each run
-- New GL accounts: 5304 Overhead Applied, 5305 Overhead Pool Released

SET search_path = ims;

-- ── GL accounts ───────────────────────────────────────────────────────────────
INSERT INTO ims.gl_accounts (account_code, name, account_group_id, account_type, normal_balance, company_id)
SELECT '5304', 'Overhead Applied to Products',
       (SELECT id FROM ims.gl_account_groups WHERE group_code = '52'),
       'EXPENSE', 'DEBIT', 1
WHERE NOT EXISTS (SELECT 1 FROM ims.gl_accounts WHERE account_code = '5304');

INSERT INTO ims.gl_accounts (account_code, name, account_group_id, account_type, normal_balance, company_id)
SELECT '5305', 'Overhead Pool Released',
       (SELECT id FROM ims.gl_account_groups WHERE group_code = '52'),
       'EXPENSE', 'CREDIT', 1
WHERE NOT EXISTS (SELECT 1 FROM ims.gl_accounts WHERE account_code = '5305');

-- ── Tables ────────────────────────────────────────────────────────────────────
CREATE TABLE ims.cost_allocation_rules (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cost_centre_id      BIGINT NOT NULL REFERENCES ims.cost_centres(id),
    allocation_base     VARCHAR(20) NOT NULL CHECK (allocation_base IN ('MACHINE_HOURS','LABOUR_HOURS','PRODUCTION_QTY')),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    company_id          BIGINT NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_alloc_rule_cc_base UNIQUE (cost_centre_id, allocation_base)
);

CREATE TABLE ims.cost_allocation_runs (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    period_id           BIGINT NOT NULL REFERENCES ims.fiscal_periods(id),
    status              VARCHAR(15) NOT NULL DEFAULT 'COMPLETED',
    run_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    run_by              BIGINT NOT NULL,
    journal_header_id   BIGINT REFERENCES ims.journal_headers(id),
    total_allocated     NUMERIC(18,4) NOT NULL DEFAULT 0,
    company_id          BIGINT NOT NULL DEFAULT 1
);

CREATE TABLE ims.cost_allocation_lines (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id              BIGINT NOT NULL REFERENCES ims.cost_allocation_runs(id) ON DELETE CASCADE,
    cost_centre_id      BIGINT NOT NULL REFERENCES ims.cost_centres(id),
    product_id          BIGINT NOT NULL REFERENCES ims.products(id),
    allocation_base     VARCHAR(20) NOT NULL,
    base_qty            NUMERIC(18,3) NOT NULL DEFAULT 0,
    rate_per_unit       NUMERIC(18,4) NOT NULL DEFAULT 0,
    allocated_amount    NUMERIC(18,4) NOT NULL DEFAULT 0
);

CREATE INDEX ix_alloc_lines_run ON ims.cost_allocation_lines(run_id);
CREATE INDEX ix_alloc_runs_period ON ims.cost_allocation_runs(period_id);

-- ── Permissions ───────────────────────────────────────────────────────────────
INSERT INTO ims.permissions (code, module, description) VALUES
    ('OVERHEAD_ALLOC_VIEW',  'CONTROLLING', 'View overhead allocation runs and lines'),
    ('OVERHEAD_ALLOC_RUN',   'CONTROLLING', 'Execute period-end overhead allocation cycle');

INSERT INTO ims.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ims.roles r
CROSS JOIN ims.permissions p
WHERE r.code IN ('ADMIN', 'CONTROLLER', 'FINANCE_MANAGER')
  AND p.code IN ('OVERHEAD_ALLOC_VIEW', 'OVERHEAD_ALLOC_RUN');

-- ── Seed one allocation rule for the demo cost centre ─────────────────────────
INSERT INTO ims.cost_allocation_rules (cost_centre_id, allocation_base)
SELECT id, 'LABOUR_HOURS' FROM ims.cost_centres WHERE cost_centre_code = 'CC-PROD-01'
ON CONFLICT DO NOTHING;
