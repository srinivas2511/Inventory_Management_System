-- R9-7.8: the purchase price variance of a supplier invoice is captured when the invoice is entered (INR, the part of the
-- invoice subtotal above the value still open on its goods receipt), whether or not the invoice later needs an override.
ALTER TABLE ap_invoices ADD COLUMN IF NOT EXISTS price_variance_inr NUMERIC(18,4) NOT NULL DEFAULT 0;
UPDATE ap_invoices SET price_variance_inr = GREATEST(match_variance, 0) WHERE price_variance_inr = 0;
