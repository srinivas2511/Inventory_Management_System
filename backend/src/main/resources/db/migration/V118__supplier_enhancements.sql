-- V118: Supplier form enhancements
-- - Structured address fields (line1, line2, city, state, pin, country)
-- - payment_terms_id FK so payment terms come from the master table
-- - Supplier code auto-generation sequence
-- - Seed payment_terms and vendor_types with sensible values

-- Structured address columns
ALTER TABLE ims.suppliers
    ADD COLUMN IF NOT EXISTS address_line1    VARCHAR(200),
    ADD COLUMN IF NOT EXISTS address_line2    VARCHAR(200),
    ADD COLUMN IF NOT EXISTS city             VARCHAR(100),
    ADD COLUMN IF NOT EXISTS state_name       VARCHAR(100),
    ADD COLUMN IF NOT EXISTS pin_code         VARCHAR(10),
    ADD COLUMN IF NOT EXISTS country          VARCHAR(100) DEFAULT 'India',
    ADD COLUMN IF NOT EXISTS payment_terms_id BIGINT REFERENCES ims.payment_terms(id);

-- Sequence for auto-generating supplier codes (SUP-1001, SUP-1002, ...)
CREATE SEQUENCE IF NOT EXISTS ims.supplier_code_seq
    START WITH 1001 INCREMENT BY 1 NO CYCLE;

-- Seed payment terms (idempotent)
INSERT INTO ims.payment_terms (term_code, name, net_days, discount_pct, discount_days, active, company_id)
VALUES
    ('IMMEDIATE',  'Immediate Payment',                        0,  0.0000, NULL, TRUE, 1),
    ('NET_7',      'Net 7 Days',                               7,  0.0000, NULL, TRUE, 1),
    ('NET_15',     'Net 15 Days',                             15,  0.0000, NULL, TRUE, 1),
    ('NET_30',     'Net 30 Days',                             30,  0.0000, NULL, TRUE, 1),
    ('NET_45',     'Net 45 Days',                             45,  0.0000, NULL, TRUE, 1),
    ('NET_60',     'Net 60 Days',                             60,  0.0000, NULL, TRUE, 1),
    ('NET_90',     'Net 90 Days',                             90,  0.0000, NULL, TRUE, 1),
    ('COD',        'Cash on Delivery',                         0,  0.0000, NULL, TRUE, 1),
    ('ADV_100',    '100% Advance',                             0,  0.0000, NULL, TRUE, 1),
    ('ADV_50',     '50% Advance, Balance on Delivery',         0,  0.0000, NULL, TRUE, 1),
    ('NET_30_2_10','Net 30 (2% discount if paid within 10 days)', 30, 2.0000, 10, TRUE, 1)
ON CONFLICT (company_id, term_code) DO NOTHING;

-- Seed vendor types (idempotent)
INSERT INTO ims.vendor_types (code, description, active, company_id)
VALUES
    ('LOCAL',    'Local Supplier',             TRUE, 1),
    ('IMPORT',   'Import / Foreign Supplier',  TRUE, 1),
    ('DOMESTIC', 'Domestic Manufacturer',      TRUE, 1),
    ('SERVICE',  'Service Provider',           TRUE, 1),
    ('TRADER',   'Trader / Distributor',       TRUE, 1)
ON CONFLICT (company_id, code) DO NOTHING;
