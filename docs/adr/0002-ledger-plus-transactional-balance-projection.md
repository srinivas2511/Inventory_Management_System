# ADR-02: Ledger plus transactional balance projection

- **Status:** Accepted
- **Date:** 2026-10-01
- **Source:** ARCHITECTURE.md §20

## Decision
Stock is derived from an append-only ledger; a balance table is kept in step in the same transaction.

## Rationale
The requirements mandate ledger-derived stock; the projection gives O(1) availability checks and row-level locking.

## Alternatives rejected
Ledger-only (slow availability checks); balance-only (violates the requirement).
