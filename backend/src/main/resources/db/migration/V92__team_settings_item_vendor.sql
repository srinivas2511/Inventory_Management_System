-- Phase 9 task 9.7 — Team notification settings + module access per team
-- Phase 9 task 9.11 — Item-vendor mappings
SET search_path TO ims;

-- 9.7 team settings
ALTER TABLE teams
  ADD COLUMN IF NOT EXISTS notification_settings JSONB NOT NULL DEFAULT '{}',
  ADD COLUMN IF NOT EXISTS module_access TEXT[] NOT NULL DEFAULT '{}';

-- 9.11 item-vendor mappings
CREATE TABLE IF NOT EXISTS item_vendor_mappings (
  id                  BIGSERIAL PRIMARY KEY,
  company_id          BIGINT NOT NULL DEFAULT 1,
  item_id             BIGINT NOT NULL REFERENCES materials(id),
  supplier_id         BIGINT NOT NULL REFERENCES suppliers(id),
  supplier_item_code  VARCHAR(100),
  supplier_item_name  VARCHAR(255),
  last_price          NUMERIC(18,4),
  moq                 NUMERIC(18,3) NOT NULL DEFAULT 0,
  lead_time_days      INT NOT NULL DEFAULT 0,
  preferred           BOOLEAN NOT NULL DEFAULT FALSE,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (item_id, supplier_id)
);

INSERT INTO permissions (code, module, description) VALUES
  ('ITEM_VENDOR_VIEW',   'PROCUREMENT', 'View item-vendor mappings'),
  ('ITEM_VENDOR_MANAGE', 'PROCUREMENT', 'Manage item-vendor mappings')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
  SELECT r.id, p.id FROM roles r, permissions p
  WHERE r.code IN ('ADMIN', 'PURCHASE_MANAGER')
    AND p.code IN ('ITEM_VENDOR_VIEW', 'ITEM_VENDOR_MANAGE')
ON CONFLICT DO NOTHING;
