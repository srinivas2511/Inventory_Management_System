# ADR-16: Runtime configuration via /config.json

- **Status:** Accepted
- **Date:** 2026-10-01
- **Source:** ARCHITECTURE.md §20 (Phase 0 addition)

## Decision
The SPA loads its API base URL and name from /config.json at startup.

## Rationale
One build artifact can be deployed to any environment by replacing a file.

## Alternatives rejected
Build-time environment files (a rebuild per environment).
