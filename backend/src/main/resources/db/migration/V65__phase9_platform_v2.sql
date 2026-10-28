-- Phase 9 — Platform extensions, part 2 (ERP-A-4, A-5, A-6, A-8, A-9)
-- Tasks: 9.4 custom fields/tags; 9.5 comments/attachments/timeline; 9.6 approval engine;
--        9.8 import framework; 9.9 global search
-- Design-gap G3: "document_links" renamed → transaction_links (avoids DMS package collision)
-- Design-gap G5: notification_preferences (per-user toggle) coexists with notification_subscriptions (V31)
SET search_path TO ims;

-- ── Custom field definitions ───────────────────────────────────────────────────
CREATE TABLE custom_field_definitions (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  entity_type    VARCHAR(40)  NOT NULL,  -- MATERIAL, PRODUCT, SUPPLIER, CUSTOMER, PURCHASE_ORDER, ...
  field_key      VARCHAR(40)  NOT NULL,  -- machine-readable key used in entity.custom JSONB
  label          VARCHAR(100) NOT NULL,
  field_type     VARCHAR(15)  NOT NULL CHECK (field_type IN ('TEXT','NUMBER','DATE','BOOLEAN','SELECT','MULTI_SELECT')),
  options        JSONB,                   -- for SELECT/MULTI_SELECT: [{value, label}]
  mandatory      BOOLEAN      NOT NULL DEFAULT FALSE,
  show_in_list   BOOLEAN      NOT NULL DEFAULT FALSE,
  sort_order     INT          NOT NULL DEFAULT 0,
  active         BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by     BIGINT,
  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by     BIGINT,
  version        BIGINT       NOT NULL DEFAULT 0,
  company_id     BIGINT       NOT NULL DEFAULT 1,
  UNIQUE (company_id, entity_type, field_key)
);

-- ── Tags ───────────────────────────────────────────────────────────────────────
CREATE TABLE tags (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name        VARCHAR(60)  NOT NULL,
  color       VARCHAR(9),               -- #RRGGBB
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by  BIGINT,
  company_id  BIGINT       NOT NULL DEFAULT 1,
  UNIQUE (company_id, name)
);

CREATE TABLE entity_tags (
  tag_id      BIGINT      NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
  entity_type VARCHAR(40) NOT NULL,
  entity_id   BIGINT      NOT NULL,
  tagged_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  tagged_by   BIGINT,
  PRIMARY KEY (tag_id, entity_type, entity_id)
);
CREATE INDEX ix_entity_tags_entity ON entity_tags(entity_type, entity_id);

-- ── Comments ───────────────────────────────────────────────────────────────────
CREATE TABLE comments (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  entity_type   VARCHAR(40)  NOT NULL,
  entity_id     BIGINT       NOT NULL,
  parent_id     BIGINT       REFERENCES comments(id),  -- for threaded replies
  body          TEXT         NOT NULL,
  edited_at     TIMESTAMPTZ,
  edited_by     BIGINT,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by    BIGINT       NOT NULL,
  company_id    BIGINT       NOT NULL DEFAULT 1,
  deleted_at    TIMESTAMPTZ            -- soft-delete only; never hard-delete a comment
);
CREATE INDEX ix_comments_entity ON comments(entity_type, entity_id, created_at);

-- ── Attachments ────────────────────────────────────────────────────────────────
CREATE TABLE attachments (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  entity_type     VARCHAR(40)  NOT NULL,
  entity_id       BIGINT       NOT NULL,
  file_name       VARCHAR(260) NOT NULL,
  content_type    VARCHAR(100),
  size_bytes      BIGINT,
  storage_key     VARCHAR(500) NOT NULL,   -- relative path or object key
  checksum_sha256 VARCHAR(64),
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by      BIGINT       NOT NULL,
  company_id      BIGINT       NOT NULL DEFAULT 1
);
CREATE INDEX ix_attachments_entity ON attachments(entity_type, entity_id);

-- ── Transaction links (ERP inter-document links; G3: renamed from document_links) ──
CREATE TABLE transaction_links (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  from_type     VARCHAR(40) NOT NULL,
  from_id       BIGINT      NOT NULL,
  to_type       VARCHAR(40) NOT NULL,
  to_id         BIGINT      NOT NULL,
  link_type     VARCHAR(20) NOT NULL DEFAULT 'RELATED' CHECK (link_type IN ('PARENT','CHILD','RELATED','FOLLOWS','PRECEDES')),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    BIGINT,
  company_id    BIGINT      NOT NULL DEFAULT 1,
  UNIQUE (from_type, from_id, to_type, to_id, link_type),
  CHECK (NOT (from_type = to_type AND from_id = to_id))
);
CREATE INDEX ix_tx_links_from ON transaction_links(from_type, from_id);
CREATE INDEX ix_tx_links_to   ON transaction_links(to_type, to_id);

