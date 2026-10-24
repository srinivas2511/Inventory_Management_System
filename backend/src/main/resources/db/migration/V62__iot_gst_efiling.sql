-- V62: R19-6.3 IoT ingestion permissions + R19-6.5 GST e-invoice adapter

SET search_path = ims;

-- ── R19-6.3: IoT event ingestion ─────────────────────────────────────────────
-- The sensor_readings table already exists from V61.
-- Add an IoT API key table for edge-device authentication (read-only token, no user session).
CREATE TABLE iot_api_keys (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  key_hash    VARCHAR(64)  NOT NULL UNIQUE,
  label       VARCHAR(100) NOT NULL,
  active      BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by  BIGINT,
  company_id  BIGINT       NOT NULL DEFAULT 1
);

INSERT INTO permissions (code, module, description)
VALUES ('IOT_INGEST', 'INTEGRATION', 'Ingest machine sensor and event data from IoT devices / edge gateways')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('ADMIN', 'MAINTENANCE_MANAGER')
  AND p.code = 'IOT_INGEST'
ON CONFLICT DO NOTHING;

-- ── R19-6.5: GST e-invoice adapter ───────────────────────────────────────────
-- Stores generated e-invoice JSON payloads and their GSTN IRN (Invoice Reference Number).
CREATE TABLE gst_einvoices (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  dispatch_id     BIGINT       NOT NULL UNIQUE,
  irn             VARCHAR(64),
  ack_no          VARCHAR(30),
  ack_date        TIMESTAMPTZ,
  status          VARCHAR(20)  NOT NULL DEFAULT 'GENERATED'
                  CHECK (status IN ('GENERATED','SUBMITTED','ACCEPTED','REJECTED','CANCELLED')),
  payload_json    TEXT         NOT NULL,
  gstn_response   TEXT,
  generated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  generated_by    BIGINT,
  company_id      BIGINT       NOT NULL DEFAULT 1
);
CREATE INDEX ix_gst_einvoices_dispatch ON gst_einvoices(dispatch_id);

INSERT INTO permissions (code, module, description)
VALUES ('GST_EINVOICE_VIEW',     'INTEGRATION', 'View GST e-invoice payloads and status'),
       ('GST_EINVOICE_GENERATE', 'INTEGRATION', 'Generate GST e-invoice JSON for dispatches')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('ADMIN', 'FINANCE_MANAGER')
  AND p.code IN ('GST_EINVOICE_VIEW', 'GST_EINVOICE_GENERATE')
ON CONFLICT DO NOTHING;
