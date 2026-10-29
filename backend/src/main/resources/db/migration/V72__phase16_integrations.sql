-- Phase 16 — Integrations: secrets, call log, Tally ledger mappings, voucher queue

SET search_path TO ims;

-- Connector credentials (secrets stored encrypted by application layer)
CREATE TABLE integration_secrets (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    connector       VARCHAR(20) NOT NULL,
    name            VARCHAR(80) NOT NULL,
    secret_value    TEXT NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (company_id, connector, name)
);

-- Integration call log (one row per outbound/inbound call)
CREATE TABLE integration_calls (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    connector       VARCHAR(20) NOT NULL,
    direction       VARCHAR(3)  NOT NULL CHECK (direction IN ('OUT','IN')),
    request_summary VARCHAR(500),
    status_code     INT,
    ok              BOOLEAN,
    duration_ms     INT,
    correlation_id  VARCHAR(40),
    error_message   VARCHAR(1000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_integration_calls_connector ON integration_calls(company_id, connector, created_at DESC);

-- Tally ledger mappings: GL account code → Tally ledger name
CREATE TABLE tally_ledger_mappings (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    gl_account_code VARCHAR(20) NOT NULL,
    tally_ledger    VARCHAR(120) NOT NULL,
    notes           VARCHAR(200),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (company_id, gl_account_code)
);

-- Tally voucher queue: generated vouchers awaiting approval and dispatch
CREATE TABLE tally_voucher_queue (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    source_type     VARCHAR(40),
    source_id       BIGINT,
    voucher_type    VARCHAR(30) NOT NULL,
    payload_xml     TEXT NOT NULL,
    status          VARCHAR(18) NOT NULL DEFAULT 'PENDING_APPROVAL'
                    CHECK (status IN ('PENDING_APPROVAL','APPROVED','SENT','POSTED','EXCEPTION','REJECTED')),
    exception_code  VARCHAR(30),
    exception_detail VARCHAR(400),
    approved_by     BIGINT,
    approved_at     TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_tally_voucher_queue_status ON tally_voucher_queue(company_id, status);

-- Permissions
INSERT INTO permissions (code, module, description) VALUES
    ('INTEGRATION_MANAGE',           'INTEGRATION', 'Manage integration connectors, secrets and call log'),
    ('ACCOUNTING_VOUCHER_APPROVE',   'INTEGRATION', 'Approve and send Tally vouchers'),
    ('ACCOUNTING_VOUCHER_VIEW',      'INTEGRATION', 'View Tally voucher queue')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('INTEGRATION_MANAGE','ACCOUNTING_VOUCHER_APPROVE','ACCOUNTING_VOUCHER_VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('FINANCE_MANAGER','CONTROLLER')
  AND p.code IN ('ACCOUNTING_VOUCHER_APPROVE','ACCOUNTING_VOUCHER_VIEW')
ON CONFLICT DO NOTHING;
