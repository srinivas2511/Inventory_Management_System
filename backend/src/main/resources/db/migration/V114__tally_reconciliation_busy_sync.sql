-- V114: Tally reconciliation support, Busy last-sync setting, voucher-combine setting
SET search_path TO ims;

-- Add reconciled_at column to tally_voucher_queue
ALTER TABLE tally_voucher_queue ADD COLUMN IF NOT EXISTS reconciled_at TIMESTAMPTZ;

-- Extend status constraint to include LEDGER_MISSING
ALTER TABLE tally_voucher_queue DROP CONSTRAINT IF EXISTS tally_voucher_queue_status_check;
ALTER TABLE tally_voucher_queue ADD CONSTRAINT tally_voucher_queue_status_check
    CHECK (status IN ('PENDING_APPROVAL','APPROVED','SENT','POSTED','EXCEPTION','REJECTED','LEDGER_MISSING'));

-- Busy: track last successful sync timestamp
INSERT INTO system_settings (key, value, value_type, description) VALUES
    ('busy.last_sync_at', '1970-01-01T00:00:00Z', 'STRING',
     'Timestamp of last successful Busy accounting sync')
ON CONFLICT (key) DO NOTHING;

-- Tally: purchase voucher combining option
INSERT INTO system_settings (key, value, value_type, description) VALUES
    ('tally.combine_purchase_vouchers', 'false', 'BOOLEAN',
     'Combine multiple PO payments with same supplier invoice into one Tally voucher')
ON CONFLICT (key) DO NOTHING;
