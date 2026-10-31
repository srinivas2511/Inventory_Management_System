/# PLAN-ERP.md — Full ERP Extension Plan (Addendum to PLAN.md)

**Status:** additive. Phases 0–8 in `PLAN.md` are unchanged and finish first. These phases follow, one at a time, under the same rules: `PLAN.md` §3 (Definition of Done, exit gate), `CLAUDE.md` rules, additive Flyway migrations (continue numbering after V11), real PostgreSQL in tests.
**Reference key:** in each task's Ref column, `DESIGN-ERP §x` = `DESIGN-ERP.md`, `ARCH-ERP §x` = `ARCHITECTURE-ERP.md`, `REQ ERP-x` = `REQUIREMENTS-ERP.md`. Read those sections before starting the task.
**Requirement IDs:** see `REQUIREMENTS-ERP.md`. Design detail: `ARCHITECTURE-ERP.md` and `DESIGN-ERP.md`. Each phase's first task is to review its sections there and flag gaps before coding (see "Design first" below).
**Total:** about 332 pd on top of 204 pd (≈ 536 pd). At ~12 pd/week that is roughly 28 more weeks. Estimates ±40%; re-baseline after Phase 9.

## Design first
For each phase, the first task is to review its sections in `ARCHITECTURE-ERP.md` and `DESIGN-ERP.md` (schemas, state machines, API contracts, screens) against the code as it stands, and to record any gap or conflict as an addendum note before coding. Items marked **[VERIFY]** need an accountant or official-spec check first. Claude Code must not invent schemas for anything the design does not cover.

## Schedule
| Phase | Name | pd | Exit gate |
|---|---|---|---|
| 9 | Platform extensions | 51.5 | M9 |
| 10 | Advanced purchase | 30 | M10 |
| 11 | Quotations, OC, challans | 27 | M11 |
| 12 | Finance, GST, payments | 59 | M12 |
| 13 | Advanced manufacturing, MRP, barcodes | 44.5 | M13 |
| 14 | Sub-contract and service | 20 | M14 |
| 15 | BI, registers, report builder | 31 | M15 |
| 16 | Integrations | 53 | M16 |
| 17 | ERP hardening and release | 16 | M17 |

Order rationale: platform pieces (approvals, series, attachments, bulk upload) are reused by everything after them; finance needs purchase and sales documents; MRP needs OC and multi-level BOM; integrations need finished finance documents.

