-- R20 Industry verticals: product types become data, not a spring-only CHECK constraint.
--   * product_types is the lookup behind products.spring_type; the seven spring types are seeded, so existing
--     products and payloads are unchanged. A vertical pack (db/vertical/<name>) only inserts rows here.
--   * spring_attribute_definitions is kept as the attribute store (no breaking rename); its spring_type column now
--     holds any product type code. DESIGN section 26 names this table product_attribute_definitions with an
--     industry_type column - the lookup's `industry` column plus the existing table cover the same ground.
--   * ATTRIBUTE_MANAGE (ADMIN only) guards the attribute-definition admin screen (R20-4.1).
--   * hrm.payroll.rule_pack selects the statutory payroll rule pack; IN (India) is the only pack (R20-5.1).
SET search_path = ims;

CREATE TABLE product_types (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code          VARCHAR(20) NOT NULL,
  name          VARCHAR(80) NOT NULL,
  industry      VARCHAR(20) NOT NULL DEFAULT 'SPRING',
  display_order INT         NOT NULL DEFAULT 0,
  active        BOOLEAN     NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by    BIGINT,
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by    BIGINT,
  version       BIGINT      NOT NULL DEFAULT 0,
  company_id    BIGINT      NOT NULL DEFAULT 1,
  CONSTRAINT uq_product_type_code UNIQUE (code)
);

INSERT INTO product_types (code, name, industry, display_order) VALUES
  ('COMPRESSION', 'Compression spring',       'SPRING', 1),
  ('EXTENSION',   'Extension spring',         'SPRING', 2),
  ('TORSION',     'Torsion spring',           'SPRING', 3),
  ('CONICAL',     'Conical spring',           'SPRING', 4),
  ('BELLEVILLE',  'Belleville (disc) spring', 'SPRING', 5),
  ('WIRE_FORM',   'Wire form',                'SPRING', 6),
  ('CUSTOM',      'Custom spring',            'SPRING', 7);

-- The spring-only CHECK on products.spring_type is replaced by a foreign key to the lookup.
DO $$
DECLARE c RECORD;
BEGIN
  FOR c IN SELECT conname FROM pg_constraint
           WHERE conrelid = 'ims.products'::regclass AND contype = 'c'
             AND pg_get_constraintdef(oid) LIKE '%spring_type%'
  LOOP
    EXECUTE format('ALTER TABLE ims.products DROP CONSTRAINT %I', c.conname);
  END LOOP;
END $$;

ALTER TABLE products ADD CONSTRAINT fk_products_product_type
  FOREIGN KEY (spring_type) REFERENCES product_types (code);
ALTER TABLE spring_attribute_definitions ADD CONSTRAINT fk_attr_def_product_type
  FOREIGN KEY (spring_type) REFERENCES product_types (code);
ALTER TABLE spring_attribute_definitions ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

INSERT INTO permissions (code, module, description) VALUES
  ('ATTRIBUTE_MANAGE', 'ENGINEERING', 'Create, edit and deactivate product attribute definitions')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND p.code = 'ATTRIBUTE_MANAGE'
ON CONFLICT DO NOTHING;

INSERT INTO system_settings (key, value, value_type, description) VALUES
  ('hrm.payroll.rule_pack', 'IN', 'STRING',
   'Statutory payroll rule pack applied by payroll runs. IN (India: PF, ESI, professional tax, TDS) is the only pack')
ON CONFLICT (key) DO NOTHING;
