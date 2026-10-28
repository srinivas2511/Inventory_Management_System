# ERP Design Gaps

**Created:** 2026-10-28

## Phase 9 Gaps (design review 9.0, commit f4ebaad)

| ID | Description | Resolution | Status |
|---|---|---|---|
| G1 | Migration numbers: DESIGN-ERP §1.3 uses V12/V13; project is at V63 | V12→V64, V13→V65, mapped in PLAN-ERP.md | RESOLVED |
| G2 | `uom_conversions` name conflict check | V64 creates new `uom_conversions` table; inventory tables don't have this name | RESOLVED |
| G3 | `document_links` name collision with existing DMS `documents` package | ERP table renamed `transaction_links` in V65 | RESOLVED |
| G4 | `document` Java package reserved for DMS | ERP commercial kernel package: `com.mfg.ims.doccore` | RESOLVED |
| G5 | `notification_preferences` (new V65) vs `notification_subscriptions` (V31) | Two coexisting tables with different purposes | RESOLVED |

## Open Decisions (deferred E1–E8)

These decisions from docs/decisions.md are deferred to the phases that need them:

| Decision | Description | Target Phase |
|---|---|---|
| E1 | File storage backend for attachments (local FS vs S3) | Phase 9 |
| E2 | GST sandbox credentials availability | Phase 12 |
| E3 | Functional currency and costing method per company | Phase 12 |
| E4 | Invoice numbering statutory compliance rules | Phase 12 |
| E5 | Tally integration mode (browser helper vs file export) | Phase 16 |
| E6 | Payment gateway provider selection | Phase 16 |
| E7 | Barcode format standards (EAN-13 vs QR vs Code128) | Phase 13 |
| E8 | MRP lot sizing default configuration | Phase 13 |

## Phase 10+ Gaps
*To be recorded before each phase begins.*
