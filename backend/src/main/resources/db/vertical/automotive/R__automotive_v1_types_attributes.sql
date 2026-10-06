-- Automotive vertical (R20-1.1): product type and its attribute definitions. Idempotent: safe to re-run.
SET search_path = ims;

INSERT INTO product_types (code, name, industry, display_order) VALUES
  ('AUTOMOTIVE_PART', 'Automotive part', 'AUTOMOTIVE', 20)
ON CONFLICT (code) DO NOTHING;

INSERT INTO spring_attribute_definitions
  (spring_type, attribute_code, label, data_type, unit, required, min_value, display_order) VALUES
  ('AUTOMOTIVE_PART', 'thread_pitch',      'Thread Pitch',         'NUMBER', 'mm',  true,  0, 1),
  ('AUTOMOTIVE_PART', 'tensile_strength',  'Tensile Strength',     'NUMBER', 'MPa', true,  0, 2),
  ('AUTOMOTIVE_PART', 'hardness',          'Hardness',             'NUMBER', 'HRC', false, 0, 3),
  ('AUTOMOTIVE_PART', 'surface_roughness', 'Surface Roughness Ra', 'NUMBER', 'um',  false, 0, 4),
  ('AUTOMOTIVE_PART', 'weight_kg',         'Weight',               'NUMBER', 'kg',  false, 0, 5)
ON CONFLICT (spring_type, attribute_code) DO NOTHING;
