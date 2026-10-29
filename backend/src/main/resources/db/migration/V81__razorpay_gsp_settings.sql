-- V81: Razorpay payment gateway + GSP e-invoice settings
SET search_path TO ims;

-- Razorpay integration settings (values filled by operator in admin settings)
INSERT INTO system_settings (key, value, value_type, description)
VALUES
    ('payment.razorpay.key_id',      '',       'STRING',  'Razorpay Key ID (from Razorpay dashboard)'),
    ('payment.razorpay.key_secret',  '',       'STRING',  'Razorpay Key Secret — stored encrypted, never logged'),
    ('payment.razorpay.enabled',     'false',  'BOOLEAN', 'Enable Razorpay payment links'),
    ('gst.gsp.api_url',              '',       'STRING',  'GSP API base URL for e-invoice submission'),
    ('gst.gsp.auth_token',           '',       'STRING',  'GSP auth token / client credentials'),
    ('gst.gsp.enabled',              'false',  'BOOLEAN', 'Auto-submit e-invoices to GSP after generation')
ON CONFLICT (key) DO NOTHING;

-- razorpay_orders table: tracks payment order creation / verification
CREATE TABLE IF NOT EXISTS razorpay_orders (
    id                  BIGSERIAL PRIMARY KEY,
    ar_invoice_id       BIGINT REFERENCES ar_invoices(id),
    razorpay_order_id   VARCHAR(100) NOT NULL,
    amount_paise        BIGINT NOT NULL,
    currency            VARCHAR(10)  NOT NULL DEFAULT 'INR',
    status              VARCHAR(30)  NOT NULL DEFAULT 'CREATED',
    razorpay_payment_id VARCHAR(100),
    razorpay_signature  VARCHAR(255),
    created_by          BIGINT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    verified_at         TIMESTAMPTZ
);

INSERT INTO permissions (code, module, description) VALUES
    ('PAYMENT_GATEWAY_USE', 'FINANCE', 'Initiate and verify Razorpay payment orders')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('ADMIN','FINANCE_MANAGER','ACCOUNTANT')
  AND p.code = 'PAYMENT_GATEWAY_USE'
ON CONFLICT DO NOTHING;
