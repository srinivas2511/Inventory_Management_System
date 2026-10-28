-- Phase 13: Advanced manufacturing, MRP, barcodes, cost roll-up
SET search_path TO ims;

-- 1. BOM alternate items (additive columns on bom_items)
ALTER TABLE bom_items ADD COLUMN IF NOT EXISTS is_alternate BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE bom_items ADD COLUMN IF NOT EXISTS alternate_group VARCHAR(30);
ALTER TABLE bom_items ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;

-- 2. Service items and min/max on materials (additive)
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema='ims' AND table_name='materials' AND column_name='is_service') THEN
    ALTER TABLE materials ADD COLUMN is_service BOOLEAN NOT NULL DEFAULT false;
  END IF;
END $$;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema='ims' AND table_name='materials' AND column_name='min_stock') THEN
    ALTER TABLE materials ADD COLUMN min_stock    NUMERIC(18,3);
    ALTER TABLE materials ADD COLUMN max_stock    NUMERIC(18,3);
    ALTER TABLE materials ADD COLUMN reorder_qty  NUMERIC(18,3);
  END IF;
END $$;

-- 3. Barcode labels
CREATE TABLE barcode_labels (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    label_type      VARCHAR(20) NOT NULL
                        CHECK (label_type IN ('MATERIAL','PRODUCT','BATCH','BIN','LOCATION')),
    ref_type        VARCHAR(20) NOT NULL,
    ref_id          BIGINT NOT NULL,
    barcode_value   VARCHAR(100) NOT NULL,
    qr_data         TEXT,
    printed_at      TIMESTAMPTZ,
    printed_by      BIGINT,
    print_count     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_barcode_labels_ref ON barcode_labels(ref_type, ref_id);

-- 5. FG cost roll-up log
CREATE TABLE cost_rollup_logs (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    material_id     BIGINT NOT NULL REFERENCES materials(id),
    rolled_cost     NUMERIC(18,4) NOT NULL,
    bom_id          BIGINT,
    depth           INT NOT NULL DEFAULT 0,
    rolled_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    rolled_by       BIGINT,
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_cost_rollup_logs_material ON cost_rollup_logs(material_id);

-- 6. Phase 13 permissions (MRP_VIEW/MRP_RUN already seeded in V22)
INSERT INTO permissions (code, module, description) VALUES
  ('BARCODE_VIEW',     'PRODUCTION', 'View and print barcodes'),
  ('BARCODE_MANAGE',   'PRODUCTION', 'Generate and manage barcode labels'),
  ('COST_ROLLUP_RUN',  'PRODUCTION', 'Run FG cost roll-up'),
  ('COST_ROLLUP_VIEW', 'PRODUCTION', 'View cost roll-up log')
ON CONFLICT DO NOTHING;

-- Grant ADMIN all Phase 13 permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('BARCODE_VIEW','BARCODE_MANAGE','COST_ROLLUP_RUN','COST_ROLLUP_VIEW')
ON CONFLICT DO NOTHING;

-- PRODUCTION_MANAGER and STORE_MANAGER get full set
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('PRODUCTION_MANAGER','STORE_MANAGER')
  AND p.code IN ('BARCODE_VIEW','BARCODE_MANAGE','COST_ROLLUP_RUN','COST_ROLLUP_VIEW')
ON CONFLICT DO NOTHING;
