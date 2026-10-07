-- Conversion-cost absorption: what a production order's operations cost to run (labour, machine, overhead at the
-- cost-centre rates) is fixed when the order completes and debited to WIP against "Conversion Cost Absorbed".
SET search_path = ims;

CREATE TABLE production_order_conversion_costs (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    production_order_id BIGINT        NOT NULL REFERENCES production_orders(id),
    cost_centre_id      BIGINT        REFERENCES cost_centres(id),
    actual_hours        NUMERIC(18,6) NOT NULL DEFAULT 0,
    labour_cost         NUMERIC(18,4) NOT NULL DEFAULT 0,
    machine_cost        NUMERIC(18,4) NOT NULL DEFAULT 0,
    overhead_cost       NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_cost          NUMERIC(18,4) NOT NULL DEFAULT 0,
    status              VARCHAR(12)   NOT NULL CHECK (status IN ('NOT_REQUIRED','PENDING','POSTED','FAILED')),
    journal_header_id   BIGINT        REFERENCES journal_headers(id),
    failure_reason      VARCHAR(300),
    frozen_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    posted_at           TIMESTAMPTZ,
    company_id          BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_conversion_production_order UNIQUE (production_order_id)
);
CREATE INDEX ix_conversion_status ON production_order_conversion_costs(status) WHERE status IN ('PENDING','FAILED');

-- Credit-balance clearing account (like 5302 Maintenance Labour Absorbed): it clears against the real labour and
-- overhead expense at period end; its balance is the under- or over-absorbed conversion cost.
INSERT INTO gl_accounts (account_code, name, account_group_id, account_type, normal_balance, company_id) VALUES
('5303', 'Conversion Cost Absorbed', (SELECT id FROM gl_account_groups WHERE group_code = '52'), 'EXPENSE', 'CREDIT', 1);
