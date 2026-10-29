-- ERP-E-6 Production convenience features: planned schedule windows, order templates, plan board

SET search_path TO ims;

-- Add planned schedule columns to production_operations
ALTER TABLE production_operations
    ADD COLUMN IF NOT EXISTS planned_start  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS planned_end    TIMESTAMPTZ;

-- Production order templates (reusable order blueprints)
CREATE TABLE production_order_templates (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    code            VARCHAR(40) NOT NULL,
    name            VARCHAR(120) NOT NULL,
    product_id      BIGINT NOT NULL REFERENCES products(id),
    bom_id          BIGINT REFERENCES bom(id),
    planned_qty     NUMERIC(18,3) NOT NULL DEFAULT 1,
    lead_time_days  INT NOT NULL DEFAULT 1,
    notes           VARCHAR(500),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (company_id, code)
);

-- Permissions
INSERT INTO permissions (code, module, description) VALUES
    ('PLAN_BOARD_VIEW',    'PRODUCTION', 'View production plan board'),
    ('SHOP_FLOOR_VIEW',    'PRODUCTION', 'View shop floor real-time status'),
    ('PROD_TEMPLATE_VIEW', 'PRODUCTION', 'View production order templates'),
    ('PROD_TEMPLATE_MANAGE','PRODUCTION','Manage production order templates')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('PLAN_BOARD_VIEW','SHOP_FLOOR_VIEW','PROD_TEMPLATE_VIEW','PROD_TEMPLATE_MANAGE')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('PRODUCTION_MANAGER','SUPERVISOR')
  AND p.code IN ('PLAN_BOARD_VIEW','SHOP_FLOOR_VIEW','PROD_TEMPLATE_VIEW','PROD_TEMPLATE_MANAGE')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('MANAGEMENT')
  AND p.code IN ('PLAN_BOARD_VIEW','SHOP_FLOOR_VIEW','PROD_TEMPLATE_VIEW')
ON CONFLICT DO NOTHING;
