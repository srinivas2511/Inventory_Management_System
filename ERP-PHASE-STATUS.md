# ERP Phase Status — Phases 9–17

**Created:** 2026-10-28  
**Last updated:** 2026-10-28  
**Session:** Implementing ERP Phases 9–17

## Migration Mapping (DESIGN-ERP §1.3 → actual)

| Design ref | Actual file | Phase | Content |
|---|---|---|---|
| V12 | V64 | 9 | UOM conversions, item categories/series, item_kind, company profile, branches, bank accounts, transporters, payment terms, T&C, teams, notification prefs, document_series |
| V13 | V65 | 9 | Custom fields, tags, comments, attachments, transaction_links, search_index, approval rules+columns, import_jobs, jobs |
| V14 | V66 | 10 | RFQ, quotes, PO extensions, GRN/QIR links, purchase invoices, match, purchase returns |
| V15 | V67 | 11 | Quotations, order confirmations, delivery challans, dispatch communication |
| V16 | V68 | 12 | Currencies, rates, tax codes/rates, accounts, mappings, fiscal periods, journal, balances, open items, triggers |
| V17 | V69 | 12 | Sales invoices, notes, payments, allocations, advance vouchers, TDS, reminders, e-invoice/e-way tables, GST report views |
| V18 | V70 | 13 | Multi-level BOM, alternates, MRP, costing, labels, barcodes, min/max, stock card views |
| V19 | V71 | 14 | Job work, service orders/confirmations, SUBCONTRACT location type |
| V20 | V72 | 15 | BI views and materialized views, saved reports, registers |
| V21 | V73 | 16 | Outbox, inbox, connections, calls, Tally/Busy rules, tracking, gateway tables |
| V22+ | V74+ | 17 | Indexes/partitions from performance review, retention jobs |

## Design gaps (from commit f4ebaad design review 9.0)

| Gap | Description | Resolution |
|---|---|---|
| G1 | Migration numbers: DESIGN-ERP §1.3 uses V12/V13; project is at V63 | V12→V64, V13→V65, etc. (committed to PLAN-ERP.md) |
| G2 | `uom_conversions` name conflict with existing inventory table if any | V64 creates new table; verify no name collision |
| G3 | `document_links` name collision with existing DMS package | ERP table renamed `transaction_links` in V65 |
| G4 | `document` Java package is DMS (Document Management System) | ERP commercial kernel Java package is `com.mfg.ims.doccore` |
| G5 | `notification_preferences` (V65) vs existing `notification_subscriptions` (V31) | Two coexisting tables; notification_preferences is per user/event/channel toggle |

Open design decisions E1–E8 deferred (see docs/decisions.md).

---

## Phase 9 — Platform Extensions

**Status:** `IN_PROGRESS`  
**Target migrations:** V64, V65  
**Requirement IDs:** ERP-A-1 through ERP-A-9 (committed scope); ERP-A-10..15 in working tree (tasks 9.11-9.14)

| Task | Description | Status | Notes |
|---|---|---|---|
| 9.0 | Design review | `TESTS_PASSED` | Complete 2026-10-28; 5 gaps recorded |
| 9.1 | UOM conversion, item categories, custom item series | `NOT_STARTED` | V64 |
| 9.2 | Company profile: logos, signatures, bank, addresses, T&C, payment terms, transporters | `NOT_STARTED` | V64 |
| 9.3 | Document series (auto/manual/reset) and discount; migrate existing numbering | `NOT_STARTED` | V64 |
| 9.4 | Custom fields and tags | `NOT_STARTED` | V65 |
| 9.5 | Comments, attachments, transaction timeline | `NOT_STARTED` | V65 |
| 9.6 | Configurable approval engine | `NOT_STARTED` | V65 |
| 9.7 | Teams, team/report permissions, notification settings | `NOT_STARTED` | V64 |
| 9.8 | Excel bulk-upload framework | `NOT_STARTED` | V65 |
| 9.9 | Global search | `NOT_STARTED` | V65 |
| 9.10 | Frontend for all of the above | `NOT_STARTED` | Angular components |
| 9.11 | Item–vendor mapping, item delete/deactivate rules, bulk edit | `NOT_STARTED` | Working tree scope |
| 9.12 | Document reminders, @mentions, team notification defaults | `NOT_STARTED` | Working tree scope |
| 9.13 | Explainability, custom-field propagation, party override, packaging | `NOT_STARTED` | Working tree scope |
| 9.14 | Setup wizard, Help menu, version banner, demo-data reset | `NOT_STARTED` | Working tree scope |

### Phase 9 Exit Gates
- [ ] Gate A — All tasks 9.1-9.10 (committed scope) mapped to implementation and tests
- [ ] Gate B — No placeholder endpoints remain
- [ ] Gate C — V64, V65 migrations pass on real PostgreSQL
- [ ] Gate D — Backend unit + integration tests pass
- [ ] Gate E — Frontend lint, unit tests, production build pass
- [ ] Gate F — Playwright acceptance tests pass
- [ ] Gate G — Code review: no unresolved critical/high findings
- [ ] Gate H — Existing E2E suite (388 tests, 387/388 expected) still passes
- [ ] Gate I — Documentation updated
- [ ] Gate J — ERP-A-1..9 requirement IDs marked implemented+verified
- [ ] Gate K — CLAUDE.md status line updated

---

## Phase 10 — Advanced Purchase  
**Status:** `NOT_STARTED` (prerequisite: Phase 9)

## Phase 11 — Quotations, OC, Challans  
**Status:** `NOT_STARTED` (prerequisite: Phase 10)

## Phase 12 — Finance, GST, Payments  
**Status:** `NOT_STARTED` (prerequisite: Phases 10+11)

## Phase 13 — Advanced Manufacturing, MRP, Barcodes  
**Status:** `NOT_STARTED` (prerequisite: Phase 11)

## Phase 14 — Sub-contract and Service  
**Status:** `NOT_STARTED` (prerequisite: Phases 12+13)

## Phase 15 — BI, Registers, Report Builder  
**Status:** `NOT_STARTED` (prerequisite: Phases 12-14)

## Phase 16 — Integrations  
**Status:** `NOT_STARTED` (prerequisite: Phase 12)

## Phase 17 — ERP Hardening and Release  
**Status:** `NOT_STARTED` (prerequisite: Phases 9-16)
