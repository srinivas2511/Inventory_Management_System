-- 12.9: E-way bill generation
SET search_path = ims;

CREATE TABLE eway_bills (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT NOT NULL DEFAULT 1,
    invoice_id          BIGINT REFERENCES sales_invoices(id),
    dispatch_id         BIGINT REFERENCES dispatches(id),
    eway_bill_number    VARCHAR(50),
    generated_at        TIMESTAMPTZ,
    valid_until         TIMESTAMPTZ,
    transporter_id      BIGINT REFERENCES transporters(id),
    vehicle_number      VARCHAR(20),
    distance_km         INT,
    request_payload     JSONB,
    response_payload    JSONB,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          BIGINT,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by          BIGINT,
    version             BIGINT NOT NULL DEFAULT 0
);

INSERT INTO permissions (code, module, description) VALUES
  ('EWAY_BILL_MANAGE', 'FINANCE', 'Generate e-way bills')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('ADMIN', 'SALES_MANAGER', 'FINANCE_MANAGER')
  AND p.code = 'EWAY_BILL_MANAGE'
ON CONFLICT DO NOTHING;
