-- R14 WMS: zones, bins (each bin is backed by a warehouse_location so the ledger keeps one location key),
-- transfer orders, pick waves, cycle counts, WMS permissions and the WAREHOUSE_MANAGER role.
-- Every table carries the DESIGN section 1.1 standard columns.
SET search_path = ims;

CREATE TABLE warehouse_zones (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    warehouse_id  BIGINT       NOT NULL REFERENCES warehouses(id),
    zone_code     VARCHAR(10)  NOT NULL,
    name          VARCHAR(60)  NOT NULL,
    zone_type     VARCHAR(15)  NOT NULL CHECK (zone_type IN ('BULK','PICKING','RECEIVING','DISPATCH','QC','STAGING')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    BIGINT,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by    BIGINT,
    version       BIGINT       NOT NULL DEFAULT 0,
    company_id    BIGINT       NOT NULL DEFAULT 1,
    CONSTRAINT uq_zone_code UNIQUE (warehouse_id, zone_code)
);

CREATE TABLE warehouse_bins (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    zone_id                BIGINT         NOT NULL REFERENCES warehouse_zones(id),
    location_id            BIGINT         NOT NULL REFERENCES warehouse_locations(id),
    bin_code               VARCHAR(20)    NOT NULL,
    aisle                  VARCHAR(10),
    rack                   VARCHAR(10),
    level                  VARCHAR(10),
    bin_position           VARCHAR(10),
    bin_type               VARCHAR(15)    NOT NULL DEFAULT 'STANDARD' CHECK (bin_type IN ('STANDARD','OVERSIZED','QUARANTINE','QC')),
    max_weight_kg          NUMERIC(10,3)  CHECK (max_weight_kg IS NULL OR max_weight_kg > 0),
    max_volume_m3          NUMERIC(10,4)  CHECK (max_volume_m3 IS NULL OR max_volume_m3 > 0),
    current_weight_kg      NUMERIC(10,3)  NOT NULL DEFAULT 0,
    allowed_material_types VARCHAR(200),
    active                 BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by             BIGINT,
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by             BIGINT,
    version                BIGINT         NOT NULL DEFAULT 0,
    company_id             BIGINT         NOT NULL DEFAULT 1,
    CONSTRAINT uq_bin_zone_code UNIQUE (zone_id, bin_code),
    CONSTRAINT uq_bin_location UNIQUE (location_id)
);
CREATE INDEX ix_bin_zone ON warehouse_bins (zone_id);

CREATE TABLE bin_item_assignments (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bin_id       BIGINT      NOT NULL REFERENCES warehouse_bins(id),
    material_id  BIGINT      REFERENCES materials(id),
    product_id   BIGINT      REFERENCES products(id),
    is_primary   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   BIGINT,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by   BIGINT,
    version      BIGINT      NOT NULL DEFAULT 0,
    company_id   BIGINT      NOT NULL DEFAULT 1,
    CONSTRAINT chk_bia_one_item CHECK ((material_id IS NOT NULL) <> (product_id IS NOT NULL))
);
CREATE UNIQUE INDEX ux_bin_item ON bin_item_assignments (bin_id, COALESCE(material_id, 0), COALESCE(product_id, 0));
CREATE INDEX ix_bia_material ON bin_item_assignments (material_id) WHERE material_id IS NOT NULL;
CREATE INDEX ix_bia_product  ON bin_item_assignments (product_id)  WHERE product_id IS NOT NULL;

CREATE TABLE putaway_strategy_rules (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    warehouse_id   BIGINT      NOT NULL REFERENCES warehouses(id),
    material_type  VARCHAR(40),
    strategy       VARCHAR(15) NOT NULL CHECK (strategy IN ('FIXED_BIN','NEAREST_EMPTY','BULK_FIRST')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     BIGINT,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by     BIGINT,
    version        BIGINT      NOT NULL DEFAULT 0,
    company_id     BIGINT      NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX ux_putaway_rule ON putaway_strategy_rules (warehouse_id, COALESCE(material_type, ''));

CREATE TABLE pick_waves (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    wave_number  VARCHAR(20) NOT NULL,
    warehouse_id BIGINT      REFERENCES warehouses(id),
    status       VARCHAR(12) NOT NULL DEFAULT 'CREATED' CHECK (status IN ('CREATED','RELEASED','COMPLETED','CANCELLED')),
    released_at  TIMESTAMPTZ,
    released_by  BIGINT,
    notes        VARCHAR(300),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   BIGINT,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by   BIGINT,
    version      BIGINT      NOT NULL DEFAULT 0,
    company_id   BIGINT      NOT NULL DEFAULT 1,
    CONSTRAINT uq_wave_number UNIQUE (wave_number)
);

CREATE TABLE pick_wave_orders (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    wave_id        BIGINT      NOT NULL REFERENCES pick_waves(id),
    sales_order_id BIGINT      NOT NULL REFERENCES sales_orders(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     BIGINT,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by     BIGINT,
    version        BIGINT      NOT NULL DEFAULT 0,
    company_id     BIGINT      NOT NULL DEFAULT 1,
    CONSTRAINT uq_wave_order UNIQUE (wave_id, sales_order_id)
);

CREATE TABLE transfer_orders (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    to_number              VARCHAR(20) NOT NULL,
    warehouse_id           BIGINT      NOT NULL REFERENCES warehouses(id),
    movement_type          VARCHAR(18) NOT NULL CHECK (movement_type IN ('PUTAWAY','PICKING','INTERNAL_TRANSFER','CYCLE_COUNT','CROSS_DOCK')),
    source_document_type   VARCHAR(20) CHECK (source_document_type IS NULL OR source_document_type IN ('GOODS_RECEIPT','PRODUCTION_ORDER','SALES_ORDER','CYCLE_COUNT')),
    source_document_id     BIGINT,
    cross_dock_target_type VARCHAR(20) CHECK (cross_dock_target_type IS NULL OR cross_dock_target_type IN ('PRODUCTION_ORDER','SALES_ORDER')),
    cross_dock_target_id   BIGINT,
    wave_id                BIGINT      REFERENCES pick_waves(id),
    status                 VARCHAR(12) NOT NULL DEFAULT 'CREATED' CHECK (status IN ('CREATED','IN_PROGRESS','COMPLETED','CANCELLED')),
    assigned_to            BIGINT      REFERENCES users(id),
    completed_at           TIMESTAMPTZ,
    notes                  VARCHAR(500),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             BIGINT,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by             BIGINT,
    version                BIGINT      NOT NULL DEFAULT 0,
    company_id             BIGINT      NOT NULL DEFAULT 1,
    CONSTRAINT uq_to_number UNIQUE (to_number)
);
CREATE INDEX ix_to_source ON transfer_orders (source_document_type, source_document_id);
CREATE INDEX ix_to_status ON transfer_orders (status, movement_type);
CREATE INDEX ix_to_wave   ON transfer_orders (wave_id) WHERE wave_id IS NOT NULL;

CREATE TABLE transfer_order_lines (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transfer_order_id BIGINT        NOT NULL REFERENCES transfer_orders(id),
    line_no           INT           NOT NULL,
    item_type         VARCHAR(10)   NOT NULL CHECK (item_type IN ('MATERIAL','PRODUCT')),
    material_id       BIGINT        REFERENCES materials(id),
    product_id        BIGINT        REFERENCES products(id),
    batch_id          BIGINT,
    stock_status      VARCHAR(20),
    from_bin_id       BIGINT        REFERENCES warehouse_bins(id),
    to_bin_id         BIGINT        REFERENCES warehouse_bins(id),
    from_location_id  BIGINT        NOT NULL REFERENCES warehouse_locations(id),
    to_location_id    BIGINT        REFERENCES warehouse_locations(id),
    planned_qty       NUMERIC(18,3) NOT NULL CHECK (planned_qty > 0),
    confirmed_qty     NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (confirmed_qty >= 0),
    status            VARCHAR(10)   NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','PARTIAL','CONFIRMED','CANCELLED')),
    confirmed_at      TIMESTAMPTZ,
    confirmed_by      BIGINT,
    warning           VARCHAR(300),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        BIGINT,
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by        BIGINT,
    version           BIGINT        NOT NULL DEFAULT 0,
    company_id        BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_tol_line UNIQUE (transfer_order_id, line_no),
    CONSTRAINT chk_tol_item CHECK (
        (item_type = 'MATERIAL' AND material_id IS NOT NULL AND product_id IS NULL) OR
        (item_type = 'PRODUCT'  AND product_id  IS NOT NULL AND material_id IS NULL)),
    CONSTRAINT chk_tol_confirmed CHECK (confirmed_qty <= planned_qty)
);

CREATE TABLE cycle_counts (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    count_number  VARCHAR(20) NOT NULL,
    warehouse_id  BIGINT      NOT NULL REFERENCES warehouses(id),
    count_type    VARCHAR(10) NOT NULL CHECK (count_type IN ('ZONE','ABC','RANDOM')),
    zone_id       BIGINT      REFERENCES warehouse_zones(id),
    abc_class     VARCHAR(1)  CHECK (abc_class IS NULL OR abc_class IN ('A','B','C')),
    sample_size   INT         CHECK (sample_size IS NULL OR sample_size > 0),
    status        VARCHAR(12) NOT NULL DEFAULT 'CREATED' CHECK (status IN ('CREATED','IN_PROGRESS','REVIEW','POSTED','CANCELLED')),
    reviewed_at   TIMESTAMPTZ,
    posted_at     TIMESTAMPTZ,
    notes         VARCHAR(300),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    BIGINT,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by    BIGINT,
    version       BIGINT      NOT NULL DEFAULT 0,
    company_id    BIGINT      NOT NULL DEFAULT 1,
    CONSTRAINT uq_count_number UNIQUE (count_number)
);

CREATE TABLE cycle_count_lines (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cycle_count_id  BIGINT        NOT NULL REFERENCES cycle_counts(id),
    line_no         INT           NOT NULL,
    bin_id          BIGINT        REFERENCES warehouse_bins(id),
    location_id     BIGINT        NOT NULL REFERENCES warehouse_locations(id),
    item_type       VARCHAR(10)   NOT NULL CHECK (item_type IN ('MATERIAL','PRODUCT')),
    material_id     BIGINT        REFERENCES materials(id),
    product_id      BIGINT        REFERENCES products(id),
    batch_id        BIGINT,
    stock_status    VARCHAR(20)   NOT NULL,
    system_qty      NUMERIC(18,3) NOT NULL,
    counted_qty     NUMERIC(18,3) CHECK (counted_qty IS NULL OR counted_qty >= 0),
    variance_qty    NUMERIC(18,3),
    adjustment_id   BIGINT        REFERENCES stock_adjustments(id),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT        NOT NULL DEFAULT 0,
    company_id      BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_ccl_line UNIQUE (cycle_count_id, line_no),
    CONSTRAINT chk_ccl_item CHECK (
        (item_type = 'MATERIAL' AND material_id IS NOT NULL AND product_id IS NULL) OR
        (item_type = 'PRODUCT'  AND product_id  IS NOT NULL AND material_id IS NULL))
);

-- Settings
INSERT INTO system_settings (key, value, value_type, description) VALUES
('wms.bin_capacity.enforce',      'false',        'BOOLEAN', 'false: bin capacity breaches only warn; true: a breach rejects the put-away or transfer (decision #15)'),
('wms.putaway.default_strategy',  'FIXED_BIN',    'STRING',  'Put-away strategy when no warehouse rule matches: FIXED_BIN, NEAREST_EMPTY or BULK_FIRST'),
('wms.putaway.auto_create',       'true',         'BOOLEAN', 'Create a put-away transfer order automatically when a goods receipt is posted'),
('wms.picking.auto_create',       'true',         'BOOLEAN', 'Create picking transfer orders automatically when a sales order is approved'),
('wms.cycle_count.abc_a_pct',     '80',           'DECIMAL', 'Cumulative consumption value share (percent) that makes up class A items'),
('wms.cycle_count.abc_b_pct',     '95',           'DECIMAL', 'Cumulative consumption value share (percent) that makes up class A and B items'),
('wms.cycle_count.abc_lookback_days', '365',      'INTEGER', 'Days of consumption history used to classify items A, B or C')
ON CONFLICT (key) DO NOTHING;

-- Permissions
INSERT INTO permissions (code, module, description) VALUES
('WMS_VIEW',           'WMS', 'View zones, bins, bin stock, transfer orders, pick waves and cycle counts'),
('BIN_MANAGE',         'WMS', 'Maintain zones, bins, item-to-bin assignments and put-away rules'),
('PUTAWAY_MANAGE',     'WMS', 'Create, assign and confirm put-away and cross-dock transfer orders'),
('PICKING_MANAGE',     'WMS', 'Create, assign and confirm picking transfer orders'),
('WAVE_MANAGE',        'WMS', 'Create, release and cancel pick waves'),
('TRANSFER_MANAGE',    'WMS', 'Create, assign and confirm internal transfer orders'),
('CYCLE_COUNT_MANAGE', 'WMS', 'Create, count, review and post cycle counts');

INSERT INTO roles (code, name, description, system_role)
VALUES ('WAREHOUSE_MANAGER', 'Warehouse Manager', 'Bins, put-away, picking, waves, transfers and cycle counts', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('WAREHOUSE_MANAGER', 'STORE_MANAGER', 'ADMIN')
  AND p.code IN ('WMS_VIEW','BIN_MANAGE','PUTAWAY_MANAGE','PICKING_MANAGE','WAVE_MANAGE','TRANSFER_MANAGE','CYCLE_COUNT_MANAGE')
ON CONFLICT DO NOTHING;

-- Store operators execute tasks: they see the warehouse and confirm put-away, picking and transfers.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'STORE_OPERATOR' AND p.code IN ('WMS_VIEW','PUTAWAY_MANAGE','PICKING_MANAGE','TRANSFER_MANAGE')
ON CONFLICT DO NOTHING;

-- The warehouse manager also needs everything a store manager has for the underlying stock work.
INSERT INTO role_permissions (role_id, permission_id)
SELECT wm.id, rp.permission_id
FROM roles wm
JOIN roles sm ON sm.code = 'STORE_MANAGER'
JOIN role_permissions rp ON rp.role_id = sm.id
WHERE wm.code = 'WAREHOUSE_MANAGER'
ON CONFLICT DO NOTHING;
