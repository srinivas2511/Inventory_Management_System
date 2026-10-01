-- Phase 0 baseline. Version 0.1 deliberately sorts before the Phase 1 schema migration (V1).
-- pg_trgm powers fast ILIKE search on codes and names (DESIGN.md section 8.6). It is a trusted extension
-- in PostgreSQL 13+, so the database owner can create it without superuser rights.
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;
