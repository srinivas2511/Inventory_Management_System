-- ERP-B-5 Purchase-return delivery challan: dispatch document for goods returned to supplier

SET search_path TO ims;

CREATE TABLE purchase_return_challans (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    doc_no          VARCHAR(40) NOT NULL,
    doc_date        DATE NOT NULL DEFAULT CURRENT_DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    supplier_id     BIGINT NOT NULL REFERENCES suppliers(id),
    purchase_return_id BIGINT REFERENCES purchase_returns(id),
    vehicle_no      VARCHAR(20),
    lr_no           VARCHAR(60),
    remarks         VARCHAR(1000),
    grand_total     NUMERIC(18,4),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (company_id, doc_no)
);

CREATE TABLE purchase_return_challan_items (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    challan_id      BIGINT NOT NULL REFERENCES purchase_return_challans ON DELETE CASCADE,
    line_no         INT NOT NULL,
    material_id     BIGINT NOT NULL REFERENCES materials(id),
    qty             NUMERIC(18,3) NOT NULL,
    uom             VARCHAR(20) NOT NULL DEFAULT 'PCS',
    unit_price      NUMERIC(18,4) NOT NULL DEFAULT 0,
    line_total      NUMERIC(18,4) GENERATED ALWAYS AS (qty * unit_price) STORED
);

INSERT INTO permissions (code, module, description) VALUES
    ('PRC_CHALLAN_VIEW',   'PROCUREMENT', 'View purchase-return challans'),
    ('PRC_CHALLAN_CREATE', 'PROCUREMENT', 'Create purchase-return challans'),
    ('PRC_CHALLAN_DISPATCH','PROCUREMENT','Dispatch purchase-return challans')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('PRC_CHALLAN_VIEW','PRC_CHALLAN_CREATE','PRC_CHALLAN_DISPATCH')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('PURCHASE_MANAGER','STORE_MANAGER')
  AND p.code IN ('PRC_CHALLAN_VIEW','PRC_CHALLAN_CREATE','PRC_CHALLAN_DISPATCH')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('MANAGEMENT','ACCOUNTANT','FINANCE_MANAGER')
  AND p.code IN ('PRC_CHALLAN_VIEW')
ON CONFLICT DO NOTHING;
