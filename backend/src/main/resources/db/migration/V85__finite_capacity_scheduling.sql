-- GROUP 8 (ERP gap): Finite-capacity scheduling for the plan board
SET search_path TO ims;

-- Work-centre daily capacity (hours available per shift)
CREATE TABLE IF NOT EXISTS work_centre_capacity (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      BIGINT NOT NULL DEFAULT 1,
    machine_id      BIGINT NOT NULL REFERENCES machines(id),
    capacity_date   DATE NOT NULL,
    shifts          INT NOT NULL DEFAULT 1,
    hours_per_shift NUMERIC(5,2) NOT NULL DEFAULT 8,
    available_hours NUMERIC(7,2) GENERATED ALWAYS AS (shifts * hours_per_shift) STORED,
    notes           VARCHAR(200),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT NOT NULL DEFAULT 1,
    updated_at      TIMESTAMPTZ DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (company_id, machine_id, capacity_date)
);

-- Scheduled slots: one row per operation that has been forward-scheduled
CREATE TABLE IF NOT EXISTS scheduled_operations (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id            BIGINT NOT NULL DEFAULT 1,
    production_operation_id BIGINT NOT NULL REFERENCES production_operations(id),
    machine_id            BIGINT REFERENCES machines(id),
    scheduled_start       TIMESTAMPTZ NOT NULL,
    scheduled_end         TIMESTAMPTZ NOT NULL,
    planned_hours         NUMERIC(7,2) NOT NULL DEFAULT 0,
    status                VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
                              CHECK (status IN ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED')),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            BIGINT NOT NULL DEFAULT 1,
    updated_at            TIMESTAMPTZ DEFAULT now(),
    updated_by            BIGINT,
    version               BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_scheduled_ops_machine ON scheduled_operations(machine_id, scheduled_start);
CREATE INDEX ix_scheduled_ops_op      ON scheduled_operations(production_operation_id);

-- Permissions
INSERT INTO ims.permissions (code, module, description) VALUES
    ('CAPACITY_VIEW',     'PRODUCTION', 'View capacity board and scheduled operations'),
    ('CAPACITY_MANAGE',   'PRODUCTION', 'Schedule operations and manage capacity')
ON CONFLICT (code) DO NOTHING;

INSERT INTO ims.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM ims.roles r, ims.permissions p
WHERE r.code IN ('ADMIN','PRODUCTION_MANAGER') AND p.code IN ('CAPACITY_VIEW','CAPACITY_MANAGE')
ON CONFLICT DO NOTHING;

INSERT INTO ims.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM ims.roles r, ims.permissions p
WHERE r.code IN ('PRODUCTION_SUPERVISOR','MACHINE_OPERATOR') AND p.code = 'CAPACITY_VIEW'
ON CONFLICT DO NOTHING;
