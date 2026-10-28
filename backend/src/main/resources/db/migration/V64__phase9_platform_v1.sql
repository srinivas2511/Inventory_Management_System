-- Phase 9 â€” Platform extensions, part 1 (ERP-A-1, A-2, A-3, A-7)
-- Tasks: 9.1 UOM/item categories/series; 9.2 company profile; 9.3 document series; 9.7 teams/notifications
-- Migration mapping: DESIGN-ERP Â§1.3 V12 â†’ actual V64
-- NOTE: uom_conversions already exists (V55, GC-1: material-specific alt-UOM factors). This migration
--       adds uom_conversion_factors for the general fromâ†’to pattern (design gap G2 resolution).
SET search_path TO ims;

-- â”€â”€ UOM general conversion factors â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- Keeps existing uom_conversions (V55) for material-specific alt-UOM.
-- uom_conversion_factors: general (global) or item-specific from_uom â†’ to_uom.
CREATE TABLE uom_conversion_factors (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  from_uom    VARCHAR(10)   NOT NULL REFERENCES uoms(code),
  to_uom      VARCHAR(10)   NOT NULL REFERENCES uoms(code),
  factor      NUMERIC(18,8) NOT NULL CHECK (factor > 0),
  material_id BIGINT        REFERENCES materials(id),
  product_id  BIGINT        REFERENCES products(id),
  created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
  created_by  BIGINT,
  updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_by  BIGINT,
  version     BIGINT        NOT NULL DEFAULT 0,
  company_id  BIGINT        NOT NULL DEFAULT 1,
  UNIQUE NULLS NOT DISTINCT (company_id, from_uom, to_uom, material_id, product_id),
  CHECK (from_uom <> to_uom)
);

-- â”€â”€ Item categories (hierarchical) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE item_categories (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  category_code VARCHAR(20)  NOT NULL,
  name          VARCHAR(100) NOT NULL,
  parent_id     BIGINT       REFERENCES item_categories(id),
  item_type     VARCHAR(10)  NOT NULL CHECK (item_type IN ('MATERIAL','PRODUCT','BOTH')),
  active        BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by    BIGINT,
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by    BIGINT,
  version       BIGINT       NOT NULL DEFAULT 0,
  company_id    BIGINT       NOT NULL DEFAULT 1,
  UNIQUE (company_id, category_code)
);

-- â”€â”€ Item series (custom code series per category) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE item_series (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  series_code VARCHAR(20)  NOT NULL,
  name        VARCHAR(80)  NOT NULL,
  prefix      VARCHAR(20),
  next_no     BIGINT       NOT NULL DEFAULT 1 CHECK (next_no > 0),
  padding     INT          NOT NULL DEFAULT 3 CHECK (padding BETWEEN 1 AND 10),
  category_id BIGINT       REFERENCES item_categories(id),
  active      BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by  BIGINT,
  company_id  BIGINT       NOT NULL DEFAULT 1,
  UNIQUE (company_id, series_code)
);

-- â”€â”€ Extend materials (skip purchase_uom â€” already exists from V55) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
ALTER TABLE materials
  ADD COLUMN IF NOT EXISTS item_kind   VARCHAR(10)  NOT NULL DEFAULT 'STOCK'
      CHECK (item_kind IN ('STOCK','SERVICE')),
  ADD COLUMN IF NOT EXISTS category_id BIGINT       REFERENCES item_categories(id),
  ADD COLUMN IF NOT EXISTS hsn_sac     VARCHAR(10),
  ADD COLUMN IF NOT EXISTS barcode     VARCHAR(60),
  ADD COLUMN IF NOT EXISTS custom      JSONB        NOT NULL DEFAULT '{}';

