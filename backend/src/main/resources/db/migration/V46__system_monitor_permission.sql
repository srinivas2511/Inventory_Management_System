-- E2E batch 6 (D-35): the actuator endpoints other than health and info (metrics, and prometheus when the production profile
-- exposes it) reveal JVM, HTTP and connection-pool internals. They were reachable by any signed-in user, so reading them
-- becomes a permission of its own, held by the Administrator only.
INSERT INTO permissions (code, module, description) VALUES
('SYSTEM_MONITOR', 'IAM', 'Read operational metrics (actuator endpoints other than health and info)')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND p.code = 'SYSTEM_MONITOR'
ON CONFLICT DO NOTHING;
