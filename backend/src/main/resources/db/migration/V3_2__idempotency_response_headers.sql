-- Phase 1 / task 1.7 - replayed responses must carry the same Location (and content type) as the original,
-- so the headers worth keeping are stored beside the status and body (DESIGN.md section 2.2).
ALTER TABLE idempotency_keys ADD COLUMN response_headers JSONB;
