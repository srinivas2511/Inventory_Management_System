-- R10-3.1: Production order variance results + GL accounts for the variance journal.
SET search_path = ims;

CREATE TABLE production_order_variances (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    production_order_id   BIGINT        NOT NULL REFERENCES production_orders(id),
    product_id            BIGINT        NOT NULL REFERENCES products(id),
    estimate_id           BIGINT        REFERENCES product_cost_estimates(id),
    good_qty              NUMERIC(18,3) NOT NULL DEFAULT 0,
    standard_cost         NUMERIC(18,4) NOT NULL DEFAULT 0,
    actual_material_cost  NUMERIC(18,4) NOT NULL DEFAULT 0,
    actual_labour_cost    NUMERIC(18,4) NOT NULL DEFAULT 0,
    actual_machine_cost   NUMERIC(18,4) NOT NULL DEFAULT 0,
    actual_overhead_cost  NUMERIC(18,4) NOT NULL DEFAULT 0,
    actual_cost           NUMERIC(18,4) NOT NULL DEFAULT 0,
    price_variance        NUMERIC(18,4) NOT NULL DEFAULT 0,
    quantity_variance     NUMERIC(18,4) NOT NULL DEFAULT 0,
    efficiency_variance   NUMERIC(18,4) NOT NULL DEFAULT 0,
    other_variance        NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_variance        NUMERIC(18,4) NOT NULL DEFAULT 0,
    status                VARCHAR(12)   NOT NULL CHECK (status IN ('POSTED','NO_VARIANCE','NO_ESTIMATE')),
    journal_header_id     BIGINT        REFERENCES journal_headers(id),
    calculated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    calculated_by         BIGINT,
    company_id            BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_variance_production_order UNIQUE (production_order_id)
);
CREATE INDEX ix_variances_product ON production_order_variances(product_id);

INSERT INTO gl_accounts (account_code, name, account_group_id, account_type, normal_balance, company_id) VALUES
('5204', 'Production Variance Expense', (SELECT id FROM gl_account_groups WHERE group_code = '52'), 'EXPENSE', 'DEBIT',  1),
('4101', 'Production Variance Income',  (SELECT id FROM gl_account_groups WHERE group_code = '40'), 'REVENUE', 'CREDIT', 1);
