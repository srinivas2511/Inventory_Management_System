-- 14.2 Service confirmation: inward (GRN) and invoice
-- Adds service_confirmation_id FK to goods_receipts and sales_invoices.
-- Makes purchase_order_id nullable on goods_receipts to support service-based GRNs
-- that are not backed by a purchase order.

SET search_path = ims;

ALTER TABLE goods_receipts
    ALTER COLUMN purchase_order_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS service_confirmation_id BIGINT
        REFERENCES service_order_confirmations(id);

-- Allow service-based GRN items that are not linked to a PO item or warehouse location
ALTER TABLE goods_receipt_items
    ALTER COLUMN po_item_id DROP NOT NULL,
    ALTER COLUMN location_id DROP NOT NULL;

ALTER TABLE sales_invoices
    ADD COLUMN IF NOT EXISTS service_confirmation_id BIGINT
        REFERENCES service_order_confirmations(id);

-- Extend invoice_kind to include SERVICE invoices generated from service confirmations
DO $$
DECLARE
    cname TEXT;
BEGIN
    SELECT conname INTO cname
    FROM pg_constraint
    WHERE conrelid = 'ims.sales_invoices'::regclass
      AND contype   = 'c'
      AND conname   ILIKE '%invoice_kind%';
    IF cname IS NOT NULL THEN
        EXECUTE 'ALTER TABLE ims.sales_invoices DROP CONSTRAINT ' || quote_ident(cname);
    END IF;
END $$;
ALTER TABLE ims.sales_invoices
    ADD CONSTRAINT sales_invoices_invoice_kind_check
    CHECK (invoice_kind IN ('TAX','PROFORMA','EXPORT','BILL_OF_SUPPLY','SERVICE'));
