-- R11-1.1 / R11-4.x: MRP runs, proposals, demand forecasts, planning parameters, MRP permissions and settings.
SET search_path = ims;

CREATE TABLE mrp_runs (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_number            VARCHAR(20)  NOT NULL,
    initiated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at          TIMESTAMPTZ,
    planning_horizon_days INT          NOT NULL DEFAULT 90 CHECK (planning_horizon_days BETWEEN 1 AND 730),
    include_safety_stock  BOOLEAN      NOT NULL DEFAULT TRUE,
    status                VARCHAR(12)  NOT NULL DEFAULT 'INITIATED'
                              CHECK (status IN ('INITIATED','RUNNING','COMPLETED','FAILED')),
    initiated_by          BIGINT       NOT NULL,
    total_proposals       INT,
    capacity_overloads    INT,
    notes                 VARCHAR(1000),
    error_message         VARCHAR(500),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            BIGINT,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by            BIGINT,
    version               BIGINT       NOT NULL DEFAULT 0,
    company_id            BIGINT       NOT NULL DEFAULT 1,
    CONSTRAINT uq_mrp_runs_number UNIQUE (run_number)
);

CREATE TABLE mrp_proposals (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mrp_run_id              BIGINT        NOT NULL REFERENCES mrp_runs(id),
    proposal_type           VARCHAR(12)   NOT NULL CHECK (proposal_type IN ('PURCHASE','PRODUCTION')),
    material_id             BIGINT        REFERENCES materials(id),
    product_id              BIGINT        REFERENCES products(id),
    bom_id                  BIGINT        REFERENCES bom(id),
    uom                     VARCHAR(10),
    required_qty            NUMERIC(18,3) NOT NULL CHECK (required_qty > 0),
    proposed_qty            NUMERIC(18,3) NOT NULL CHECK (proposed_qty > 0),
    need_date               DATE          NOT NULL,
    proposed_order_date     DATE          NOT NULL,
    lead_time_days          INT           NOT NULL DEFAULT 0 CHECK (lead_time_days >= 0),
    lead_time_adjusted      BOOLEAN       NOT NULL DEFAULT FALSE,
    source_demand_type      VARCHAR(20),
    source_demand_id        BIGINT,
    lot_size_rule           VARCHAR(10),
    machine_hours_required  NUMERIC(18,3),
    capacity_overload       BOOLEAN       NOT NULL DEFAULT FALSE,
    status                  VARCHAR(10)   NOT NULL DEFAULT 'OPEN'
                                CHECK (status IN ('OPEN','CONVERTED','DISCARDED')),
    converted_document_type VARCHAR(20),
    converted_document_id   BIGINT,
    converted_by            BIGINT,
    converted_at            TIMESTAMPTZ,
    discard_reason          VARCHAR(300),
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by              BIGINT,
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by              BIGINT,
    version                 BIGINT        NOT NULL DEFAULT 0,
    company_id              BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT chk_mrp_proposal_item CHECK (
        (proposal_type = 'PURCHASE'   AND material_id IS NOT NULL AND product_id IS NULL) OR
        (proposal_type = 'PRODUCTION' AND product_id  IS NOT NULL AND material_id IS NULL AND bom_id IS NOT NULL)),
    CONSTRAINT chk_mrp_proposal_dates CHECK (proposed_order_date <= need_date)
);
CREATE INDEX ix_mrp_proposals_run    ON mrp_proposals (mrp_run_id, proposal_type);
CREATE INDEX ix_mrp_proposals_status ON mrp_proposals (status) WHERE status = 'OPEN';
CREATE INDEX ix_mrp_proposals_material ON mrp_proposals (material_id) WHERE material_id IS NOT NULL;
CREATE INDEX ix_mrp_proposals_product  ON mrp_proposals (product_id)  WHERE product_id  IS NOT NULL;

CREATE TABLE demand_forecasts (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id   BIGINT        NOT NULL REFERENCES products(id),
    target_date  DATE          NOT NULL,
    quantity     NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
    notes        VARCHAR(300),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by   BIGINT,
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by   BIGINT,
    version      BIGINT        NOT NULL DEFAULT 0,
    company_id   BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_demand_forecast UNIQUE (product_id, target_date)
);
CREATE INDEX ix_demand_forecasts_date ON demand_forecasts (target_date);

