# ADR-15: Self-host UI fonts and icons

- **Status:** Accepted
- **Date:** 2026-10-01
- **Source:** ARCHITECTURE.md §20 (Phase 0 addition)

## Decision
Roboto and Material Icons are installed from npm and bundled, not loaded from Google Fonts.

## Rationale
The production build tries to inline remote fonts and fails without internet; factory networks and sandboxed CI may have none; also avoids third-party requests.

## Alternatives rejected
Disabling font inlining and relying on the Google CDN at runtime.
