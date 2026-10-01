# ADR-12: Flyway migrations and Testcontainers PostgreSQL

- **Status:** Accepted
- **Date:** 2026-10-01
- **Source:** ARCHITECTURE.md §20

## Decision
Schema is owned by Flyway (ddl-auto=validate); tests run on real PostgreSQL.

## Rationale
Tests exercise the same engine, constraints, triggers and partial indexes used in production.

## Alternatives rejected
H2 in-memory (divergent behaviour).
