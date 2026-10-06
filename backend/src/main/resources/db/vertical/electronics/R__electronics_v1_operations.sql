-- Electronics vertical (R20-3.1): sample operations. Standard times are illustrative - set your own.
SET search_path = ims;

INSERT INTO operations (operation_code, name, description, standard_time_min, setup_time_min, is_inspection)
SELECT v.operation_code, v.name, v.description, v.standard_time_min, v.setup_time_min, v.is_inspection
FROM (VALUES
  ('ELE-SOL', 'Soldering',       'Manual / selective soldering',  8.0::numeric,  5::numeric, false),
  ('ELE-RFL', 'Reflow',          'Reflow oven pass',             15.0::numeric, 20::numeric, false),
  ('ELE-WAV', 'Wave Solder',     'Wave soldering pass',          10.0::numeric, 20::numeric, false),
  ('ELE-FT',  'Functional Test', 'Functional test',               5.0::numeric,  5::numeric, true),
  ('ELE-BI',  'Burn-In',         'Burn-in soak',                240.0::numeric, 10::numeric, true)
) AS v(operation_code, name, description, standard_time_min, setup_time_min, is_inspection)
WHERE NOT EXISTS (SELECT 1 FROM operations o WHERE o.operation_code = v.operation_code);
