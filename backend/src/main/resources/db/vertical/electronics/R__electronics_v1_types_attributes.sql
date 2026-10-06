-- Electronics vertical (R20-3.1): product type and its attribute definitions. Idempotent: safe to re-run.
SET search_path = ims;

INSERT INTO product_types (code, name, industry, display_order) VALUES
  ('ELECTRONIC_COMPONENT', 'Electronic component', 'ELECTRONICS', 40)
ON CONFLICT (code) DO NOTHING;

INSERT INTO spring_attribute_definitions
  (spring_type, attribute_code, label, data_type, unit, required, min_value, enum_values, display_order) VALUES
  ('ELECTRONIC_COMPONENT', 'pcb_layer_count', 'PCB Layer Count', 'NUMBER', NULL, false, 1,    NULL, 1),
  ('ELECTRONIC_COMPONENT', 'tolerance_mm',    'Tolerance',       'NUMBER', 'mm', false, 0,    NULL, 2),
  ('ELECTRONIC_COMPONENT', 'component_type',  'Component Type',  'ENUM',   NULL, true,  NULL, '["RESISTOR","CAPACITOR","IC","CONNECTOR","PCB"]'::jsonb, 3),
  ('ELECTRONIC_COMPONENT', 'rework_type',     'Rework Type',     'ENUM',   NULL, false, NULL, '["NONE","TOUCH_UP","REFLOW","REPLACE"]'::jsonb, 4)
ON CONFLICT (spring_type, attribute_code) DO NOTHING;
