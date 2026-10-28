# REQUIREMENTS-ERP.md — Full ERP Extension (Addendum)

**Status:** addendum to `REQUIREMENTS.md`. Additive only: nothing in REQUIREMENTS.md, ARCHITECTURE.md, DESIGN.md or PLAN.md (Phases 0–8) is changed or removed. Where this file conflicts with REQUIREMENTS.md, the product owner decides; Claude Code must stop and flag it.
**Effect on REQUIREMENTS §16:** the items "cost accounting" and "multi-company" stay future. Everything below moves from "out of scope" to "in scope", delivered in Phases 9–17 of `PLAN-ERP.md`, after Phase 8.
**Source:** the feature list supplied by the product owner on 2026-10-28.

IDs (`ERP-<area>-n`) are used by `PLAN-ERP.md` for traceability.

## A. Platform and administration (Phase 9)
- ERP-A-1 Multiple units of measure per item with conversion factors; item categories; custom item series.
- ERP-A-2 Company profile: logos, signatures, bank details, addresses, terms and conditions, payment terms, transporters master.
- ERP-A-3 Document series: auto or manual numbering, reset by financial year or month, per document type; discount option on documents.
- ERP-A-4 Custom (flex) fields and tags on masters and documents.
- ERP-A-5 Comments (internal and external), document uploads, transaction timeline on every document.
- ERP-A-6 Configurable approval rules (auto or manual): approve, approve-and-send, withdraw, approval history, amend or cancel approved documents.
- ERP-A-7 Teams: add, remove, change team; team and report permissions; per-user notification settings.
- ERP-A-8 Excel bulk upload with dry-run validation: masters, items, BOM, buyers/suppliers, opening stock and balances.
- ERP-A-9 Global search across all documents.
- ERP-A-10 Item–vendor mapping ("vendor mapping"): per item, the suppliers that can supply it, with supplier item code/name, last price, MOQ, lead time and preferred flag. Items already used in documents cannot be deleted, only deactivated; inactive items stay visible in history, stock card and reports. Bulk edit of items (by Excel or multi-select).
- ERP-A-11 Document reminders (remind me / my team on a date), @mentions of users or teams in comments, team-level default notification settings, module-level enablement per team ("unlock team permissions"), including service documents and production.
- ERP-A-12 An "All Documents" module listing every document type with common filters (type, status, party, date, tag, owner) and a unified status view.
- ERP-A-13 Onboarding: quick setup wizard (company, branches, users, masters, opening stock/balances), in-app Help menu with links to guides and support contact, "new version available" banner, and a controlled "remove demo data" action available only outside production.
- ERP-A-14 Explainability: pickers (items, parties, BOMs, ledgers) can say why a record is not listed (inactive, wrong type, not mapped, outside branch, no permission); every disabled workflow action returns a reason (for example "Generate OC is unavailable until the quotation is approved").
- ERP-A-15 Document enhancements: custom fields can be copied from one document to the next on conversion and shown on item lines (including item-master custom fields); document-level override of party name/address/GSTIN without changing the master; packaging details (packages, type, gross/net weight) on invoices, challans and dispatches; total-quantity footer; invoices and challans can be created for customers without an e-mail address (sending is simply skipped).

## B. Purchase (Phase 10)
- ERP-B-1 RFQs sent to suppliers; supplier quotes compared side by side.
- ERP-B-2 Purchase orders: standard, from an RFQ, foreign (currency), PO advance, amendments with revision history.
- ERP-B-3 Inward and GRN, quality inspection reports (GRN-QIR), GRN pending report, GRN-invoice mismatch report.
- ERP-B-4 Purchase invoices (matched to PO and GRN), payments against them, purchase returns.
- ERP-B-5 Last-price insight on every document line (last price for the item and party, and best recent price); PO balance and undelivered quantity per line and as a register; amendment of POs/OCs/quotations/service documents even after downstream documents exist, under impact rules; inward allowed while an amendment is pending (against the last approved revision); inward against a PO, a delivery challan, a service order or a service confirmation; purchase-return delivery challan; approval mode (auto or manual) for inventory documents such as inward, stock entry and transfer; changing a posted stock entry is done by reversal and re-entry.

## C. Sales (Phase 11)
- ERP-C-1 Quotations: create, draft, amend, cancel, convert to Order Confirmation or Sales Order.
- ERP-C-2 Order Confirmations (OC), including foreign OC and copy OC.
- ERP-C-3 Create a work order (production order) from an OC; OC drives MRP.
- ERP-C-4 Delivery challans; dispatch details sent to customers (PDF and e-mail).
- ERP-C-5 A quotation that has been converted can be cancelled only through its converted documents (cascade rules); challan kinds include non-returnable, returnable, service, purchase-return and goods-out-for-service, and may be raised from a service order or service confirmation; whether a price is mandatory on a challan is a setting per kind.