-- Per-item planning parameters. An item without a row plans as MTS with EXACT lot sizing and default lead times.
CREATE TABLE mrp_planning_params (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_type           VARCHAR(10)   NOT NULL CHECK (item_type IN ('MATERIAL','PRODUCT')),
    material_id         BIGINT        REFERENCES materials(id),
    product_id          BIGINT        REFERENCES products(id),
    planning_strategy   VARCHAR(3)    NOT NULL DEFAULT 'MTS' CHECK (planning_strategy IN ('MTS','MTO')),
    lot_size_rule       VARCHAR(10)   NOT NULL DEFAULT 'EXACT' CHECK (lot_size_rule IN ('EXACT','FIXED','MIN_MAX','EOQ')),
    fixed_lot_qty       NUMERIC(18,3) CHECK (fixed_lot_qty > 0),
    min_lot_qty         NUMERIC(18,3) CHECK (min_lot_qty >= 0),
    max_lot_qty         NUMERIC(18,3) CHECK (max_lot_qty > 0),
    eoq_annual_demand   NUMERIC(18,3) CHECK (eoq_annual_demand > 0),
    eoq_order_cost      NUMERIC(18,4) CHECK (eoq_order_cost > 0),
    eoq_holding_cost    NUMERIC(18,4) CHECK (eoq_holding_cost > 0),
    lead_time_days      INT           CHECK (lead_time_days >= 0),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by          BIGINT,
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by          BIGINT,
    version             BIGINT        NOT NULL DEFAULT 0,
    company_id          BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT chk_mrp_param_item CHECK (
        (item_type = 'MATERIAL' AND material_id IS NOT NULL AND product_id IS NULL) OR
        (item_type = 'PRODUCT'  AND product_id  IS NOT NULL AND material_id IS NULL)),
    CONSTRAINT chk_mrp_param_lot CHECK (
        (lot_size_rule <> 'FIXED'   OR fixed_lot_qty IS NOT NULL) AND
        (lot_size_rule <> 'MIN_MAX' OR (min_lot_qty IS NOT NULL OR max_lot_qty IS NOT NULL)) AND
        (lot_size_rule <> 'EOQ'     OR (eoq_annual_demand IS NOT NULL AND eoq_order_cost IS NOT NULL
                                        AND eoq_holding_cost IS NOT NULL)))
);
CREATE UNIQUE INDEX ux_mrp_param_material ON mrp_planning_params (material_id) WHERE material_id IS NOT NULL;
CREATE UNIQUE INDEX ux_mrp_param_product  ON mrp_planning_params (product_id)  WHERE product_id  IS NOT NULL;

-- Settings
INSERT INTO system_settings (key, value, value_type, description) VALUES
('mrp.default_horizon_days',        '90', 'INTEGER', 'Default MRP planning horizon in days'),
('mrp.default_purchase_lead_days',  '7',  'INTEGER', 'Purchase lead time when the material has no preferred supplier lead time'),
('mrp.default_production_lead_days','3',  'INTEGER', 'Production lead time when the product has no planning parameter lead time'),
('mrp.machine_hours_per_week',      '80', 'INTEGER', 'Rough-cut capacity: available hours per machine per week'),
('production.monthly_target',       '0',  'INTEGER', 'Plant-wide monthly production target in pieces (0 = not set); shown on the production dashboard')
ON CONFLICT (key) DO NOTHING;

-- Permissions
INSERT INTO permissions (code, module, description) VALUES
('MRP_VIEW',            'MRP', 'View MRP runs, proposals, forecasts and planning parameters'),
('MRP_RUN',             'MRP', 'Start MRP runs and discard proposals'),
('DEMAND_PLAN',         'MRP', 'Maintain demand forecasts and item planning parameters'),
('PURCHASE_PROPOSAL',   'MRP', 'Convert MRP purchase proposals into purchase requisitions'),
('PRODUCTION_PROPOSAL', 'MRP', 'Convert MRP production proposals into production orders');

INSERT INTO roles (code, name, description, system_role)
VALUES ('PLANNER', 'Planner', 'Demand planning, MRP runs and proposal conversion', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'PLANNER'
  AND p.code IN ('MRP_VIEW','MRP_RUN','DEMAND_PLAN','PURCHASE_PROPOSAL','PRODUCTION_PROPOSAL','REPORT_VIEW','DASHBOARD_VIEW')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('MRP_VIEW','MRP_RUN','DEMAND_PLAN','PURCHASE_PROPOSAL','PRODUCTION_PROPOSAL')
ON CONFLICT DO NOTHING;
