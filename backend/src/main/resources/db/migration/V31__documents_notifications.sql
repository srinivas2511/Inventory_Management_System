-- R17 Document Management (DOC) and Notification Engine (NTF): document categories, documents with versions and
-- entity links, notification templates, per-user preferences and a notification table that doubles as the e-mail
-- outbox. Every table carries the DESIGN section 1.1 standard columns.
--
-- Deviation from DESIGN 23.1: file facts (storage key, size, checksum, type) live on document_versions and the
-- entity link on document_links, so a document can be versioned and attached to several records. The DESIGN
-- documents.version (a label) is current_version_no here because `version` is the optimistic-lock column.
SET search_path = ims;

CREATE TABLE document_categories (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_code      VARCHAR(20) NOT NULL UNIQUE,
    name               VARCHAR(80) NOT NULL,
    retention_years    INT,
    -- Extra authority needed to upload, version or link a document of this category (NULL = none beyond
    -- DOCUMENT_UPLOAD). Drawings and specifications keep their existing engineering permissions.
    required_authority VARCHAR(40),
    active             BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         BIGINT,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by         BIGINT,
    version            BIGINT      NOT NULL DEFAULT 0,
    company_id         BIGINT      NOT NULL DEFAULT 1
);

CREATE TABLE documents (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_number    VARCHAR(30)  NOT NULL UNIQUE,
    category_id        BIGINT       NOT NULL REFERENCES document_categories(id),
    title              VARCHAR(200) NOT NULL,
    description        VARCHAR(500),
    status             VARCHAR(12)  NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT chk_documents_status CHECK (status IN ('DRAFT','REVIEW','APPROVED','OBSOLETE')),
    current_version_no INT          NOT NULL DEFAULT 1,
    approved_by        BIGINT       REFERENCES users(id),
    approved_at        TIMESTAMPTZ,
    status_reason      VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         BIGINT,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by         BIGINT,
    version            BIGINT       NOT NULL DEFAULT 0,
    company_id         BIGINT       NOT NULL DEFAULT 1
);
CREATE INDEX ix_documents_category ON documents(category_id);
CREATE INDEX ix_documents_status   ON documents(status);

CREATE TABLE document_versions (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id       BIGINT       NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version_no        INT          NOT NULL,
    revision_label    VARCHAR(20),
    storage_key       VARCHAR(200) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    mime_type         VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL CONSTRAINT chk_docver_size CHECK (size_bytes > 0),
    sha256            VARCHAR(64)  NOT NULL,
    change_note       VARCHAR(500),
    uploaded_by       BIGINT       NOT NULL REFERENCES users(id),
    uploaded_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        BIGINT,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by        BIGINT,
    version           BIGINT       NOT NULL DEFAULT 0,
    company_id        BIGINT       NOT NULL DEFAULT 1,
    CONSTRAINT uq_docver UNIQUE (document_id, version_no)
);

CREATE TABLE document_links (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id BIGINT      NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    entity_type VARCHAR(20) NOT NULL
        CONSTRAINT chk_doclink_type CHECK (entity_type IN
            ('PRODUCT','BOM','ECO','SUPPLIER','MACHINE','CAPA','COMPLAINT')),
    entity_id   BIGINT      NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  BIGINT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  BIGINT,
    version     BIGINT      NOT NULL DEFAULT 0,
    company_id  BIGINT      NOT NULL DEFAULT 1,
    CONSTRAINT uq_doclink UNIQUE (document_id, entity_type, entity_id)
);
CREATE INDEX ix_doclink_entity ON document_links(entity_type, entity_id);

CREATE TABLE notification_templates (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    template_code    VARCHAR(40)  NOT NULL UNIQUE,
    channel          VARCHAR(10)  NOT NULL
        CONSTRAINT chk_ntpl_channel CHECK (channel IN ('EMAIL','IN_APP','BOTH')),
    description      VARCHAR(200),
    subject_template VARCHAR(200),
    body_template    TEXT         NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       BIGINT,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by       BIGINT,
    version          BIGINT       NOT NULL DEFAULT 0,
    company_id       BIGINT       NOT NULL DEFAULT 1
);

