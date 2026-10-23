# test/finance/ CLAUDE.md

Financial accounting ITs and unit tests.

## Test classes
| Class | Type | Tests | What it covers |
|-------|------|-------|----------------|
| `FinanceIT` | IT | 8 | Journal entry, submit/approve/post lifecycle; period close; reopen with Finance Manager override (reason required); double-entry balance enforced; posting to CLOSED period refused |
| `ArFromDispatchIT` | IT | 8 | AR invoice auto-generated from `DispatchCreatedEvent`; value = sum of qty × unit price per line; zero-value dispatch gets settled zero invoice without GL journal; failure raises `MISSING_AR_INVOICE` alert; retry endpoint backfills |
| `ThreeWayMatchIT` | IT | 14 | AP invoice three-way match: exact match, within tolerance, over tolerance → `EXCEPTION`; Finance Manager match override (audited, SoD); under-billing (partial) |
| `JournalReversalIT` | IT | 2 | Reversal marks the original `REVERSED`; cannot reverse twice; reversal lines are mirror images |
| `FinishedGoodsCostingIT` | IT | 4 | FG receipt at standard cost; FG receipt at actual material cost; COGS journal at dispatch (`Dr 5001 / Cr 1410`); zero-value stock movement raises `MISSING_JOURNAL` alert. Non-transactional; uses `TestClock` to prevent timing variance. |
| `FinanceReportsIT` | IT | — | GST outward register, input credit register, and other finance reports return correct values |
| `ForeignCurrencyIT` | IT | — | Multi-currency: purchase in USD converted to INR at the exchange rate; exchange rate table lookup |
| `GstTaxIT` | IT | — | GST tax code application on AP invoices; CGST/SGST/IGST split based on supplier state code |
| `UserAttributionIT` | IT | 2 | `createdBy` and `updatedBy` are populated by JPA auditing for journal entries |
| `AccountingBridgeServiceTest` | Unit | — | Event-to-journal mapping: `StockPostedEvent` → correct DR/CR pairs; no-op for zero-value events |
| `ThreeWayMatchCalculatorTest` | Unit | — | `finance/ap/` — pure calculation: remaining GR value, tolerance check, variance amount |

## Notes
- `FinishedGoodsCostingIT` must use `TestClock` because slow machines can absorb a few cents of conversion cost between test steps, making FGC-IT-2's expected unit cost non-deterministic (see `docs/decisions.md`).
- `ThreeWayMatchIT` is the guard that a `HOLD` invoice cannot be opened or paid without a Finance Manager override.
- SoD on journals (approver ≠ preparer) is tested in `security/SegregationOfDutiesIT`, not here.
