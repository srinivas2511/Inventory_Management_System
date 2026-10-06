-- Automotive vertical (R20-1.1): sample operations. Standard times are illustrative - set your own.
SET search_path = ims;

INSERT INTO operations (operation_code, name, description, standard_time_min, setup_time_min, is_inspection)
SELECT v.operation_code, v.name, v.description, v.standard_time_min, v.setup_time_min, v.is_inspection
FROM (VALUES
  ('AUT-STM', 'Stamping',       'Press stamping',               2.5::numeric,   5::numeric, false),
  ('AUT-WLD', 'Welding',        'Welding',                      5.0::numeric,  10::numeric, false),
  ('AUT-MCH', 'Machining',      'CNC machining',                8.0::numeric,  15::numeric, false),
  ('AUT-HT',  'Heat Treatment', 'Hardening / tempering cycle', 120.0::numeric, 20::numeric, false),
  ('AUT-PLT', 'Plating',        'Surface plating',             45.0::numeric,  20::numeric, false),
  ('AUT-INS', 'Inspection',     'Dimensional inspection',       3.0::numeric,   2::numeric, true)
) AS v(operation_code, name, description, standard_time_min, setup_time_min, is_inspection)
WHERE NOT EXISTS (SELECT 1 FROM operations o WHERE o.operation_code = v.operation_code);
