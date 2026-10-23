# features/inventory/ CLAUDE.md

Inventory management screens: stock balances, ledger, adjustments, batch list, and opening balances.

## Screens
| Component | Purpose |
|-----------|---------|
| `StockBalanceListComponent` | Current stock by material/product and warehouse/location |
| `StockLedgerComponent` | Transaction ledger with date/material/warehouse filters |
| `StockAdjustmentListComponent` | Adjustment list; create adjustment dialog |
| `BatchListComponent` | Raw material batch list with expiry dates |
| `OpeningBalanceDialogComponent` | Opening stock entry dialog (Dr 1401 or 1410 / Cr 3901) |

## API
`inventory.api.ts` → `/api/inventory/**`.

## Permissions
`INVENTORY_VIEW` for read; `INVENTORY_ADJUST` to create adjustments; adjustments ≥ ₹10,000 require `ADMIN` (threshold from `inventory.adjustment.admin_threshold`).

## Notes
- **Opening balance dialog** is accessible from the `StockBalanceListComponent` — opens `OpeningBalanceDialogComponent`.
- Adjustments above the admin threshold are refused by the server and the UI shows a permission-gated message.
- Alt-UOM factors are applied by the backend; the UI always displays in the base UOM with the alt-UOM label for reference.
