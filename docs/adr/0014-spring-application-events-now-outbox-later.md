# ADR-14: Spring application events now, outbox later

- **Status:** Accepted
- **Date:** 2026-10-01
- **Source:** ARCHITECTURE.md §20

## Decision
In-process events after commit; a path to an outbox/broker for IoT and ERP integration.

## Rationale
Simplicity today with a defined growth path.

## Alternatives rejected
Message broker from day one (over-engineering).