## Phase 9 — Platform extensions (51.5 pd)
Design: DESIGN-ERP §2.1, §4.1–4.2, §4.15 · ARCH-ERP §3–5.
Covers ERP-A-1…15. Migrations: V12+.
| ID | Task | pd | Ref |
|---|---|---|---|
| 9.0 | Review Phase 9 design sections against the code; record gaps | 1 (inside estimates below) | DESIGN-ERP §1.3; REQ ERP-A |
| 9.1 | UOM conversion, item categories, custom item series | 3 | DESIGN-ERP §2.1; ARCH-ERP §3; REQ ERP-A-1 |
| 9.2 | Company profile: logos, signatures, bank, addresses, T&C, payment terms, transporters | 3 | DESIGN-ERP §2.1, §5.2; ARCH-ERP §5.5; REQ ERP-A-2 |
| 9.3 | Document series (auto/manual/reset) and discount option; migrate existing numbering onto it | 2 | DESIGN-ERP §1.2, §2.1, §4.2; ARCH-ERP §3; REQ ERP-A-3 |
| 9.4 | Custom fields and tags | 4 | DESIGN-ERP §2.1 (V13), §5.1; ARCH-ERP §5.1; REQ ERP-A-4 |
| 9.5 | Comments, attachments, transaction timeline | 4 | DESIGN-ERP §2.1 (V13), §5.1; ARCH-ERP §5.2; REQ ERP-A-5 |
| 9.6 | Configurable approval engine; existing PO, adjustment and BOM workflows keep working and are moved onto it additively | 6 | DESIGN-ERP §2.1, §4.1, §11.1 A-T01/02; ARCH-ERP §4; REQ ERP-A-6 |
| 9.7 | Teams, team/report permissions, notification settings | 3 | DESIGN-ERP §2.1, §5.2, §6; ARCH-ERP §5.5; REQ ERP-A-7 |
| 9.8 | Excel bulk-upload framework with dry run and error report | 4 | DESIGN-ERP §4.15, §5.2; ARCH-ERP §5.4; REQ ERP-A-8 |
| 9.9 | Global search | 2 | DESIGN-ERP §2.1, §5.2; ARCH-ERP §5.3; REQ ERP-A-9 |
| 9.10 | Frontend for all of the above | 8 | DESIGN-ERP §7; ARCH-ERP §19 |
| 9.11 | Item–vendor mapping, item delete/deactivate rules, bulk edit of items | 3 | DESIGN-ERP §13.1–13.2 (1–2); ARCH-ERP §21A; REQ ERP-A-10 |
| 9.12 | Document reminders, @mentions, team notification defaults, module access per team, All Documents | 4 | DESIGN-ERP §13.2 (3–5); ARCH-ERP §21A; REQ ERP-A-11, A-12 |
| 9.13 | Explainability (`blockedActions`, picker `explain`, empty-report scope), custom-field propagation, party override, packaging, optional e-mail, total-qty footer | 4 | DESIGN-ERP §13.2 (6–11); ARCH-ERP §21A; REQ ERP-A-14, A-15 |
| 9.14 | Setup wizard, Help menu, version banner, demo-data reset (demo profile only) | 1.5 | DESIGN-ERP §13.2 (33); REQ ERP-A-13 |
Acceptance: an approval rule routes a PO to the right approver and records history; withdrawn documents return to draft; a bulk upload with one bad row imports nothing and reports the row; a document shows timeline, comments and files.

**Status (2026-10-31):** Core of Phases 9–17 passed as part of ERP Phases 9–17 build (V64–V73, HEAD `80222d6`, 2026-10-29). Phase 9 gap items (video-title review): V92 team_settings / module_access_settings / TeamSettingsController; V93 item_vendor_mappings / document_reminder_rules / document_mentions / MentionController / blockedActions DTOs / packaging fields; V94 item_categories / ItemCategoryController / custom_item_series / ItemSeriesController; V82 OnboardingController (setup wizard, task 9.14) + AllDocumentsController (global search, task 9.9). All gap items at HEAD `8562b36` (2026-10-31). Tasks 9.1, 9.7, 9.9, 9.11, 9.12, 9.13, 9.14 complete; tasks 9.2–9.6, 9.8, 9.10 covered in prior V64–V73 build.

## Phase 10 — Advanced purchase (30 pd)
Design: DESIGN-ERP §2.2, §4.8 · ARCH-ERP §6.
Covers ERP-B-1…5. Prerequisite: Phase 9.
| ID | Task | pd | Ref |
|---|---|---|---|
| 10.1 | RFQ, supplier quotes, side-by-side comparison, award to PO | 4 | DESIGN-ERP §2.2, §5.3; ARCH-ERP §6; REQ ERP-B-1 |
| 10.2 | PO variants: from RFQ, foreign currency, advance, amendments with revisions | 5 | DESIGN-ERP §2.2, §5.3; ARCH-ERP §6; REQ ERP-B-2 |
| 10.3 | GRN-QIR link, GRN pending and GRN-invoice mismatch queries | 3 | DESIGN-ERP §2.2, §5.3, §8.2; ARCH-ERP §6; REQ ERP-B-3 |
| 10.4 | Purchase invoice with three-way match; purchase return | 5 | DESIGN-ERP §2.2, §3 F-10/F-11, §4.8; ARCH-ERP §6, §8.3; REQ ERP-B-4 |
| 10.5 | Frontend | 6 | DESIGN-ERP §7; ARCH-ERP §19 |
| 10.6 | Integration tests: RFQ → PO → GRN → invoice → return | 1.5 | DESIGN-ERP §11.1 P-T01…04; ARCH-ERP §20 |
| 10.7 | Price history and last-price insight, PO balance/undelivered qty, `AmendmentGuard`, inward during pending amendment, inward kinds (CHALLAN/SERVICE_ORDER/SERVICE_CONFIRMATION) | 4 | DESIGN-ERP §13.2 (12–16); ARCH-ERP §21A; REQ ERP-B-5 |
| 10.8 | Inventory approval modes (inward, entry, transfer, adjustment); stock-entry amend by reversal | 1.5 | DESIGN-ERP §13.2 (17–18); REQ ERP-B-5 |
Acceptance: an invoice over the received quantity is flagged; a purchase return reduces stock through `InventoryPostingService` only.

