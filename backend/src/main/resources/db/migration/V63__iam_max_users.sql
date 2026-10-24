-- R-IAM: configurable user-count limit
-- iam.max_users = 0 means unlimited (default)
INSERT INTO ims.system_settings (key, value, value_type, description)
VALUES ('iam.max_users', '0', 'INTEGER',
        'Maximum number of user accounts (0 = unlimited)')
ON CONFLICT (key) DO NOTHING;