-- â”€â”€ Extend products â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
ALTER TABLE products
  ADD COLUMN IF NOT EXISTS item_kind   VARCHAR(10)  NOT NULL DEFAULT 'STOCK'
      CHECK (item_kind IN ('STOCK','SERVICE')),
  ADD COLUMN IF NOT EXISTS category_id BIGINT       REFERENCES item_categories(id),
  ADD COLUMN IF NOT EXISTS hsn_sac     VARCHAR(10),
  ADD COLUMN IF NOT EXISTS sales_uom   VARCHAR(10),
  ADD COLUMN IF NOT EXISTS barcode     VARCHAR(60),
  ADD COLUMN IF NOT EXISTS max_stock   NUMERIC(18,3),
  ADD COLUMN IF NOT EXISTS custom      JSONB        NOT NULL DEFAULT '{}';

-- â”€â”€ Extend suppliers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
ALTER TABLE suppliers
  ADD COLUMN IF NOT EXISTS pan         VARCHAR(10),
  ADD COLUMN IF NOT EXISTS state_code  VARCHAR(2),
  ADD COLUMN IF NOT EXISTS currency    VARCHAR(3)      DEFAULT 'INR',
  ADD COLUMN IF NOT EXISTS custom      JSONB        NOT NULL DEFAULT '{}',
  ADD COLUMN IF NOT EXISTS credit_days INT;

-- â”€â”€ Extend customers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
ALTER TABLE customers
  ADD COLUMN IF NOT EXISTS pan          VARCHAR(10),
  ADD COLUMN IF NOT EXISTS state_code   VARCHAR(2),
  ADD COLUMN IF NOT EXISTS currency     VARCHAR(3)       DEFAULT 'INR',
  ADD COLUMN IF NOT EXISTS credit_limit NUMERIC(18,4),
  ADD COLUMN IF NOT EXISTS credit_days  INT,
  ADD COLUMN IF NOT EXISTS custom       JSONB         NOT NULL DEFAULT '{}';

-- â”€â”€ Party addresses â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE party_addresses (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  party_type  VARCHAR(10)  NOT NULL CHECK (party_type IN ('SUPPLIER','CUSTOMER')),
  party_id    BIGINT       NOT NULL,
  kind        VARCHAR(10)  NOT NULL DEFAULT 'BILLING' CHECK (kind IN ('BILLING','SHIPPING','OTHER')),
  line1       VARCHAR(150),
  line2       VARCHAR(150),
  city        VARCHAR(80),
  state_code  VARCHAR(2),
  pincode     VARCHAR(10),
  country     VARCHAR(2)      NOT NULL DEFAULT 'IN',
  gstin       VARCHAR(15),
  is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by  BIGINT,
  company_id  BIGINT       NOT NULL DEFAULT 1
);
CREATE INDEX ix_party_addr ON party_addresses(party_type, party_id);

-- â”€â”€ Party contacts â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE party_contacts (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  party_type  VARCHAR(10)  NOT NULL,
  party_id    BIGINT       NOT NULL,
  name        VARCHAR(100) NOT NULL,
  designation VARCHAR(60),
  phone       VARCHAR(20),
  email       VARCHAR(160),
  is_primary  BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by  BIGINT,
  company_id  BIGINT       NOT NULL DEFAULT 1
);
CREATE INDEX ix_party_contact ON party_contacts(party_type, party_id);

-- â”€â”€ Company profile â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE company_profile (
  id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  company_id               BIGINT       NOT NULL DEFAULT 1 UNIQUE,
  legal_name               VARCHAR(200) NOT NULL,
  trade_name               VARCHAR(200),
  pan                      VARCHAR(10),
  cin                      VARCHAR(25),
  logo_storage_key         VARCHAR(200),
  signature_storage_key    VARCHAR(200),
  default_payment_terms_id BIGINT,
  fiscal_year_start_month  INT          NOT NULL DEFAULT 4 CHECK (fiscal_year_start_month BETWEEN 1 AND 12),
  website                  VARCHAR(200),
  updated_at               TIMESTAMPTZ,
  updated_by               BIGINT
);

