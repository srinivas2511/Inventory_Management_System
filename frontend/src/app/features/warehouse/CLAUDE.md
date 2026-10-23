# features/warehouse/ CLAUDE.md

Warehouse and location master: warehouse list and create/edit.

## Screens
| Component | Purpose |
|-----------|---------|
| `WarehouseListComponent` | Warehouse list with location sub-list; create/edit warehouse and storage locations |

## API
`warehouse.api.ts` → `/api/warehouses/**`; requires `MASTER_WAREHOUSE_VIEW` / `MASTER_WAREHOUSE_MANAGE`.

## Notes
- A warehouse belongs to a plant; plant-scoped users see only their plants' warehouses.
- WMS bin/zone management is in `wms/` — `warehouse/` covers only the flat warehouse → location hierarchy used by basic inventory.
- Every stock posting references a warehouse location; adding a new location here makes it available in all posting dialogs.
