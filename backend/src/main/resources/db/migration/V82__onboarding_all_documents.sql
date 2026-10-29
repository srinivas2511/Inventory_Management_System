-- V82: Onboarding wizard progress + "All Documents" unified document list
SET search_path TO ims;

-- Onboarding wizard: tracks which setup steps the admin has completed
CREATE TABLE IF NOT EXISTS onboarding_steps (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT        NOT NULL DEFAULT 1,
    step_key     VARCHAR(60)   NOT NULL,
    step_label   VARCHAR(120)  NOT NULL,
    completed    BOOLEAN       NOT NULL DEFAULT false,
    completed_by BIGINT,
    completed_at TIMESTAMPTZ,
    sort_order   INT           NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_onboarding_step
    ON onboarding_steps (company_id, step_key);

-- Seed standard onboarding steps
INSERT INTO onboarding_steps (company_id, step_key, step_label, sort_order) VALUES
    (1, 'company_profile',    'Set up company profile',         10),
    (1, 'chart_of_accounts',  'Verify chart of accounts',       20),
    (1, 'warehouses',         'Create at least one warehouse',  30),
    (1, 'materials',          'Import or create materials',     40),
    (1, 'suppliers',          'Add suppliers',                  50),
    (1, 'customers',          'Add customers',                  60),
    (1, 'opening_stock',      'Load opening stock balances',    70),
    (1, 'first_purchase',     'Create first purchase order',    80),
    (1, 'first_sale',         'Create first sales order',       90),
    (1, 'fiscal_period',      'Open the first fiscal period',  100)
ON CONFLICT (company_id, step_key) DO NOTHING;

-- Permissions for onboarding
INSERT INTO permissions (code, module, description) VALUES
    ('ONBOARDING_VIEW',   'SETTINGS', 'View onboarding wizard status'),
    ('ONBOARDING_MANAGE', 'SETTINGS', 'Mark onboarding steps complete / reset')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('ONBOARDING_VIEW', 'ONBOARDING_MANAGE')
ON CONFLICT DO NOTHING;
