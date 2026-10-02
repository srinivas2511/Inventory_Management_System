-- Phase 5 — Quality and Finished Goods (DESIGN.md §4.8, PLAN tasks 5.1–5.7)
SET search_path TO ims;

-- Add deviation approval columns to quality_inspection_items
ALTER TABLE quality_inspection_items
    ADD COLUMN IF NOT EXISTS deviation_approved_by BIGINT,
    ADD COLUMN IF NOT EXISTS deviation_approved_at TIMESTAMPTZ;

-- Add FG batch quality lookup indexes
CREATE INDEX IF NOT EXISTS ix_fg_batch_quality_status ON fg_batches (quality_status);
CREATE INDEX IF NOT EXISTS ix_fg_batch_product        ON fg_batches (product_id);
CREATE INDEX IF NOT EXISTS ix_qi_fg_batch             ON quality_inspections (fg_batch_id);
CREATE INDEX IF NOT EXISTS ix_qi_type_status          ON quality_inspections (inspection_type, status);
CREATE INDEX IF NOT EXISTS ix_rejections_reason       ON rejections (rejection_reason_id);
CREATE INDEX IF NOT EXISTS ix_rejections_product      ON rejections (product_id);
