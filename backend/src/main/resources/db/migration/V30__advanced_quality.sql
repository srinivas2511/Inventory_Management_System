-- R16 Advanced Quality (QMS-007..010): SPC control charts and data points, CAPA, customer complaints, first article
-- inspection (FAI), their permissions, one alert type and two settings. Every table carries the DESIGN section 1.1
-- standard columns. DESIGN section 22 numbered this V27; V27-V29 were taken by WMS, PLM and the permission alignment.
SET search_path = ims;

-- ── Alerts: an out-of-control SPC signal raises an alert --------------------------------------------------------
ALTER TABLE alerts DROP CONSTRAINT IF EXISTS chk_alert_type;
ALTER TABLE alerts ADD CONSTRAINT chk_alert_type CHECK (alert_type IN (
  'LOW_STOCK','OVERSTOCK','PENDING_INSPECTION','PRODUCTION_DELAY',
  'MATERIAL_SHORTAGE','AGING_BATCH','PENDING_PO','LEDGER_MISMATCH',
  'OVERDUE_PO','OVERDUE_PM','LOW_MATERIAL_BATCH','SPC_OUT_OF_CONTROL'
));

-- ── Inspections: a first article inspection is an inspection of its own type -------------------------------------
-- The original CHECK was declared inline on the column in V7, so find it by definition instead of by name.
DO $$
DECLARE c text;
BEGIN
  FOR c IN SELECT conname FROM pg_constraint
           WHERE conrelid = 'ims.quality_inspections'::regclass AND contype = 'c'
             AND pg_get_constraintdef(oid) LIKE '%inspection_type%'
  LOOP
    EXECUTE format('ALTER TABLE ims.quality_inspections DROP CONSTRAINT %I', c);
  END LOOP;
END $$;
ALTER TABLE quality_inspections ADD CONSTRAINT chk_qi_inspection_type
  CHECK (inspection_type IN ('INCOMING','IN_PROCESS','FINAL','FAI'));

