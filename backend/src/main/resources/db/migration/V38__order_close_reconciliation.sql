-- Production order close: per-issue-line material reconciliation, the MATERIAL_RETURN journal, and the scrap
-- already charged to WIP (so the variance journal does not charge it again).
SET search_path = ims;

-- Each issued line is used up in three ways, tracked per line so consumption, returns and write-offs always come
-- out of the batches the order itself issued (the WIP balance of a batch is shared by every order that issued it).
ALTER TABLE material_issue_lines
    ADD COLUMN consumed_qty    NUMERIC(18,3) NOT NULL DEFAULT 0,
    ADD COLUMN returned_qty    NUMERIC(18,3) NOT NULL DEFAULT 0,
    ADD COLUMN written_off_qty NUMERIC(18,3) NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_mil_used CHECK (
        consumed_qty >= 0 AND returned_qty >= 0 AND written_off_qty >= 0
        AND consumed_qty + returned_qty + written_off_qty <= quantity);

-- Returning unused material to stock reverses the issue: Dr 1401 Raw Material Stock / Cr 1501 WIP.
INSERT INTO inventory_account_assignments
    (company_id, transaction_type, item_type, valuation_class, gl_account_debit_id, gl_account_credit_id, cost_centre_id)
VALUES (1, 'MATERIAL_RETURN', 'MATERIAL', NULL,
        (SELECT id FROM gl_accounts WHERE account_code = '1401'),
        (SELECT id FROM gl_accounts WHERE account_code = '1501'),
        (SELECT id FROM cost_centres WHERE cost_centre_code = 'CC-PROD-01'));

-- Scrap recorded on the operations is already relieved from WIP (Dr 5202 / Cr 1501) at the time it is recorded; the
-- variance journal posts only what that did not cover.
ALTER TABLE production_order_variances
    ADD COLUMN scrap_relieved NUMERIC(18,4) NOT NULL DEFAULT 0;
