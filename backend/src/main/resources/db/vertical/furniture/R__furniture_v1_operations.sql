-- Furniture vertical (R20-2.1): sample operations. Standard times are illustrative - set your own.
SET search_path = ims;

INSERT INTO operations (operation_code, name, description, standard_time_min, setup_time_min, is_inspection)
SELECT v.operation_code, v.name, v.description, v.standard_time_min, v.setup_time_min, v.is_inspection
FROM (VALUES
  ('FUR-CUT',  'Cutting',       'Panel and timber cutting',    12.0::numeric, 10::numeric, false),
  ('FUR-SAND', 'Sanding',       'Surface sanding',             20.0::numeric,  5::numeric, false),
  ('FUR-ASM',  'Assembly',      'Frame and joint assembly',    35.0::numeric, 10::numeric, false),
  ('FUR-PNT',  'Painting',      'Painting / lacquering',       40.0::numeric, 15::numeric, false),
  ('FUR-QC',   'QC Inspection', 'Final finish and fit check',   6.0::numeric,  2::numeric, true)
) AS v(operation_code, name, description, standard_time_min, setup_time_min, is_inspection)
WHERE NOT EXISTS (SELECT 1 FROM operations o WHERE o.operation_code = v.operation_code);
