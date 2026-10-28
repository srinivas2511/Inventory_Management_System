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

## B. Purchase (Phase 10)
- ERP-B-1 RFQs sent to suppliers; supplier quotes compared side by side.
- ERP-B-2 Purchase orders: standard, from an RFQ, foreign (currency), PO advance, amendments with revision history.
- ERP-B-3 Inward and GRN, quality inspection reports (GRN-QIR), GRN pending report, GRN-invoice mismatch report.
- ERP-B-4 Purchase invoices (matched to PO and GRN), payments against them, purchase returns.

## C. Sales (Phase 11)
- ERP-C-1 Quotations: create, draft, amend, cancel, convert to Order Confirmation or Sales Order.
- ERP-C-2 Order Confirmations (OC), including foreign OC and copy OC.
- ERP-C-3 Create a work order (production order) from an OC; OC drives MRP.
- ERP-C-4 Delivery challans; dispatch details sent to customers (PDF and e-mail).

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

## E. Inventory and manufacturing extensions (Phase 13)
- ERP-E-1 Multi-level BOM, alternate items, duplicate BOM, cost check, BOM bulk upload.
- ERP-E-2 FG costing and process log on production orders.
- ERP-E-3 MRP: by finished good or by OC; procurement planning; handling of negative quantities.
- ERP-E-4 Barcodes/QR, batch tracking labels, label printing.
- ERP-E-5 Service items; min/max stock levels; stock card; inventory history and dashboard; multi-store stock.

## F. Sub-contract and service (Phase 14)
- ERP-F-1 Sub-contract (job work): material sent to vendor, received back, scrap, job charges, delivery challan.
- ERP-F-2 Service orders and service confirmations, with delivery challans, inward and invoices raised against them.

## G. Reporting (Phase 15)
- ERP-G-1 Business Intelligence by area (Sales, Purchase, Inventory, Production, Quotations, Accounts) and an aggregate view.
- ERP-G-2 Registers: sales invoice, sales order, PO, payment, GRN pending, GRN-invoice mismatch, item price, item BOM, product ageing, new product, process details.
- ERP-G-3 Custom report builder and data grouping.

## H. Integrations (Phase 16)
- ERP-H-1 Tally: ledger setup, TDL file and extension, rule definition, voucher flow, invoice and payment approval, data extraction, report download. The TDL extension and the Chrome extension (browser helper) are in scope (confirmed by the product owner 2026-10-28).
- ERP-H-2 Busy accounting integration.
- ERP-H-3 Aftership shipment tracking.
- ERP-H-4 Online payments through a payment gateway (replaces "TranZact Payments", which is a third-party product).

## Not in scope
Free vs paid plans, Knowledge Centre, reset profile / remove dummy data as a product feature, "technical expert support", and the "Why…" troubleshooting videos. Those belong to the product the list came from. The matching behaviour is covered instead by validation messages and empty-state help (for example, "no data" explains the missing filter or permission).

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