-- A row overrides the default (every channel the template allows is on). enabled = FALSE is an unsubscribe.
CREATE TABLE notification_subscriptions (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users(id),
    template_code VARCHAR(40) NOT NULL REFERENCES notification_templates(template_code),
    channel       VARCHAR(10) NOT NULL CONSTRAINT chk_nsub_channel CHECK (channel IN ('EMAIL','IN_APP')),
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    BIGINT,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by    BIGINT,
    version       BIGINT      NOT NULL DEFAULT 0,
    company_id    BIGINT      NOT NULL DEFAULT 1,
    CONSTRAINT uq_nsub UNIQUE (user_id, template_code, channel)
);

-- One row per recipient and channel. IN_APP rows are SENT on creation. EMAIL rows start PENDING and are the outbox:
-- the delivery job sends them, retries with back-off and ends in SENT or FAILED. user_id is NULL for external
-- recipients (a supplier contact), who are addressed by recipient_email.
CREATE TABLE notifications (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       REFERENCES users(id),
    recipient_email VARCHAR(160),
    template_code   VARCHAR(40)  NOT NULL,
    channel         VARCHAR(10)  NOT NULL CONSTRAINT chk_notif_channel CHECK (channel IN ('EMAIL','IN_APP')),
    subject         VARCHAR(200),
    body            TEXT         NOT NULL,
    entity_type     VARCHAR(30),
    entity_id       BIGINT,
    status          VARCHAR(10)  NOT NULL DEFAULT 'PENDING'
        CONSTRAINT chk_notif_status CHECK (status IN ('PENDING','SENT','FAILED','READ')),
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error      VARCHAR(300),
    sent_at         TIMESTAMPTZ,
    read_at         TIMESTAMPTZ,
    dedup_key       VARCHAR(200) UNIQUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      BIGINT,
    version         BIGINT       NOT NULL DEFAULT 0,
    company_id      BIGINT       NOT NULL DEFAULT 1,
    CONSTRAINT chk_notif_recipient CHECK (user_id IS NOT NULL OR recipient_email IS NOT NULL)
);
CREATE INDEX ix_notif_inbox ON notifications(user_id, channel, created_at DESC);
CREATE INDEX ix_notif_outbox ON notifications(next_attempt_at) WHERE channel = 'EMAIL' AND status = 'PENDING';

-- Categories --------------------------------------------------------------------------------------------------------
INSERT INTO document_categories (category_code, name, retention_years, required_authority) VALUES
('DRAWING',       'Drawing',                        10, 'DRAWING_MANAGE'),
('SPECIFICATION', 'Specification / change notice',  10, 'SPECIFICATION_MANAGE'),
('SOP',           'Standard operating procedure',    5, NULL),
('CERTIFICATE',   'Certificate',                     7, NULL),
('CONTRACT',      'Contract',                       10, NULL),
('CAPA_EVIDENCE', 'CAPA evidence',                   5, NULL),
('MANUAL',        'Manual',                          5, NULL),
('OTHER',         'Other',                        NULL, NULL);

-- Permissions -------------------------------------------------------------------------------------------------------
INSERT INTO permissions (code, module, description) VALUES
('DOCUMENT_VIEW',       'DOCUMENT',     'View and download documents (linked documents also need read access to what they are linked to)'),
('DOCUMENT_UPLOAD',     'DOCUMENT',     'Upload documents and new versions, link them to records and submit them for approval'),
('DOCUMENT_APPROVE',    'DOCUMENT',     'Approve, return or obsolete documents'),
('DOCUMENT_MANAGE',     'DOCUMENT',     'Manage document categories and read every document regardless of its links'),
('NOTIFICATION_MANAGE', 'NOTIFICATION', 'Maintain notification templates and view the delivery log');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN'
  AND p.code IN ('DOCUMENT_VIEW','DOCUMENT_UPLOAD','DOCUMENT_APPROVE','DOCUMENT_MANAGE','NOTIFICATION_MANAGE')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('ENGINEER','PLM_ENGINEER','PRODUCTION_MANAGER','SUPERVISOR','QUALITY_MANAGER','STORE_MANAGER',
                 'PURCHASE_MANAGER','MAINTENANCE','MANAGEMENT')
  AND p.code = 'DOCUMENT_VIEW'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('ENGINEER','PLM_ENGINEER','PRODUCTION_MANAGER','QUALITY_MANAGER','PURCHASE_MANAGER','MAINTENANCE')
  AND p.code = 'DOCUMENT_UPLOAD'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('PRODUCTION_MANAGER','QUALITY_MANAGER')
  AND p.code = 'DOCUMENT_APPROVE'
