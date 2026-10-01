# ADR-04: Add MATERIAL_CONSUMPTION ledger type

- **Status:** Accepted
- **Date:** 2026-10-01
- **Source:** ARCHITECTURE.md §20

## Decision
An additive transaction type for material consumed in production.

## Rationale
Without it consumed wire would remain in WIP-location stock forever.

## Alternatives rejected
Tracking consumption outside the ledger (violates 'every stock change creates a transaction').
