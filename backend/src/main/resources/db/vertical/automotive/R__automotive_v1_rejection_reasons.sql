-- Automotive vertical (R20-1.1): rejection reasons.
SET search_path = ims;

INSERT INTO rejection_reasons (reason_code, name, applies_to) VALUES
  ('AUT-01', 'Dimensional out of tolerance', 'BOTH'),
  ('AUT-02', 'Surface defect / burr',        'BOTH'),
  ('AUT-03', 'Wrong material grade',         'INCOMING'),
  ('AUT-04', 'Hardness out of range',        'INCOMING')
ON CONFLICT (reason_code) DO NOTHING;
