-- V99: OC copy — original_oc_id FK tracks which OC was copied from
-- Task 11.2: foreign OC and copy-OC support

ALTER TABLE ims.order_confirmations ADD COLUMN IF NOT EXISTS original_oc_id BIGINT REFERENCES ims.order_confirmations(id);
