# features/finance/ CLAUDE.md

Financial accounting screens: journals, AP invoicing, AR receipts, period close, tax, exchange rates, credit notes, and finance dashboard.

## Screens
| Component | Purpose |
|-----------|---------|
| `JournalListComponent` | Journal list with status filter; Submit/Approve/Post/Reverse actions |
| `JournalFormComponent` | Manual journal entry with live running balance |
| `JournalDetailComponent` | Journal detail; permission-gated Submit/Approve/Post/Reverse with reason dialog |
| `ReasonDialogComponent` | Shared reason-capture dialog (used by journal, period close, AP override) |
| `ApListComponent` | AP invoice list; Open/Pay/Cancel actions |
| `ApInvoiceFormComponent` | AP invoice create with three-way match result display |
| `ArListComponent` | AR invoice list |
| `ArReconciliationComponent` | Missing-invoice reconciliation; retry button (`AR_RECONCILE`) |
| `PeriodListComponent` | Accounting period list; Finalize/Reopen with mandatory reason |
| `TaxAdminComponent` | GST tax code management |
| `ExchangeRatesComponent` | Exchange rate maintenance |
| `CreditNotesComponent` | Credit note list |
| `CreditNoteDialogComponent` | Credit note create dialog |
| `SettlementDialogComponent` | Payment/receipt settlement dialog (shared AP/AR) |
| `SettlementHistoryDialogComponent` | Settlement history for an invoice |
| `BudgetVsActualComponent` | Budget import (CSV) and actual comparison |
| `FinanceDashboardComponent` | Finance KPI tiles |
| `BankReconciliationComponent` | Bank reconciliation screen |

## API
`finance.api.ts` → `/api/finance/**`, `/api/ap/**`, `/api/ar/**`.
`finance-dates.ts` — date helpers for Indian fiscal year (April–March).

## Key rules
- **Segregation of duties**: Approve action is disabled for the journal's preparer (tooltip shown; server enforces with 403).
- `allowDirectPosting` — enforced by the screen only (not a server rule on its own).
- Period Finalize/Reopen always opens `ReasonDialogComponent` first.
- Hidden for plant-scoped users (company-level function).

## Permissions
`FINANCE_VIEW`, `FINANCE_MANAGE`, `JOURNAL_APPROVE`, `JOURNAL_POST`, `PERIOD_CLOSE`, `AP_MANAGE`, `AR_MANAGE`, `AR_RECONCILE`, `AP_MATCH_OVERRIDE`.
