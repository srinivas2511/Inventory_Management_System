# inventory/ CLAUDE.md

Stock management: postings, balances, ledger, adjustments, valuations.

## The golden rule
**All stock changes go through `InventoryPostingService`.**
Never write `inventory_transactions` or `inventory_balances` directly from anywhere else.
`ArchUnit` + `InventoryPostingIT` enforce this.

## Sub-packages
| Package | Responsibility |
|---------|---------------|
| `service/` | `InventoryPostingService`, `BalanceService`, `ValuationService` |
| `repository/` | JPA repos for `inventory_transactions`, `inventory_balances` |
| `dto/` | `StockPostingRequest`, `BalanceDto`, `LedgerEntryDto` |
| `event/` | `StockPostedEvent` (consumed by `AccountingBridgeService`) |
| `web/` | REST controllers: `/api/inventory/**` |
| `job/` | Scheduled jobs (materialized-view refresh trigger) |

## Posting model
Each posting is one or two legs: an out-leg from a source location and/or an in-leg to a destination.
`SCRAP` is an **in-leg** into the `SCRAP` location — never an out-leg from empty.
Adjustment postings require `INVENTORY_ADJUST`; adjustments ≥ ₹10,000 require `ADMIN` (`inventory.adjustment.admin_threshold`).
Negative stock is refused by default; `INVENTORY_NEGATIVE_OVERRIDE` (per material, admin-only, audited with reason) lifts it.

## Stock statuses
`AVAILABLE` → `QUARANTINE` → `HOLD` → `AVAILABLE` / `REJECTED`
Final inspection moves FG from `QUALITY_PENDING` → `AVAILABLE`.
Dispatch and reserve pick only `AVAILABLE` (APPROVED/PARTIALLY_APPROVED FG batches via `/api/lookups/fg-stock`).

## Key IT coverage
- `InventoryPostingIT` — 11 tests; covers all posting paths and negative-stock rules.
- `GcGapRowsIT` — 10 tests; covers alt-UOM conversions and negative-stock override.
