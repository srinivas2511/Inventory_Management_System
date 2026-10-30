-- Phase 9 task 9.12 — Document reminders + @mentions
-- Phase 9 task 9.13 — Packaging details on dispatches and sales invoices
SET search_path TO ims;

-- 9.12 document reminders
CREATE TABLE IF NOT EXISTS document_reminders (
  id          BIGSERIAL PRIMARY KEY,
  entity_type VARCHAR(50) NOT NULL,
  entity_id   BIGINT NOT NULL,
  remind_at   TIMESTAMPTZ NOT NULL,
  user_id     BIGINT NOT NULL REFERENCES users(id),
  note        TEXT,
  dismissed   BOOLEAN NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_doc_reminders_user_remind ON document_reminders (user_id, remind_at) WHERE dismissed = FALSE;
CREATE INDEX IF NOT EXISTS idx_doc_reminders_entity ON document_reminders (entity_type, entity_id);

-- 9.12 document mentions
CREATE TABLE IF NOT EXISTS document_mentions (
  id                 BIGSERIAL PRIMARY KEY,
  entity_type        VARCHAR(50) NOT NULL,
  entity_id          BIGINT NOT NULL,
  mentioned_user_id  BIGINT NOT NULL REFERENCES users(id),
  mentioned_by_id    BIGINT NOT NULL REFERENCES users(id),
  note               TEXT,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_doc_mentions_entity ON document_mentions (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_doc_mentions_user ON document_mentions (mentioned_user_id);

INSERT INTO permissions (code, module, description) VALUES
  ('REMINDER_CREATE', 'DOCUMENTS', 'Create document reminders')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
  SELECT r.id, p.id FROM roles r, permissions p
  WHERE r.code = 'ADMIN' AND p.code = 'REMINDER_CREATE'
ON CONFLICT DO NOTHING;

-- 9.13 packaging details on dispatches and sales invoices
ALTER TABLE dispatches
  ADD COLUMN IF NOT EXISTS packaging_details JSONB;

ALTER TABLE sales_invoices
  ADD COLUMN IF NOT EXISTS packaging_details JSONB;
