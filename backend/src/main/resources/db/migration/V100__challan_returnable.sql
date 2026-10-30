-- V100: Delivery challan returnable/non-returnable tracking
-- Task 11.4: is_returnable flag, expected return date, returned_at timestamp

ALTER TABLE ims.delivery_challans ADD COLUMN IF NOT EXISTS is_returnable        BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE ims.delivery_challans ADD COLUMN IF NOT EXISTS expected_return_date DATE;
ALTER TABLE ims.delivery_challans ADD COLUMN IF NOT EXISTS returned_at          TIMESTAMPTZ;
