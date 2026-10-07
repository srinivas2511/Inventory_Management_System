-- E2E fix batch 3 (D-17): opening stock never reached the GL because OPENING_BALANCE had no account assignment
-- (the bridge raised "no GL account assignment for OPENING_BALANCE/MATERIAL" for every opening posting).
-- Standard treatment: the opening stock is debited to the inventory account and credited to an Opening Balance Equity
-- account, which is closed against the other opening balances (AP, AR, bank, fixed assets) when the books are opened.
SET search_path = ims;

INSERT INTO gl_accounts (account_code, name, account_group_id, account_type, normal_balance, allow_direct_posting, company_id)
VALUES ('3901', 'Opening Balance Equity', (SELECT id FROM gl_account_groups WHERE group_code = '30'), 'EQUITY', 'CREDIT', TRUE, 1);

-- No cost centre: the credit is an equity clearing entry, not an expense of any cost centre.
INSERT INTO inventory_account_assignments
    (company_id, transaction_type, item_type, valuation_class, gl_account_debit_id, gl_account_credit_id, cost_centre_id)
VALUES (1, 'OPENING_BALANCE', 'MATERIAL', NULL,
        (SELECT id FROM gl_accounts WHERE account_code = '1401'),
        (SELECT id FROM gl_accounts WHERE account_code = '3901'),
        NULL),
       (1, 'OPENING_BALANCE', 'PRODUCT', NULL,
        (SELECT id FROM gl_accounts WHERE account_code = '1410'),
        (SELECT id FROM gl_accounts WHERE account_code = '3901'),
        NULL);