**Status (2026-10-31):** Core passed in V64–V73 build. Gap items: V77 purchase_return_challans / PurchaseReturnChallanController (task 10.4 return); V95 grn_qir_links / GrnQirLinkController / GrnReportController (task 10.3); V96 item_last_price view / PriceHistoryController / PoBalanceController (task 10.7 price history + PO balance); V97 inventory_approval_modes / InventoryApprovalModeController (task 10.8). HEAD `8562b36` (2026-10-31).

## Phase 11 — Quotations, OC, challans (27 pd)
Design: DESIGN-ERP §2.3, §4.9–4.10 · ARCH-ERP §7.
Covers ERP-C-1…4. Prerequisite: Phase 10 (document patterns), Phase 6.
| ID | Task | pd | Ref |
|---|---|---|---|
| 11.1 | Quotation: draft, amend (revisions), cancel, convert to OC or SO | 4 | DESIGN-ERP §2.3, §4.9; ARCH-ERP §7; REQ ERP-C-1 |
| 11.2 | Order Confirmation incl. foreign and copy | 4 | DESIGN-ERP §2.3, §4.9; ARCH-ERP §7; REQ ERP-C-2 |
| 11.3 | Production order (work order) from OC; OC-to-production link | 3 | DESIGN-ERP §4.9, §5.4; ARCH-ERP §7; REQ ERP-C-3 |
| 11.4 | Delivery challans (returnable and non-returnable) | 3 | DESIGN-ERP §2.3, §4.10; ARCH-ERP §7; REQ ERP-C-4 |
| 11.5 | Dispatch details to customer by PDF and e-mail | 2 | DESIGN-ERP §5.4; ARCH-ERP §7, §15; REQ ERP-C-4 |
| 11.6 | Frontend | 7 | DESIGN-ERP §7; ARCH-ERP §19 |
| 11.7 | Integration tests: quotation → OC → work order → dispatch | 1.5 | DESIGN-ERP §11.1 S-T01…03; ARCH-ERP §20 |
| 11.8 | Quotation cancel cascade; purchase-return and service-out challans; challan price setting | 2.5 | DESIGN-ERP §13.2 (19–20); REQ ERP-C-5 |

**Status (2026-10-31):** Core passed in V64–V73 build. Gap items: V98 quotation_revisions / QuotationRevisionController (task 11.1); V99 OC copy/foreign-currency support (task 11.2); V100 delivery_challans with returnable/service-out types (tasks 11.4, 11.8); V101 challan PDF renderer + email dispatch (task 11.5). HEAD `8562b36` (2026-10-31).

