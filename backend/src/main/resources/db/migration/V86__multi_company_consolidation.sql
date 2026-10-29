-- GROUP 9 (ERP gap): Multi-company financial consolidation
SET search_path TO ims;

-- Register subsidiary/sister companies for consolidation purposes
CREATE TABLE IF NOT EXISTS consolidation_companies (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_company_id BIGINT NOT NULL DEFAULT 1,
    child_company_id  BIGINT NOT NULL,
    ownership_pct   NUMERIC(5,2) NOT NULL DEFAULT 100.00
                        CHECK (ownership_pct > 0 AND ownership_pct <= 100),
    include_in_consolidation BOOLEAN NOT NULL DEFAULT TRUE,
    notes           VARCHAR(300),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL DEFAULT 1,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (parent_company_id, child_company_id)
);

-- Intercompany elimination entries (manual overrides)
CREATE TABLE IF NOT EXISTS intercompany_eliminations (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    period_id       BIGINT NOT NULL REFERENCES fiscal_periods(id),
    description     VARCHAR(300) NOT NULL,
    amount          NUMERIC(18,4) NOT NULL,
    debit_account_code  VARCHAR(20) NOT NULL,
    credit_account_code VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL DEFAULT 1
);

INSERT INTO ims.permissions (code, module, description) VALUES
    ('CONSOLIDATION_VIEW',   'FINANCE', 'View multi-company consolidation report'),
    ('CONSOLIDATION_MANAGE', 'FINANCE', 'Manage consolidation companies and eliminations')
ON CONFLICT (code) DO NOTHING;

INSERT INTO ims.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM ims.roles r, ims.permissions p
WHERE r.code IN ('ADMIN','FINANCE_MANAGER') AND p.code IN ('CONSOLIDATION_VIEW','CONSOLIDATION_MANAGE')
ON CONFLICT DO NOTHING;

INSERT INTO ims.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM ims.roles r, ims.permissions p
WHERE r.code IN ('CONTROLLER','MANAGEMENT') AND p.code = 'CONSOLIDATION_VIEW'
ON CONFLICT DO NOTHING;
