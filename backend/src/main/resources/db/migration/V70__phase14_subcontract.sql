-- Phase 14 — Sub-contract and service (ERP-F-1..2)

SET search_path TO ims;

-- Job work orders (send material to sub-contractor for processing)
CREATE TABLE job_work_orders (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    doc_no          VARCHAR(40) NOT NULL,
    doc_date        DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT','ISSUED','PARTIALLY_RECEIVED','RECEIVED','CLOSED','CANCELLED')),
    vendor_id       BIGINT NOT NULL REFERENCES suppliers(id),
    challan_no      VARCHAR(40),
    issue_date      DATE,
    expected_return DATE,
    actual_return   DATE,
    job_charges     NUMERIC(18,4) NOT NULL DEFAULT 0,
    scrap_qty       NUMERIC(18,3) NOT NULL DEFAULT 0,
    remarks         VARCHAR(1000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (company_id, doc_no)
);

CREATE TABLE job_work_order_items (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    jwo_id          BIGINT NOT NULL REFERENCES job_work_orders(id) ON DELETE CASCADE,
    line_no         INT NOT NULL,
    material_id     BIGINT REFERENCES materials(id),
    product_id      BIGINT REFERENCES products(id),
    description     VARCHAR(300),
    sent_qty        NUMERIC(18,3) NOT NULL,
    received_qty    NUMERIC(18,3) NOT NULL DEFAULT 0,
    scrapped_qty    NUMERIC(18,3) NOT NULL DEFAULT 0,
    uom             VARCHAR(10),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL DEFAULT 1,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (jwo_id, line_no)
);

-- Service orders
CREATE TABLE service_orders (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    doc_no          VARCHAR(40) NOT NULL,
    doc_date        DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT','CONFIRMED','IN_PROGRESS','COMPLETED','CANCELLED')),
    service_kind    VARCHAR(15) NOT NULL DEFAULT 'INWARD'
                    CHECK (service_kind IN ('INWARD','OUTWARD','AMC','INSTALLATION')),
    vendor_id       BIGINT REFERENCES suppliers(id),
    customer_id     BIGINT REFERENCES customers(id),
    description     VARCHAR(500),
    scheduled_date  DATE,
    completed_date  DATE,
    service_charge  NUMERIC(18,4) NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(18,4) NOT NULL DEFAULT 0,
    grand_total     NUMERIC(18,4) NOT NULL DEFAULT 0,
    remarks         VARCHAR(1000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (company_id, doc_no)
);

-- Permissions
INSERT INTO permissions (code, module, description) VALUES
    ('JWO_VIEW',            'PRODUCTION', 'View job work orders'),
    ('JWO_CREATE',          'PRODUCTION', 'Create job work orders'),
    ('JWO_ISSUE',           'PRODUCTION', 'Issue material for job work'),
    ('JWO_RECEIVE',         'PRODUCTION', 'Receive job work back'),
    ('SERVICE_ORDER_VIEW',  'PRODUCTION', 'View service orders'),
    ('SERVICE_ORDER_CREATE','PRODUCTION', 'Create and manage service orders')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('JWO_VIEW','JWO_CREATE','JWO_ISSUE','JWO_RECEIVE','SERVICE_ORDER_VIEW','SERVICE_ORDER_CREATE')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'PRODUCTION_MANAGER'
  AND p.code IN ('JWO_VIEW','JWO_CREATE','JWO_ISSUE','JWO_RECEIVE','SERVICE_ORDER_VIEW','SERVICE_ORDER_CREATE')
ON CONFLICT DO NOTHING;
