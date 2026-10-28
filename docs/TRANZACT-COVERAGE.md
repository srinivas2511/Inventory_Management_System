# TRANZACT-COVERAGE.md — Video-title coverage matrix

**Source:** Google Drive folder "Tranzact ERP" — 309 unique video titles (reviewed 2026-10-28).
**Limits:** only titles were read, not the videos. A title can hide behaviour that differs from our reading, so the matrix says where each topic is designed; verify against the videos when a phase starts.
**Ignored:** one unrelated (non-ERP) video, one `.part` duplicate, plan/pricing videos, webinars and "Knowledge Centre" promos (covered only by Help-menu links, ERP-A-13).
**Key:** REQ = `REQUIREMENTS-ERP.md`, DES = `DESIGN-ERP.md`, ARC = `ARCHITECTURE-ERP.md`, task IDs in `PLAN-ERP.md`. ★ = gap found in this review and filled.

| Topic group (titles) | Requirement | Design / architecture | Tasks |
|---|---|---|---|
| Users, teams, roles, permissions, report access | A-7 | DES §2.1, §5.2, §6 | 9.7 |
| Team module access, notification defaults ★ | A-11 | DES §13.2 (3–4) | 9.12 |
| Masters: items, UOM, categories, parties, bank, custom fields | A-1, A-3 | DES §2.1, §4.1 | 9.1–9.5 |
| Item–vendor mapping, item delete/deactivate, bulk edit ★ | A-10 | DES §13.1–13.2 (1–2) | 9.11 |
| Custom-field propagation, party override, packaging, optional e-mail, qty footer ★ | A-15 | DES §13.2 (7–11) | 9.13 |
| Document series, numbering, branches/locations | A-2 | DES §1, §4.2 | 9.2 |
| Bulk upload (all masters, BOM, opening data) | A-8 | DES §4.15 | 9.8, 13.2 |
| Approvals, withdraw, approval rules | A-4 | ARC §4, DES §4.1 | 9.3 |
| Timeline, comments, attachments | A-5 | ARC §3 | 9.4–9.5 |
| Reminders, @mentions ★ | A-11 | DES §13.2 (3) | 9.12 |
| All Documents, global search | A-9, A-12 ★ | ARC §5.3, DES §13.2 (5) | 9.9, 9.12 |
| "Why can't I…/why isn't X listed/why no data" ★ | A-14, G-4 | ARC §21A, DES §13.2 (6) | 9.13, 15.5 |
| Setup wizard, Help, version banner, demo-data removal ★ | A-13 | DES §13.2 (33) | 9.14 |
| RFQ, supplier quotes, comparison | B-1 | DES §2.2 | 10.1 |
| PO (foreign currency, advance, amendment) | B-2 | DES §2.2, §5.3 | 10.2 |
| Amendment impact, inward while amendment pending ★ | B-5 | DES §13.2 (14–15) | 10.7 |
| GRN, QIR link, pending GRN, GRN–invoice mismatch | B-3 | DES §8.2 | 10.3 |
| Inward kinds (challan, service) ★ | B-5 | DES §13.2 (16) | 10.7 |
| Purchase invoice, three-way match, purchase return | B-4 | DES §3 F-10/F-11, §4.8 | 10.4 |
| Last-price insight, PO balance/undelivered qty ★ | B-5 | DES §13.2 (12–13) | 10.7 |
| Inventory approval modes, stock-entry amend ★ | B-5 | DES §13.2 (17–18) | 10.8 |
| Quotation, amend, cancel, convert | C-1 | DES §2.3, §4.9 | 11.1 |
| Quotation cancel cascade ★ | C-5 | DES §13.2 (19) | 11.8 |
| Order Confirmation, SO, copy | C-2 | DES §4.9 | 11.2 |
| Work order / production from OC | C-3 | DES §5.4 | 11.3 |
| Challans (returnable, non-returnable), dispatch mail | C-4 | DES §4.10 | 11.4–11.5 |
| Purchase-return / service-out challans, challan price setting ★ | C-5 | DES §13.2 (20) | 11.8 |
| Invoices (tax, export, bill of supply), credit/debit notes | D-1…3 | DES §2.4, §4.3 | 12.1–12.4 |
| GST, place of supply, tax not calculating | D-4 | DES §4.3, §13.2 (6d) | 12.2, 9.13 |
| Payments, allocation, advances, ledgers, ageing, TDS, reminders | D-5…8 | DES §4.4–4.5, §4.11 | 12.5–12.8 |
| E-invoice, e-way bill, GST reports | D-9 | DES §4.12 | 12.9 |
| Direct-invoice dispatch, invoice from service confirmation, cancel dependency, opening balance delete ★ | D-10 | DES §13.2 (21–24) | 12.12 |
| Multi-level BOM, alternates, costing, MRP, barcodes, min/max | E-1…5 | DES §2.5, §4.6–4.7, §4.16 | 13.1–13.6 |
| Quick complete, direct FG, bulk orders, skip child, routing update, scrap, shop-floor view, planning board ★ | E-6 | DES §13.2 (25–31) | 13.9 |
| Job work, service orders and confirmations | F-1, F-2 | DES §2.6 | 14.1–14.2 |
| BI dashboards, registers, report builder | G-1…3 | DES §8, §4.14 | 15.1–15.3 |
| Additional registers (itemwise, notes, price/inventory, GRN-QIR, counterparty) ★ | G-4 | DES §13.3 | 15.5 |
| Tally Prime (vouchers, ledgers, day book, TDL, browser helper) | H-1 | DES §4.13, §4.13.1 | 16.2, 16.8, 16.9 |
| Tally voucher number, combine, readiness, flow sections ★ | H-5 | DES §13.2 (32) | 16.10 |
| Busy, Aftership, payment gateway | H-2…4 | DES §4.13 | 16.3–16.5 |

## To confirm
- **"Vendor Mapping"** is interpreted as item ↔ supplier mapping (DES §13.2 rule 1). Confirm or correct.
- E1–E8 decisions (see `REQUIREMENTS-ERP.md`) remain open except E5.
- Items tagged [VERIFY] need accountant or official-spec review.
