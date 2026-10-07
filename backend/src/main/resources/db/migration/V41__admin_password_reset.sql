-- Resetting someone else's password lets the caller sign in as them, so it is a permission of its own (not USER_UPDATE).
INSERT INTO permissions (code, module, description) VALUES
('USER_PASSWORD_RESET', 'IAM', 'Reset another user''s password to a temporary one that must be changed at first sign-in')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND p.code = 'USER_PASSWORD_RESET'
ON CONFLICT DO NOTHING;
