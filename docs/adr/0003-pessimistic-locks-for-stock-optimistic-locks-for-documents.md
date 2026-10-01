# ADR-03: Pessimistic locks for stock, optimistic locks for documents

- **Status:** Accepted
- **Date:** 2026-10-01
- **Source:** ARCHITECTURE.md §20

## Decision
Row locks (ordered) when posting stock; @Version on documents returning 409.

## Rationale
Stock contention is real and correctness-critical; documents rarely conflict.

## Alternatives rejected
Optimistic locking for stock (retry storms).
