# test/inventory/ CLAUDE.md

Inventory posting ITs — the most critical tests in the project.

## Test classes
| Class | Tests | What it covers |
|-------|-------|----------------|
| `InventoryPostingIT` | 11 | All posting paths through `InventoryPostingService`: goods receipt in-leg, material issue out-leg, FG receipt in-leg, dispatch out-leg, adjustment, scrap in-leg to SCRAP location, transfer; negative stock refused without override; negative stock allowed with `INVENTORY_NEGATIVE_OVERRIDE`; balance update correctness; `qtyReserved` deducted on dispatch |
| `InventoryValuationIT` | — | Moving-weighted-average cost: first receipt, subsequent receipt at different price, issue at current average; valuation report matches ledger |
| `OpeningBalanceGlIT` | — | Opening balance stock entry posts `Dr 1401/1410 / Cr 3901 Opening Balance Equity`; appears in the GL ledger |
| `StockAdjustmentIT` | — | Positive and negative adjustments; admin-threshold enforcement; adjustment audited with reason |

## Notes
- `InventoryPostingIT` is **non-transactional** — it tests that committed balances are correct after each posting step. This is the most important IT class in the codebase.
- These tests are the E2E guard for the non-negotiable rule: **all stock changes go through `InventoryPostingService`**. If a new code path writes directly to `inventory_transactions` and bypasses the service, it will not be caught here — `WmsArchTest` and `ArchitectureTest` cover that side.
- `InventoryValuationIT` uses `TestClock` to prevent the hourly materialized-view refresh from interfering with valuation snapshot assertions.
