-- V117 — product_materials junction: multiple raw materials per product
SET search_path TO ims;

CREATE TABLE product_materials (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_id   BIGINT NOT NULL REFERENCES products(id)  ON DELETE CASCADE,
  material_id  BIGINT NOT NULL REFERENCES materials(id) ON DELETE CASCADE,
  display_order INT  NOT NULL DEFAULT 0,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by   BIGINT,
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by   BIGINT,
  version      BIGINT      NOT NULL DEFAULT 0,
  company_id   BIGINT      NOT NULL DEFAULT 1,
  CONSTRAINT uq_product_materials UNIQUE (product_id, material_id)
);
