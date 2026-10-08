-- R9-7.5: GST tax engine. Tax determination is DATA: slabs map a supply type (intra-state, inter-state, union territory)
-- to tax codes, tax codes carry the rate and the input / output GL accounts, and the state of a partner comes from the
-- first two characters of its GSTIN. No rate or rule is coded in Java.
--
-- SAMPLE DATA: the rates (18 / 12 / 5 %), the component split and the state list below are illustrative seed data for
-- the Spring Manufacturing vertical. Finance must verify them against the current GST schedule before go-live; the
-- tables are meant to be edited.
SET search_path = ims;

-- ── GL accounts ──────────────────────────────────────────────────────────────
INSERT INTO gl_accounts (account_code, name, account_group_id, account_type, normal_balance, company_id) VALUES
('1311', 'Input CGST',   (SELECT id FROM gl_account_groups WHERE group_code = '13'), 'ASSET',     'DEBIT',  1),
('1312', 'Input SGST',   (SELECT id FROM gl_account_groups WHERE group_code = '13'), 'ASSET',     'DEBIT',  1),
('1313', 'Input IGST',   (SELECT id FROM gl_account_groups WHERE group_code = '13'), 'ASSET',     'DEBIT',  1),
('1314', 'Input UTGST',  (SELECT id FROM gl_account_groups WHERE group_code = '13'), 'ASSET',     'DEBIT',  1),
('2311', 'Output CGST',  (SELECT id FROM gl_account_groups WHERE group_code = '20'), 'LIABILITY', 'CREDIT', 1),
('2312', 'Output SGST',  (SELECT id FROM gl_account_groups WHERE group_code = '20'), 'LIABILITY', 'CREDIT', 1),
('2313', 'Output IGST',  (SELECT id FROM gl_account_groups WHERE group_code = '20'), 'LIABILITY', 'CREDIT', 1),
('2314', 'Output UTGST', (SELECT id FROM gl_account_groups WHERE group_code = '20'), 'LIABILITY', 'CREDIT', 1)
ON CONFLICT (account_code) DO NOTHING;

-- ── Tax codes: the missing components and the account links ──────────────────
ALTER TABLE tax_codes ADD COLUMN IF NOT EXISTS input_gl_account_id  BIGINT REFERENCES gl_accounts(id);
ALTER TABLE tax_codes ADD COLUMN IF NOT EXISTS output_gl_account_id BIGINT REFERENCES gl_accounts(id);

INSERT INTO tax_codes (tax_code, description, tax_type, rate_pct, company_id) VALUES
('CGST2.5',  'CGST @ 2.5%',  'CGST',  2.50, 1),
('SGST2.5',  'SGST @ 2.5%',  'SGST',  2.50, 1),
('UTGST9',   'UTGST @ 9%',   'UTGST', 9.00, 1),
('UTGST6',   'UTGST @ 6%',   'UTGST', 6.00, 1),
('UTGST2.5', 'UTGST @ 2.5%', 'UTGST', 2.50, 1)
ON CONFLICT (tax_code) DO NOTHING;

UPDATE tax_codes SET
  input_gl_account_id  = (SELECT id FROM gl_accounts WHERE account_code = CASE tax_type
                            WHEN 'CGST' THEN '1311' WHEN 'SGST' THEN '1312' WHEN 'IGST' THEN '1313' WHEN 'UTGST' THEN '1314' END),
  output_gl_account_id = (SELECT id FROM gl_accounts WHERE account_code = CASE tax_type
                            WHEN 'CGST' THEN '2311' WHEN 'SGST' THEN '2312' WHEN 'IGST' THEN '2313' WHEN 'UTGST' THEN '2314' END)
WHERE tax_type IN ('CGST', 'SGST', 'IGST', 'UTGST');

-- ── States (sample list) ─────────────────────────────────────────────────────
CREATE TABLE gst_states (
  state_code        VARCHAR(2)   PRIMARY KEY,
  name              VARCHAR(60)  NOT NULL,
  union_territory   BOOLEAN      NOT NULL DEFAULT FALSE,
  sample_data       BOOLEAN      NOT NULL DEFAULT TRUE
);
COMMENT ON TABLE gst_states IS 'GST state codes. union_territory = a union territory without a legislature (CGST + UTGST apply). SAMPLE rows: extend and verify.';
INSERT INTO gst_states (state_code, name, union_territory) VALUES
('07', 'Delhi',                                    FALSE),
('08', 'Rajasthan',                                FALSE),
('09', 'Uttar Pradesh',                            FALSE),
('19', 'West Bengal',                              FALSE),
('24', 'Gujarat',                                  FALSE),
('27', 'Maharashtra',                              FALSE),
('29', 'Karnataka',                                FALSE),
('33', 'Tamil Nadu',                               FALSE),
('36', 'Telangana',                                FALSE),
('04', 'Chandigarh',                               TRUE),
('26', 'Dadra and Nagar Haveli and Daman and Diu', TRUE),
('31', 'Lakshadweep',                              TRUE),
('35', 'Andaman and Nicobar Islands',              TRUE),
('38', 'Ladakh',                                   TRUE);

