# ADR-09: Short-lived JWT with rotating HttpOnly refresh cookie; permission version in token

- **Status:** Accepted
- **Date:** 2026-10-01
- **Source:** ARCHITECTURE.md §20

## Decision
15-minute access token held in memory; refresh token in an HttpOnly cookie; token carries a permission version.

## Rationale
Limits XSS exposure and makes revocation and permission changes effective quickly.

## Alternatives rejected
Long-lived JWT in localStorage.
