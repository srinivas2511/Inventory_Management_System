# ADR-05: Stock status as a balance dimension; status changes are transfers

- **Status:** Accepted
- **Date:** 2026-10-01
- **Source:** ARCHITECTURE.md §20

## Decision
QUARANTINE/HOLD/REJECTED/QUALITY_PENDING are first-class statuses of a balance row.

## Rationale
Quality gates become fully auditable and support partial acceptance.

## Alternatives rejected
A status flag on batches only (loses partial acceptance and ledger history).
