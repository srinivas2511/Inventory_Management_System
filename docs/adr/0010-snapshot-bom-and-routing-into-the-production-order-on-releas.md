# ADR-10: Snapshot BOM and routing into the production order on release

- **Status:** Accepted
- **Date:** 2026-10-01
- **Source:** ARCHITECTURE.md §20

## Decision
Release copies the active BOM and routing into the order.

## Rationale
Later revisions never change running orders; traceability is reproducible.

## Alternatives rejected
Live reference to the BOM.
