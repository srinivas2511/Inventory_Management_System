-- Phase 11 — Quotations, Order Confirmations, Delivery Challans (ERP-C-1..4)

-- Quotations
CREATE TABLE ims.quotations (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    doc_no          VARCHAR(40) NOT NULL,
    doc_date        DATE NOT NULL,
    status          VARCHAR(25) NOT NULL DEFAULT 'DRAFT',
    revision_no     INT NOT NULL DEFAULT 0,
    customer_id     BIGINT NOT NULL REFERENCES ims.customers(id),
    currency        VARCHAR(3) NOT NULL DEFAULT 'INR',
    exchange_rate   NUMERIC(18,8) NOT NULL DEFAULT 1,
    valid_until     DATE,
    salesperson_id  BIGINT REFERENCES ims.users(id),
    payment_terms_id BIGINT,
    delivery_terms  VARCHAR(200),
    enquiry_ref     VARCHAR(60),
    lost_reason     VARCHAR(200),
    converted_to_type VARCHAR(25),
    converted_to_id BIGINT,
    grand_total     NUMERIC(18,4),
    remarks         VARCHAR(1000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (company_id, doc_no)
);

CREATE TABLE ims.quotation_items (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quotation_id    BIGINT NOT NULL REFERENCES ims.quotations(id),
    line_no         INT NOT NULL,
    item_kind       VARCHAR(10) NOT NULL DEFAULT 'PRODUCT',
    material_id     BIGINT REFERENCES ims.materials(id),
    product_id      BIGINT REFERENCES ims.products(id),
    description     VARCHAR(300),
    qty             NUMERIC(18,3) NOT NULL,
    uom             VARCHAR(10),
    rate            NUMERIC(18,4),
    discount_pct    NUMERIC(9,4) DEFAULT 0,
    taxable_value   NUMERIC(18,4),
    line_total      NUMERIC(18,4),
    lead_time_days  INT,
    customer_part_no VARCHAR(40),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL DEFAULT 1,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (quotation_id, line_no)
);

-- Order Confirmations
CREATE TABLE ims.order_confirmations (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    doc_no          VARCHAR(40) NOT NULL,
    doc_date        DATE NOT NULL,
    status          VARCHAR(25) NOT NULL DEFAULT 'DRAFT',
    oc_kind         VARCHAR(10) NOT NULL DEFAULT 'DOMESTIC',
    customer_id     BIGINT NOT NULL REFERENCES ims.customers(id),
    quotation_id    BIGINT REFERENCES ims.quotations(id),
    customer_po_no  VARCHAR(60),
    customer_po_date DATE,
    delivery_date   DATE,
    currency        VARCHAR(3) NOT NULL DEFAULT 'INR',
    exchange_rate   NUMERIC(18,8) NOT NULL DEFAULT 1,
    incoterm        VARCHAR(10),
    grand_total     NUMERIC(18,4),
    remarks         VARCHAR(1000),
    sales_order_id  BIGINT UNIQUE REFERENCES ims.sales_orders(id),
    copied_from_id  BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (company_id, doc_no)
);

CREATE TABLE ims.order_confirmation_items (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    oc_id           BIGINT NOT NULL REFERENCES ims.order_confirmations(id),
    line_no         INT NOT NULL,
    item_kind       VARCHAR(10) NOT NULL DEFAULT 'PRODUCT',
    material_id     BIGINT REFERENCES ims.materials(id),
    product_id      BIGINT REFERENCES ims.products(id),
    description     VARCHAR(300),
    qty             NUMERIC(18,3) NOT NULL,
    uom             VARCHAR(10),
    rate            NUMERIC(18,4),
    line_total      NUMERIC(18,4),
    delivery_date   DATE,
    customer_part_no VARCHAR(40),
    production_order_id BIGINT,
    qty_dispatched  NUMERIC(18,3) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL DEFAULT 1,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (oc_id, line_no)
);

-- Extend sales_orders to link back to OC
ALTER TABLE ims.sales_orders ADD COLUMN IF NOT EXISTS oc_id BIGINT REFERENCES ims.order_confirmations(id);
ALTER TABLE ims.sales_orders ADD COLUMN IF NOT EXISTS branch_id BIGINT;

-- Delivery Challans
CREATE TABLE ims.delivery_challans (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    doc_no          VARCHAR(40) NOT NULL,
    doc_date        DATE NOT NULL,
    status          VARCHAR(25) NOT NULL DEFAULT 'DRAFT',
    challan_kind    VARCHAR(20) NOT NULL DEFAULT 'DELIVERY',
    customer_id     BIGINT NOT NULL REFERENCES ims.customers(id),
    oc_id           BIGINT REFERENCES ims.order_confirmations(id),
    sales_order_id  BIGINT REFERENCES ims.sales_orders(id),
    dispatch_id     BIGINT REFERENCES ims.dispatches(id),
    delivery_address VARCHAR(500),
    vehicle_no      VARCHAR(20),
    driver_name     VARCHAR(100),
    lr_no           VARCHAR(60),
    courier         VARCHAR(100),
    remarks         VARCHAR(1000),
    grand_total     NUMERIC(18,4),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (company_id, doc_no)
);

CREATE TABLE ims.delivery_challan_items (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    challan_id      BIGINT NOT NULL REFERENCES ims.delivery_challans(id),
    line_no         INT NOT NULL,
    item_kind       VARCHAR(10) NOT NULL DEFAULT 'PRODUCT',
    material_id     BIGINT REFERENCES ims.materials(id),
    product_id      BIGINT REFERENCES ims.products(id),
    description     VARCHAR(300),
    qty             NUMERIC(18,3) NOT NULL,
    uom             VARCHAR(10),
    rate            NUMERIC(18,4),
    line_total      NUMERIC(18,4),
    batch_no        VARCHAR(50),
    oc_line_id      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL DEFAULT 1,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (challan_id, line_no)
);

-- Extend dispatches with logistics columns
ALTER TABLE ims.dispatches ADD COLUMN IF NOT EXISTS lr_no VARCHAR(40);
ALTER TABLE ims.dispatches ADD COLUMN IF NOT EXISTS courier VARCHAR(60);
ALTER TABLE ims.dispatches ADD COLUMN IF NOT EXISTS tracking_no VARCHAR(60);

-- Seed SALES_MANAGER role (referenced by V64/V65 permission grants but not yet seeded)
INSERT INTO ims.roles (code, name, description, system_role)
VALUES ('SALES_MANAGER', 'Sales Manager', 'Manage sales quotations, order confirmations, delivery challans and dispatch', true)
ON CONFLICT (code) DO NOTHING;

-- Backfill V64/V65 grants that referenced SALES_MANAGER (were no-ops before this role existed)
INSERT INTO ims.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM ims.roles r, ims.permissions p
WHERE r.code = 'SALES_MANAGER'
  AND p.code IN (
    'ATTACHMENT_UPLOAD','COMMENT_ADD','GLOBAL_SEARCH','NOTIFICATION_PREF_MANAGE',
    'SALES_VIEW','SALES_CREATE','SALES_APPROVE','DISPATCH_VIEW','DISPATCH_CREATE',
    'INVENTORY_VIEW','REPORT_VIEW','DASHBOARD_VIEW','CUSTOMER_MANAGE'
  )
ON CONFLICT DO NOTHING;

-- Permissions (Phase 11 sales)
INSERT INTO ims.permissions (code, module, description) VALUES
    ('QUOTATION_VIEW',          'SALES', 'View quotations'),
    ('QUOTATION_CREATE',        'SALES', 'Create quotations'),
    ('QUOTATION_UPDATE',        'SALES', 'Update quotations'),
    ('QUOTATION_AMEND',         'SALES', 'Amend (revise) quotations'),
    ('QUOTATION_CANCEL',        'SALES', 'Cancel quotations'),
    ('QUOTATION_CONVERT',       'SALES', 'Convert quotation to OC or SO'),
    ('OC_VIEW',                 'SALES', 'View order confirmations'),
    ('OC_CREATE',               'SALES', 'Create order confirmations'),
    ('OC_UPDATE',               'SALES', 'Update order confirmations'),
    ('OC_CONFIRM',              'SALES', 'Confirm an order confirmation'),
    ('OC_CANCEL',               'SALES', 'Cancel order confirmations'),
    ('OC_CREATE_WORKORDER',     'SALES', 'Create work orders from OC lines'),
    ('CHALLAN_VIEW',            'SALES', 'View delivery challans'),
    ('CHALLAN_CREATE',          'SALES', 'Create delivery challans'),
    ('CHALLAN_ISSUE',           'SALES', 'Issue delivery challans'),
    ('CHALLAN_RETURN',          'SALES', 'Record challan returns'),
    ('DISPATCH_SEND',           'SALES', 'Send dispatch details to customer')
ON CONFLICT (code) DO NOTHING;

-- Grant all Phase 11 permissions to ADMIN
INSERT INTO ims.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM ims.roles r, ims.permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN (
    'QUOTATION_VIEW','QUOTATION_CREATE','QUOTATION_UPDATE','QUOTATION_AMEND',
    'QUOTATION_CANCEL','QUOTATION_CONVERT',
    'OC_VIEW','OC_CREATE','OC_UPDATE','OC_CONFIRM','OC_CANCEL','OC_CREATE_WORKORDER',
    'CHALLAN_VIEW','CHALLAN_CREATE','CHALLAN_ISSUE','CHALLAN_RETURN','DISPATCH_SEND'
) ON CONFLICT DO NOTHING;

-- Grant to SALES_MANAGER
INSERT INTO ims.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM ims.roles r, ims.permissions p
WHERE r.code = 'SALES_MANAGER'
  AND p.code IN (
    'QUOTATION_VIEW','QUOTATION_CREATE','QUOTATION_UPDATE','QUOTATION_AMEND',
    'QUOTATION_CANCEL','QUOTATION_CONVERT',
    'OC_VIEW','OC_CREATE','OC_UPDATE','OC_CONFIRM','OC_CANCEL','OC_CREATE_WORKORDER',
    'CHALLAN_VIEW','CHALLAN_CREATE','CHALLAN_ISSUE','CHALLAN_RETURN','DISPATCH_SEND'
) ON CONFLICT DO NOTHING;
