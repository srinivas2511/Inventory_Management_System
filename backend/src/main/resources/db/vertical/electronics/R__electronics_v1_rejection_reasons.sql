-- Electronics vertical (R20-3.1): rejection reasons.
SET search_path = ims;

INSERT INTO rejection_reasons (reason_code, name, applies_to) VALUES
  ('ELE-01', 'Solder defect (bridge / cold joint)', 'PRODUCTION'),
  ('ELE-02', 'Component out of tolerance',          'INCOMING'),
  ('ELE-03', 'Functional test failure',             'PRODUCTION'),
  ('ELE-04', 'PCB layer / via defect',              'INCOMING')
ON CONFLICT (reason_code) DO NOTHING;
