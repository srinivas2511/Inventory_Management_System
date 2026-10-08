-- R9-7.6 / FIN-009: multi-currency posting. INR is the functional currency: every journal line is in INR. An invoice keeps
-- the currency and the rate it was booked at; a settlement at another rate books the realised exchange difference.
-- Rate method (decision #14): the rate of the fiscal period that contains the transaction date.
SET search_path = ims;

ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS currency_code VARCHAR(3) NOT NULL DEFAULT 'INR';
ALTER TABLE customers ADD COLUMN IF NOT EXISTS currency_code VARCHAR(3) NOT NULL DEFAULT 'INR';

ALTER TABLE ap_invoices ADD COLUMN IF NOT EXISTS exchange_rate NUMERIC(12,6) NOT NULL DEFAULT 1;
ALTER TABLE ap_invoices ADD COLUMN IF NOT EXISTS total_inr     NUMERIC(18,4) NOT NULL DEFAULT 0;
ALTER TABLE ar_invoices ADD COLUMN IF NOT EXISTS exchange_rate NUMERIC(12,6) NOT NULL DEFAULT 1;
ALTER TABLE ar_invoices ADD COLUMN IF NOT EXISTS total_inr     NUMERIC(18,4) NOT NULL DEFAULT 0;
-- every invoice before this migration is in INR, so its INR total is its total
UPDATE ap_invoices SET total_inr = total_amount WHERE total_inr = 0;
UPDATE ar_invoices SET total_inr = total_amount WHERE total_inr = 0;

ALTER TABLE ap_payments ADD COLUMN IF NOT EXISTS exchange_rate NUMERIC(12,6) NOT NULL DEFAULT 1;
ALTER TABLE ap_payments ADD COLUMN IF NOT EXISTS carrying_inr  NUMERIC(18,4) NOT NULL DEFAULT 0;
ALTER TABLE ap_payments ADD COLUMN IF NOT EXISTS fx_difference NUMERIC(18,4) NOT NULL DEFAULT 0;
ALTER TABLE ar_receipts ADD COLUMN IF NOT EXISTS exchange_rate NUMERIC(12,6) NOT NULL DEFAULT 1;
ALTER TABLE ar_receipts ADD COLUMN IF NOT EXISTS carrying_inr  NUMERIC(18,4) NOT NULL DEFAULT 0;
ALTER TABLE ar_receipts ADD COLUMN IF NOT EXISTS fx_difference NUMERIC(18,4) NOT NULL DEFAULT 0;
UPDATE ap_payments SET carrying_inr = amount WHERE carrying_inr = 0;
UPDATE ar_receipts SET carrying_inr = amount WHERE carrying_inr = 0;

INSERT INTO gl_accounts (account_code, name, account_group_id, account_type, normal_balance, company_id) VALUES
('4901', 'Realised Exchange Gain', (SELECT id FROM gl_account_groups WHERE group_code = '40'), 'REVENUE', 'CREDIT', 1),
('5208', 'Realised Exchange Loss', (SELECT id FROM gl_account_groups WHERE group_code = '52'), 'EXPENSE', 'DEBIT',  1)
ON CONFLICT (account_code) DO NOTHING;

INSERT INTO permissions (code, module, description) VALUES
('EXCHANGE_RATE_MANAGE', 'FINANCE', 'Maintain the exchange rates used to convert foreign-currency documents')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
 WHERE r.code IN ('ADMIN', 'FINANCE_MANAGER') AND p.code = 'EXCHANGE_RATE_MANAGE'
ON CONFLICT DO NOTHING;
