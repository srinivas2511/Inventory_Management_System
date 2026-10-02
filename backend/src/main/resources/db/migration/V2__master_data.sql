-- Phase 1 — Master data schema + standard reference seed (DESIGN.md §2.3, §11.1)
SET search_path TO ims;

-- ── Suppliers ─────────────────────────────────────────────────────────────────
CREATE TABLE suppliers (
  id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  supplier_code        VARCHAR(20)  NOT NULL,
  name                 VARCHAR(150) NOT NULL,
  contact_person       VARCHAR(100),
  phone                VARCHAR(20),
  email                VARCHAR(160),
  address              VARCHAR(400),
  gst_number           VARCHAR(15),
  payment_terms        VARCHAR(60),
  lead_time_days       INT,
  status               VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
  created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by           BIGINT,
  updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by           BIGINT,
  version              BIGINT       NOT NULL DEFAULT 0,
  company_id           BIGINT       NOT NULL DEFAULT 1,
  CONSTRAINT uq_suppliers_code UNIQUE (supplier_code)
);

-- ── Customers ─────────────────────────────────────────────────────────────────
CREATE TABLE customers (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  customer_code  VARCHAR(20)  NOT NULL,
  name           VARCHAR(150) NOT NULL,
  contact_person VARCHAR(100),
  phone          VARCHAR(20),
  email          VARCHAR(160),
  address        VARCHAR(400),
  gst_number     VARCHAR(15),
  payment_terms  VARCHAR(60),
  status         VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by     BIGINT,
  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by     BIGINT,
  version        BIGINT       NOT NULL DEFAULT 0,
  company_id     BIGINT       NOT NULL DEFAULT 1,
  CONSTRAINT uq_customers_code UNIQUE (customer_code)
);

-- ── Materials ─────────────────────────────────────────────────────────────────
CREATE TABLE materials (
  id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  material_code        VARCHAR(30)  NOT NULL,
  name                 VARCHAR(150) NOT NULL,
  material_type        VARCHAR(40)  NOT NULL,
  grade                VARCHAR(30),
  diameter_mm          NUMERIC(10,3),
  uom                  VARCHAR(10)  NOT NULL REFERENCES uoms(code),
  preferred_supplier_id BIGINT      REFERENCES suppliers(id),
  min_stock            NUMERIC(18,3) NOT NULL DEFAULT 0,
  reorder_level        NUMERIC(18,3) NOT NULL DEFAULT 0,
  max_stock            NUMERIC(18,3),
  standard_cost        NUMERIC(18,4) NOT NULL DEFAULT 0,
  shelf_life_days      INT,
  description          VARCHAR(500),
  active               BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  created_by           BIGINT,
  updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_by           BIGINT,
  version              BIGINT        NOT NULL DEFAULT 0,
  company_id           BIGINT        NOT NULL DEFAULT 1,
  CONSTRAINT uq_materials_code UNIQUE (material_code),
  CONSTRAINT chk_materials_stock_levels CHECK (min_stock <= reorder_level AND (max_stock IS NULL OR reorder_level <= max_stock))
);
CREATE INDEX ix_materials_type ON materials(material_type);

-- ── Warehouses ────────────────────────────────────────────────────────────────
CREATE TABLE warehouses (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  warehouse_code VARCHAR(20)  NOT NULL,
  name           VARCHAR(100) NOT NULL,
  address        VARCHAR(300),
  active         BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by     BIGINT,
  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by     BIGINT,
  version        BIGINT       NOT NULL DEFAULT 0,
  company_id     BIGINT       NOT NULL DEFAULT 1,
  CONSTRAINT uq_warehouses_code UNIQUE (warehouse_code)
);

CREATE TABLE warehouse_locations (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  warehouse_id   BIGINT      NOT NULL REFERENCES warehouses(id),
  location_code  VARCHAR(20) NOT NULL,
  name           VARCHAR(100) NOT NULL,
  location_type  VARCHAR(15) NOT NULL CHECK (
    location_type IN ('RAW_MATERIAL','WIP','FINISHED','QUARANTINE','QUALITY_PENDING','SCRAP','DISPATCH')
  ),
  active         BOOLEAN     NOT NULL DEFAULT TRUE,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by     BIGINT,
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by     BIGINT,
  version        BIGINT      NOT NULL DEFAULT 0,
  company_id     BIGINT      NOT NULL DEFAULT 1,
  CONSTRAINT uq_location_code UNIQUE (warehouse_id, location_code)
);

