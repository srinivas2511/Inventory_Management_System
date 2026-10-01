# ADR-11: Corrections by reversal; database triggers enforce immutability

- **Status:** Accepted
- **Date:** 2026-10-01
- **Source:** ARCHITECTURE.md §20

## Decision
Ledger and audit tables reject UPDATE/DELETE at the database level.

## Rationale
Satisfies 'history must not be silently deleted' even against a faulty script or a rogue user.

## Alternatives rejected
Application-level convention only.