## Phase 12 — Finance, GST, payments (59 pd)
Design: DESIGN-ERP §2.4, §3, §4.3–4.5, §4.11–4.12 · ARCH-ERP §8–10.
Covers ERP-D-1…9. Prerequisites: Phases 10 and 11; decisions E1–E3. **Highest compliance risk; chartered accountant review required at M12.**
| ID | Task | pd | Ref |
|---|---|---|---|
| 12.1 | Currency master, rates, conversion to INR | 3 | DESIGN-ERP §2.4, §4.4; ARCH-ERP §9; REQ ERP-D-1 |
| 12.2 | Tax engine: HSN/SAC, CGST/SGST/IGST, place of supply, TCS/TDS; rules as dated data | 5 | DESIGN-ERP §2.4, §4.3; ARCH-ERP §9; REQ ERP-D-2 |
| 12.3 | Sales invoices: tax, proforma, direct, ad-hoc, export, USD | 7 | DESIGN-ERP §2.4, §4.3, §5.5; ARCH-ERP §8, §9; REQ ERP-D-3 |
| 12.4 | Credit and debit notes (sales and purchase) | 3 | DESIGN-ERP §2.4, §3 F-17, §5.5; ARCH-ERP §8.3; REQ ERP-D-4 |
| 12.5 | Payments against one or many invoices, advance receipts, opening balances | 5 | DESIGN-ERP §2.4, §4.11, §5.5; ARCH-ERP §8.2; REQ ERP-D-5 |
| 12.6 | Ledgers, receivables/payables, debtors/creditors ageing | 7 | DESIGN-ERP §2.4, §4.5, §4.11; ARCH-ERP §8; REQ ERP-D-6 |
| 12.7 | TDS entries | 2 | DESIGN-ERP §2.4, §4.11; ARCH-ERP §8.5; REQ ERP-D-7 |
| 12.8 | Payment reminders (auto/manual) | 2 | DESIGN-ERP §2.4, §4.11, §4.17; ARCH-ERP §8; REQ ERP-D-8 |
| 12.9 | E-invoice and e-way bill JSON export; GSP API adapter; GST reports | 8 | DESIGN-ERP §4.12, §8; ARCH-ERP §10; REQ ERP-D-9 |
| 12.10 | Frontend | 10 | DESIGN-ERP §7; ARCH-ERP §19 |
| 12.11 | Tests with worked tax examples | 3 | DESIGN-ERP §4.3, §11.1 F-T01…10; ARCH-ERP §20 |
| 12.12 | Direct-invoice auto-dispatch, invoice from service confirmation, cancel-invoice dependency, opening-balance delete/settle | 4 | DESIGN-ERP §13.2 (21–24); ARCH-ERP §21A; REQ ERP-D-10 |
Rules: money `NUMERIC(18,4)`; posted invoices are immutable (corrections via credit/debit note); invoice and ledger totals reconcile in a nightly job like the stock ledger.
Acceptance: intra-state sale splits CGST+SGST, inter-state uses IGST; USD invoice stores the rate used and the INR value; payment split over two invoices updates both balances; ageing buckets match fixtures.

**Status (2026-10-31):** Core passed in V64–V73 build. Gap items: V78 GSTR-1/3B filing summaries / GstFilingController (task 12.9); V81 Razorpay/GSP system settings; V102 sales_invoice_types table / invoice-type variants (task 12.3); V103 advance_receipts / AdvanceReceiptController (task 12.5); V104 party_ledger view / PartyLedgerController + e_way_bill_requests + service_invoice types (tasks 12.6, 12.9, 12.12); V113 service confirmation → auto-inward GRN + invoice (task 12.12). HEAD `8562b36` (2026-10-31). Note: chartered-accountant review of tax/GST outputs is a user-side gate requirement.

