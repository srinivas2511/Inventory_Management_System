-- Phase 1 — IAM + platform schema + reference seed (DESIGN.md §2.1–2.2, §7.1, §11.1)
SET search_path TO ims;

-- ── UOM ─────────────────────────────────────────────────────────────────────
CREATE TABLE uoms (
  code VARCHAR(10) PRIMARY KEY,
  name VARCHAR(40)  NOT NULL,
  kind VARCHAR(10)  NOT NULL
);

-- ── Users / IAM ──────────────────────────────────────────────────────────────
CREATE TABLE users (
  id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username             VARCHAR(50)  NOT NULL,
  employee_code        VARCHAR(20),
  full_name            VARCHAR(120) NOT NULL,
  email                VARCHAR(160) NOT NULL,
  phone                VARCHAR(20),
  password_hash        VARCHAR(100) NOT NULL,
  password_changed_at  TIMESTAMPTZ,
  must_change_password BOOLEAN      NOT NULL DEFAULT TRUE,
  failed_attempts      INT          NOT NULL DEFAULT 0,
  locked_until         TIMESTAMPTZ,
  permission_version   INT          NOT NULL DEFAULT 1,
  active               BOOLEAN      NOT NULL DEFAULT TRUE,
  last_login_at        TIMESTAMPTZ,
  created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by           BIGINT,
  updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by           BIGINT,
  version              BIGINT       NOT NULL DEFAULT 0,
  company_id           BIGINT       NOT NULL DEFAULT 1,
  CONSTRAINT uq_users_username      UNIQUE (username),
  CONSTRAINT uq_users_email         UNIQUE (email),
  CONSTRAINT uq_users_employee_code UNIQUE (employee_code)
);

CREATE TABLE roles (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code        VARCHAR(40)  NOT NULL,
  name        VARCHAR(80)  NOT NULL,
  description VARCHAR(300),
  system_role BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by  BIGINT,
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by  BIGINT,
  version     BIGINT       NOT NULL DEFAULT 0,
  company_id  BIGINT       NOT NULL DEFAULT 1,
  CONSTRAINT uq_roles_code UNIQUE (code)
);

CREATE TABLE permissions (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code        VARCHAR(60) NOT NULL,
  module      VARCHAR(30) NOT NULL,
  description VARCHAR(200),
  CONSTRAINT uq_permissions_code UNIQUE (code)
);

