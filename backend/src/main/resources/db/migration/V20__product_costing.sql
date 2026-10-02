-- R10-2.1: Product cost estimates (standard cost) and their breakdown lines.
SET search_path = ims;

CREATE TABLE product_cost_estimates (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    estimate_number     VARCHAR(20)   NOT NULL,
    product_id          BIGINT        NOT NULL REFERENCES products(id),
    bom_id              BIGINT        NOT NULL REFERENCES bom(id),
    cost_centre_id      BIGINT        REFERENCES cost_centres(id),
    estimated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    valid_from          DATE,
    valid_to            DATE,
    material_cost       NUMERIC(18,4) NOT NULL DEFAULT 0,
    labour_cost         NUMERIC(18,4) NOT NULL DEFAULT 0,
    machine_cost        NUMERIC(18,4) NOT NULL DEFAULT 0,
    overhead_cost       NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_standard_cost NUMERIC(18,4) NOT NULL DEFAULT 0,
    cost_lot_size       NUMERIC(18,3) NOT NULL DEFAULT 1 CHECK (cost_lot_size > 0),
    status              VARCHAR(10)   NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','RELEASED','HISTORICAL')),
    released_by         BIGINT,
    released_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by          BIGINT,
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by          BIGINT,
    version             BIGINT        NOT NULL DEFAULT 0,
    company_id          BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_cost_estimate_number UNIQUE (estimate_number)
);
CREATE UNIQUE INDEX ux_cost_estimate_one_released ON product_cost_estimates(product_id) WHERE status = 'RELEASED';
CREATE INDEX ix_cost_estimates_product ON product_cost_estimates(product_id);

CREATE TABLE cost_estimate_lines (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    estimate_id      BIGINT        NOT NULL REFERENCES product_cost_estimates(id) ON DELETE CASCADE,
    cost_element     VARCHAR(15)   NOT NULL CHECK (cost_element IN ('MATERIAL','LABOUR','MACHINE','OVERHEAD')),
    item_description VARCHAR(100),
    material_id      BIGINT        REFERENCES materials(id),
    operation_id     BIGINT        REFERENCES operations(id),
    quantity         NUMERIC(18,6),
    uom              VARCHAR(10),
    unit_rate        NUMERIC(18,4),
    amount           NUMERIC(18,4) NOT NULL
);
CREATE INDEX ix_cost_estimate_lines_estimate ON cost_estimate_lines(estimate_id);
