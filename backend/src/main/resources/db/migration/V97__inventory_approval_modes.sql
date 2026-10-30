-- V97: Inventory document approval modes (item 10.8)
SET search_path TO ims;

-- ── Schema additions ──────────────────────────────────────────────────────────
ALTER TABLE goods_receipts
    ADD COLUMN IF NOT EXISTS requires_approval BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE stock_adjustments
    ADD COLUMN IF NOT EXISTS requires_approval BOOLEAN NOT NULL DEFAULT FALSE;

-- ── System settings ───────────────────────────────────────────────────────────
INSERT INTO system_settings (key, value, value_type, description) VALUES
    ('inventory.inward.approval_mode',     'NONE', 'STRING',
     'NONE or REQUIRED — whether inward receipts require approval before posting'),
    ('inventory.adjustment.approval_mode', 'NONE', 'STRING',
     'NONE or REQUIRED — whether adjustments require approval before posting'),
    ('inventory.transfer.approval_mode',   'NONE', 'STRING',
     'NONE or REQUIRED — whether transfers require approval before posting')
ON CONFLICT (key) DO NOTHING;
