-- 13.1 Multi-level BOM alternate items (gap item 13.1)
SET search_path TO ims;

ALTER TABLE bom_items
    ADD COLUMN IF NOT EXISTS alternate_material_id BIGINT REFERENCES materials(id),
    ADD COLUMN IF NOT EXISTS is_alternate BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS alternate_factor NUMERIC(18,6) NOT NULL DEFAULT 1.0;

-- 13.6 Service items on materials
ALTER TABLE materials
    ADD COLUMN IF NOT EXISTS is_service_item BOOLEAN NOT NULL DEFAULT FALSE;

-- 13.9 Production order convenience
ALTER TABLE production_orders
    ADD COLUMN IF NOT EXISTS complete_in_one_step BOOLEAN NOT NULL DEFAULT FALSE;
