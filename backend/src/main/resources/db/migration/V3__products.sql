-- Phase 1 — Product schema + spring-type attribute definitions (DESIGN.md §2.3, §11.1)
SET search_path TO ims;

-- ── Products ──────────────────────────────────────────────────────────────────
CREATE TABLE products (
  id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_code       VARCHAR(30)   NOT NULL,
  name               VARCHAR(150)  NOT NULL,
  spring_type        VARCHAR(20)   NOT NULL CHECK (
    spring_type IN ('COMPRESSION','EXTENSION','TORSION','CONICAL','BELLEVILLE','WIRE_FORM','CUSTOM')
  ),
  primary_material_id BIGINT       REFERENCES materials(id),
  wire_diameter      NUMERIC(10,3),
  outer_diameter     NUMERIC(10,3),
  inner_diameter     NUMERIC(10,3),
  free_length        NUMERIC(10,3),
  number_of_coils    NUMERIC(8,2),
  active_coils       NUMERIC(8,2),
  spring_rate        NUMERIC(12,4),
  max_load           NUMERIC(12,3),
  min_load           NUMERIC(12,3),
  working_length     NUMERIC(10,3),
  solid_height       NUMERIC(10,3),
  end_type           VARCHAR(30),
  surface_treatment  VARCHAR(60),
  heat_treatment     VARCHAR(60),
  tolerance          VARCHAR(60),
  unit_weight_kg     NUMERIC(12,6),
  uom                VARCHAR(10)   NOT NULL DEFAULT 'PCS',
  drawing_number     VARCHAR(40),
  drawing_revision   VARCHAR(10),
  customer_id        BIGINT        REFERENCES customers(id),
  status             VARCHAR(15)   NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE','OBSOLETE')),
  reorder_level      NUMERIC(18,3) DEFAULT 0,
  standard_cost      NUMERIC(18,4) DEFAULT 0,
  active             BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
  created_by         BIGINT,
  updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_by         BIGINT,
  version            BIGINT        NOT NULL DEFAULT 0,
  company_id         BIGINT        NOT NULL DEFAULT 1,
  CONSTRAINT uq_products_code UNIQUE (product_code)
);
CREATE INDEX ix_products_spring_type ON products(spring_type);
CREATE INDEX ix_products_status      ON products(status);