-- ── Machines ──────────────────────────────────────────────────────────────────
CREATE TABLE machines (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  machine_code    VARCHAR(20)  NOT NULL,
  name            VARCHAR(100) NOT NULL,
  machine_type    VARCHAR(40),
  production_line VARCHAR(40),
  status          VARCHAR(15)  NOT NULL DEFAULT 'AVAILABLE',
  active          BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by      BIGINT,
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by      BIGINT,
  version         BIGINT       NOT NULL DEFAULT 0,
  company_id      BIGINT       NOT NULL DEFAULT 1,
  CONSTRAINT uq_machines_code UNIQUE (machine_code)
);

-- ── Operations ────────────────────────────────────────────────────────────────
CREATE TABLE operations (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  operation_code      VARCHAR(20)   NOT NULL,
  name                VARCHAR(80)   NOT NULL,
  description         VARCHAR(300),
  default_machine_type VARCHAR(40),
  standard_time_min   NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (standard_time_min >= 0),
  setup_time_min      NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (setup_time_min >= 0),
  is_inspection       BOOLEAN       NOT NULL DEFAULT FALSE,
  active              BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
  created_by          BIGINT,
  updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_by          BIGINT,
  version             BIGINT        NOT NULL DEFAULT 0,
  company_id          BIGINT        NOT NULL DEFAULT 1,
  CONSTRAINT uq_operations_code UNIQUE (operation_code)
);

-- ── Rejection reasons ─────────────────────────────────────────────────────────
CREATE TABLE rejection_reasons (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  reason_code VARCHAR(20)  NOT NULL,
  name        VARCHAR(100) NOT NULL,
  applies_to  VARCHAR(15),   -- INCOMING | PRODUCTION | BOTH
  active      BOOLEAN      NOT NULL DEFAULT TRUE,
  CONSTRAINT uq_rejection_reason_code UNIQUE (reason_code)
);

-- ── Seed: default warehouse and locations (DESIGN §11.1) ─────────────────────
INSERT INTO warehouses (warehouse_code, name, address) VALUES
  ('MAIN-WH', 'Main Warehouse', 'Factory premises');

INSERT INTO warehouse_locations (warehouse_id, location_code, name, location_type)
SELECT w.id, loc.code, loc.name, loc.type
FROM warehouses w, (VALUES
  ('RM-01',  'Raw Material Store 1',       'RAW_MATERIAL'),
  ('RM-02',  'Raw Material Store 2',       'RAW_MATERIAL'),
  ('WIP-01', 'WIP Area',                   'WIP'),
  ('FG-01',  'Finished Goods Store',       'FINISHED'),
  ('QRN-01', 'Quarantine Area',            'QUARANTINE'),
  ('FGQ-01', 'FG Quality Pending',         'QUALITY_PENDING'),
  ('SCRAP-01','Scrap Yard',                'SCRAP'),
  ('DISP-01', 'Dispatch Bay',              'DISPATCH')
) AS loc(code, name, type)
WHERE w.warehouse_code = 'MAIN-WH';

-- ── Seed: standard operations ─────────────────────────────────────────────────
INSERT INTO operations (operation_code, name, description, standard_time_min, setup_time_min, is_inspection) VALUES
  ('WIRE-DRAW',    'Wire Drawing',       'Draw wire to required diameter',                 30, 15, false),
  ('COILING',      'Coiling',            'Form wire into spring coils on CNC coiler',      15, 10, false),
  ('CUTTING',      'Cutting',            'Cut wire / spring to required length',           10,  5, false),
  ('GRINDING',     'Grinding',           'Grind spring ends flat',                         20, 10, false),
  ('HEAT-TREAT',   'Heat Treatment',     'Stress-relief or hardening furnace cycle',       60, 20, false),
  ('SHOT-PEEN',    'Shot Peening',       'Shot peening for fatigue resistance',            30, 15, false),
  ('SURFACE-TREAT','Surface Treatment',  'Zinc/nickel plating or phosphating',             45, 20, false),
  ('INSPECTION',   'Inspection',         'Dimensional and functional inspection',          20,  5, true),
  ('PACKAGING',    'Packaging',          'Count, pack and label finished goods',           15,  5, false);

-- ── Seed: rejection reasons ────────────────────────────────────────────────────
INSERT INTO rejection_reasons (reason_code, name, applies_to) VALUES
  ('DIM-OOT',      'Out of dimensional tolerance',    'BOTH'),
  ('SURFACE-DEF',  'Surface defect / corrosion',      'BOTH'),
  ('LOAD-FAIL',    'Load / rate test failure',         'BOTH'),
  ('HEAT-DEFECT',  'Heat treatment deficiency',        'BOTH'),
  ('COIL-BIND',    'Coil binding or tangling',         'PRODUCTION'),
  ('VISUAL-REJ',   'Visual inspection failure',        'BOTH'),
  ('FREE-LEN-OOT', 'Free length out of specification', 'BOTH'),
  ('MAT-DEFECT',   'Raw material defect',              'INCOMING'),
  ('CUST-RETURN',  'Customer return / complaint',      'PRODUCTION');