-- â”€â”€ Company branches â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE company_branches (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  branch_code VARCHAR(10)  NOT NULL,
  name        VARCHAR(100) NOT NULL,
  gstin       VARCHAR(15),
  state_code  VARCHAR(2)      NOT NULL,
  address     VARCHAR(400),
  phone       VARCHAR(20),
  email       VARCHAR(160),
  is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
  active      BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by  BIGINT,
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by  BIGINT,
  version     BIGINT       NOT NULL DEFAULT 0,
  company_id  BIGINT       NOT NULL DEFAULT 1,
  UNIQUE (company_id, branch_code)
);

-- â”€â”€ Bank accounts â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE bank_accounts (
  id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  branch_id          BIGINT,
  account_name       VARCHAR(100) NOT NULL,
  bank_name          VARCHAR(100),
  account_no_last4   VARCHAR(4),
  ifsc               VARCHAR(11),
  upi_id             VARCHAR(60),
  show_on_documents  BOOLEAN      NOT NULL DEFAULT TRUE,
  is_default         BOOLEAN      NOT NULL DEFAULT FALSE,
  active             BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by         BIGINT,
  updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by         BIGINT,
  version            BIGINT       NOT NULL DEFAULT 0,
  company_id         BIGINT       NOT NULL DEFAULT 1
);

-- â”€â”€ Transporters â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE transporters (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  transporter_code    VARCHAR(20),
  name                VARCHAR(120) NOT NULL,
  gst_transporter_id  VARCHAR(15),
  contact             VARCHAR(100),
  active              BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by          BIGINT,
  updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by          BIGINT,
  version             BIGINT       NOT NULL DEFAULT 0,
  company_id          BIGINT       NOT NULL DEFAULT 1,
  UNIQUE NULLS NOT DISTINCT (company_id, transporter_code)
);

-- â”€â”€ Payment terms â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE payment_terms (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  term_code     VARCHAR(20)  NOT NULL,
  name          VARCHAR(80)  NOT NULL,
  net_days      INT          NOT NULL DEFAULT 0 CHECK (net_days >= 0),
  discount_pct  NUMERIC(9,4) DEFAULT 0,
  discount_days INT,
  active        BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by    BIGINT,
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by    BIGINT,
  version       BIGINT       NOT NULL DEFAULT 0,
  company_id    BIGINT       NOT NULL DEFAULT 1,
  UNIQUE (company_id, term_code)
);

-- â”€â”€ Terms / T&C templates â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE terms_templates (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  template_code VARCHAR(20)  NOT NULL,
  applies_to    VARCHAR(25),
  body          TEXT         NOT NULL,
  is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
  active        BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by    BIGINT,
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by    BIGINT,
  version       BIGINT       NOT NULL DEFAULT 0,
  company_id    BIGINT       NOT NULL DEFAULT 1,
  UNIQUE (company_id, template_code)
);

-- â”€â”€ User branch access â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE user_branch_access (
  user_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  branch_id BIGINT NOT NULL REFERENCES company_branches(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, branch_id)
);

-- â”€â”€ Teams â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE teams (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  team_code   VARCHAR(20)  NOT NULL,
  name        VARCHAR(80)  NOT NULL,
  active      BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by  BIGINT,
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by  BIGINT,
  version     BIGINT       NOT NULL DEFAULT 0,
  company_id  BIGINT       NOT NULL DEFAULT 1,
  UNIQUE (company_id, team_code)
);

CREATE TABLE team_members (
  team_id   BIGINT      NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
  user_id   BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (team_id, user_id)
);

CREATE TABLE team_report_access (
  team_id     BIGINT      NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
  report_code VARCHAR(60) NOT NULL,
  PRIMARY KEY (team_id, report_code)
);

-- â”€â”€ Notification preferences â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- Coexists with notification_subscriptions (V31) â€” different purpose:
-- notification_preferences: per-user channel enable/disable toggle.
CREATE TABLE notification_preferences (
  user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  event_type VARCHAR(50) NOT NULL,
  channel    VARCHAR(10) NOT NULL CHECK (channel IN ('IN_APP','EMAIL')),
  enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
  PRIMARY KEY (user_id, event_type, channel)
);