-- Spring-type–specific attributes stored as JSONB
CREATE TABLE spring_specifications (
  product_id BIGINT PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
  attributes JSONB  NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX ix_specs_gin ON spring_specifications USING GIN (attributes jsonb_path_ops);

-- Attribute definition catalogue (drives the DynamicSpecForm on the frontend)
CREATE TABLE spring_attribute_definitions (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  spring_type    VARCHAR(20)  NOT NULL,
  attribute_code VARCHAR(40)  NOT NULL,
  label          VARCHAR(80)  NOT NULL,
  data_type      VARCHAR(12)  NOT NULL CHECK (data_type IN ('NUMBER','TEXT','ENUM','BOOLEAN')),
  unit           VARCHAR(10),
  required       BOOLEAN      NOT NULL DEFAULT FALSE,
  min_value      NUMERIC,
  max_value      NUMERIC,
  enum_values    JSONB,
  display_order  INT          NOT NULL DEFAULT 0,
  CONSTRAINT uq_attr_def UNIQUE (spring_type, attribute_code)
);

-- Customer-specific product specs
CREATE TABLE customer_product_specs (
  id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  customer_id          BIGINT      NOT NULL REFERENCES customers(id),
  product_id           BIGINT      NOT NULL REFERENCES products(id),
  customer_part_no     VARCHAR(40),
  special_requirements VARCHAR(500),
  attributes           JSONB,
  CONSTRAINT uq_customer_product_spec UNIQUE (customer_id, product_id)
);

-- ── Seed: spring attribute definitions per type ───────────────────────────────

-- COMPRESSION
INSERT INTO spring_attribute_definitions (spring_type, attribute_code, label, data_type, unit, required, min_value, display_order) VALUES
  ('COMPRESSION', 'spring_rate',    'Spring Rate',      'NUMBER', 'N/mm', true,  0,   1),
  ('COMPRESSION', 'max_load',       'Maximum Load',     'NUMBER', 'N',    true,  0,   2),
  ('COMPRESSION', 'min_load',       'Minimum Load',     'NUMBER', 'N',    false, 0,   3),
  ('COMPRESSION', 'solid_height',   'Solid Height',     'NUMBER', 'mm',   false, 0,   4),
  ('COMPRESSION', 'end_type',       'End Type',         'ENUM',   NULL,   true,  NULL, 5),
  ('COMPRESSION', 'coil_direction', 'Coil Direction',   'ENUM',   NULL,   false, NULL, 6);

UPDATE spring_attribute_definitions SET enum_values = '["CLOSED_GROUND","CLOSED_NOT_GROUND","OPEN_GROUND","OPEN_NOT_GROUND"]'::jsonb
  WHERE spring_type = 'COMPRESSION' AND attribute_code = 'end_type';
UPDATE spring_attribute_definitions SET enum_values = '["RIGHT","LEFT"]'::jsonb
  WHERE spring_type = 'COMPRESSION' AND attribute_code = 'coil_direction';

-- EXTENSION
INSERT INTO spring_attribute_definitions (spring_type, attribute_code, label, data_type, unit, required, min_value, display_order) VALUES
  ('EXTENSION', 'body_diameter',  'Body Diameter',     'NUMBER', 'mm',   true,  0,   1),
  ('EXTENSION', 'hook_type',      'Hook Type',         'ENUM',   NULL,   true,  NULL, 2),
  ('EXTENSION', 'hook_length',    'Hook Length',       'NUMBER', 'mm',   false, 0,   3),
  ('EXTENSION', 'spring_rate',    'Spring Rate',       'NUMBER', 'N/mm', true,  0,   4),
  ('EXTENSION', 'initial_tension','Initial Tension',   'NUMBER', 'N',    false, 0,   5),
  ('EXTENSION', 'coil_direction', 'Coil Direction',    'ENUM',   NULL,   false, NULL, 6);

UPDATE spring_attribute_definitions SET enum_values = '["MACHINE_LOOP","EXTENDED_LOOP","FULL_LOOP","REDUCED_LOOP","HOOK"]'::jsonb
  WHERE spring_type = 'EXTENSION' AND attribute_code = 'hook_type';
UPDATE spring_attribute_definitions SET enum_values = '["RIGHT","LEFT"]'::jsonb
  WHERE spring_type = 'EXTENSION' AND attribute_code = 'coil_direction';

-- TORSION
INSERT INTO spring_attribute_definitions (spring_type, attribute_code, label, data_type, unit, required, min_value, display_order) VALUES
  ('TORSION', 'coil_diameter', 'Coil Diameter',    'NUMBER', 'mm',   true,  0,   1),
  ('TORSION', 'leg_length_1',  'Leg Length 1',     'NUMBER', 'mm',   true,  0,   2),
  ('TORSION', 'leg_length_2',  'Leg Length 2',     'NUMBER', 'mm',   false, 0,   3),
  ('TORSION', 'leg_angle',     'Leg Angle',        'NUMBER', 'deg',  true,  0,   4),
  ('TORSION', 'torque',        'Rated Torque',     'NUMBER', 'Nmm',  true,  0,   5),
  ('TORSION', 'spring_rate',   'Angular Rate',     'NUMBER', 'Nmm/deg', false, 0, 6),
  ('TORSION', 'wind_direction','Wind Direction',   'ENUM',   NULL,   false, NULL, 7);

UPDATE spring_attribute_definitions SET enum_values = '["RIGHT","LEFT"]'::jsonb
  WHERE spring_type = 'TORSION' AND attribute_code = 'wind_direction';

-- CONICAL
INSERT INTO spring_attribute_definitions (spring_type, attribute_code, label, data_type, unit, required, min_value, display_order) VALUES
  ('CONICAL', 'large_od',     'Large End O.D.',   'NUMBER', 'mm', true,  0,   1),
  ('CONICAL', 'small_od',     'Small End O.D.',   'NUMBER', 'mm', true,  0,   2),
  ('CONICAL', 'spring_rate',  'Spring Rate',      'NUMBER', 'N/mm', true, 0,  3),
  ('CONICAL', 'solid_height', 'Solid Height',     'NUMBER', 'mm', false, 0,   4),
  ('CONICAL', 'end_type',     'End Type',         'ENUM',   NULL,  false, NULL, 5);

UPDATE spring_attribute_definitions SET enum_values = '["CLOSED_GROUND","CLOSED_NOT_GROUND","OPEN_GROUND","OPEN_NOT_GROUND"]'::jsonb
  WHERE spring_type = 'CONICAL' AND attribute_code = 'end_type';

-- BELLEVILLE (disc spring) — default from PLAN §14 item 11
INSERT INTO spring_attribute_definitions (spring_type, attribute_code, label, data_type, unit, required, min_value, display_order) VALUES
  ('BELLEVILLE', 'inner_diameter', 'Inner Diameter',  'NUMBER', 'mm', true,  0,   1),
  ('BELLEVILLE', 'thickness',      'Thickness',       'NUMBER', 'mm', true,  0,   2),
  ('BELLEVILLE', 'free_height',    'Free Height',     'NUMBER', 'mm', true,  0,   3),
  ('BELLEVILLE', 'load',           'Rated Load',      'NUMBER', 'N',  true,  0,   4),
  ('BELLEVILLE', 'stack_qty',      'Stack Quantity',  'NUMBER', NULL, false, 1,   5);

-- WIRE_FORM (free-form, basic dimensions only)
INSERT INTO spring_attribute_definitions (spring_type, attribute_code, label, data_type, unit, required, display_order) VALUES
  ('WIRE_FORM', 'form_description', 'Form Description', 'TEXT', NULL, true,  1),
  ('WIRE_FORM', 'finish',           'Surface Finish',   'TEXT', NULL, false, 2);

-- CUSTOM (completely free-form)
INSERT INTO spring_attribute_definitions (spring_type, attribute_code, label, data_type, unit, required, display_order) VALUES
  ('CUSTOM', 'specification', 'Specification', 'TEXT', NULL, true, 1);
