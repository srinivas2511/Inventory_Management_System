-- V115: Add last_polled_at to shipment_trackings for Aftership background polling
SET search_path TO ims;

ALTER TABLE shipment_trackings ADD COLUMN IF NOT EXISTS last_polled_at TIMESTAMPTZ;