ON CONFLICT DO NOTHING;

-- Settings ----------------------------------------------------------------------------------------------------------
INSERT INTO system_settings (key, value, value_type, description) VALUES
('documents.max_size_mb',            '25',
    'INTEGER', 'Largest file accepted by document upload, in megabytes'),
('documents.allowed_extensions',     'pdf,png,jpg,jpeg,gif,txt,csv,docx,xlsx,pptx,dwg,dxf,step,stp',
    'STRING',  'Comma-separated file extensions accepted by document upload'),
('notification.email.max_attempts',  '5',
    'INTEGER', 'E-mail delivery attempts before a notification is marked FAILED'),
('notification.email.retry_base_seconds', '60',
    'INTEGER', 'First retry delay for a failed e-mail; it doubles on every further attempt (capped at one hour)'),
('notification.alert.email_min_severity', 'HIGH',
    'STRING',  'Lowest alert severity (LOW, MEDIUM, HIGH, CRITICAL) that is also e-mailed; every alert is shown in-app')
ON CONFLICT (key) DO NOTHING;

-- Templates (Mustache-style {{variable}} placeholders) --------------------------------------------------------------
INSERT INTO notification_templates (template_code, channel, description, subject_template, body_template) VALUES
('PO_APPROVED_SUPPLIER', 'EMAIL',  'Approved purchase order sent to the supplier contact',
    'Purchase order {{poNumber}} approved',
    E'Dear {{contactName}},\n\nPurchase order {{poNumber}} has been approved by {{companyName}}.\nOrder date: {{orderDate}}.\n\nPlease confirm receipt and your delivery date.\n\nRegards,\n{{companyName}}'),
('PO_APPROVED',          'IN_APP', 'Purchase order approved (to the person who raised it)',
    'Purchase order {{poNumber}} approved',
    'Purchase order {{poNumber}} to {{supplierName}} has been approved.'),
('CAPA_ASSIGNED',        'IN_APP', 'A CAPA has been assigned to you',
    'CAPA {{capaNumber}} assigned to you',
    'CAPA {{capaNumber}} "{{title}}" has been assigned to you.'),
('LOW_STOCK',            'BOTH', 'Alert: stock below its reorder level',        '[{{severity}}] Low stock',                 '{{message}}'),
('OVERSTOCK',            'BOTH', 'Alert: stock above its maximum level',        '[{{severity}}] Overstock',                 '{{message}}'),
('PENDING_INSPECTION',   'BOTH', 'Alert: inspection waiting too long',          '[{{severity}}] Inspection pending',        '{{message}}'),
('PRODUCTION_DELAY',     'BOTH', 'Alert: production order behind schedule',     '[{{severity}}] Production delay',          '{{message}}'),
('MATERIAL_SHORTAGE',    'BOTH', 'Alert: material shortage',                    '[{{severity}}] Material shortage',         '{{message}}'),
('AGING_BATCH',          'BOTH', 'Alert: batch ageing towards expiry',          '[{{severity}}] Ageing batch',              '{{message}}'),
('PENDING_PO',           'BOTH', 'Alert: purchase order pending',               '[{{severity}}] Purchase order pending',    '{{message}}'),
('LEDGER_MISMATCH',      'BOTH', 'Alert: inventory ledger does not reconcile',  '[{{severity}}] Ledger mismatch',           '{{message}}'),
('OVERDUE_PO',           'BOTH', 'Alert: purchase order overdue',               '[{{severity}}] Purchase order overdue',    '{{message}}'),
('OVERDUE_PM',           'BOTH', 'Alert: preventive maintenance overdue',       '[{{severity}}] Maintenance overdue',       '{{message}}'),
('LOW_MATERIAL_BATCH',   'BOTH', 'Alert: low material batch',                   '[{{severity}}] Low material batch',        '{{message}}'),
('SPC_OUT_OF_CONTROL',   'BOTH', 'Alert: process out of statistical control',   '[{{severity}}] SPC out of control',        '{{message}}'),
('ALERT_GENERIC',        'BOTH', 'Alert of a type without its own template',    '[{{severity}}] {{alertType}}',             '{{message}}');