-- ── Search index (full-text, GIN index) ───────────────────────────────────────
CREATE TABLE search_index (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  entity_type VARCHAR(40) NOT NULL,
  entity_id   BIGINT      NOT NULL,
  text_vector TSVECTOR    NOT NULL,
  display     VARCHAR(200),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  company_id  BIGINT      NOT NULL DEFAULT 1,
  UNIQUE (company_id, entity_type, entity_id)
);
CREATE INDEX ix_search_vector ON search_index USING GIN(text_vector);
CREATE INDEX ix_search_entity  ON search_index(company_id, entity_type, entity_id);

-- ── Approval rules (configurable approval engine) ─────────────────────────────
CREATE TABLE approval_rules (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  document_type   VARCHAR(40)  NOT NULL,
  rule_name       VARCHAR(100) NOT NULL,
  condition_expr  TEXT,                   -- JEXL or JSON condition (NULL = always apply)
  active          BOOLEAN      NOT NULL DEFAULT TRUE,
  priority        INT          NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by      BIGINT,
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by      BIGINT,
  version         BIGINT       NOT NULL DEFAULT 0,
  company_id      BIGINT       NOT NULL DEFAULT 1
);

CREATE TABLE approval_rule_steps (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rule_id             BIGINT      NOT NULL REFERENCES approval_rules(id) ON DELETE CASCADE,
  step_no             INT         NOT NULL,
  step_group          INT         NOT NULL DEFAULT 1,  -- parallel group; same group = any-of
  approver_type       VARCHAR(10) NOT NULL CHECK (approver_type IN ('ROLE','USER','TEAM')),
  approver_ref        VARCHAR(40) NOT NULL,            -- role code / user id / team code
  required_permission VARCHAR(60),
  UNIQUE (rule_id, step_no)
);

-- ── Extend approval_requests for engine columns ────────────────────────────────
-- approval_requests was created in V1; we ADD COLUMN only (additive rule).
ALTER TABLE approval_requests
  ADD COLUMN IF NOT EXISTS rule_id      BIGINT REFERENCES approval_rules(id),
  ADD COLUMN IF NOT EXISTS cycle_no     INT    NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS step_group   INT    NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS withdrawn_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS approver_type VARCHAR(10),
  ADD COLUMN IF NOT EXISTS approver_ref  VARCHAR(40);

-- ── Import jobs ────────────────────────────────────────────────────────────────
CREATE TABLE import_jobs (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  entity_type   VARCHAR(40)  NOT NULL,
  file_name     VARCHAR(260) NOT NULL,
  storage_key   VARCHAR(500) NOT NULL,
  status        VARCHAR(10)  NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','PROCESSING','DONE','FAILED','CANCELLED')),
  total_rows    INT,
  imported_rows INT          NOT NULL DEFAULT 0,
  error_rows    INT          NOT NULL DEFAULT 0,
  started_at    TIMESTAMPTZ,
  completed_at  TIMESTAMPTZ,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by    BIGINT       NOT NULL,
  company_id    BIGINT       NOT NULL DEFAULT 1
);

CREATE TABLE import_job_rows (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  job_id     BIGINT      NOT NULL REFERENCES import_jobs(id) ON DELETE CASCADE,
  row_no     INT         NOT NULL,
  status     VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','OK','ERROR','SKIPPED')),
  raw_data   JSONB       NOT NULL,
  error_msg  TEXT,
  entity_id  BIGINT
);
CREATE INDEX ix_import_job_rows ON import_job_rows(job_id, status);

-- ── Background jobs ────────────────────────────────────────────────────────────
CREATE TABLE background_jobs (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  job_type     VARCHAR(60)  NOT NULL,
  payload      JSONB        NOT NULL DEFAULT '{}',
  status       VARCHAR(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','DONE','FAILED','CANCELLED')),
  progress_pct INT          NOT NULL DEFAULT 0 CHECK (progress_pct BETWEEN 0 AND 100),
  result_msg   TEXT,
  started_at   TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  retry_count  INT          NOT NULL DEFAULT 0,
  next_retry_at TIMESTAMPTZ,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by   BIGINT,
  company_id   BIGINT       NOT NULL DEFAULT 1
);
CREATE INDEX ix_bg_jobs_status ON background_jobs(status, created_at) WHERE status IN ('PENDING','RUNNING');

-- ── Additional permissions (Phase 9 part 2 – smaller set, already covered main list in V64) ──
INSERT INTO permissions (code, module, description) VALUES
  ('APPROVAL_RULE_VIEW', 'SETTINGS', 'View configurable approval rules'),
  ('IMPORT_VIEW',        'MASTERDATA','View import job status and row errors')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('APPROVAL_RULE_VIEW','IMPORT_VIEW')
ON CONFLICT DO NOTHING;

-- Grant import and approval-view to managers
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('PURCHASE_MANAGER','SALES_MANAGER','PRODUCTION_MANAGER',
                 'FINANCE_MANAGER','QUALITY_MANAGER','HR_MANAGER')
  AND p.code IN ('IMPORT_RUN','IMPORT_VIEW','APPROVAL_RULE_VIEW')
ON CONFLICT DO NOTHING;
