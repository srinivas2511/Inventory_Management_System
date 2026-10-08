-- R9-7.1 / R9-7.2: plant scoping and plant-level setting overrides (ENT-002 .. ENT-005).
--
-- A user with no row in user_plant_access, or one who holds PLANT_ALL_ACCESS, sees every plant: a default single-plant
-- install behaves exactly as before. A user with rows is restricted to those plants. Existing documents and warehouses
-- belong to the default (first) plant.
SET search_path = ims;

CREATE TABLE user_plant_access (
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plant_id   BIGINT      NOT NULL REFERENCES plants(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by BIGINT,
    PRIMARY KEY (user_id, plant_id)
);
CREATE INDEX ix_user_plant_access_plant ON user_plant_access(plant_id);

-- Warehouses (storage locations hang off them) carry the plant, so inventory balances and the ledger are plant-scoped
-- through warehouse -> plant.
ALTER TABLE warehouses ADD COLUMN IF NOT EXISTS plant_id BIGINT REFERENCES plants(id);

UPDATE warehouses        SET plant_id = (SELECT MIN(id) FROM plants) WHERE plant_id IS NULL;
UPDATE purchase_orders   SET plant_id = (SELECT MIN(id) FROM plants) WHERE plant_id IS NULL;
UPDATE goods_receipts    SET plant_id = (SELECT MIN(id) FROM plants) WHERE plant_id IS NULL;
UPDATE production_orders SET plant_id = (SELECT MIN(id) FROM plants) WHERE plant_id IS NULL;
UPDATE sales_orders      SET plant_id = (SELECT MIN(id) FROM plants) WHERE plant_id IS NULL;
UPDATE dispatches        SET plant_id = (SELECT MIN(id) FROM plants) WHERE plant_id IS NULL;

CREATE INDEX IF NOT EXISTS ix_purchase_orders_plant   ON purchase_orders(plant_id);
CREATE INDEX IF NOT EXISTS ix_goods_receipts_plant    ON goods_receipts(plant_id);
CREATE INDEX IF NOT EXISTS ix_production_orders_plant ON production_orders(plant_id);
CREATE INDEX IF NOT EXISTS ix_sales_orders_plant      ON sales_orders(plant_id);
CREATE INDEX IF NOT EXISTS ix_dispatches_plant        ON dispatches(plant_id);
CREATE INDEX IF NOT EXISTS ix_warehouses_plant        ON warehouses(plant_id);

-- Overrides resolved plant -> company -> system default (ENT-005). plant_id NULL is the company-level default.
CREATE TABLE plant_settings (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    BIGINT       NOT NULL REFERENCES companies(id),
    plant_id      BIGINT       REFERENCES plants(id),
    setting_key   VARCHAR(60)  NOT NULL,
    setting_value VARCHAR(200) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    BIGINT,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by    BIGINT,
    version       BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_plant_settings_scope_key
    ON plant_settings (company_id, COALESCE(plant_id, 0), setting_key);

-- System defaults for the four overridable settings.
INSERT INTO system_settings (key, value, value_type, description) VALUES
('enterprise.default_warehouse_id', '',              'STRING', 'Default warehouse id for new documents (blank = none); overridable per company and plant'),
('enterprise.timezone',            'Asia/Kolkata',   'STRING', 'Default time zone; overridable per company and plant'),
('enterprise.production_calendar', 'DEFAULT',        'STRING', 'Production calendar code; overridable per company and plant'),
('enterprise.costing_method',      'MOVING_AVERAGE', 'STRING', 'Costing method (MOVING_AVERAGE or STANDARD); overridable per company and plant')
ON CONFLICT (key) DO NOTHING;

INSERT INTO permissions (code, module, description) VALUES
('PLANT_MANAGE',     'IAM', 'Maintain plants and plant settings and assign users to plants'),
('PLANT_ALL_ACCESS', 'IAM', 'See and work in every plant regardless of plant assignments (cross-plant visibility)')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = 'ADMIN' AND p.code IN ('PLANT_MANAGE', 'PLANT_ALL_ACCESS')
ON CONFLICT DO NOTHING;

-- Roles whose work is company-wide see all plants: management, finance, controlling.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('MANAGEMENT', 'FINANCE_MANAGER', 'CONTROLLER') AND p.code = 'PLANT_ALL_ACCESS'
ON CONFLICT DO NOTHING;
