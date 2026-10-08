-- R9-7.4 / FIN-004: AR credit notes against an invoice, with a reversing journal and an optional customer return.
SET search_path = ims;

-- The generated balance of an AR invoice must also subtract what has been credited. A generated expression cannot be
-- altered in place, and the reporting view reads the column, so the view is dropped and recreated around the change.
DROP VIEW IF EXISTS reporting.ar_invoices;
ALTER TABLE ar_invoices DROP COLUMN balance_amount;
ALTER TABLE ar_invoices ADD COLUMN credited_amount NUMERIC(18,4) NOT NULL DEFAULT 0;
ALTER TABLE ar_invoices ADD COLUMN balance_amount NUMERIC(18,4)
    GENERATED ALWAYS AS (total_amount - received_amount - credited_amount) STORED;

CREATE VIEW reporting.ar_invoices AS
    SELECT id, invoice_number, customer_id, dispatch_id, invoice_date, due_date, subtotal, tax_total, total_amount,
           received_amount, credited_amount, balance_amount, status, currency_code FROM ar_invoices;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ims_reporting') THEN
        EXECUTE 'GRANT SELECT ON reporting.ar_invoices TO ims_reporting';
    END IF;
END $$;

CREATE TABLE credit_notes (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credit_note_number        VARCHAR(25)   NOT NULL,
    ar_invoice_id             BIGINT        NOT NULL REFERENCES ar_invoices(id),
    credit_date               DATE          NOT NULL,
    reason                    VARCHAR(300)  NOT NULL,
    subtotal                  NUMERIC(18,4) NOT NULL CHECK (subtotal > 0),
    tax_total                 NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_amount              NUMERIC(18,4) NOT NULL,
    carrying_inr              NUMERIC(18,4) NOT NULL DEFAULT 0,
    status                    VARCHAR(10)   NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED')),
    journal_header_id         BIGINT        REFERENCES journal_headers(id),
    approved_by               BIGINT,
    approved_at               TIMESTAMPTZ,
    return_fg_batch_id        BIGINT        REFERENCES fg_batches(id),
    return_quantity           NUMERIC(18,3),
    return_warehouse_id       BIGINT        REFERENCES warehouses(id),
    return_location_id        BIGINT        REFERENCES warehouse_locations(id),
    return_transaction_number VARCHAR(30),
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by                BIGINT,
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by                BIGINT,
    version                   BIGINT        NOT NULL DEFAULT 0,
    company_id                BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_credit_notes_number UNIQUE (credit_note_number),
    CONSTRAINT chk_credit_note_return CHECK (
        (return_fg_batch_id IS NULL AND return_quantity IS NULL)
        OR (return_fg_batch_id IS NOT NULL AND return_quantity > 0 AND return_warehouse_id IS NOT NULL
            AND return_location_id IS NOT NULL))
);
CREATE INDEX ix_credit_notes_invoice ON credit_notes (ar_invoice_id);

-- A returned finished good is received at the cost the batch carries: Dr FG stock / Cr cost of goods sold, reversing the
-- cost of sales booked at dispatch. The goods arrive in QUARANTINE and wait for re-inspection.
INSERT INTO inventory_account_assignments
    (company_id, transaction_type, item_type, valuation_class, gl_account_debit_id, gl_account_credit_id, cost_centre_id)
VALUES (1, 'CUSTOMER_RETURN', 'PRODUCT', NULL,
        (SELECT id FROM gl_accounts WHERE account_code = '1410'),
        (SELECT id FROM gl_accounts WHERE account_code = '5001'),
        NULL);

INSERT INTO permissions (code, module, description) VALUES
('CREDIT_NOTE_APPROVE', 'FINANCE', 'Approve and post an AR credit note (never the user who raised it)')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
 WHERE r.code IN ('ADMIN', 'FINANCE_MANAGER') AND p.code = 'CREDIT_NOTE_APPROVE'
ON CONFLICT DO NOTHING;
