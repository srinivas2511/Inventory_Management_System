-- Phase 4 — BOM, Product Routing (DESIGN.md §4, PLAN tasks 4.1–4.2)
SET search_path TO ims;

-- ── BOM header ────────────────────────────────────────────────────────────────
CREATE TABLE bom (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  bom_number     VARCHAR(20)  NOT NULL UNIQUE,
  revision       VARCHAR(5)   NOT NULL DEFAULT 'A',
  product_id     BIGINT       NOT NULL REFERENCES products(id),
  status         VARCHAR(25)  NOT NULL DEFAULT 'DRAFT',
  effective_from DATE,
  effective_to   DATE,
  base_quantity  NUMERIC(18,3) NOT NULL DEFAULT 1 CHECK (base_quantity > 0),
  change_reason  VARCHAR(500),
  approved_by    BIGINT,
  approved_at    TIMESTAMPTZ,
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by     BIGINT,
  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by     BIGINT,
  version        BIGINT       NOT NULL DEFAULT 0,
  company_id     BIGINT       NOT NULL DEFAULT 1,
  UNIQUE (bom_number, revision)
);
-- One ACTIVE BOM per product enforced by partial unique index
CREATE UNIQUE INDEX ux_bom_one_active ON bom(product_id) WHERE status = 'ACTIVE';

-- ── BOM items ─────────────────────────────────────────────────────────────────
CREATE TABLE bom_items (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  bom_id       BIGINT       NOT NULL REFERENCES bom(id) ON DELETE CASCADE,
  line_no      INT          NOT NULL,
  item_kind    VARCHAR(12)  NOT NULL CHECK (item_kind IN ('MATERIAL','PROCESS','PACKAGING')),
  material_id  BIGINT       REFERENCES materials(id),
  operation_id BIGINT       REFERENCES operations(id),
  required_qty NUMERIC(18,6),
  uom          VARCHAR(10),
  scrap_pct    NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (scrap_pct BETWEEN 0 AND 100),
  instruction  VARCHAR(300),
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by   BIGINT,
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by   BIGINT,
  version      BIGINT       NOT NULL DEFAULT 0,
  company_id   BIGINT       NOT NULL DEFAULT 1,
  UNIQUE (bom_id, line_no),
  CHECK ((item_kind = 'MATERIAL' AND material_id IS NOT NULL AND required_qty > 0) OR item_kind <> 'MATERIAL')
);

-- ── Product Routing ───────────────────────────────────────────────────────────
CREATE TABLE product_routing (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_id       BIGINT       NOT NULL REFERENCES products(id),
  sequence_no      INT          NOT NULL,
  operation_id     BIGINT       NOT NULL REFERENCES operations(id),
  machine_id       BIGINT       REFERENCES machines(id),
  standard_time_min NUMERIC(10,2),
  setup_time_min   NUMERIC(10,2),
  inspection_point BOOLEAN      NOT NULL DEFAULT FALSE,
  active           BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by       BIGINT,
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_by       BIGINT,
  version          BIGINT       NOT NULL DEFAULT 0,
  company_id       BIGINT       NOT NULL DEFAULT 1,
  UNIQUE (product_id, sequence_no)
);

-- ── System settings: BOM defaults ────────────────────────────────────────────
INSERT INTO system_settings (key, value, value_type, description)
VALUES
  ('bom.default_base_quantity', '1', 'DECIMAL', 'Default base quantity for new BOMs')
ON CONFLICT (key) DO NOTHING;
