-- R10-6.1: Internal orders for project-based cost tracking (capex, maintenance, project).
SET search_path = ims;

CREATE TABLE internal_orders (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_number               VARCHAR(20)   NOT NULL,
    description                VARCHAR(200)  NOT NULL,
    order_type                 VARCHAR(20)   NOT NULL DEFAULT 'CAPEX',
    responsible_cost_centre_id BIGINT        REFERENCES cost_centres(id),
    budget_amount              NUMERIC(18,4) NOT NULL DEFAULT 0,
    actual_amount              NUMERIC(18,4) NOT NULL DEFAULT 0,
    status                     VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    settlement_cost_centre_id  BIGINT        REFERENCES cost_centres(id),
    settled_amount             NUMERIC(18,4),
    settled_at                 TIMESTAMPTZ,
    settled_by                 BIGINT        REFERENCES users(id),
    settlement_journal_id      BIGINT        REFERENCES journal_headers(id),
    start_date                 DATE,
    end_date                   DATE,
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by                 BIGINT,
    updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by                 BIGINT,
    version                    BIGINT        NOT NULL DEFAULT 0,
    company_id                 BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_internal_orders_number UNIQUE (order_number),
    CONSTRAINT chk_internal_orders_type   CHECK (order_type IN ('CAPEX','MAINTENANCE','PROJECT','OTHER')),
    CONSTRAINT chk_internal_orders_status CHECK (status IN ('OPEN','SETTLED','CLOSED'))
);

-- Cost assignment: tag a journal line to an internal order
ALTER TABLE journal_lines
    ADD COLUMN internal_order_id BIGINT REFERENCES internal_orders(id);
CREATE INDEX ix_journal_lines_internal_order ON journal_lines(internal_order_id) WHERE internal_order_id IS NOT NULL;

-- Permissions
INSERT INTO permissions (code, module, description) VALUES
    ('INTERNAL_ORDER_VIEW',   'CONTROLLING', 'View internal orders and their collected costs'),
    ('INTERNAL_ORDER_MANAGE', 'CONTROLLING', 'Create, update and settle internal orders');

INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r, permissions p
    WHERE r.code IN ('ADMIN', 'CONTROLLER', 'FINANCE_MANAGER')
      AND p.code IN ('INTERNAL_ORDER_VIEW', 'INTERNAL_ORDER_MANAGE')
    ON CONFLICT DO NOTHING;
