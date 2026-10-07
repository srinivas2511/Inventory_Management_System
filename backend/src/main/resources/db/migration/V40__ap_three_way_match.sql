-- AP three-way match (business rule 18 / FIN-003): a supplier invoice must reconcile to the goods receipt it bills
-- before it can be opened or paid. The match is recorded on the invoice; an invoice outside tolerance is an EXCEPTION
-- until it is corrected (cancelled and re-entered) or a finance manager with AP_MATCH_OVERRIDE approves the variance.
SET search_path = ims;

ALTER TABLE ap_invoices
    ADD COLUMN match_status          VARCHAR(12)   NOT NULL DEFAULT 'NOT_REQUIRED'
        CONSTRAINT chk_ap_match_status CHECK (match_status IN ('MATCHED','EXCEPTION','OVERRIDDEN','NOT_REQUIRED')),
    ADD COLUMN match_expected_amount NUMERIC(18,4),
    ADD COLUMN match_cleared_amount  NUMERIC(18,4) NOT NULL DEFAULT 0,
    ADD COLUMN match_variance        NUMERIC(18,4) NOT NULL DEFAULT 0,
    ADD COLUMN match_message         VARCHAR(500),
    ADD COLUMN match_override_by     BIGINT,
    ADD COLUMN match_override_at     TIMESTAMPTZ,
    ADD COLUMN match_override_reason VARCHAR(500);

-- Invoices entered before the match existed are not re-judged, but what they billed still counts against their
-- goods receipt so the same receipt cannot be invoiced twice.
UPDATE ap_invoices SET match_cleared_amount = subtotal
 WHERE goods_receipt_id IS NOT NULL AND status <> 'CANCELLED';

CREATE INDEX ix_ap_invoices_goods_receipt ON ap_invoices(goods_receipt_id) WHERE goods_receipt_id IS NOT NULL;

INSERT INTO system_settings (key, value, value_type, description) VALUES
('ap.match.price_tolerance_pct', '0', 'DECIMAL',
    'Percent by which a supplier invoice may exceed the goods-receipt value still open for matching before it becomes a match exception'),
('ap.match.absolute_tolerance', '0', 'DECIMAL',
    'Absolute amount (INR) by which a supplier invoice may exceed the goods-receipt value still open for matching; the larger of this and the percentage applies')
ON CONFLICT (key) DO NOTHING;

-- Purchase price variance: what an approved invoice bills above the received value (GR/IR clears for the received
-- value only). Input tax receivable: the tax an invoice carries, so GR/IR is not debited for it.
INSERT INTO gl_account_groups (group_code, name, account_type, company_id) VALUES
('13', 'Taxes Receivable', 'ASSET', 1)
ON CONFLICT (group_code) DO NOTHING;

INSERT INTO gl_accounts (account_code, name, account_group_id, account_type, normal_balance, company_id) VALUES
('1301', 'Input Tax Credit',          (SELECT id FROM gl_account_groups WHERE group_code = '13'), 'ASSET',   'DEBIT', 1),
('5207', 'Purchase Price Variance',   (SELECT id FROM gl_account_groups WHERE group_code = '52'), 'EXPENSE', 'DEBIT', 1);

INSERT INTO permissions (code, module, description) VALUES
('AP_MATCH_OVERRIDE', 'FINANCE', 'Approve a supplier invoice that is outside the three-way match tolerance (never your own entry)')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('ADMIN', 'FINANCE_MANAGER') AND p.code = 'AP_MATCH_OVERRIDE'
ON CONFLICT DO NOTHING;