-- ── SPC (R16-1.x) -------------------------------------------------------------------------------------------------
CREATE TABLE spc_control_charts (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id            BIGINT       NOT NULL REFERENCES products(id),
    parameter_name        VARCHAR(60)  NOT NULL,
    chart_type            VARCHAR(10)  NOT NULL
        CONSTRAINT chk_spc_chart_type CHECK (chart_type IN ('XBAR_R','P','C')),
    subgroup_size         INT          NOT NULL DEFAULT 5
        CONSTRAINT chk_spc_subgroup_size CHECK (subgroup_size BETWEEN 1 AND 10),
    cl                    NUMERIC(14,6),
    ucl                   NUMERIC(14,6),
    lcl                   NUMERIC(14,6),
    cl_r                  NUMERIC(14,6),
    ucl_r                 NUMERIC(14,6),
    lcl_r                 NUMERIC(14,6),
    usl                   NUMERIC(14,6),
    lsl                   NUMERIC(14,6),
    limits_established_at TIMESTAMPTZ,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            BIGINT,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by            BIGINT,
    version               BIGINT       NOT NULL DEFAULT 0,
    company_id            BIGINT       NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX ux_spc_chart_active ON spc_control_charts(product_id, parameter_name) WHERE active;

CREATE TABLE spc_data_points (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    control_chart_id BIGINT        NOT NULL REFERENCES spc_control_charts(id),
    inspection_id    BIGINT        REFERENCES quality_inspections(id),
    subgroup_no      INT           NOT NULL,
    measurement_no   INT           NOT NULL,
    measured_value   NUMERIC(14,6) NOT NULL,
    measured_at      TIMESTAMPTZ   NOT NULL,
    subgroup_mean    NUMERIC(14,6),
    subgroup_range   NUMERIC(14,6),
    sample_size      INT,
    defect_count     INT,
    point_ucl        NUMERIC(14,6),
    point_lcl        NUMERIC(14,6),
    out_of_control   BOOLEAN       NOT NULL DEFAULT FALSE,
    violation_rule   VARCHAR(60),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by       BIGINT,
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by       BIGINT,
    version          BIGINT        NOT NULL DEFAULT 0,
    company_id       BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT uq_spc_point UNIQUE (control_chart_id, subgroup_no, measurement_no)
);
CREATE INDEX ix_spc_points_chart ON spc_data_points(control_chart_id, subgroup_no, measurement_no);
CREATE INDEX ix_spc_points_inspection ON spc_data_points(inspection_id);

-- ── CAPA (R16-2.x) ------------------------------------------------------------------------------------------------
CREATE TABLE capa_records (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    capa_number            VARCHAR(20)  NOT NULL UNIQUE,
    title                  VARCHAR(200) NOT NULL,
    description            TEXT,
    source_type            VARCHAR(20)  NOT NULL
        CONSTRAINT chk_capa_source CHECK (source_type IN
            ('CUSTOMER_COMPLAINT','INTERNAL_AUDIT','REJECTION_ANALYSIS','SPC_VIOLATION')),
    source_id              BIGINT,
    root_cause             TEXT,
    corrective_action      TEXT,
    preventive_action      TEXT,
    verification_notes     TEXT,
    target_completion_date DATE,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
        CONSTRAINT chk_capa_status CHECK (status IN
            ('OPEN','ROOT_CAUSE_ANALYSIS','ACTION_IN_PROGRESS','VERIFICATION','CLOSED')),
    assigned_to            BIGINT       REFERENCES users(id),
    verified_by            BIGINT       REFERENCES users(id),
    opened_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at              TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by             BIGINT,
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by             BIGINT,
    version                BIGINT       NOT NULL DEFAULT 0,
    company_id             BIGINT       NOT NULL DEFAULT 1
);
CREATE INDEX ix_capa_status ON capa_records(status);
CREATE INDEX ix_capa_source ON capa_records(source_type, source_id);

CREATE TABLE customer_complaints (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    complaint_number    VARCHAR(20)  NOT NULL UNIQUE,
    customer_id         BIGINT       NOT NULL REFERENCES customers(id),
    product_id          BIGINT       REFERENCES products(id),
    fg_batch_id         BIGINT       REFERENCES fg_batches(id),
    complaint_date      DATE         NOT NULL,
    received_by         BIGINT       NOT NULL REFERENCES users(id),
    category            VARCHAR(20)  NOT NULL DEFAULT 'OTHER'
        CONSTRAINT chk_complaint_category CHECK (category IN
            ('DIMENSIONAL','SURFACE','PERFORMANCE','PACKAGING','DELIVERY','OTHER')),
    description         TEXT         NOT NULL,
    customer_ref        VARCHAR(60),
    quantity_complained NUMERIC(18,3),
    quantity_returned   NUMERIC(18,3),
    disposition         VARCHAR(15)
        CONSTRAINT chk_complaint_disposition CHECK (disposition IN ('REPLACEMENT','CREDIT_NOTE','REWORK','SCRAP')),
    resolution_notes    VARCHAR(1000),
    status              VARCHAR(12)  NOT NULL DEFAULT 'OPEN'
        CONSTRAINT chk_complaint_status CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED')),
    capa_id             BIGINT       REFERENCES capa_records(id),
    closed_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          BIGINT,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          BIGINT,
    version             BIGINT       NOT NULL DEFAULT 0,
    company_id          BIGINT       NOT NULL DEFAULT 1
);
CREATE INDEX ix_complaints_status ON customer_complaints(status);
CREATE INDEX ix_complaints_customer ON customer_complaints(customer_id, complaint_date);
CREATE INDEX ix_complaints_fg_batch ON customer_complaints(fg_batch_id);

-- ── First article inspection (R16-4.1) --------------------------------------------------------------------------
CREATE TABLE first_article_inspections (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fai_number         VARCHAR(20)  NOT NULL UNIQUE,
    product_id         BIGINT       NOT NULL REFERENCES products(id),
    trigger_type       VARCHAR(15)  NOT NULL
        CONSTRAINT chk_fai_trigger CHECK (trigger_type IN ('NEW_PRODUCT','ECO_IMPLEMENTED','MANUAL')),
    eco_id             BIGINT       REFERENCES ecos(id),
    trigger_reference  VARCHAR(40),
    inspection_plan_id BIGINT       REFERENCES inspection_plans(id),
    production_order_id BIGINT      REFERENCES production_orders(id),
    fg_batch_id        BIGINT       REFERENCES fg_batches(id),
    inspection_id      BIGINT       REFERENCES quality_inspections(id),
    status             VARCHAR(12)  NOT NULL DEFAULT 'REQUIRED'
        CONSTRAINT chk_fai_status CHECK (status IN ('REQUIRED','IN_PROGRESS','PASSED','FAILED')),
    result_summary     VARCHAR(1000),
    completed_by       BIGINT       REFERENCES users(id),
    completed_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         BIGINT,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by         BIGINT,
    version            BIGINT       NOT NULL DEFAULT 0,
    company_id         BIGINT       NOT NULL DEFAULT 1
);
-- One open FAI per product keeps the automatic triggers idempotent.
CREATE UNIQUE INDEX ux_fai_open_per_product ON first_article_inspections(product_id)
    WHERE status IN ('REQUIRED','IN_PROGRESS');
CREATE INDEX ix_fai_status ON first_article_inspections(status);

-- ── Settings -------------------------------------------------------------------------------------------------------
INSERT INTO system_settings (key, value, value_type, description) VALUES
('spc.min_subgroups_for_limits', '20', 'INTEGER',
    'Number of subgroups (or points, for P and C charts) a control chart needs before its limits are established automatically'),
('spc.nelson_rule2_run_length', '9', 'INTEGER',
    'Nelson rule 2: this many consecutive points on the same side of the centre line signal a shift')
ON CONFLICT (key) DO NOTHING;

-- ── Permissions ---------------------------------------------------------------------------------------------------
-- Reading charts, CAPA, complaints and FAI uses QUALITY_VIEW; entering SPC data and starting an FAI use QUALITY_INSPECT;
-- completing an FAI uses QUALITY_APPROVE. Only the codes below are new. REQUIREMENTS names no role for them, so the
-- grant is the narrowest sensible one (ADMIN and QUALITY_MANAGER) - review before go-live.
INSERT INTO permissions (code, module, description) VALUES
('SPC_MANAGE',       'QUALITY', 'Define control charts and spec limits, establish or recalculate control limits'),
('CAPA_MANAGE',      'QUALITY', 'Raise and work corrective and preventive actions'),
('CAPA_CLOSE',       'QUALITY', 'Verify the effectiveness of a CAPA and close it'),
('COMPLAINT_MANAGE', 'QUALITY', 'Register, investigate and close customer complaints')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('ADMIN','QUALITY_MANAGER')
  AND p.code IN ('SPC_MANAGE','CAPA_MANAGE','CAPA_CLOSE','COMPLAINT_MANAGE')
ON CONFLICT DO NOTHING;
