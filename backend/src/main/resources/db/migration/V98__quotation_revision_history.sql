-- V98: Quotation revision history (amend flow)
-- Task 11.1: snapshot previous state when amending an APPROVED/SENT quotation

CREATE TABLE IF NOT EXISTS ims.quotation_revisions (
    id              BIGSERIAL PRIMARY KEY,
    quotation_id    BIGINT NOT NULL REFERENCES ims.quotations(id),
    revision_number INT    NOT NULL DEFAULT 1,
    revised_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    revised_by      BIGINT REFERENCES users(id),
    reason          TEXT,
    snapshot        JSONB  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_quot_rev_quotation_id ON ims.quotation_revisions(quotation_id);
