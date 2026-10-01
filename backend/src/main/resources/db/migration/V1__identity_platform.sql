-- Phase 1 / task 1.1 - identity, access and platform tables (DESIGN.md sections 2.1, 2.2).
-- Reference data (roles, permissions, settings) is seeded by a later migration (task 1.2) so the schema stays reviewable.
-- Flyway creates and targets schema "ims" (application.yml).

CREATE TABLE uoms (
  code VARCHAR(10) PRIMARY KEY,
  name VARCHAR(40) NOT NULL,
  kind VARCHAR(10) NOT NULL
);
INSERT INTO uoms (code, name, kind) VALUES
  ('KG',     'Kilogram',  'WEIGHT'),
  ('G',      'Gram',      'WEIGHT'),
  ('PCS',    'Pieces',    'COUNT'),
  ('M',      'Metre',     'LENGTH'),
  ('MM',     'Millimetre','LENGTH'),
  ('CARTON', 'Carton',    'COUNT');

-- ---------------------------------------------------------------- identity
CREATE TABLE users (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  employee_code VARCHAR(20) UNIQUE,
  full_name VARCHAR(120) NOT NULL,
  email VARCHAR(160) NOT NULL UNIQUE,
  phone VARCHAR(20),
  password_hash VARCHAR(100) NOT NULL,
  password_changed_at TIMESTAMPTZ,
  must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
  failed_attempts INT NOT NULL DEFAULT 0,
  locked_until TIMESTAMPTZ,
  permission_version INT NOT NULL DEFAULT 1,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  last_login_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT,
  version BIGINT NOT NULL DEFAULT 0,
  company_id BIGINT NOT NULL DEFAULT 1,
  CONSTRAINT ck_users_failed_attempts CHECK (failed_attempts >= 0)
);
-- usernames and e-mail are matched case-insensitively
CREATE UNIQUE INDEX ux_users_username_lower ON users (lower(username));
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

CREATE TABLE roles (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code VARCHAR(40) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(300),
  system_role BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT,
  version BIGINT NOT NULL DEFAULT 0,
  company_id BIGINT NOT NULL DEFAULT 1
);

CREATE TABLE permissions (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code VARCHAR(60) NOT NULL UNIQUE,
  module VARCHAR(30) NOT NULL,
  description VARCHAR(200)
);

CREATE TABLE user_roles (
  user_id BIGINT NOT NULL REFERENCES users,
  role_id BIGINT NOT NULL REFERENCES roles,
  PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_user_roles_role ON user_roles (role_id);

CREATE TABLE role_permissions (
  role_id BIGINT NOT NULL REFERENCES roles,
  permission_id BIGINT NOT NULL REFERENCES permissions,
  PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX ix_role_permissions_permission ON role_permissions (permission_id);

-- warehouse FK is added in V2 once warehouses exist
CREATE TABLE user_warehouse_access (
  user_id BIGINT NOT NULL REFERENCES users,
  warehouse_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, warehouse_id)
);

CREATE TABLE refresh_tokens (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users,
  token_hash CHAR(64) NOT NULL UNIQUE,
  family_id UUID NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  replaced_by BIGINT REFERENCES refresh_tokens,
  user_agent VARCHAR(200),
  ip VARCHAR(45),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);

CREATE TABLE password_reset_tokens (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users,
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ
);
CREATE INDEX ix_password_reset_user ON password_reset_tokens (user_id);

CREATE TABLE password_history (
  user_id BIGINT NOT NULL REFERENCES users,
  password_hash VARCHAR(100) NOT NULL,
  changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_password_history_user ON password_history (user_id, changed_at DESC);

-- ---------------------------------------------------------------- platform
CREATE TABLE system_settings (
  key VARCHAR(100) PRIMARY KEY,
  value VARCHAR(500) NOT NULL,
  value_type VARCHAR(15) NOT NULL CHECK (value_type IN ('BOOLEAN','INTEGER','DECIMAL','STRING')),
  description VARCHAR(300),
  updated_at TIMESTAMPTZ,
  updated_by BIGINT
);

CREATE TABLE number_sequences (
  prefix VARCHAR(20) NOT NULL,
  seq_year INT NOT NULL,
  last_value BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (prefix, seq_year)
);

CREATE TABLE idempotency_keys (
  key VARCHAR(80) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  request_hash CHAR(64) NOT NULL,
  response_status INT,
  response_body JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_idempotency_created ON idempotency_keys (created_at);

-- ---------------------------------------------------------------- audit (append-only, monthly partitions)
CREATE TABLE audit_logs (
  id BIGINT GENERATED ALWAYS AS IDENTITY,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  user_id BIGINT,
  username VARCHAR(50),
  roles VARCHAR(200),
  action VARCHAR(60) NOT NULL,
  entity VARCHAR(60) NOT NULL,
  entity_id VARCHAR(60),
  old_value JSONB,
  new_value JSONB,
  reason VARCHAR(500),
  ip_address VARCHAR(45),
  correlation_id VARCHAR(40),
  PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

CREATE INDEX ix_audit_entity ON audit_logs (entity, entity_id);
CREATE INDEX ix_audit_user_time ON audit_logs (user_id, occurred_at DESC);
CREATE INDEX ix_audit_action_time ON audit_logs (action, occurred_at DESC);

CREATE TABLE audit_logs_default PARTITION OF audit_logs DEFAULT;

-- Creates the monthly partition containing the given date; idempotent. Also used by the maintenance job (task 1.6).
CREATE FUNCTION ensure_audit_partition(p_month DATE) RETURNS TEXT AS $$
DECLARE
  v_start DATE := date_trunc('month', p_month)::date;
  v_end   DATE := (date_trunc('month', p_month) + INTERVAL '1 month')::date;
  v_name  TEXT := 'audit_logs_' || to_char(v_start, 'YYYY_MM');
BEGIN
  IF to_regclass(format('%I.%I', current_schema(), v_name)) IS NULL THEN
    EXECUTE format('CREATE TABLE %I PARTITION OF audit_logs FOR VALUES FROM (%L) TO (%L)',
                   v_name, v_start::timestamptz, v_end::timestamptz);
  END IF;
  RETURN v_name;
END;
$$ LANGUAGE plpgsql;

-- current month plus the next 12
DO $$
BEGIN
  FOR i IN 0..12 LOOP
    PERFORM ensure_audit_partition((current_date + (i || ' month')::interval)::date);
  END LOOP;
END $$;

-- History is immutable (ARCHITECTURE P6): no UPDATE or DELETE, even by a rogue script.
CREATE FUNCTION forbid_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION '% on % is not permitted: this table is append-only', TG_OP, TG_TABLE_NAME
    USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_immutable
  BEFORE UPDATE OR DELETE ON audit_logs
  FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