-- ── Slabs: slab x supply type -> tax code components ─────────────────────────
CREATE TABLE tax_slabs (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  slab_code    VARCHAR(15)  NOT NULL,
  description  VARCHAR(100) NOT NULL,
  supply_type  VARCHAR(5)   NOT NULL CHECK (supply_type IN ('INTRA', 'INTER', 'UT')),
  tax_code_id  BIGINT       NOT NULL REFERENCES tax_codes(id),
  active       BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by   BIGINT,
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by   BIGINT,
  version      BIGINT       NOT NULL DEFAULT 0,
  company_id   BIGINT       NOT NULL DEFAULT 1,
  CONSTRAINT uq_tax_slab_component UNIQUE (slab_code, supply_type, tax_code_id)
);

INSERT INTO tax_slabs (slab_code, description, supply_type, tax_code_id)
SELECT s.slab, s.descr, s.supply, (SELECT id FROM tax_codes WHERE tax_code = s.tax_code)
  FROM (VALUES
    ('GST18', 'GST 18% (sample rate: verify)', 'INTRA', 'CGST9'),
    ('GST18', 'GST 18% (sample rate: verify)', 'INTRA', 'SGST9'),
    ('GST18', 'GST 18% (sample rate: verify)', 'INTER', 'IGST18'),
    ('GST18', 'GST 18% (sample rate: verify)', 'UT',    'CGST9'),
    ('GST18', 'GST 18% (sample rate: verify)', 'UT',    'UTGST9'),
    ('GST12', 'GST 12% (sample rate: verify)', 'INTRA', 'CGST6'),
    ('GST12', 'GST 12% (sample rate: verify)', 'INTRA', 'SGST6'),
    ('GST12', 'GST 12% (sample rate: verify)', 'INTER', 'IGST12'),
    ('GST12', 'GST 12% (sample rate: verify)', 'UT',    'CGST6'),
    ('GST12', 'GST 12% (sample rate: verify)', 'UT',    'UTGST6'),
    ('GST5',  'GST 5% (sample rate: verify)',  'INTRA', 'CGST2.5'),
    ('GST5',  'GST 5% (sample rate: verify)',  'INTRA', 'SGST2.5'),
    ('GST5',  'GST 5% (sample rate: verify)',  'INTER', 'IGST5'),
    ('GST5',  'GST 5% (sample rate: verify)',  'UT',    'CGST2.5'),
    ('GST5',  'GST 5% (sample rate: verify)',  'UT',    'UTGST2.5'),
    ('GST0',  'Exempt / nil rated',            'INTRA', 'EXEMPT'),
    ('GST0',  'Exempt / nil rated',            'INTER', 'EXEMPT'),
    ('GST0',  'Exempt / nil rated',            'UT',    'EXEMPT')
  ) AS s(slab, descr, supply, tax_code);

-- ── Tax lines of a document, and the slab of a product ───────────────────────
CREATE TABLE invoice_tax_lines (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  document_type   VARCHAR(20)   NOT NULL,
  document_id     BIGINT        NOT NULL,
  direction       VARCHAR(8)    NOT NULL CHECK (direction IN ('PURCHASE', 'SALES')),
  supply_type     VARCHAR(5)    NOT NULL,
  tax_code_id     BIGINT        NOT NULL REFERENCES tax_codes(id),
  tax_code        VARCHAR(15)   NOT NULL,
  tax_type        VARCHAR(10)   NOT NULL,
  rate_pct        NUMERIC(5,2)  NOT NULL,
  taxable_amount  NUMERIC(18,4) NOT NULL,
  tax_amount      NUMERIC(18,4) NOT NULL,
  gl_account_id   BIGINT        REFERENCES gl_accounts(id),
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
  created_by      BIGINT,
  updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_by      BIGINT,
  version         BIGINT        NOT NULL DEFAULT 0,
  company_id      BIGINT        NOT NULL DEFAULT 1
);
CREATE INDEX ix_invoice_tax_lines_doc ON invoice_tax_lines (document_type, document_id);

CREATE TABLE product_tax_slabs (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_id  BIGINT       NOT NULL UNIQUE REFERENCES products(id),
  slab_code   VARCHAR(15)  NOT NULL,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by  BIGINT,
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by  BIGINT,
  version     BIGINT       NOT NULL DEFAULT 0,
  company_id  BIGINT       NOT NULL DEFAULT 1
);

ALTER TABLE ap_invoices ADD COLUMN IF NOT EXISTS tax_slab_code VARCHAR(15);
ALTER TABLE ap_invoices ADD COLUMN IF NOT EXISTS supply_type   VARCHAR(5);
ALTER TABLE ar_invoices ADD COLUMN IF NOT EXISTS supply_type   VARCHAR(5);

-- ── Permission: who may assign a slab to a product ───────────────────────────
INSERT INTO permissions (code, module, description) VALUES
('TAX_MANAGE', 'FINANCE', 'Assign GST tax slabs to products and maintain tax determination data')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
 WHERE r.code IN ('ADMIN', 'FINANCE_MANAGER') AND p.code = 'TAX_MANAGE'
ON CONFLICT DO NOTHING;
