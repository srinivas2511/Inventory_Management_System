# ADR-07: Permissions as data; authorities checked server-side

- **Status:** Accepted
- **Date:** 2026-10-01
- **Source:** ARCHITECTURE.md §20

## Decision
Roles map to permission codes in the database; the backend enforces them on every endpoint.

## Rationale
Meets 'managed centrally by the backend'; roles are editable without code changes.

## Alternatives rejected
Role-name checks in code (brittle); frontend-only guards (insecure).