-- â”€â”€ Document series â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- Replaces direct number_sequences for ERP documents.
-- Existing number_sequences rows remain for legacy R1-R20 document types.
CREATE TABLE document_series (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  document_kind  VARCHAR(30)  NOT NULL,
  series_code    VARCHAR(20)  NOT NULL,
  branch_id      BIGINT       REFERENCES company_branches(id),
  format         VARCHAR(80)  NOT NULL,  -- tokens: {FY} {yyyy} {MM} {BR} {00000}
  numbering      VARCHAR(10)  NOT NULL DEFAULT 'AUTO' CHECK (numbering IN ('AUTO','MANUAL')),
  reset_policy   VARCHAR(10)  NOT NULL DEFAULT 'FY'   CHECK (reset_policy IN ('NEVER','FY','MONTH')),
  is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
  active         BOOLEAN      NOT NULL DEFAULT TRUE,
  statutory      BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by     BIGINT,
  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by     BIGINT,
  version        BIGINT       NOT NULL DEFAULT 0,
  company_id     BIGINT       NOT NULL DEFAULT 1,
  UNIQUE (company_id, document_kind, series_code)
);
CREATE UNIQUE INDEX ux_series_default
  ON document_series(company_id, document_kind, COALESCE(branch_id, 0))
  WHERE is_default AND active;

CREATE TABLE document_series_counters (
  id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  series_id  BIGINT      NOT NULL REFERENCES document_series(id) ON DELETE CASCADE,
  period_key VARCHAR(10) NOT NULL,
  last_value BIGINT      NOT NULL DEFAULT 0,
  UNIQUE (series_id, period_key)
);

-- â”€â”€ Seed default document series â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
INSERT INTO document_series (document_kind, series_code, format, numbering, reset_policy, is_default, active, statutory, company_id) VALUES
  ('RFQ',                 'RFQ-DEFAULT', 'RFQ-{FY}-{00000}',  'AUTO', 'FY', TRUE, TRUE, FALSE, 1),
  ('SUPPLIER_QUOTE',      'SQ-DEFAULT',  'SQ-{FY}-{00000}',   'AUTO', 'FY', TRUE, TRUE, FALSE, 1),
  ('QUOTATION',           'QT-DEFAULT',  'QT-{FY}-{00000}',   'AUTO', 'FY', TRUE, TRUE, FALSE, 1),
  ('ORDER_CONFIRMATION',  'OC-DEFAULT',  'OC-{FY}-{00000}',   'AUTO', 'FY', TRUE, TRUE, FALSE, 1),
  ('DELIVERY_CHALLAN',    'DC-DEFAULT',  'DC-{FY}-{00000}',   'AUTO', 'FY', TRUE, TRUE, FALSE, 1),
  ('SALES_INVOICE',       'INV-DEFAULT', 'INV-{FY}-{00000}',  'AUTO', 'FY', TRUE, TRUE, TRUE,  1),
  ('PROFORMA_INVOICE',    'PI-DEFAULT',  'PI-{FY}-{00000}',   'AUTO', 'FY', TRUE, TRUE, FALSE, 1),
  ('CREDIT_NOTE',         'CN-DEFAULT',  'CN-{FY}-{00000}',   'AUTO', 'FY', TRUE, TRUE, TRUE,  1),
  ('DEBIT_NOTE',          'DN-DEFAULT',  'DN-{FY}-{00000}',   'AUTO', 'FY', TRUE, TRUE, TRUE,  1),
  ('PURCHASE_INVOICE',    'PIN-DEFAULT', 'PIN-{FY}-{00000}',  'AUTO', 'FY', TRUE, TRUE, FALSE, 1),
  ('PURCHASE_RETURN',     'PRT-DEFAULT', 'PRT-{FY}-{00000}',  'AUTO', 'FY', TRUE, TRUE, FALSE, 1),
  ('PAYMENT',             'PAY-DEFAULT', 'PAY-{FY}-{00000}',  'AUTO', 'FY', TRUE, TRUE, FALSE, 1),
  ('RECEIPT',             'RCT-DEFAULT', 'RCT-{FY}-{00000}',  'AUTO', 'FY', TRUE, TRUE, FALSE, 1),
  ('ADVANCE_RECEIPT',     'ARV-DEFAULT', 'ARV-{FY}-{00000}',  'AUTO', 'FY', TRUE, TRUE, FALSE, 1),
  ('JOB_WORK_ORDER',      'JW-DEFAULT',  'JW-{FY}-{00000}',   'AUTO', 'FY', TRUE, TRUE, FALSE, 1),
  ('SERVICE_ORDER',       'SVO-DEFAULT', 'SVO-{FY}-{00000}',  'AUTO', 'FY', TRUE, TRUE, FALSE, 1),
  ('SERVICE_CONFIRMATION','SVC-DEFAULT', 'SVC-{FY}-{00000}',  'AUTO', 'FY', TRUE, TRUE, FALSE, 1),
  ('MRP_RUN',             'MRP-DEFAULT', 'MRP-{yyyy}{MM}-{000}','AUTO','MONTH',TRUE,TRUE,FALSE, 1);

