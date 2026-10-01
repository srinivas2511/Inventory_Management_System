# ADR-01: Modular monolith

- **Status:** Accepted
- **Date:** 2026-10-01
- **Source:** ARCHITECTURE.md §20

## Decision
One deployable with package-by-feature modules and verified boundaries.

## Rationale
ACID transactions across stock and documents, easy local run; modules keep the option to split later.

## Alternatives rejected
Microservices: distributed transactions on inventory are the hardest problem here.
