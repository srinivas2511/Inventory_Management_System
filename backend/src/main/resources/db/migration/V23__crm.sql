-- R12 CRM: contacts, leads, opportunities, quotations, activities, customer pricing, customer tags,
-- CRM permissions and the CRM_AGENT role. Every table carries the DESIGN section 1.1 standard columns.
SET search_path = ims;

CREATE TABLE crm_contacts (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id  BIGINT       REFERENCES customers(id),
    first_name   VARCHAR(60)  NOT NULL,
    last_name    VARCHAR(60),
    email        VARCHAR(160),
    phone        VARCHAR(20),
    designation  VARCHAR(80),
    department   VARCHAR(60),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   BIGINT,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by   BIGINT,
    version      BIGINT       NOT NULL DEFAULT 0,
    company_id   BIGINT       NOT NULL DEFAULT 1
);
CREATE INDEX ix_crm_contacts_customer ON crm_contacts (customer_id) WHERE customer_id IS NOT NULL;

CREATE TABLE crm_leads (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lead_number           VARCHAR(20)   NOT NULL,
    company_name          VARCHAR(150)  NOT NULL,
    contact_id            BIGINT        REFERENCES crm_contacts(id),
    source                VARCHAR(30),
    status                VARCHAR(15)   NOT NULL DEFAULT 'NEW'
                              CHECK (status IN ('NEW','CONTACTED','QUALIFIED','UNQUALIFIED','CONVERTED')),
    owner_id              BIGINT        NOT NULL REFERENCES users(id),
    estimated_value       NUMERIC(18,4) CHECK (estimated_value >= 0),
    expected_close_date   DATE,
    converted_customer_id BIGINT        REFERENCES customers(id),
    converted_at          TIMESTAMPTZ,
    disqualify_reason     VARCHAR(300),
    notes                 TEXT,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by            BIGINT,
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by            BIGINT,
    version               BIGINT        NOT NULL DEFAULT 0,
    company_id            BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_crm_leads_number UNIQUE (lead_number)
);
CREATE INDEX ix_crm_leads_status ON crm_leads (status);
CREATE INDEX ix_crm_leads_owner  ON crm_leads (owner_id);

CREATE TABLE crm_opportunities (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    opportunity_number     VARCHAR(20)   NOT NULL,
    lead_id                BIGINT        REFERENCES crm_leads(id),
    customer_id            BIGINT        REFERENCES customers(id),
    contact_id             BIGINT        REFERENCES crm_contacts(id),
    description            VARCHAR(200)  NOT NULL,
    stage                  VARCHAR(20)   NOT NULL DEFAULT 'PROSPECT'
                               CHECK (stage IN ('PROSPECT','QUALIFIED','PROPOSAL','NEGOTIATION','CLOSED_WON','CLOSED_LOST')),
    probability_pct        SMALLINT      NOT NULL DEFAULT 10 CHECK (probability_pct BETWEEN 0 AND 100),
    estimated_value        NUMERIC(18,4) CHECK (estimated_value >= 0),
    expected_close_date    DATE,
    owner_id               BIGINT        NOT NULL REFERENCES users(id),
    converted_quotation_id BIGINT,
    won_at                 TIMESTAMPTZ,
    lost_reason            VARCHAR(200),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by             BIGINT,
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by             BIGINT,
    version                BIGINT        NOT NULL DEFAULT 0,
    company_id             BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_crm_opportunities_number UNIQUE (opportunity_number),
    CONSTRAINT chk_crm_opp_party CHECK (lead_id IS NOT NULL OR customer_id IS NOT NULL),
    CONSTRAINT chk_crm_opp_lost CHECK (stage <> 'CLOSED_LOST' OR lost_reason IS NOT NULL)
);
CREATE INDEX ix_crm_opportunities_stage    ON crm_opportunities (stage);
CREATE INDEX ix_crm_opportunities_customer ON crm_opportunities (customer_id) WHERE customer_id IS NOT NULL;

