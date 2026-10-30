-- V91: Phase 13/14 depth — job work charges, service order confirmations
SET search_path TO ims;

-- Job work charges (sub-contractor service invoices/charges recorded per order)
CREATE TABLE IF NOT EXISTS job_work_charges (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    jwo_id          BIGINT NOT NULL REFERENCES job_work_orders(id) ON DELETE CASCADE,
    charge_desc     VARCHAR(300) NOT NULL,
    amount          NUMERIC(18,4) NOT NULL DEFAULT 0,
    charge_date     DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL DEFAULT 1,
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS ix_jw_charges_jwo ON job_work_charges(jwo_id);

-- Service order confirmations (records each service delivery confirmation)
CREATE TABLE IF NOT EXISTS service_order_confirmations (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    so_id           BIGINT NOT NULL REFERENCES service_orders(id) ON DELETE CASCADE,
    confirmed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_by    BIGINT NOT NULL DEFAULT 1,
    remarks         VARCHAR(500),
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS ix_so_confirmations_so ON service_order_confirmations(so_id);