-- â”€â”€ Permissions (Phase 9) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
INSERT INTO permissions (code, module, description) VALUES
  ('UOM_MANAGE',              'MASTERDATA', 'Manage UOM conversion factors'),
  ('CATEGORY_MANAGE',         'MASTERDATA', 'Manage item categories and series'),
  ('COMPANY_MANAGE',          'SETTINGS',   'Manage company profile, branches, bank accounts'),
  ('BRANCH_MANAGE',           'SETTINGS',   'Manage company branches'),
  ('PAYMENT_TERMS_MANAGE',    'SETTINGS',   'Manage payment terms templates'),
  ('TRANSPORTER_MANAGE',      'SETTINGS',   'Manage transporter master'),
  ('DOCUMENT_SERIES_MANAGE',  'SETTINGS',   'Manage document numbering series'),
  ('TEAM_MANAGE',             'IAM',        'Manage teams and team membership'),
  ('APPROVAL_RULE_MANAGE',    'SETTINGS',   'Manage configurable approval rules'),
  ('ATTACHMENT_UPLOAD',       'DOCUMENTS',  'Upload attachments on any entity'),
  ('COMMENT_ADD',             'DOCUMENTS',  'Add comments on any entity'),
  ('IMPORT_RUN',              'MASTERDATA', 'Run bulk import jobs'),
  ('GLOBAL_SEARCH',           'PLATFORM',   'Use global search'),
  ('TAG_MANAGE',              'MASTERDATA', 'Manage tags'),
  ('CUSTOM_FIELD_MANAGE',     'SETTINGS',   'Manage custom field definitions'),
  ('NOTIFICATION_PREF_MANAGE','SETTINGS',   'Manage notification preferences'),
  ('BACKGROUND_JOB_VIEW',     'PLATFORM',   'View background job status')
ON CONFLICT (code) DO NOTHING;

-- Grant all Phase 9 permissions to ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN (
    'UOM_MANAGE','CATEGORY_MANAGE','COMPANY_MANAGE','BRANCH_MANAGE',
    'PAYMENT_TERMS_MANAGE','TRANSPORTER_MANAGE','DOCUMENT_SERIES_MANAGE',
    'TEAM_MANAGE','APPROVAL_RULE_MANAGE','ATTACHMENT_UPLOAD','COMMENT_ADD',
    'IMPORT_RUN','GLOBAL_SEARCH','TAG_MANAGE','CUSTOM_FIELD_MANAGE',
    'NOTIFICATION_PREF_MANAGE','BACKGROUND_JOB_VIEW'
  )
ON CONFLICT DO NOTHING;

-- Grant operational permissions to managers
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('PURCHASE_MANAGER','SALES_MANAGER','PRODUCTION_MANAGER','FINANCE_MANAGER',
                 'QUALITY_MANAGER','STORE_MANAGER','HR_MANAGER','MAINTENANCE_MANAGER')
  AND p.code IN ('ATTACHMENT_UPLOAD','COMMENT_ADD','GLOBAL_SEARCH')
ON CONFLICT DO NOTHING;
