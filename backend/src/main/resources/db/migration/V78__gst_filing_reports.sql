-- V78: GST filing run tracking table + GST_REPORT_VIEW / GST_REPORT_FILE / EWAY_BILL_CREATE permissions

SET search_path TO ims;

CREATE TABLE gst_filing_runs (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id  BIGINT NOT NULL DEFAULT 1,
    return_type VARCHAR(10) NOT NULL CHECK (return_type IN ('GSTR1','GSTR3B','EWAY')),
    period_from DATE NOT NULL,
    period_to   DATE NOT NULL,
    status      VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','FILED')),
    filed_at    TIMESTAMPTZ,
    filed_by    BIGINT,
    row_count   INT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  BIGINT NOT NULL DEFAULT 1
);

INSERT INTO permissions (code, module, description) VALUES
    ('GST_REPORT_VIEW',  'FINANCE', 'View GST filing reports (GSTR-1, GSTR-3B)'),
    ('GST_REPORT_FILE',  'FINANCE', 'Mark GST returns as filed'),
    ('EWAY_BILL_CREATE', 'FINANCE', 'Generate e-way bill JSON')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('GST_REPORT_VIEW','GST_REPORT_FILE','EWAY_BILL_CREATE')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'FINANCE_MANAGER'
  AND p.code IN ('GST_REPORT_VIEW','GST_REPORT_FILE','EWAY_BILL_CREATE')
ON CONFLICT DO NOTHING;