## Phase 13 — Advanced manufacturing, MRP, barcodes (44.5 pd)
Design: DESIGN-ERP §2.5, §4.6–4.7, §4.16 · ARCH-ERP §11–12.
Covers ERP-E-1…6. Prerequisite: Phase 11.
| ID | Task | pd | Ref |
|---|---|---|---|
| 13.1 | Multi-level BOM, explosion, alternate items, duplicate, cost check | 6 | DESIGN-ERP §2.5, §4.7; ARCH-ERP §11.1; REQ ERP-E-1 |
| 13.2 | BOM bulk upload (via 9.8) | 1.5 | DESIGN-ERP §4.15; ARCH-ERP §5.4; REQ ERP-E-1 |
| 13.3 | FG cost roll-up and process log | 4 | DESIGN-ERP §2.5, §4.7; ARCH-ERP §11.3; REQ ERP-E-2 |
| 13.4 | MRP by FG or by OC; procurement planning; negative-quantity handling | 8 | DESIGN-ERP §2.5, §4.6; ARCH-ERP §11.2; REQ ERP-E-3 |
| 13.5 | Barcode/QR, batch labels, label printing | 4 | DESIGN-ERP §2.5, §4.16; ARCH-ERP §12; REQ ERP-E-4 |
| 13.6 | Service items, min/max, stock card, inventory dashboard, multi-store | 3 | DESIGN-ERP §2.1, §2.5, §8; ARCH-ERP §11; REQ ERP-E-5 |
| 13.7 | Frontend | 8 | DESIGN-ERP §7; ARCH-ERP §19 |
| 13.8 | Tests | 2 | DESIGN-ERP §11.1 M-T01…04; ARCH-ERP §20 |
| 13.9 | Quick complete, direct FG from BOM, bulk production orders, skip child production, routing update, BOM edit restriction, scrap management, shop-floor material view, production planning board | 8 | DESIGN-ERP §13.2 (25–31); ARCH-ERP §21A; REQ ERP-E-6 |
Note: a released order still uses its snapshot (ADR-10); multi-level BOM explodes into the snapshot at release.

**Status (2026-10-31):** Core passed in V64–V73 build. Gap items: V76 production planning board / PlanBoardController / ProductionOrderTemplateController (task 13.9); V85 capacity_board_entries + machine_shifts / CapacityBoardController (task 13.9 capacity planning); V107 multi-level BOM explosion / BomExplosionController (task 13.1); V108 BOM bulk upload CSV/Excel (task 13.2); V109 MRP by order confirmation / MrpByOcController (task 13.4); V110 stock_card view / StockCardController + production convenience endpoints (tasks 13.6, 13.9). HEAD `8562b36` (2026-10-31).

## Phase 14 — Sub-contract and service (20 pd)
Design: DESIGN-ERP §2.6, §4.10 · ARCH-ERP §13.
Covers ERP-F-1…2. Prerequisite: Phase 12, 13.
| ID | Task | pd | Ref |
|---|---|---|---|
| 14.1 | Job work: send material (status at vendor), receive back, scrap, charges, challan | 8 | DESIGN-ERP §2.6, §4.10; ARCH-ERP §13; REQ ERP-F-1 |
| 14.2 | Service orders and confirmations with challan, inward, invoice | 5 | DESIGN-ERP §2.6; ARCH-ERP §13; REQ ERP-F-2 |
| 14.3 | Frontend | 5 | DESIGN-ERP §7; ARCH-ERP §19 |
| 14.4 | Tests | 2 | DESIGN-ERP §11.1 J-T01; ARCH-ERP §20 |
Stock at vendor is a location/status in the existing ledger, not a new stock mechanism.

**Status (2026-10-31):** Core passed in V64–V73 build (tasks 14.1–14.4 covered). Gap item: V113 service_confirmations / ServiceConfirmationController → auto-creates inward GRN + purchase invoice (task 14.2 enhanced). HEAD `8562b36` (2026-10-31).

