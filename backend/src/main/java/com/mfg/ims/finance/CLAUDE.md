# finance/ CLAUDE.md

Full financial accounting: GL, AP, AR, tax, multi-currency, period close, and finance dashboard.

## Sub-packages
| Package | Responsibility |
|---------|---------------|
| `gl/` | `JournalHeader`, `JournalLine`, `GlAccount`; journal engine, period close, manual entry, reversal |
| `ap/` | Supplier invoices, three-way match, AP payments, credit notes, debit notes |
| `ar/` | Customer invoices (auto-generated from dispatch), receipts, AR reconciliation |
| `tax/` | GST engine: tax codes, outward register, input-credit register, GST reports |
| `bank/` | Bank accounts, bank reconciliation |
| `dashboard/` | Finance KPI summary endpoint |
| `config/` | `GlAccountResolver` (resolves GL account by code), `AccountingBridgeService` |

## Non-negotiable rules
- **Journals must balance (Dr = Cr)** — `JournalService` enforces before posting; a DB trigger enforces immutability after posting.
- **Financial periods gate all postings** — never post to a CLOSED period without a Finance Manager override (audited, mandatory reason).
- **Auto-journals via `AccountingBridgeService` only** — it listens to domain events (`StockPostedEvent`, `DispatchCreatedEvent`, etc.). Never write directly to `journal_headers`/`journal_lines` from inventory, production, or sales services.
- **Segregation of duties on journals** — a journal cannot be approved by its preparer (Business Rule 19, `SEC-008`). Use `SegregationOfDuties`.
- Functional currency: **INR**. Costing method: **moving weighted average** (switchable per company). Fiscal year: **April–March**.

## AP three-way match (V40)
- An AP invoice must reference its goods receipt (same supplier, POSTED status).
- May bill at most: `received qty × PO rate − already cleared` + tolerance (`ap.match.price_tolerance_pct`, `ap.match.absolute_tolerance`).
- Outside tolerance → `DRAFT` + `match_status = EXCEPTION`; cannot be opened or paid until either cancelled or a Finance Manager approves the variance (`AP_MATCH_OVERRIDE`, audited, never the person who entered it).
- Opening posts: `Dr 2401 / Dr 5207 Purchase Price Variance / Dr 1301 Input Tax Credit / Cr 2402`.

## AR auto-invoicing
- `ArService` generates AR invoices from `DispatchCreatedEvent` in its own transaction.
- A failure raises `MISSING_AR_INVOICE` alert; `POST /api/ar/reconciliation/missing-invoices/retry` backfills.
- Zero-value dispatches get a settled zero invoice without a journal.

## Key GL accounts
| Code | Account |
|------|---------|
| 1101 | Trade Receivables |
| 1301 | GST Input Credit |
| 1401 | Raw Material Stock |
| 1410 | Finished Goods Stock |
| 1501 | WIP |
| 2401 | Trade Payables (GR/IR clearing) |
| 2402 | Supplier Payables |
| 3901 | Opening Balance Equity |
| 4001 | Revenue |
| 5001 | COGS |
| 5202 | Scrap Expense |
| 5207 | Purchase Price Variance |
| 5301 | Labour Expense |
| 5302 | Overhead Expense |
| 5303 | Conversion Cost Absorbed |

## IT coverage
- `FinanceIT` (8 tests), `ThreeWayMatchIT` (14 tests), `JournalReversalIT` (2 tests), `ArFromDispatchIT` (8 tests).
