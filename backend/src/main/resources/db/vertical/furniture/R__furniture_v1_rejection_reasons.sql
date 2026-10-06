-- Furniture vertical (R20-2.1): rejection reasons.
SET search_path = ims;

INSERT INTO rejection_reasons (reason_code, name, applies_to) VALUES
  ('FUR-01', 'Dimensional deviation',                 'BOTH'),
  ('FUR-02', 'Finish defect (scratch / uneven coat)', 'PRODUCTION'),
  ('FUR-03', 'Wrong wood grade',                      'INCOMING'),
  ('FUR-04', 'Joint / assembly defect',               'PRODUCTION')
ON CONFLICT (reason_code) DO NOTHING;
