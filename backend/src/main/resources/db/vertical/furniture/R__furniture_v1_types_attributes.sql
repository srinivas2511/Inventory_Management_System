-- Furniture vertical (R20-2.1): product type and its attribute definitions. Idempotent: safe to re-run.
SET search_path = ims;

INSERT INTO product_types (code, name, industry, display_order) VALUES
  ('FURNITURE_ITEM', 'Furniture item', 'FURNITURE', 30)
ON CONFLICT (code) DO NOTHING;

INSERT INTO spring_attribute_definitions
  (spring_type, attribute_code, label, data_type, unit, required, min_value, enum_values, display_order) VALUES
  ('FURNITURE_ITEM', 'leg_height',       'Leg Height',    'NUMBER', 'mm', false, 0,    NULL, 1),
  ('FURNITURE_ITEM', 'load_capacity_kg', 'Load Capacity', 'NUMBER', 'kg', true,  0,    NULL, 2),
  ('FURNITURE_ITEM', 'finish_type',      'Finish Type',   'ENUM',   NULL, true,  NULL, '["MATTE","GLOSS","NATURAL","LACQUER"]'::jsonb, 3),
  ('FURNITURE_ITEM', 'wood_grade',       'Wood Grade',    'ENUM',   NULL, false, NULL, '["A","B","C"]'::jsonb, 4)
ON CONFLICT (spring_type, attribute_code) DO NOTHING;
