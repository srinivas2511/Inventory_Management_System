-- Phase 1 / task 1.1 - master data: suppliers, customers, materials, warehouses, machines, operations,
-- rejection reasons (DESIGN.md section 2.3). Master data is deactivated, never deleted.

CREATE TABLE suppliers (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  supplier_code VARCHAR(20) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL,
  contact_person VARCHAR(100),
  phone VARCHAR(20),
  email VARCHAR(160),
  address VARCHAR(400),
  gst_number VARCHAR(15),
  payment_terms VARCHAR(60),
  status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  lead_time_days INT CHECK (lead_time_days IS NULL OR lead_time_days >= 0),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT,
  version BIGINT NOT NULL DEFAULT 0,
  company_id BIGINT NOT NULL DEFAULT 1
);

CREATE TABLE customers (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  customer_code VARCHAR(20) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL,
  contact_person VARCHAR(100),
  phone VARCHAR(20),
  email VARCHAR(160),
  address VARCHAR(400),
  gst_number VARCHAR(15),
  payment_terms VARCHAR(60),
  status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
  lead_time_days INT CHECK (lead_time_days IS NULL OR lead_time_days >= 0),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT,
  version BIGINT NOT NULL DEFAULT 0,
  company_id BIGINT NOT NULL DEFAULT 1
);

CREATE TABLE materials (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  material_code VARCHAR(30) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL,
  material_type VARCHAR(40) NOT NULL CHECK (material_type IN
    ('SPRING_STEEL','STAINLESS','HIGH_CARBON','ALLOY','PHOSPHOR_BRONZE','MUSIC_WIRE','CONSUMABLE')),
  grade VARCHAR(30),
  diameter_mm NUMERIC(10,3),
  uom VARCHAR(10) NOT NULL REFERENCES uoms,
  preferred_supplier_id BIGINT REFERENCES suppliers,
  min_stock NUMERIC(18,3) NOT NULL DEFAULT 0,
  reorder_level NUMERIC(18,3) NOT NULL DEFAULT 0,
  max_stock NUMERIC(18,3),
  standard_cost NUMERIC(18,4) NOT NULL DEFAULT 0,
  shelf_life_days INT CHECK (shelf_life_days IS NULL OR shelf_life_days > 0),
  description VARCHAR(500),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT,
  version BIGINT NOT NULL DEFAULT 0,
  company_id BIGINT NOT NULL DEFAULT 1,
  CONSTRAINT ck_materials_stock_levels CHECK (min_stock <= reorder_level AND (max_stock IS NULL OR reorder_level <= max_stock)),
  CONSTRAINT ck_materials_non_negative CHECK (min_stock >= 0 AND standard_cost >= 0)
);

CREATE TABLE warehouses (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  warehouse_code VARCHAR(20) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  address VARCHAR(300),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT,
  version BIGINT NOT NULL DEFAULT 0,
  company_id BIGINT NOT NULL DEFAULT 1
);

ALTER TABLE user_warehouse_access
  ADD CONSTRAINT fk_uwa_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses;

CREATE TABLE warehouse_locations (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  warehouse_id BIGINT NOT NULL REFERENCES warehouses,
  location_code VARCHAR(20) NOT NULL,
  name VARCHAR(100) NOT NULL,
  location_type VARCHAR(15) NOT NULL CHECK (location_type IN
    ('RAW_MATERIAL','WIP','FINISHED','QUARANTINE','QUALITY_PENDING','SCRAP','DISPATCH')),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT,
  version BIGINT NOT NULL DEFAULT 0,
  company_id BIGINT NOT NULL DEFAULT 1,
  UNIQUE (warehouse_id, location_code)
);

CREATE TABLE machines (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  machine_code VARCHAR(20) NOT NULL UNIQUE,
  name VARCHAR(100),
  machine_type VARCHAR(40),
  production_line VARCHAR(40),
  status VARCHAR(15) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE','RUNNING','DOWN','MAINTENANCE')),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT,
  version BIGINT NOT NULL DEFAULT 0,
  company_id BIGINT NOT NULL DEFAULT 1
);

CREATE TABLE operations (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  operation_code VARCHAR(20) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(300),
  default_machine_type VARCHAR(40),
  standard_time_min NUMERIC(10,2),
  setup_time_min NUMERIC(10,2),
  is_inspection BOOLEAN NOT NULL DEFAULT FALSE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT,
  version BIGINT NOT NULL DEFAULT 0,
  company_id BIGINT NOT NULL DEFAULT 1
);

CREATE TABLE rejection_reasons (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  reason_code VARCHAR(20) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  applies_to VARCHAR(15) NOT NULL DEFAULT 'ALL' CHECK (applies_to IN ('INCOMING','PROCESS','FINAL','ALL')),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT,
  version BIGINT NOT NULL DEFAULT 0,
  company_id BIGINT NOT NULL DEFAULT 1
);

-- trigram indexes for fast ILIKE search on codes and names (pg_trgm lives in schema public, see V0_1)
CREATE INDEX ix_materials_search ON materials USING GIN ((material_code || ' ' || name) public.gin_trgm_ops);
CREATE INDEX ix_suppliers_search ON suppliers USING GIN ((supplier_code || ' ' || name) public.gin_trgm_ops);
CREATE INDEX ix_customers_search ON customers USING GIN ((customer_code || ' ' || name) public.gin_trgm_ops);
CREATE INDEX ix_materials_supplier ON materials (preferred_supplier_id);