## D. Finance, GST and payments (Phase 12)
- ERP-D-1 Currency master and exchange rates; USD (and other) invoices converted to INR.
- ERP-D-2 Tax engine: HSN/SAC, CGST/SGST/IGST by place of supply, TCS/TDS.
- ERP-D-3 Sales invoices: tax, proforma, direct, ad-hoc, export.
- ERP-D-4 Credit and debit notes (sales and purchase).
- ERP-D-5 Payments: log against single or multiple invoices, payment entries, advance receipt vouchers, opening balances.
- ERP-D-6 Counterparty and company ledgers, receivables and payables, debtors and creditors ageing.
- ERP-D-7 TDS entries.
- ERP-D-8 Payment reminders (automatic or manual).
- ERP-D-9 E-invoice and e-way bill: JSON export first, API generation through a GST Suvidha Provider later; CGST/IGST calculation; GST filing reports.
- ERP-D-10 A direct sales invoice for stock items (no dispatch yet) reduces inventory automatically through a system-created dispatch; invoices (sales and purchase) can be raised against a service confirmation; cancelling an invoice that has payments requires reversing those payments first; opening balances can be deleted (while unused) or settled by payments; payments can be logged against purchase invoices and cancelled.

## E. Inventory and manufacturing extensions (Phase 13)
- ERP-E-1 Multi-level BOM, alternate items, duplicate BOM, cost check, BOM bulk upload.
- ERP-E-2 FG costing and process log on production orders.
- ERP-E-3 MRP: by finished good or by OC; procurement planning; handling of negative quantities.
- ERP-E-4 Barcodes/QR, batch tracking labels, label printing.
- ERP-E-5 Service items; min/max stock levels; stock card; inventory history and dashboard; multi-store stock.
- ERP-E-6 Production convenience: complete an order in a single step; create production orders in bulk; create finished goods directly from a BOM (consumption and receipt in one entry); skip production of a child BOM in a multi-level order; update routing on a not-yet-started order with audit; restrict who may edit BOMs; scrap management (record, value, hold, sell, reuse or dispose); "shop floor material" view; production planning board (planned orders by date with material availability; finite-capacity scheduling remains future).

## F. Sub-contract and service (Phase 14)
- ERP-F-1 Sub-contract (job work): material sent to vendor, received back, scrap, job charges, delivery challan.
- ERP-F-2 Service orders and service confirmations, with delivery challans, inward and invoices raised against them.

## G. Reporting (Phase 15)
- ERP-G-1 Business Intelligence by area (Sales, Purchase, Inventory, Production, Quotations, Accounts) and an aggregate view.
- ERP-G-2 Registers: sales invoice, sales order, PO, payment, GRN pending, GRN-invoice mismatch, item price, item BOM, product ageing, new product, process details.
- ERP-G-3 Custom report builder and data grouping.
- ERP-G-4 Additional registers and reports: sales quotations, item-wise PO register, item-wise sales order register, sales invoice register (item), GRN-QIR register, counterparty details, all invoices, debit/credit note list, PO balance/undelivered quantity, product price and inventory, inventory price and stock adjustment, last-price report; every production-module report; "no data" explanations (which filter, permission or missing setup).

## H. Integrations (Phase 16)
- ERP-H-1 Tally: ledger setup, TDL file and extension, rule definition, voucher flow, invoice and payment approval, data extraction, report download. The TDL extension and the Chrome extension (browser helper) are in scope (confirmed by the product owner 2026-10-28).
- ERP-H-2 Busy accounting integration.
- ERP-H-3 Aftership shipment tracking.
- ERP-H-4 Online payments through a payment gateway (replaces "TranZact Payments", which is a third-party product).
- ERP-H-5 Tally details: the IMS document number is passed as the Tally voucher number (configurable); several IMS purchase documents with the same supplier invoice number combine into one Tally voucher when configured; a Tally readiness check verifies company settings, voucher types and ledgers before first sync; vouchers flow Completed → Approved → Sent with the correct voucher type per document.

## Not in scope
Free vs paid plans and billing, webinars/marketing content, a staffed "technical expert" service, and browser-cache instructions. The in-app Help menu, setup wizard, demo-data removal outside production, the version banner, and the "Why…" troubleshooting behaviours **are** in scope (ERP-A-13, ERP-A-14, ERP-G-4).

## Review against the supplied video-title list (2026-10-28)
309 titles in the Drive folder "Tranzact ERP" were reviewed. Coverage by topic and the gaps found are in `TRANZACT-COVERAGE.md`; the gaps are requirements ERP-A-10…15, B-5, C-5, D-10, E-6, G-4 and H-5 above. One title is unrelated (a consumer-electronics video) and one is a partial-download duplicate; both were ignored. Only titles were available, not video content, so where a title was ambiguous (notably "Vendor Mapping") the interpretation is stated in the design and should be confirmed.

## Decisions needed before the affected phase
| # | Decision | Needed by |
|---|---|---|
| E1 | Chart of accounts: lightweight ledgers inside the system, or Tally/Busy remain the books of account | Phase 12 |
| E2 | GST Suvidha Provider for e-invoice and e-way bill APIs (needs credentials and sandbox) | Phase 12 |
| E3 | Exchange-rate source: manual entry, daily import, or per transaction | Phase 12 |
| E4 | Financial year and document reset rules; GSTIN per branch (multi-GSTIN) | Phase 9 |
| E5 | **Decided 2026-10-28:** Tally Prime, running on a PC separate from IMS. Connector mode = browser helper (Chrome extension) by default; file export as fallback; direct server-to-Tally HTTP only if a network path is later provided | Phase 16 |
| E6 | Payment gateway provider | Phase 16 |
| E7 | Single company or multi-company | Phase 9 |
| E8 | Print/PDF templates: who supplies the invoice format | Phase 11 |

## Compliance note
GST, e-invoice, e-way bill and TDS rules change often. Tax logic must be data-driven (rates and rules in tables, versioned by effective date), reviewed by a chartered accountant before production use, and covered by tests with their worked examples.
