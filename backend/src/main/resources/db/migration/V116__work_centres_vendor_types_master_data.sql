-- V116 — Work centres, vendor types, operator flag, extended master data fields
SET search_path TO ims;

-- ── Work Centres ──────────────────────────────────────────────────────────────
CREATE TABLE work_centres (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code         VARCHAR(20)  NOT NULL,
  name         VARCHAR(100) NOT NULL,
  description  VARCHAR(300),
  active       BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by   BIGINT,
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by   BIGINT,
  version      BIGINT       NOT NULL DEFAULT 0,
  company_id   BIGINT       NOT NULL DEFAULT 1,
  CONSTRAINT uq_work_centres_code UNIQUE (company_id, code)
);

-- ── Vendor Types ──────────────────────────────────────────────────────────────
CREATE TABLE vendor_types (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code         VARCHAR(20)  NOT NULL,
  description  VARCHAR(200) NOT NULL,
  active       BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by   BIGINT,
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by   BIGINT,
  version      BIGINT       NOT NULL DEFAULT 0,
  company_id   BIGINT       NOT NULL DEFAULT 1,
  CONSTRAINT uq_vendor_types_code UNIQUE (company_id, code)
);

-- ── Machines: add description + work_centre_id ────────────────────────────────
ALTER TABLE machines
  ADD COLUMN IF NOT EXISTS description    VARCHAR(300),
  ADD COLUMN IF NOT EXISTS work_centre_id BIGINT REFERENCES work_centres(id);

-- ── Suppliers: add notes + vendor_type_id ─────────────────────────────────────
ALTER TABLE suppliers
  ADD COLUMN IF NOT EXISTS notes          TEXT,
  ADD COLUMN IF NOT EXISTS vendor_type_id BIGINT REFERENCES vendor_types(id);

-- ── Customers: split address into structured fields ───────────────────────────
ALTER TABLE customers
  ADD COLUMN IF NOT EXISTS region_code VARCHAR(20),
  ADD COLUMN IF NOT EXISTS state       VARCHAR(60),
  ADD COLUMN IF NOT EXISTS city        VARCHAR(60),
  ADD COLUMN IF NOT EXISTS country     VARCHAR(60) DEFAULT 'India';

-- ── Materials: source type + customer material flag ───────────────────────────
ALTER TABLE materials
  ADD COLUMN IF NOT EXISTS source_type          VARCHAR(10) NOT NULL DEFAULT 'LOCAL'
    CHECK (source_type IN ('LOCAL','IMPORT')),
  ADD COLUMN IF NOT EXISTS is_customer_material BOOLEAN     NOT NULL DEFAULT FALSE;

-- ── Products: work_centre_id + weight_per_1000_kgs ───────────────────────────
ALTER TABLE products
  ADD COLUMN IF NOT EXISTS work_centre_id      BIGINT      REFERENCES work_centres(id),
  ADD COLUMN IF NOT EXISTS weight_per_1000_kgs NUMERIC(12,6);

-- ── Product-Customers junction (multi-customer support) ───────────────────────
CREATE TABLE product_customers (
  product_id  BIGINT NOT NULL REFERENCES products(id)  ON DELETE CASCADE,
  customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
  PRIMARY KEY (product_id, customer_id)
);

-- ── Employees: operator flag ──────────────────────────────────────────────────
ALTER TABLE employees
  ADD COLUMN IF NOT EXISTS is_operator   BOOLEAN     NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS operator_code VARCHAR(20);
CREATE UNIQUE INDEX IF NOT EXISTS uq_employees_operator_code
  ON employees(company_id, operator_code) WHERE operator_code IS NOT NULL;

-- ── Permissions ───────────────────────────────────────────────────────────────
INSERT INTO permissions (code, module, description) VALUES
  ('WORK_CENTRE_VIEW',   'MASTERDATA',  'View work centres'),
  ('WORK_CENTRE_MANAGE', 'MASTERDATA',  'Create and edit work centres'),
  ('VENDOR_TYPE_VIEW',   'PROCUREMENT', 'View vendor types'),
  ('VENDOR_TYPE_MANAGE', 'PROCUREMENT', 'Create and edit vendor types'),
  ('OPERATOR_VIEW',      'PRODUCTION',  'View operators'),
  ('OPERATOR_MANAGE',    'PRODUCTION',  'Create and edit operators')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
  SELECT r.id, p.id FROM roles r, permissions p
  WHERE r.code = 'ADMIN'
    AND p.code IN ('WORK_CENTRE_VIEW','WORK_CENTRE_MANAGE','VENDOR_TYPE_VIEW','VENDOR_TYPE_MANAGE','OPERATOR_VIEW','OPERATOR_MANAGE')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
  SELECT r.id, p.id FROM roles r, permissions p
  WHERE r.code IN ('PRODUCTION_MANAGER','STORE_MANAGER')
    AND p.code IN ('WORK_CENTRE_VIEW','OPERATOR_VIEW','OPERATOR_MANAGE')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
  SELECT r.id, p.id FROM roles r, permissions p
  WHERE r.code IN ('PURCHASE_MANAGER')
    AND p.code IN ('VENDOR_TYPE_VIEW','VENDOR_TYPE_MANAGE')
ON CONFLICT DO NOTHING;
