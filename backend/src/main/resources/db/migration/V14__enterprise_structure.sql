-- R9-1.1: Enterprise structure — companies, plants; plant_id on transacting documents.
SET search_path = ims;

CREATE TABLE companies (
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_code             VARCHAR(10)  NOT NULL,
    name                     VARCHAR(150) NOT NULL,
    legal_name               VARCHAR(200),
    gstin                    VARCHAR(20),
    pan                      VARCHAR(15),
    registered_address       TEXT,
    currency_code            VARCHAR(3)      NOT NULL DEFAULT 'INR',
    fiscal_year_start_month  SMALLINT     NOT NULL DEFAULT 4,
    active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               BIGINT,
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by               BIGINT,
    version                  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_companies_code UNIQUE (company_code)
);

CREATE TABLE plants (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plant_code  VARCHAR(10)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    company_id  BIGINT       NOT NULL REFERENCES companies(id),
    address     TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  BIGINT,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  BIGINT,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_plants_code UNIQUE (plant_code)
);

-- Add plant_id to transacting documents (nullable for backward compatibility)
ALTER TABLE purchase_orders   ADD COLUMN IF NOT EXISTS plant_id BIGINT REFERENCES plants(id);
ALTER TABLE production_orders ADD COLUMN IF NOT EXISTS plant_id BIGINT REFERENCES plants(id);
ALTER TABLE sales_orders      ADD COLUMN IF NOT EXISTS plant_id BIGINT REFERENCES plants(id);
ALTER TABLE goods_receipts    ADD COLUMN IF NOT EXISTS plant_id BIGINT REFERENCES plants(id);
ALTER TABLE dispatches        ADD COLUMN IF NOT EXISTS plant_id BIGINT REFERENCES plants(id);

-- Seed: Spring Manufacturing company
INSERT INTO companies (company_code, name, legal_name, gstin, pan, registered_address, currency_code, fiscal_year_start_month)
VALUES ('SMC001', 'Spring Manufacturing Co.', 'Spring Manufacturing Company Pvt Ltd',
        '27AABCS1234A1Z5', 'AABCS1234A', '123 Industrial Estate, Pune, Maharashtra 411018', 'INR', 4);

INSERT INTO plants (plant_code, name, company_id, address)
VALUES ('P001', 'Pune Main Plant', 1, '123 Industrial Estate, Pune, Maharashtra 411018');
