-- R9-7.9 / SEC-010: step-up authentication. The trial balance and AP payments (payment runs) can require the user to
-- re-enter their credential shortly before. It is configurable: security.stepup.enabled switches it on, and
-- security.stepup.minutes is how long one successful re-authentication lasts. The method is pluggable (password today,
-- MFA later) through security.stepup.method.
SET search_path = ims;

ALTER TABLE users ADD COLUMN IF NOT EXISTS step_up_until TIMESTAMPTZ;

INSERT INTO system_settings (key, value, value_type, description) VALUES
('security.stepup.enabled', 'false', 'BOOLEAN',
    'Require re-authentication shortly before opening the trial balance or recording a supplier payment (SEC-010)'),
('security.stepup.minutes', '5', 'INTEGER',
    'How many minutes one successful re-authentication stays valid'),
('security.stepup.method', 'PASSWORD', 'STRING',
    'The re-authentication method: PASSWORD today; an MFA method can be added without changing the endpoints')
ON CONFLICT (key) DO NOTHING;