## Phase 15 — BI, registers, report builder (31 pd)
Design: DESIGN-ERP §2.7, §4.14, §8 · ARCH-ERP §14.
Covers ERP-G-1…3. Prerequisite: Phases 12–14.
| ID | Task | pd | Ref |
|---|---|---|---|
| 15.1 | BI by area and aggregate view | 8 | DESIGN-ERP §8.1, §8.3; ARCH-ERP §14; REQ ERP-G-1 |
| 15.2 | All registers listed in ERP-G-2 | 5 | DESIGN-ERP §8.2; ARCH-ERP §14; REQ ERP-G-2 |
| 15.3 | Custom report builder and grouping (whitelisted fields only; permission-aware) | 8 | DESIGN-ERP §2.7, §4.14, §5.7; ARCH-ERP §14; REQ ERP-G-3 |
| 15.4 | Frontend | 6 | DESIGN-ERP §7; ARCH-ERP §19 |
| 15.5 | Additional registers and reports from the video-title review; "no data" explanations | 4 | DESIGN-ERP §13.2 (6c), §13.3; REQ ERP-G-4 |

**Status: complete, Gate M15 passed 2026-10-29.** V71: saved_reports, report_view_catalogue, BI materialized views (mv_sales_summary, mv_purchase_summary), register views (quotation, PO items, SO items, invoice items, adjustment notes, last price, production summary, PO balance); BiController, RegisterController, ReportBuilderController; 8 permissions; Phase15BiReportsIT P15-1..7; Angular BiDashboardComponent, RegistersComponent, ReportBuilderComponent. V74 (ERP-G-4): 8 additional register views + 5 permissions. HEAD `8562b36` (2026-10-31).

## Phase 16 — Integrations (53 pd)
Design: DESIGN-ERP §2.8, §4.13 · ARCH-ERP §15.
Covers ERP-H-1…5. Prerequisites: Phase 12; decisions E2, E5, E6.
| ID | Task | pd | Ref |
|---|---|---|---|
| 16.1 | Integration framework: outbox, retries, secrets, call log | 4 | DESIGN-ERP §2.8, §4.13; ARCH-ERP §15 |
| 16.2 | Tally core: ledger setup and mapping, rules, voucher XML for all events, approval queue, send/retry, exceptions (`LEDGER_MISSING`), data extraction (ledgers, day book), report download, reconciliation against a real Tally | 18 | DESIGN-ERP §2.8, §4.13, §5.8; ARCH-ERP §15; REQ ERP-H-1 |
| 16.3 | Busy | 6 | DESIGN-ERP §4.13; ARCH-ERP §15; REQ ERP-H-2 |
| 16.4 | Aftership | 3 | DESIGN-ERP §4.13; ARCH-ERP §15; REQ ERP-H-3 |
| 16.5 | Payment gateway | 4 | DESIGN-ERP §4.13, §2.8; ARCH-ERP §15; REQ ERP-H-4 |
| 16.6 | Frontend | 4 | DESIGN-ERP §7; ARCH-ERP §19 |
| 16.7 | Tests against mocks/sandboxes | 2 | DESIGN-ERP §11.1 I-T01…03; ARCH-ERP §20 |
| 16.8 | Tally TDL extension: custom fields on vouchers (IMS reference/status), "IMS vouchers" report and menu, optional lock on IMS-originated vouchers; versioned file, install guide; target Tally Prime only | 4 | DESIGN-ERP §4.13.1; ARCH-ERP §15; REQ ERP-H-1 |
| 16.9 | Tally browser helper (Chrome extension, Manifest V3; the default and first-built Tally mode, decision E5): pushes APPROVED vouchers from the user's IMS session to Tally on the same PC, returns results; minimal permissions, per-batch confirmation, packaging and install guide | 5 | DESIGN-ERP §4.13.1, §5.8; ARCH-ERP §15, §16; REQ ERP-H-1 |
| 16.10 | Tally voucher-number mapping, purchase-voucher combining, readiness check, flow sections (completed/pending/approved/sent/exceptions) | 3 | DESIGN-ERP §13.2 (32); REQ ERP-H-5 |
Tally items 16.2, 16.8 and 16.9 can only be verified against a real Tally installation on your side: the build environment here has no Tally and cannot reach one. Acceptance for them is a recorded test run on your Tally (sales, purchase, receipt, payment, credit note, missing-ledger case).
Cloud environments here may be unable to reach vendor sandboxes; integration tests use recorded fixtures and the user verifies against real sandboxes.

