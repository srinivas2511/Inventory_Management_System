-- V84: Aftership shipment tracking + Busy accounting integration stubs
SET search_path TO ims;

-- ── Aftership: shipment tracking ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS shipment_trackings (
    id              BIGSERIAL PRIMARY KEY,
    dispatch_id     BIGINT REFERENCES dispatches(id),
    courier_code    VARCHAR(50)   NOT NULL,
    tracking_number VARCHAR(200)  NOT NULL,
    tracking_url    TEXT,
    status          VARCHAR(50)   NOT NULL DEFAULT 'PENDING',
    last_checkpoint TEXT,
    last_updated    TIMESTAMPTZ,
    raw_payload     JSONB,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_shipment_tracking_dispatch
    ON shipment_trackings (dispatch_id, courier_code, tracking_number);

INSERT INTO system_settings (key, value, value_type, description) VALUES
    ('aftership.api_key',    '', 'STRING',  'AfterShip API key for shipment tracking'),
    ('aftership.enabled',    'false', 'BOOLEAN', 'Enable AfterShip tracking webhooks'),
    ('aftership.webhook_secret', '', 'STRING', 'AfterShip webhook HMAC secret')
ON CONFLICT (key) DO NOTHING;

INSERT INTO permissions (code, module, description) VALUES
    ('SHIPMENT_TRACK_VIEW',   'SALES', 'View shipment tracking'),
    ('SHIPMENT_TRACK_MANAGE', 'SALES', 'Register and update shipment tracking')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('ADMIN','SALES_MANAGER','STORE_MANAGER')
  AND p.code IN ('SHIPMENT_TRACK_VIEW','SHIPMENT_TRACK_MANAGE')
ON CONFLICT DO NOTHING;

-- ── Busy accounting integration ──────────────────────────────────────────────
-- busy_sync_queue: GL journal lines staged for export to Busy accounting software
CREATE TABLE IF NOT EXISTS busy_sync_queue (
    id              BIGSERIAL PRIMARY KEY,
    journal_id      BIGINT        NOT NULL,
    journal_number  VARCHAR(50),
    export_payload  JSONB,
    status          VARCHAR(30)   NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','EXPORTED','FAILED','SKIPPED')),
    export_attempt  INT           NOT NULL DEFAULT 0,
    last_error      TEXT,
    exported_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

INSERT INTO system_settings (key, value, value_type, description) VALUES
    ('busy.enabled',         'false', 'BOOLEAN', 'Enable Busy accounting export'),
    ('busy.export_path',     '',      'STRING',  'File-system path for Busy CSV export files'),
    ('busy.voucher_type',    'JV',    'STRING',  'Default voucher type for journal export')
ON CONFLICT (key) DO NOTHING;

INSERT INTO permissions (code, module, description) VALUES
    ('BUSY_EXPORT_VIEW',   'INTEGRATION', 'View Busy accounting export queue'),
    ('BUSY_EXPORT_RUN',    'INTEGRATION', 'Trigger Busy accounting export')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('ADMIN','FINANCE_MANAGER')
  AND p.code IN ('BUSY_EXPORT_VIEW','BUSY_EXPORT_RUN')
ON CONFLICT DO NOTHING;
