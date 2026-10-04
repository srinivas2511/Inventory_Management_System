-- R15 PLM: engineering change orders, affected items, product lifecycle (PLM-001..005), PLM permissions and the
-- PLM_ENGINEER role. Every table carries the DESIGN section 1.1 standard columns.
SET search_path = ims;

-- A BOM number identifies a BOM across its revisions. V8 declared it UNIQUE on its own as well as UNIQUE
-- (bom_number, revision), so copying a revision (same number, next letter) could never succeed. Keep the pair.
ALTER TABLE bom DROP CONSTRAINT IF EXISTS bom_bom_number_key;

-- Product lifecycle is a separate dimension from products.status (DRAFT/ACTIVE/OBSOLETE master-data state), so
-- existing products default to ACTIVE and nothing about how they are ordered or produced changes.
ALTER TABLE products
    ADD COLUMN lifecycle_status VARCHAR(15) NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT chk_products_lifecycle CHECK (lifecycle_status IN
            ('DEVELOPMENT','PROTOTYPE','APPROVED','ACTIVE','PHASE_OUT','OBSOLETE'));
CREATE INDEX ix_products_lifecycle ON products(lifecycle_status);

CREATE TABLE ecos (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    eco_number        VARCHAR(20)  NOT NULL UNIQUE,
    title             VARCHAR(200) NOT NULL,
    description       TEXT,
    reason            VARCHAR(500) NOT NULL,
    change_category   VARCHAR(20)  NOT NULL
        CONSTRAINT chk_eco_category CHECK (change_category IN ('BOM_CHANGE','ROUTING_CHANGE','SPEC_CHANGE','DRAWING_CHANGE')),
    urgency           VARCHAR(10)  NOT NULL DEFAULT 'NORMAL'
        CONSTRAINT chk_eco_urgency CHECK (urgency IN ('CRITICAL','HIGH','NORMAL','LOW')),
    effectivity_date  DATE,
    impact_assessment TEXT,
    status            VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT chk_eco_status CHECK (status IN ('DRAFT','REVIEW','APPROVED','IMPLEMENTING','IMPLEMENTED','CANCELLED')),
    initiated_by      BIGINT       NOT NULL REFERENCES users(id),
    approved_by       BIGINT       REFERENCES users(id),
    approved_at       TIMESTAMPTZ,
    implemented_at    TIMESTAMPTZ,
    status_reason     VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        BIGINT,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by        BIGINT,
    version           BIGINT       NOT NULL DEFAULT 0,
    company_id        BIGINT       NOT NULL DEFAULT 1
);
CREATE INDEX ix_ecos_status ON ecos(status);

CREATE TABLE eco_affected_items (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    eco_id             BIGINT       NOT NULL REFERENCES ecos(id) ON DELETE CASCADE,
    item_type          VARCHAR(10)  NOT NULL
        CONSTRAINT chk_eco_item_type CHECK (item_type IN ('PRODUCT','BOM','ROUTING')),
    product_id         BIGINT       NOT NULL REFERENCES products(id),
    old_bom_id         BIGINT       REFERENCES bom(id),
    new_bom_id         BIGINT       REFERENCES bom(id),
    change_description VARCHAR(300) NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         BIGINT,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by         BIGINT,
    version            BIGINT       NOT NULL DEFAULT 0,
    company_id         BIGINT       NOT NULL DEFAULT 1,
    CONSTRAINT uq_eco_item UNIQUE (eco_id, item_type, product_id)
);
CREATE INDEX ix_eco_items_product ON eco_affected_items(product_id);

CREATE TABLE product_lifecycle_events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id   BIGINT       NOT NULL REFERENCES products(id),
    from_status  VARCHAR(15),
    to_status    VARCHAR(15)  NOT NULL,
    eco_id       BIGINT       REFERENCES ecos(id),
    changed_by   BIGINT       REFERENCES users(id),
    changed_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reason       VARCHAR(500) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   BIGINT,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by   BIGINT,
    version      BIGINT       NOT NULL DEFAULT 0,
    company_id   BIGINT       NOT NULL DEFAULT 1
);
CREATE INDEX ix_lifecycle_events_product ON product_lifecycle_events(product_id, changed_at);

-- Permissions (DRAWING_MANAGE already exists from V1 and is only granted to the new role below)
INSERT INTO permissions (code, module, description) VALUES
('PLM_VIEW',             'PLM', 'View engineering change orders and product lifecycle'),
('ECO_CREATE',           'PLM', 'Create, edit, submit, cancel and implement engineering change orders'),
('ECO_APPROVE',          'PLM', 'Approve or return engineering change orders in review'),
('LIFECYCLE_MANAGE',     'PLM', 'Move products through lifecycle states'),
('SPECIFICATION_MANAGE', 'PLM', 'Maintain product specifications (used by the document module)');

INSERT INTO roles (code, name, description, system_role)
VALUES ('PLM_ENGINEER', 'PLM Engineer', 'Engineering change orders, BOM revisions and product lifecycle', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('PLM_ENGINEER', 'ADMIN')
  AND p.code IN ('PLM_VIEW','ECO_CREATE','LIFECYCLE_MANAGE','DRAWING_MANAGE','SPECIFICATION_MANAGE')
ON CONFLICT DO NOTHING;

-- The engineer works with BOM drafts and sees products; approval stays with whoever already approves BOMs.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'PLM_ENGINEER'
  AND p.code IN ('BOM_VIEW','BOM_CREATE','BOM_UPDATE','PRODUCT_VIEW','DASHBOARD_VIEW','REPORT_VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p.id FROM role_permissions rp
JOIN permissions bom ON bom.id = rp.permission_id AND bom.code = 'BOM_APPROVE'
JOIN permissions p ON p.code IN ('ECO_APPROVE','PLM_VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND p.code = 'ECO_APPROVE'
ON CONFLICT DO NOTHING;
