# features/sales/ CLAUDE.md

Order-to-cash screens: sales orders, stock reservation, dispatch, and AR invoice list.

## Screens
| Component | Purpose |
|-----------|---------|
| `SalesOrderListComponent` | SO list; Approve action (disabled for creator/submitter — SoD) |
| `SalesOrderFormComponent` | SO create dialog fed by product and customer lookup selects |
| `ReservationFormComponent` | Reserve stock for an approved SO; uses `/api/lookups/fg-stock` (APPROVED/PARTIALLY_APPROVED batches only) |
| `DispatchListComponent` | Dispatch list |
| `DispatchFormComponent` | Create dispatch against a reserved/allocated SO |

## API
`sales.api.ts` → `/api/sales/**`.

## Key rules
- **Segregation of duties**: Approve is disabled for the SO creator and submitter; tooltip explains why (server enforces with 403).
- `ReservationFormComponent` uses `fg-stock` lookup — only `APPROVED` / `PARTIALLY_APPROVED` FG batches are offered. A `HOLD` batch is never shown (D-38 fix).
- `sales-forms.spec.ts` tests all create-dialog form validations.
- A fully reserved order automatically becomes `ALLOCATED` — the list refreshes to reflect this.