CREATE TABLE crm_quotations (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quotation_number         VARCHAR(20)   NOT NULL,
    opportunity_id           BIGINT        REFERENCES crm_opportunities(id),
    customer_id              BIGINT        NOT NULL REFERENCES customers(id),
    valid_until              DATE          NOT NULL,
    status                   VARCHAR(15)   NOT NULL DEFAULT 'DRAFT'
                                 CHECK (status IN ('DRAFT','SENT','ACCEPTED','REJECTED','EXPIRED','CONVERTED')),
    total_amount             NUMERIC(18,4) NOT NULL DEFAULT 0,
    payment_terms            VARCHAR(60),
    notes                    VARCHAR(500),
    sent_at                  TIMESTAMPTZ,
    responded_at             TIMESTAMPTZ,
    reject_reason            VARCHAR(300),
    converted_sales_order_id BIGINT        REFERENCES sales_orders(id),
    converted_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by               BIGINT,
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by               BIGINT,
    version                  BIGINT        NOT NULL DEFAULT 0,
    company_id               BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_crm_quotations_number UNIQUE (quotation_number),
    CONSTRAINT chk_crm_quotation_converted CHECK (status <> 'CONVERTED' OR converted_sales_order_id IS NOT NULL)
);
CREATE INDEX ix_crm_quotations_customer ON crm_quotations (customer_id);
CREATE INDEX ix_crm_quotations_status   ON crm_quotations (status);
CREATE UNIQUE INDEX ux_crm_quotation_one_order ON crm_quotations (converted_sales_order_id)
    WHERE converted_sales_order_id IS NOT NULL;

ALTER TABLE crm_opportunities
    ADD CONSTRAINT fk_crm_opp_quotation FOREIGN KEY (converted_quotation_id) REFERENCES crm_quotations(id);

CREATE TABLE crm_quotation_items (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quotation_id  BIGINT        NOT NULL REFERENCES crm_quotations(id) ON DELETE CASCADE,
    line_no       INT           NOT NULL,
    product_id    BIGINT        NOT NULL REFERENCES products(id),
    quantity      NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
    unit_price    NUMERIC(18,4) NOT NULL CHECK (unit_price >= 0),
    discount_pct  NUMERIC(5,2)  NOT NULL DEFAULT 0 CHECK (discount_pct BETWEEN 0 AND 100),
    line_total    NUMERIC(18,4) NOT NULL CHECK (line_total >= 0),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by    BIGINT,
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by    BIGINT,
    version       BIGINT        NOT NULL DEFAULT 0,
    company_id    BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_crm_quotation_line UNIQUE (quotation_id, line_no)
);

