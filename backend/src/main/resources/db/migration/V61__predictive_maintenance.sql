-- V61: Predictive Maintenance (R19-5.2)
-- machine_sensor_readings for IoT/sensor data
-- maintenance_predictions for predicted failure windows
-- PREDICTIVE_MAINT_VIEW permission

SET search_path = ims;

CREATE TABLE machine_sensor_readings (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  machine_id   BIGINT       NOT NULL,
  reading_type VARCHAR(30)  NOT NULL CHECK (reading_type IN ('TEMPERATURE','VIBRATION','PRESSURE','RPM','CURRENT')),
  reading_value NUMERIC(18,4) NOT NULL,
  unit         VARCHAR(10),
  read_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  source       VARCHAR(20)  NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL','IOT','IMPORT')),
  company_id   BIGINT       NOT NULL DEFAULT 1
);
CREATE INDEX ix_sensor_readings_machine_time ON machine_sensor_readings(machine_id, read_at);

CREATE TABLE maintenance_predictions (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  machine_id          BIGINT       NOT NULL,
  predicted_failure_at TIMESTAMPTZ NOT NULL,
  confidence_pct      NUMERIC(5,2) NOT NULL,
  mtbf_hours          NUMERIC(10,2),
  failure_count       INTEGER      NOT NULL DEFAULT 0,
  last_failure_at     TIMESTAMPTZ,
  algorithm_version   VARCHAR(10)  NOT NULL DEFAULT '1.0',
  generated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
  acknowledged        BOOLEAN      NOT NULL DEFAULT FALSE,
  acknowledged_by     BIGINT,
  acknowledged_at     TIMESTAMPTZ,
  notes               VARCHAR(500),
  company_id          BIGINT       NOT NULL DEFAULT 1
);
CREATE INDEX ix_predictions_machine ON maintenance_predictions(machine_id);
CREATE INDEX ix_predictions_predicted_at ON maintenance_predictions(predicted_failure_at);

-- Extend alert type check to include PREDICTED_FAILURE
ALTER TABLE alerts DROP CONSTRAINT IF EXISTS chk_alert_type;
ALTER TABLE alerts ADD CONSTRAINT chk_alert_type CHECK (alert_type IN (
  'LOW_STOCK','OVERSTOCK','PENDING_INSPECTION','PRODUCTION_DELAY',
  'MATERIAL_SHORTAGE','AGING_BATCH','PENDING_PO','LEDGER_MISMATCH',
  'OVERDUE_PO','OVERDUE_PM','LOW_MATERIAL_BATCH','SPC_OUT_OF_CONTROL',
  'MISSING_AR_INVOICE','MISSING_JOURNAL','MAINTENANCE_COST_UNPOSTED',
  'WEBHOOK_DELIVERY_FAILED','ANOMALY_DETECTED','PREDICTED_FAILURE'
));

INSERT INTO notification_templates (template_code, channel, description, subject_template, body_template)
VALUES ('PREDICTED_FAILURE', 'BOTH', 'Alert: machine predicted to fail within the alert window',
        '[{{severity}}] Predicted machine failure', '{{message}}')
ON CONFLICT (template_code) DO NOTHING;

-- Permissions
INSERT INTO permissions (code, module, description)
VALUES ('PREDICTIVE_MAINT_VIEW', 'MAINTENANCE', 'View predictive maintenance analysis and run predictions')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('ADMIN', 'MAINTENANCE_MANAGER', 'PRODUCTION_MANAGER')
  AND p.code = 'PREDICTIVE_MAINT_VIEW'
ON CONFLICT DO NOTHING;