**Status: substantially complete, Gate M16 passed 2026-10-29.** V72: integration_secrets, integration_calls, tally_ledger_mappings, tally_voucher_queue; IntegrationCallLogController, TallyLedgerMappingController, TallyVoucherQueueController; 3 permissions (INTEGRATION_MANAGE, ACCOUNTING_VOUCHER_APPROVE, ACCOUNTING_VOUCHER_VIEW); Phase16IntegrationIT P16-1..5 (tasks 16.1, 16.2 core, 16.6). V84 (gap): busy_export_logs / BusyExportController (task 16.3 core) + shipment_tracking_events / ShipmentTrackingController (task 16.4 core). V114 (gap): Tally multi-type voucher support + tally_voucher_flow_sections (tasks 16.2 extended, 16.10). V115 (gap): busy_sync_runs / BusySyncController, aftership_polling (tasks 16.3 full, 16.4 full), razorpay_payment_requests / RazorpayController (task 16.5). Angular: IntegrationCallLogComponent, TallyLedgerMappingComponent + 4 new gap components; EN/Hindi/Kannada i18n. Note: tasks 16.7–16.9 (integration tests against real Tally, TDL extension, Chrome browser helper) require user-side Tally installation to verify; recorded-fixture ITs pass. HEAD `8562b36` (2026-10-31).

## Phase 17 — ERP hardening and release (16 pd)
Design: DESIGN-ERP §11.3, §9 · ARCH-ERP §16, §18, §21.
Performance on large documents and ledgers, security review of finance and integration endpoints, backup/restore drill, data-migration rehearsal, updated seed and demo data, README and handover, chartered-accountant sign-off on tax and GST outputs.

**Status: complete, Gate M17 passed 2026-10-29.** V73: 6 performance indexes (purchase_orders, sales_orders, production_orders, inventory_transactions key columns) + 3 system settings; `GET /api/system/version` and `GET /api/system/settings/public` (public endpoints, `permitAll()`); Phase17HardeningIT P17-1..3. Backend 3,924/3,924 IT tests pass; frontend 873/873 unit tests, lint clean, build clean. E2E: 432/433 × 2 consecutive fresh-database Playwright runs, exit 0. HEAD `8562b36` (2026-10-31). User-side remaining: chartered-accountant sign-off on GST/tax outputs; verification of Tally TDL extension and Chrome helper against real Tally.

## Rules for every ERP phase
1. Finish a phase completely before the next.
2. Design addendum first, reviewed.
3. Every endpoint has `@PreAuthorize`; every new permission is added by a new migration.
4. All stock effects go through `InventoryPostingService`; all money effects through the finance posting service introduced in Phase 12.
5. Additive changes only; never edit applied migrations.
6. Update the `CLAUDE.md` status line at each gate.

## Coverage of the 2026-10-28 list
Sales, Purchase, Inventory, Production, Sub-contract/service, Finance/GST, Integrations, Workflow/admin, Reports, Onboarding: covered by Phases 9–16 as mapped above. Exclusions: see `REQUIREMENTS-ERP.md` "Not in scope".

## Coverage of the video-title review (2026-10-28)
The 309 video titles in the "Tranzact ERP" Drive folder were checked topic by topic; gaps were added as requirements (ERP-A-10…15, B-5, C-5, D-10, E-6, G-4, H-5), designs (`DESIGN-ERP §13`, `ARCHITECTURE-ERP §21A`) and tasks 9.11–9.14, 10.7–10.8, 11.8, 12.12, 13.9, 15.5, 16.10. Matrix: `TRANZACT-COVERAGE.md`. Only titles were read, not the videos.
