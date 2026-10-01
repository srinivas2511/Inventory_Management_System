# ADR-06: Relational columns plus JSONB for spring specs with a metadata catalogue

- **Status:** Accepted
- **Date:** 2026-10-01
- **Source:** ARCHITECTURE.md §20

## Decision
Common fields are columns; type-specific attributes are JSONB validated by attribute definitions that also drive the UI form.

## Rationale
New spring types or attributes need no schema or code change, yet stay validated and indexable.

## Alternatives rejected
Pure EAV (painful queries); one table per spring type (rigid); unvalidated free-form JSON.
