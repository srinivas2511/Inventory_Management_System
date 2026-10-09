-- GC-1..GC-5 (requirements-traceability gap rows): the schema behind them.
--   GC-1 MDM-003  alternative units of measure with conversion factors
--   GC-2 MDM-004/020/021  purchasing info on a material, supplier approved materials, customer approved products
--   GC-3 INV-004  per-item negative-stock permission (administrator only, audited)
--   GC-5 SAL-006/MDM-012  customer tolerance overrides applied during inspection
-- GC-4 (PDF delivery note and packing list) and GC-6 (CSP header) need no schema.

-- ── GC-3 ─────────────────────────────────────────────────────────────────────
ALTER TABLE materials ADD COLUMN allow_negative_stock BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE products  ADD COLUMN allow_negative_stock BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO permissions (code, module, description) VALUES
('INVENTORY_NEGATIVE_OVERRIDE', 'INVENTORY', 'Allow an individual material or product to go negative, beside the global setting')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND p.code = 'INVENTORY_NEGATIVE_OVERRIDE'
ON CONFLICT DO NOTHING;

-- ── GC-1 ─────────────────────────────────────────────────────────────────────
-- 1 alt_uom = factor_to_base x the material's own (base) unit; stock is always held in the base unit.
CREATE TABLE uom_conversions (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  material_id     BIGINT        NOT NULL REFERENCES materials(id),
  alt_uom         VARCHAR(10)   NOT NULL,
  factor_to_base  NUMERIC(18,6) NOT NULL CHECK (factor_to_base > 0),
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
  created_by      BIGINT,
  updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_by      BIGINT,
  version         BIGINT        NOT NULL DEFAULT 0,
  company_id      BIGINT        NOT NULL DEFAULT 1,
  UNIQUE (material_id, alt_uom)
);

-- ── GC-2 ─────────────────────────────────────────────────────────────────────
ALTER TABLE materials
  ADD COLUMN purchase_uom      VARCHAR(10),
  ADD COLUMN purchase_tax_code VARCHAR(15);

CREATE TABLE supplier_approved_materials (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  supplier_id  BIGINT      NOT NULL REFERENCES suppliers(id),
  material_id  BIGINT      NOT NULL REFERENCES materials(id),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by   BIGINT,
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by   BIGINT,
  version      BIGINT      NOT NULL DEFAULT 0,
  company_id   BIGINT      NOT NULL DEFAULT 1,
  UNIQUE (supplier_id, material_id)
);

CREATE TABLE customer_approved_products (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  customer_id  BIGINT      NOT NULL REFERENCES customers(id),
  product_id   BIGINT      NOT NULL REFERENCES products(id),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by   BIGINT,
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by   BIGINT,
  version      BIGINT      NOT NULL DEFAULT 0,
  company_id   BIGINT      NOT NULL DEFAULT 1,
  UNIQUE (customer_id, product_id)
);

INSERT INTO system_settings (key, value, value_type, description) VALUES
  ('purchasing.enforce_approved_suppliers', 'false', 'BOOLEAN',
   'When true a purchase order line may only name a material on the supplier''s approved materials list'),
  ('sales.enforce_approved_products', 'false', 'BOOLEAN',
   'When true a sales order line may only name a product on the customer''s approved products list')
ON CONFLICT (key) DO NOTHING;

-- ── GC-5 ─────────────────────────────────────────────────────────────────────
CREATE TABLE customer_spec_overrides (
  id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  customer_product_spec_id  BIGINT        NOT NULL REFERENCES customer_product_specs(id),
  parameter_name            VARCHAR(60)   NOT NULL,
  lower_limit               NUMERIC(14,4),
  upper_limit               NUMERIC(14,4),
  nominal                   NUMERIC(14,4),
  active                    BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
  created_by                BIGINT,
  updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_by                BIGINT,
  version                   BIGINT        NOT NULL DEFAULT 0,
  company_id                BIGINT        NOT NULL DEFAULT 1,
  CONSTRAINT chk_cso_has_limit CHECK (lower_limit IS NOT NULL OR upper_limit IS NOT NULL),
  CONSTRAINT chk_cso_order CHECK (lower_limit IS NULL OR upper_limit IS NULL OR lower_limit <= upper_limit),
  UNIQUE (customer_product_spec_id, parameter_name)
);

-- where the limits of an inspection item came from: the standard plan, or the customer's own override
ALTER TABLE quality_inspection_items ADD COLUMN spec_source VARCHAR(20) NOT NULL DEFAULT 'STANDARD';
