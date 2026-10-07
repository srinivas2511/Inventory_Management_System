-- E2E fix batch 1: the account assignments that the stock adjustment and the finished-goods rejection postings need.
SET search_path = ims;

-- Rejected finished goods are written off the FG books with a SCRAP out-leg: Dr 5202 Scrap Loss / Cr 1410 FG Stock.
-- (The FG receipt already relieved WIP, so the write-off must not credit WIP a second time.)
INSERT INTO inventory_account_assignments
    (company_id, transaction_type, item_type, valuation_class, gl_account_debit_id, gl_account_credit_id, cost_centre_id)
VALUES (1, 'SCRAP', 'PRODUCT', NULL,
        (SELECT id FROM gl_accounts WHERE account_code = '5202'),
        (SELECT id FROM gl_accounts WHERE account_code = '1410'),
        (SELECT id FROM cost_centres WHERE cost_centre_code = 'CC-PROD-01'));

-- A stock-adjustment GAIN (STOCK_ADJUSTMENT, an in-leg) debits the stock account and credits the adjustment account.
-- The original V16 row had the two the wrong way round (Dr 5203 / Cr 1401), which books a gain as a loss.
-- The row becomes an explicit MATERIAL row: the bridge falls back to a type-only lookup, which must find exactly one.
UPDATE inventory_account_assignments
   SET item_type            = 'MATERIAL',
       gl_account_debit_id  = (SELECT id FROM gl_accounts WHERE account_code = '1401'),
       gl_account_credit_id = (SELECT id FROM gl_accounts WHERE account_code = '5203')
 WHERE company_id = 1 AND transaction_type = 'STOCK_ADJUSTMENT' AND item_type IS NULL;

INSERT INTO inventory_account_assignments
    (company_id, transaction_type, item_type, valuation_class, gl_account_debit_id, gl_account_credit_id, cost_centre_id)
VALUES (1, 'STOCK_ADJUSTMENT', 'PRODUCT', NULL,
        (SELECT id FROM gl_accounts WHERE account_code = '1410'),
        (SELECT id FROM gl_accounts WHERE account_code = '5203'),
        (SELECT id FROM cost_centres WHERE cost_centre_code = 'CC-WH-01'));

-- A stock-adjustment LOSS (STOCK_ADJUSTMENT_OUT, an out-leg) debits the adjustment account and credits the stock
-- account. There was no assignment for it, so a negative adjustment never reached the GL.
INSERT INTO inventory_account_assignments
    (company_id, transaction_type, item_type, valuation_class, gl_account_debit_id, gl_account_credit_id, cost_centre_id)
VALUES (1, 'STOCK_ADJUSTMENT_OUT', 'MATERIAL', NULL,
        (SELECT id FROM gl_accounts WHERE account_code = '5203'),
        (SELECT id FROM gl_accounts WHERE account_code = '1401'),
        (SELECT id FROM cost_centres WHERE cost_centre_code = 'CC-WH-01')),
       (1, 'STOCK_ADJUSTMENT_OUT', 'PRODUCT', NULL,
        (SELECT id FROM gl_accounts WHERE account_code = '5203'),
        (SELECT id FROM gl_accounts WHERE account_code = '1410'),
        (SELECT id FROM cost_centres WHERE cost_centre_code = 'CC-WH-01'));
