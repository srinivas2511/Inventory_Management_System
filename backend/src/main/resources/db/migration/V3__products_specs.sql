-- Phase 1 / task 1.1 - products, spring specifications, attribute-definition catalogue, customer specs
-- (DESIGN.md section 2.3, ADR-06: relational columns for common attributes plus JSONB for type-specific ones).

CREATE TABLE products (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_code VARCHAR(30) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL,
  spring_type VARCHAR(20) NOT NULL CHECK (spring_type IN
    ('COMPRESSION','EXTENSION','TORSION','CONICAL','DISC_BELLEVILLE','WIRE_FORM','CUSTOM')),
  primary_material_id BIGINT REFERENCES materials,
  wire_diameter NUMERIC(10,3),
  outer_diameter NUMERIC(10,3),
  inner_diameter NUMERIC(10,3),
  free_length NUMERIC(10,3),
  number_of_coils NUMERIC(8,2),
  active_coils NUMERIC(8,2),
  spring_rate NUMERIC(12,4),
  max_load NUMERIC(12,3),
  min_load NUMERIC(12,3),
  working_length NUMERIC(10,3),
  solid_height NUMERIC(10,3),
  end_type VARCHAR(30),
  surface_treatment VARCHAR(60),
  heat_treatment VARCHAR(60),
  tolerance VARCHAR(60),
  unit_weight_kg NUMERIC(12,6),
  uom VARCHAR(10) NOT NULL DEFAULT 'PCS' REFERENCES uoms,
  drawing_number VARCHAR(40),
  drawing_revision VARCHAR(10),
  customer_id BIGINT REFERENCES customers,
  status VARCHAR(15) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE','OBSOLETE')),
  reorder_level NUMERIC(18,3) NOT NULL DEFAULT 0,
  standard_cost NUMERIC(18,4) NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT,
  version BIGINT NOT NULL DEFAULT 0,
  company_id BIGINT NOT NULL DEFAULT 1,
  CONSTRAINT ck_products_dimensions CHECK (
    COALESCE(wire_diameter,0) >= 0 AND COALESCE(outer_diameter,0) >= 0 AND COALESCE(inner_diameter,0) >= 0
    AND COALESCE(free_length,0) >= 0 AND COALESCE(unit_weight_kg,0) >= 0),
  CONSTRAINT ck_products_loads CHECK (min_load IS NULL OR max_load IS NULL OR min_load <= max_load)
);
CREATE INDEX ix_products_type ON products (spring_type);
CREATE INDEX ix_products_customer ON products (customer_id);
CREATE INDEX ix_products_material ON products (primary_material_id);
CREATE INDEX ix_products_search ON products USING GIN ((product_code || ' ' || name) public.gin_trgm_ops);

CREATE TABLE spring_specifications (
  product_id BIGINT PRIMARY KEY REFERENCES products ON DELETE CASCADE,
  attributes JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX ix_specs_gin ON spring_specifications USING GIN (attributes jsonb_path_ops);

-- Definitions are data: adding a row changes the product form and validator with no code change (PLAN 1.9).
CREATE TABLE spring_attribute_definitions (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  spring_type VARCHAR(20) NOT NULL CHECK (spring_type IN
    ('COMPRESSION','EXTENSION','TORSION','CONICAL','DISC_BELLEVILLE','WIRE_FORM','CUSTOM')),
  attribute_code VARCHAR(40) NOT NULL,
  label VARCHAR(80) NOT NULL,
  data_type VARCHAR(12) NOT NULL CHECK (data_type IN ('NUMBER','TEXT','ENUM','BOOLEAN')),
  unit VARCHAR(10),
  required BOOLEAN NOT NULL DEFAULT FALSE,
  min_value NUMERIC,
  max_value NUMERIC,
  enum_values JSONB,
  display_order INT NOT NULL DEFAULT 0,
  UNIQUE (spring_type, attribute_code),
  CONSTRAINT ck_attr_range CHECK (min_value IS NULL OR max_value IS NULL OR min_value <= max_value),
  CONSTRAINT ck_attr_enum CHECK (data_type <> 'ENUM' OR enum_values IS NOT NULL)
);

CREATE TABLE customer_product_specs (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  customer_id BIGINT NOT NULL REFERENCES customers,
  product_id BIGINT NOT NULL REFERENCES products,
  customer_part_no VARCHAR(40),
  special_requirements VARCHAR(500),
  attributes JSONB,
  UNIQUE (customer_id, product_id)
);
CREATE INDEX ix_cps_product ON customer_product_specs (product_id);
