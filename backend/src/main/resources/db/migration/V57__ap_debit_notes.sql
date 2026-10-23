-- AP debit notes (AP counterpart to AR credit notes): reduce the amount owed on a supplier invoice.
-- Raised by AP_MANAGE, approved (posted) by DEBIT_NOTE_APPROVE (never the same user).
SET search_path = ims;

-- Track how much of an invoice has been offset by approved debit notes; balance = total - paid - debited.
ALTER TABLE ap_invoices ADD COLUMN debited_amount NUMERIC(18,4) NOT NULL DEFAULT 0;

CREATE TABLE ap_debit_notes (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    debit_note_number   VARCHAR(25)   NOT NULL,
    ap_invoice_id       BIGINT        NOT NULL REFERENCES ap_invoices(id),
    debit_date          DATE          NOT NULL,
    reason              VARCHAR(300)  NOT NULL,
    subtotal            NUMERIC(18,4) NOT NULL CHECK (subtotal > 0),
    tax_total           NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_amount        NUMERIC(18,4) NOT NULL,
    carrying_inr        NUMERIC(18,4) NOT NULL DEFAULT 0,
    status              VARCHAR(10)   NOT NULL DEFAULT 'DRAFT'
                            CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED')),
    journal_header_id   BIGINT        REFERENCES journal_headers(id),
    approved_by         BIGINT,
    approved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by          BIGINT,
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by          BIGINT,
    version             BIGINT        NOT NULL DEFAULT 0,
    company_id          BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_ap_debit_notes_number UNIQUE (debit_note_number)
);
CREATE INDEX ix_ap_debit_notes_invoice ON ap_debit_notes (ap_invoice_id);

INSERT INTO permissions (code, module, description) VALUES
('DEBIT_NOTE_APPROVE', 'FINANCE', 'Approve and post an AP debit note (never the user who raised it)')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
 WHERE r.code IN ('ADMIN', 'FINANCE_MANAGER') AND p.code = 'DEBIT_NOTE_APPROVE'
ON CONFLICT DO NOTHING;