CREATE TABLE user_roles (
  user_id BIGINT NOT NULL REFERENCES users(id),
  role_id BIGINT NOT NULL REFERENCES roles(id),
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permissions (
  role_id       BIGINT NOT NULL REFERENCES roles(id),
  permission_id BIGINT NOT NULL REFERENCES permissions(id),
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_warehouse_access (
  user_id      BIGINT NOT NULL REFERENCES users(id),
  warehouse_id BIGINT NOT NULL,
  PRIMARY KEY  (user_id, warehouse_id)
);

CREATE TABLE refresh_tokens (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id     BIGINT       NOT NULL REFERENCES users(id),
  token_hash  CHAR(64)     NOT NULL,
  family_id   UUID         NOT NULL,
  expires_at  TIMESTAMPTZ  NOT NULL,
  revoked_at  TIMESTAMPTZ,
  replaced_by BIGINT,
  user_agent  VARCHAR(200),
  ip          VARCHAR(45),
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE TABLE password_reset_tokens (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id    BIGINT      NOT NULL REFERENCES users(id),
  token_hash CHAR(64)    NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at    TIMESTAMPTZ,
  CONSTRAINT uq_pwd_reset_token_hash UNIQUE (token_hash)
);

CREATE TABLE password_history (
  user_id       BIGINT      NOT NULL REFERENCES users(id),
  password_hash VARCHAR(100) NOT NULL,
  changed_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ── Platform ──────────────────────────────────────────────────────────────────
CREATE TABLE system_settings (
  key        VARCHAR(100) PRIMARY KEY,
  value      VARCHAR(500) NOT NULL,
  value_type VARCHAR(15)  NOT NULL CHECK (value_type IN ('BOOLEAN','INTEGER','DECIMAL','STRING')),
  description VARCHAR(300),
  updated_at TIMESTAMPTZ,
  updated_by BIGINT
);

CREATE TABLE number_sequences (
  prefix     VARCHAR(20) NOT NULL,
  seq_year   INT         NOT NULL,
  last_value BIGINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (prefix, seq_year)
);

-- Partitioned audit log (append-only)
CREATE TABLE audit_logs (
  id             BIGINT      GENERATED ALWAYS AS IDENTITY NOT NULL,
  occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  user_id        BIGINT,
  username       VARCHAR(50),
  roles          VARCHAR(200),
  action         VARCHAR(60) NOT NULL,
  entity         VARCHAR(60) NOT NULL,
  entity_id      VARCHAR(60),
  old_value      JSONB,
  new_value      JSONB,
  reason         VARCHAR(500),
  ip_address     VARCHAR(45),
  correlation_id VARCHAR(40),
  PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

CREATE TABLE audit_logs_default PARTITION OF audit_logs DEFAULT;

-- Monthly partitions 2026-10 → 2027-12
CREATE TABLE audit_logs_2026_10 PARTITION OF audit_logs FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE audit_logs_2026_11 PARTITION OF audit_logs FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE audit_logs_2026_12 PARTITION OF audit_logs FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE audit_logs_2027_01 PARTITION OF audit_logs FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');
CREATE TABLE audit_logs_2027_02 PARTITION OF audit_logs FOR VALUES FROM ('2027-02-01') TO ('2027-03-01');
CREATE TABLE audit_logs_2027_03 PARTITION OF audit_logs FOR VALUES FROM ('2027-03-01') TO ('2027-04-01');
CREATE TABLE audit_logs_2027_04 PARTITION OF audit_logs FOR VALUES FROM ('2027-04-01') TO ('2027-05-01');
CREATE TABLE audit_logs_2027_05 PARTITION OF audit_logs FOR VALUES FROM ('2027-05-01') TO ('2027-06-01');
CREATE TABLE audit_logs_2027_06 PARTITION OF audit_logs FOR VALUES FROM ('2027-06-01') TO ('2027-07-01');
CREATE TABLE audit_logs_2027_07 PARTITION OF audit_logs FOR VALUES FROM ('2027-07-01') TO ('2027-08-01');
CREATE TABLE audit_logs_2027_08 PARTITION OF audit_logs FOR VALUES FROM ('2027-08-01') TO ('2027-09-01');
CREATE TABLE audit_logs_2027_09 PARTITION OF audit_logs FOR VALUES FROM ('2027-09-01') TO ('2027-10-01');
CREATE TABLE audit_logs_2027_10 PARTITION OF audit_logs FOR VALUES FROM ('2027-10-01') TO ('2027-11-01');
CREATE TABLE audit_logs_2027_11 PARTITION OF audit_logs FOR VALUES FROM ('2027-11-01') TO ('2027-12-01');
CREATE TABLE audit_logs_2027_12 PARTITION OF audit_logs FOR VALUES FROM ('2027-12-01') TO ('2028-01-01');

CREATE FUNCTION audit_log_immutable() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'audit_logs is append-only: % is not allowed', TG_OP;
END;
$$;
CREATE TRIGGER trg_audit_logs_immutable
  BEFORE UPDATE OR DELETE ON audit_logs
  FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();

CREATE TABLE approval_requests (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  document_type       VARCHAR(30) NOT NULL,
  document_id         BIGINT      NOT NULL,
  step_no             INT         NOT NULL,
  required_permission VARCHAR(60) NOT NULL,
  status              VARCHAR(15) NOT NULL,
  requested_by        BIGINT      NOT NULL,
  decided_by          BIGINT,
  decided_at          TIMESTAMPTZ,
  comment             VARCHAR(500),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_approval_doc ON approval_requests(document_type, document_id);

CREATE TABLE idempotency_keys (
  key             VARCHAR(80) PRIMARY KEY,
  user_id         BIGINT      NOT NULL,
  request_hash    CHAR(64)    NOT NULL,
  response_status INT,
  response_body   JSONB,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE alerts (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  alert_type       VARCHAR(30)  NOT NULL,
  severity         VARCHAR(10)  NOT NULL,
  entity_type      VARCHAR(40),
  entity_id        BIGINT,
  open_key         VARCHAR(120) NOT NULL,
  message          VARCHAR(300) NOT NULL,
  first_seen_at    TIMESTAMPTZ  NOT NULL,
  last_seen_at     TIMESTAMPTZ  NOT NULL,
  acknowledged_by  BIGINT,
  acknowledged_at  TIMESTAMPTZ,
  resolved_at      TIMESTAMPTZ,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_alert_open ON alerts(open_key) WHERE resolved_at IS NULL;

-- ShedLock (distributed scheduler lock)
CREATE TABLE shedlock (
  name      VARCHAR(64)   NOT NULL,
  lock_until TIMESTAMP(3) NOT NULL,
  locked_at  TIMESTAMP(3) NOT NULL,
  locked_by  VARCHAR(255)  NOT NULL,
  PRIMARY KEY (name)
);

-- ── UOM seed ──────────────────────────────────────────────────────────────────
INSERT INTO uoms (code, name, kind) VALUES
  ('KG',   'Kilogram',      'WEIGHT'),
  ('MT',   'Metric Tonne',  'WEIGHT'),
  ('G',    'Gram',          'WEIGHT'),
  ('PCS',  'Pieces',        'COUNT'),
  ('MTR',  'Metre',         'LENGTH'),
  ('MM',   'Millimetre',    'LENGTH'),
  ('ROLL', 'Roll',          'PACKAGING'),
  ('LT',   'Litre',         'VOLUME'),
  ('ML',   'Millilitre',    'VOLUME'),
  ('SET',  'Set',           'COUNT');

-- ── Roles seed (13 roles from ARCHITECTURE §10.3) ────────────────────────────
INSERT INTO roles (code, name, description, system_role) VALUES
  ('ADMIN',              'Administrator',       'Full system access; manage users, roles, settings, master data', true),
  ('ENGINEER',           'Engineer',            'Product specs, BOMs, routing, drawing management',             true),
  ('PRODUCTION_MANAGER', 'Production Manager',  'Production order management, scheduling, WIP oversight',       true),
  ('SUPERVISOR',         'Supervisor',          'Supervise assigned production operations',                     true),
  ('OPERATOR',           'Operator',            'Execute assigned work; enter quantities and problems',         true),
  ('QUALITY_MANAGER',    'Quality Manager',     'Incoming and in-process inspection, rejection management',     true),
  ('STORE_MANAGER',      'Store Manager',       'Inventory management, receipts, transfers, adjustments',       true),
  ('PURCHASE_MANAGER',   'Purchase Manager',    'Purchase orders and requisitions',                             true),
  ('SALES',              'Sales',               'Sales orders, customer management, FG reservation',           true),
  ('DISPATCH',           'Dispatch',            'Dispatch finished goods to customers',                        true),
  ('MAINTENANCE',        'Maintenance',         'Machine maintenance and downtime recording',                   true),
  ('STORE_OPERATOR',     'Store Operator',      'Warehouse-scoped stock operations',                           true),
  ('MANAGEMENT',         'Management',          'Read-only access to all modules, reports and dashboards',      true);

-- ── Permissions seed (full catalogue from DESIGN §7.1) ────────────────────────
INSERT INTO permissions (code, module, description) VALUES
  -- IAM
  ('USER_VIEW',          'IAM', 'View users'),
  ('USER_CREATE',        'IAM', 'Create users'),
  ('USER_UPDATE',        'IAM', 'Update users'),
  ('USER_DELETE',        'IAM', 'Deactivate users'),
  ('ROLE_MANAGE',        'IAM', 'Manage roles and their permission sets'),
  ('PERMISSION_MANAGE',  'IAM', 'Manage permission catalogue'),
  ('SETTINGS_MANAGE',    'IAM', 'Manage system settings'),
  ('AUDIT_VIEW',         'IAM', 'View audit log'),
  -- Master data
  ('MASTERDATA_MANAGE',         'MASTER', 'Manage all master data'),
  ('MATERIAL_VIEW',             'MASTER', 'View materials'),
  ('SUPPLIER_MANAGE',           'MASTER', 'Create / update suppliers'),
  ('CUSTOMER_MANAGE',           'MASTER', 'Create / update customers'),
  ('WAREHOUSE_MANAGE',          'MASTER', 'Create / update warehouses'),
  ('LOCATION_MANAGE',           'MASTER', 'Create / update warehouse locations'),
  ('MACHINE_MANAGE',            'MASTER', 'Create / update machines'),
  ('OPERATION_MANAGE',          'MASTER', 'Create / update operations'),
  ('REJECTION_REASON_MANAGE',   'MASTER', 'Manage rejection reasons'),
  -- Engineering
  ('PRODUCT_VIEW',    'ENGINEERING', 'View products'),
  ('PRODUCT_CREATE',  'ENGINEERING', 'Create products'),
  ('PRODUCT_UPDATE',  'ENGINEERING', 'Update products'),
  ('DRAWING_MANAGE',  'ENGINEERING', 'Manage drawing numbers and revisions'),
  ('BOM_VIEW',        'ENGINEERING', 'View BOMs'),
  ('BOM_CREATE',      'ENGINEERING', 'Create BOMs'),
  ('BOM_UPDATE',      'ENGINEERING', 'Update BOMs'),
  ('BOM_REVIEW',      'ENGINEERING', 'Review BOMs'),
  ('BOM_APPROVE',     'ENGINEERING', 'Approve BOMs'),
  ('ROUTING_MANAGE',  'ENGINEERING', 'Manage product routings'),
  -- Inventory
  ('INVENTORY_VIEW',          'INVENTORY', 'View stock balances and ledger'),
  ('INVENTORY_VIEW_ALL',      'INVENTORY', 'View all warehouses regardless of access scope'),
  ('INVENTORY_RECEIVE',       'INVENTORY', 'Post goods receipt'),
  ('INVENTORY_ISSUE',         'INVENTORY', 'Post material issue'),
  ('INVENTORY_TRANSFER',      'INVENTORY', 'Post stock transfer'),
  ('INVENTORY_RETURN',        'INVENTORY', 'Post material return'),
  ('INVENTORY_ADJUST',        'INVENTORY', 'Request stock adjustment'),
  ('ADJUST_REVIEW',           'INVENTORY', 'Review adjustment requests'),
  ('ADJUST_APPROVE',          'INVENTORY', 'Approve adjustments (below threshold)'),
  ('ADJUST_APPROVE_ADMIN',    'INVENTORY', 'Approve adjustments above threshold'),
  ('STOCK_COUNT',             'INVENTORY', 'Perform stock count'),
  ('VALUATION_VIEW',          'INVENTORY', 'View stock valuation'),
  -- Purchase
  ('PURCHASE_VIEW',       'PURCHASE', 'View purchase orders'),
  ('PURCHASE_CREATE',     'PURCHASE', 'Create purchase orders'),
  ('PURCHASE_APPROVE',    'PURCHASE', 'Approve purchase orders'),
  ('REQUISITION_CREATE',  'PURCHASE', 'Create purchase requisitions'),
  -- Production
  ('PRODUCTION_VIEW',     'PRODUCTION', 'View production orders'),
  ('PRODUCTION_CREATE',   'PRODUCTION', 'Create production orders'),
  ('PRODUCTION_UPDATE',   'PRODUCTION', 'Update production orders'),
  ('PRODUCTION_APPROVE',  'PRODUCTION', 'Approve production orders'),
  ('PRODUCTION_RELEASE',  'PRODUCTION', 'Release production orders'),
  ('PRODUCTION_SCHEDULE', 'PRODUCTION', 'Schedule and assign production orders'),
  ('PRODUCTION_ASSIGN',   'PRODUCTION', 'Assign operators and machines'),
  ('PRODUCTION_EXECUTE',  'PRODUCTION', 'Execute production operations (operators/supervisors)'),
  ('PRODUCTION_CLOSE',    'PRODUCTION', 'Close production orders'),
  ('PRODUCTION_REOPEN',   'PRODUCTION', 'Reopen completed orders'),
  ('DOWNTIME_RECORD',     'PRODUCTION', 'Record machine downtime'),
  ('MATERIAL_REQUEST',    'PRODUCTION', 'Request additional material from floor'),
  ('PROBLEM_REPORT',      'PRODUCTION', 'Report machine / material problems'),
  -- Quality
  ('QUALITY_VIEW',    'QUALITY', 'View inspection records'),
  ('QUALITY_INSPECT', 'QUALITY', 'Perform inspections'),
  ('QUALITY_APPROVE', 'QUALITY', 'Approve inspection results'),
  ('QUALITY_REJECT',  'QUALITY', 'Reject inspected items'),
  ('QUALITY_HOLD',    'QUALITY', 'Place items on hold'),
  -- Sales
  ('SALES_VIEW',       'SALES', 'View sales orders'),
  ('SALES_CREATE',     'SALES', 'Create sales orders'),
  ('SALES_UPDATE',     'SALES', 'Update sales orders'),
  ('RESERVE_FG',       'SALES', 'Reserve finished goods for an order'),
  ('DISPATCH_REQUEST', 'SALES', 'Request dispatch'),
  ('DISPATCH_VIEW',    'SALES', 'View dispatch records'),
  ('DISPATCH_CREATE',  'SALES', 'Create dispatch notes'),
  ('DISPATCH_APPROVE', 'SALES', 'Approve dispatch'),
  -- Maintenance
  ('MAINTENANCE_VIEW',   'MAINTENANCE', 'View maintenance records'),
  ('MAINTENANCE_MANAGE', 'MAINTENANCE', 'Manage maintenance activities'),
  -- Insight
  ('REPORT_VIEW',       'INSIGHT', 'View and export reports'),
  ('DASHBOARD_VIEW',    'INSIGHT', 'View dashboards'),
  ('TRACEABILITY_VIEW', 'INSIGHT', 'View traceability'),
  ('EXPORT_DATA',       'INSIGHT', 'Export data to Excel/PDF/CSV');

-- ── Role → permission mapping (ARCHITECTURE §10.3) ────────────────────────────

-- ADMIN: essentially everything except scoped-execution permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN (
    'USER_VIEW','USER_CREATE','USER_UPDATE','USER_DELETE',
    'ROLE_MANAGE','PERMISSION_MANAGE','SETTINGS_MANAGE','AUDIT_VIEW',
    'MASTERDATA_MANAGE','MATERIAL_VIEW','SUPPLIER_MANAGE','CUSTOMER_MANAGE',
    'WAREHOUSE_MANAGE','LOCATION_MANAGE','MACHINE_MANAGE','OPERATION_MANAGE','REJECTION_REASON_MANAGE',
    'PRODUCT_VIEW','PRODUCT_CREATE','PRODUCT_UPDATE','DRAWING_MANAGE',
    'BOM_VIEW','BOM_CREATE','BOM_UPDATE','BOM_REVIEW','BOM_APPROVE','ROUTING_MANAGE',
    'INVENTORY_VIEW','INVENTORY_VIEW_ALL','INVENTORY_RECEIVE','INVENTORY_ISSUE',
    'INVENTORY_TRANSFER','INVENTORY_RETURN','INVENTORY_ADJUST',
    'ADJUST_REVIEW','ADJUST_APPROVE','ADJUST_APPROVE_ADMIN','STOCK_COUNT','VALUATION_VIEW',
    'PURCHASE_VIEW','PURCHASE_CREATE','PURCHASE_APPROVE','REQUISITION_CREATE',
    'PRODUCTION_VIEW','PRODUCTION_CREATE','PRODUCTION_UPDATE','PRODUCTION_APPROVE',
    'PRODUCTION_RELEASE','PRODUCTION_SCHEDULE','PRODUCTION_CLOSE','PRODUCTION_REOPEN',
    'QUALITY_VIEW','QUALITY_INSPECT','QUALITY_APPROVE','QUALITY_REJECT','QUALITY_HOLD',
    'SALES_VIEW','SALES_CREATE','SALES_UPDATE','RESERVE_FG',
    'DISPATCH_VIEW','DISPATCH_CREATE','DISPATCH_APPROVE','DISPATCH_REQUEST',
    'MAINTENANCE_VIEW','MAINTENANCE_MANAGE',
    'REPORT_VIEW','DASHBOARD_VIEW','TRACEABILITY_VIEW','EXPORT_DATA'
  );

-- ENGINEER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ENGINEER'
  AND p.code IN (
    'PRODUCT_VIEW','PRODUCT_CREATE','PRODUCT_UPDATE','DRAWING_MANAGE',
    'BOM_VIEW','BOM_CREATE','BOM_UPDATE','BOM_REVIEW','BOM_APPROVE','ROUTING_MANAGE',
    'MATERIAL_VIEW','INVENTORY_VIEW'
  );

-- PRODUCTION_MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'PRODUCTION_MANAGER'
  AND p.code IN (
    'PRODUCTION_VIEW','PRODUCTION_CREATE','PRODUCTION_UPDATE','PRODUCTION_APPROVE',
    'PRODUCTION_RELEASE','PRODUCTION_SCHEDULE','PRODUCTION_ASSIGN','PRODUCTION_CLOSE',
    'INVENTORY_VIEW','MATERIAL_VIEW','PRODUCT_VIEW','BOM_VIEW',
    'REPORT_VIEW','DASHBOARD_VIEW'
  );

-- SUPERVISOR
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'SUPERVISOR'
  AND p.code IN (
    'PRODUCTION_VIEW','PRODUCTION_EXECUTE','PRODUCTION_ASSIGN',
    'DOWNTIME_RECORD','MATERIAL_REQUEST',
    'PRODUCT_VIEW','INVENTORY_VIEW',
    'ADJUST_REVIEW'
  );

-- OPERATOR
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'OPERATOR'
  AND p.code IN (
    'PRODUCTION_EXECUTE','PROBLEM_REPORT'
  );

-- QUALITY_MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'QUALITY_MANAGER'
  AND p.code IN (
    'QUALITY_VIEW','QUALITY_INSPECT','QUALITY_APPROVE','QUALITY_REJECT','QUALITY_HOLD',
    'REJECTION_REASON_MANAGE',
    'INVENTORY_VIEW','PRODUCT_VIEW',
    'REPORT_VIEW','DASHBOARD_VIEW'
  );

-- STORE_MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'STORE_MANAGER'
  AND p.code IN (
    'INVENTORY_VIEW','INVENTORY_VIEW_ALL','INVENTORY_RECEIVE','INVENTORY_ISSUE',
    'INVENTORY_TRANSFER','INVENTORY_RETURN','INVENTORY_ADJUST',
    'ADJUST_APPROVE','STOCK_COUNT','VALUATION_VIEW',
    'LOCATION_MANAGE','MATERIAL_VIEW',
    'REPORT_VIEW','DASHBOARD_VIEW'
  );

-- PURCHASE_MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'PURCHASE_MANAGER'
  AND p.code IN (
    'PURCHASE_VIEW','PURCHASE_CREATE','REQUISITION_CREATE',
    'SUPPLIER_MANAGE','MATERIAL_VIEW',
    'INVENTORY_VIEW','REPORT_VIEW'
  );

-- SALES
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'SALES'
  AND p.code IN (
    'SALES_VIEW','SALES_CREATE','SALES_UPDATE',
    'CUSTOMER_MANAGE','RESERVE_FG','DISPATCH_REQUEST',
    'INVENTORY_VIEW','PRODUCT_VIEW',
    'REPORT_VIEW','DASHBOARD_VIEW'
  );

-- DISPATCH
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'DISPATCH'
  AND p.code IN (
    'DISPATCH_VIEW','DISPATCH_CREATE',
    'SALES_VIEW','INVENTORY_VIEW'
  );

-- MAINTENANCE
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'MAINTENANCE'
  AND p.code IN (
    'MACHINE_MANAGE','MAINTENANCE_VIEW','MAINTENANCE_MANAGE','DOWNTIME_RECORD',
    'INVENTORY_VIEW'
  );

-- STORE_OPERATOR (warehouse-scoped; AccessScopeService enforces the scope)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'STORE_OPERATOR'
  AND p.code IN (
    'INVENTORY_VIEW','INVENTORY_RECEIVE','INVENTORY_ISSUE',
    'INVENTORY_TRANSFER','STOCK_COUNT'
  );

-- MANAGEMENT (read-only across all modules)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'MANAGEMENT'
  AND p.code IN (
    'USER_VIEW',
    'MATERIAL_VIEW','PRODUCT_VIEW','INVENTORY_VIEW','INVENTORY_VIEW_ALL','VALUATION_VIEW',
    'PURCHASE_VIEW','PRODUCTION_VIEW','QUALITY_VIEW',
    'SALES_VIEW','DISPATCH_VIEW',
    'MAINTENANCE_VIEW',
    'AUDIT_VIEW',
    'REPORT_VIEW','DASHBOARD_VIEW','TRACEABILITY_VIEW','EXPORT_DATA'
  );

-- ── Default system settings ───────────────────────────────────────────────────
INSERT INTO system_settings (key, value, value_type, description) VALUES
  ('inventory.allow_negative_stock',        'false', 'BOOLEAN', 'Allow stock to go negative'),
  ('inventory.adjustment.admin_threshold',  '10000', 'DECIMAL', 'Adjustment value (INR) requiring Admin approval'),
  ('auth.lockout.max_attempts',             '5',     'INTEGER', 'Failed login attempts before lockout'),
  ('auth.lockout.duration_minutes',         '15',    'INTEGER', 'Lockout duration in minutes'),
  ('auth.password.min_length',              '10',    'INTEGER', 'Minimum password length'),
  ('auth.password.history_count',           '5',     'INTEGER', 'Number of previous passwords that cannot be reused'),
  ('auth.password.expiry_days',             '0',     'INTEGER', 'Password expiry in days (0 = never)'),
  ('auth.token.access_minutes',             '15',    'INTEGER', 'JWT access token lifetime in minutes'),
  ('auth.token.refresh_days',               '7',     'INTEGER', 'Refresh token lifetime in days'),
  ('sales.reservation.max_days',            '30',    'INTEGER', 'FG reservation expires after N days'),
  ('po.over_receipt_tolerance_pct',         '5',     'DECIMAL', 'Allowed over-receipt percentage on a PO line');
