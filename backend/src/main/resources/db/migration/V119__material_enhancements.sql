-- feat: material master — HSN code, min order qty, auto-code sequence
ALTER TABLE ims.materials
  ADD COLUMN IF NOT EXISTS hsn_code VARCHAR(8),
  ADD COLUMN IF NOT EXISTS min_order_qty NUMERIC(18,3);

CREATE SEQUENCE IF NOT EXISTS ims.material_code_seq START WITH 1001 INCREMENT BY 1;