CREATE TABLE crm_activities (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    activity_type    VARCHAR(15)  NOT NULL CHECK (activity_type IN ('CALL','EMAIL','MEETING','DEMO','FOLLOW_UP')),
    customer_id      BIGINT       REFERENCES customers(id),
    contact_id       BIGINT       REFERENCES crm_contacts(id),
    lead_id          BIGINT       REFERENCES crm_leads(id),
    opportunity_id   BIGINT       REFERENCES crm_opportunities(id),
    subject          VARCHAR(200) NOT NULL,
    notes            TEXT,
    outcome          VARCHAR(200),
    activity_date    TIMESTAMPTZ  NOT NULL,
    duration_minutes INT          CHECK (duration_minutes >= 0),
    owner_id         BIGINT       NOT NULL REFERENCES users(id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       BIGINT,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by       BIGINT,
    version          BIGINT       NOT NULL DEFAULT 0,
    company_id       BIGINT       NOT NULL DEFAULT 1,
    CONSTRAINT chk_crm_activity_parent CHECK (
        customer_id IS NOT NULL OR contact_id IS NOT NULL OR lead_id IS NOT NULL OR opportunity_id IS NOT NULL)
);
CREATE INDEX ix_crm_activities_customer ON crm_activities (customer_id, activity_date DESC) WHERE customer_id IS NOT NULL;
CREATE INDEX ix_crm_activities_lead     ON crm_activities (lead_id)        WHERE lead_id IS NOT NULL;
CREATE INDEX ix_crm_activities_opp      ON crm_activities (opportunity_id) WHERE opportunity_id IS NOT NULL;
CREATE INDEX ix_crm_activities_contact  ON crm_activities (contact_id)     WHERE contact_id IS NOT NULL;

-- R12-4.1: customer-specific pricing. A NULL customer_id row is the standard price for everyone.
CREATE TABLE customer_prices (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id  BIGINT        REFERENCES customers(id),
    product_id   BIGINT        NOT NULL REFERENCES products(id),
    unit_price   NUMERIC(18,4) NOT NULL CHECK (unit_price >= 0),
    min_qty      NUMERIC(18,3) NOT NULL DEFAULT 1 CHECK (min_qty > 0),
    valid_from   DATE          NOT NULL,
    valid_to     DATE,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by   BIGINT,
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by   BIGINT,
    version      BIGINT        NOT NULL DEFAULT 0,
    company_id   BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT chk_customer_price_dates CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE UNIQUE INDEX ux_customer_price ON customer_prices (COALESCE(customer_id, 0), product_id, min_qty, valid_from);
CREATE INDEX ix_customer_prices_lookup ON customer_prices (product_id, customer_id) WHERE active;

-- R12-4.2: customer segmentation and tagging.
CREATE TABLE customer_tags (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tag_group   VARCHAR(12) NOT NULL CHECK (tag_group IN ('INDUSTRY','REGION','TIER','STRATEGIC','OTHER')),
    name        VARCHAR(60) NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  BIGINT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  BIGINT,
    version     BIGINT      NOT NULL DEFAULT 0,
    company_id  BIGINT      NOT NULL DEFAULT 1,
    CONSTRAINT uq_customer_tag UNIQUE (tag_group, name)
);

CREATE TABLE customer_tag_assignments (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT      NOT NULL REFERENCES customers(id),
    tag_id      BIGINT      NOT NULL REFERENCES customer_tags(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  BIGINT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  BIGINT,
    version     BIGINT      NOT NULL DEFAULT 0,
    company_id  BIGINT      NOT NULL DEFAULT 1,
    CONSTRAINT uq_customer_tag_assignment UNIQUE (customer_id, tag_id)
);
CREATE INDEX ix_customer_tag_assignments_tag ON customer_tag_assignments (tag_id);

INSERT INTO customer_tags (tag_group, name) VALUES
('INDUSTRY', 'Automotive'), ('INDUSTRY', 'Electronics'), ('INDUSTRY', 'Furniture'), ('INDUSTRY', 'Machinery'),
('REGION', 'North'), ('REGION', 'South'), ('REGION', 'East'), ('REGION', 'West'),
('TIER', 'Gold'), ('TIER', 'Silver'), ('TIER', 'Bronze'),
('STRATEGIC', 'Strategic account');

-- Settings
INSERT INTO system_settings (key, value, value_type, description) VALUES
('crm.quotation.default_validity_days', '30', 'INTEGER', 'Default validity of a new quotation in days')
ON CONFLICT (key) DO NOTHING;

-- Permissions
INSERT INTO permissions (code, module, description) VALUES
('CRM_VIEW',            'CRM', 'View CRM leads, opportunities, quotations, contacts, activities, prices and tags'),
('LEAD_MANAGE',         'CRM', 'Create, qualify, disqualify and convert leads'),
('OPPORTUNITY_MANAGE',  'CRM', 'Create opportunities and move them through the pipeline'),
('QUOTATION_CREATE',    'CRM', 'Create, send, accept, reject and convert quotations'),
('CUSTOMER_ACTIVITY',   'CRM', 'Maintain CRM contacts and log customer activities'),
('PRICE_LIST_MANAGE',   'CRM', 'Maintain customer-specific and standard prices'),
('CUSTOMER_TAG_MANAGE', 'CRM', 'Maintain customer tags and assign them to customers');

INSERT INTO roles (code, name, description, system_role)
VALUES ('CRM_AGENT', 'CRM Agent', 'Leads, opportunities, quotations and customer activity', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'CRM_AGENT'
  AND p.code IN ('CRM_VIEW','LEAD_MANAGE','OPPORTUNITY_MANAGE','QUOTATION_CREATE','CUSTOMER_ACTIVITY',
                 'SALES_VIEW','PRODUCT_VIEW','REPORT_VIEW','DASHBOARD_VIEW')
ON CONFLICT DO NOTHING;

-- Sales keeps ownership of pricing and segmentation.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'SALES'
  AND p.code IN ('CRM_VIEW','PRICE_LIST_MANAGE','CUSTOMER_TAG_MANAGE')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('CRM_VIEW','LEAD_MANAGE','OPPORTUNITY_MANAGE','QUOTATION_CREATE','CUSTOMER_ACTIVITY',
                 'PRICE_LIST_MANAGE','CUSTOMER_TAG_MANAGE')
ON CONFLICT DO NOTHING;
