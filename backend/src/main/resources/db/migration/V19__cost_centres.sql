-- R10-1.1 / R10-1.2: Cost centres, profit centres, journal dimensions, controlling permissions.
SET search_path = ims;

CREATE TABLE cost_centres (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cost_centre_code      VARCHAR(20)   NOT NULL,
    name                  VARCHAR(100)  NOT NULL,
    parent_id             BIGINT        REFERENCES cost_centres(id),
    responsible_user_id   BIGINT        REFERENCES users(id),
    plant_id              BIGINT        REFERENCES plants(id),
    labour_rate_per_hour  NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK (labour_rate_per_hour >= 0),
    machine_rate_per_hour NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK (machine_rate_per_hour >= 0),
    overhead_pct          NUMERIC(7,2)  NOT NULL DEFAULT 0 CHECK (overhead_pct >= 0),
    active                BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by            BIGINT,
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by            BIGINT,
    version               BIGINT        NOT NULL DEFAULT 0,
    company_id            BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_cost_centres_code UNIQUE (cost_centre_code),
    CONSTRAINT chk_cost_centre_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE TABLE profit_centres (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    profit_centre_code  VARCHAR(20)  NOT NULL,
    name                VARCHAR(100) NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          BIGINT,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          BIGINT,
    version             BIGINT       NOT NULL DEFAULT 0,
    company_id          BIGINT       NOT NULL DEFAULT 1,
    CONSTRAINT uq_profit_centres_code UNIQUE (profit_centre_code)
);

CREATE TABLE cost_centre_budgets (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cost_centre_id   BIGINT        NOT NULL REFERENCES cost_centres(id),
    gl_account_id    BIGINT        NOT NULL REFERENCES gl_accounts(id),
    period_id        BIGINT        NOT NULL REFERENCES fiscal_periods(id),
    budgeted_amount  NUMERIC(18,4) NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by       BIGINT,
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by       BIGINT,
    version          BIGINT        NOT NULL DEFAULT 0,
    company_id       BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_cost_centre_budget UNIQUE (cost_centre_id, gl_account_id, period_id)
);

INSERT INTO cost_centres (cost_centre_code, name, labour_rate_per_hour, machine_rate_per_hour, overhead_pct) VALUES
('CC-PROD-01', 'Production - Spring Shop', 250.0000, 400.0000, 10.00),
('CC-QUAL-01', 'Quality Control',          0, 0, 0),
('CC-WH-01',   'Warehouse and Stores',     0, 0, 0);

INSERT INTO profit_centres (profit_centre_code, name) VALUES
('PC-SPRINGS', 'Springs Business');

-- Journal dimensions (nullable: existing journals stay untouched)
ALTER TABLE journal_lines
    ADD COLUMN cost_centre_id   BIGINT REFERENCES cost_centres(id),
    ADD COLUMN profit_centre_id BIGINT REFERENCES profit_centres(id);
CREATE INDEX ix_journal_lines_cost_centre   ON journal_lines(cost_centre_id)   WHERE cost_centre_id IS NOT NULL;
CREATE INDEX ix_journal_lines_profit_centre ON journal_lines(profit_centre_id) WHERE profit_centre_id IS NOT NULL;

-- Auto-journal dimension defaults per inventory transaction type (data, not code)
ALTER TABLE inventory_account_assignments
    ADD COLUMN cost_centre_id   BIGINT REFERENCES cost_centres(id),
    ADD COLUMN profit_centre_id BIGINT REFERENCES profit_centres(id);

UPDATE inventory_account_assignments
   SET cost_centre_id = (SELECT id FROM cost_centres WHERE cost_centre_code = 'CC-PROD-01')
 WHERE transaction_type IN ('MATERIAL_ISSUE', 'PRODUCTION_RECEIPT', 'PRODUCTION_REJECTION');

UPDATE inventory_account_assignments
   SET cost_centre_id = (SELECT id FROM cost_centres WHERE cost_centre_code = 'CC-WH-01')
 WHERE transaction_type = 'STOCK_ADJUSTMENT';

UPDATE inventory_account_assignments
   SET profit_centre_id = (SELECT id FROM profit_centres WHERE profit_centre_code = 'PC-SPRINGS')
 WHERE transaction_type IN ('SALES_DISPATCH', 'PRODUCTION_RECEIPT');

-- Controlling defaults (ids resolved from the seeded rows above)
INSERT INTO system_settings (key, value, value_type, description)
SELECT 'controlling.production_cost_centre_id', id::text, 'INTEGER',
       'Cost centre whose labour/machine/overhead rates drive product costing and variance'
  FROM cost_centres WHERE cost_centre_code = 'CC-PROD-01'
ON CONFLICT (key) DO NOTHING;

INSERT INTO system_settings (key, value, value_type, description)
SELECT 'controlling.default_profit_centre_id', id::text, 'INTEGER',
       'Profit centre tagged on revenue and variance journal lines'
  FROM profit_centres WHERE profit_centre_code = 'PC-SPRINGS'
ON CONFLICT (key) DO NOTHING;

-- Permissions
INSERT INTO permissions (code, module, description) VALUES
('CONTROLLING_VIEW',     'CONTROLLING', 'View cost centres, cost estimates, variances and controlling reports'),
('CONTROLLING_MANAGE',   'CONTROLLING', 'Manage cost/profit centres, cost budgets, cost estimates and variance runs'),
('COST_ESTIMATE_RELEASE','CONTROLLING', 'Release a product cost estimate as the standard cost');

INSERT INTO roles (code, name, description, system_role)
VALUES ('CONTROLLER', 'Controller', 'Cost centre accounting, product costing and variance analysis', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'CONTROLLER'
  AND p.code IN ('CONTROLLING_VIEW','CONTROLLING_MANAGE','COST_ESTIMATE_RELEASE','GL_VIEW','REPORT_VIEW','DASHBOARD_VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('FINANCE_MANAGER', 'ADMIN')
  AND p.code IN ('CONTROLLING_VIEW','CONTROLLING_MANAGE','COST_ESTIMATE_RELEASE')
ON CONFLICT DO NOTHING;
