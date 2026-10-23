# features/wms/ CLAUDE.md

Warehouse Management System: bin view, transfer orders (tasks), wave picking, and cycle counts.

## Screens
| Component | Purpose |
|-----------|---------|
| `WmsBinsComponent` | Bin list per zone with stock levels |
| `WmsBinViewComponent` | Single-bin detail: contents, pending transfers |
| `WmsTasksComponent` | Transfer order list (bin-to-bin moves) |
| `WmsTaskDetailComponent` | Transfer order detail; confirm pick/put-away per line |
| `WmsWavesComponent` | Pick wave list; create wave from approved SOs; release wave |
| `WmsCycleCountsComponent` | Cycle count list; start a new count |
| `WmsCycleCountDetailComponent` | Count entry per bin/SKU; post variance |

## API
`wms.api.ts` → `/api/wms/**`; requires `WMS_VIEW` / `WMS_MANAGE` / `WAREHOUSE_MANAGER` role.

## Notes
- Wave creation pulls approved SOs from `/api/lookups/sales-orders/approved`.
- Cycle count variance is posted as a stock adjustment via the backend — the same threshold rules apply (variances ≥ ₹10,000 require `ADMIN`).
- WMS is currently **not plant-scoped** (deferred) — all warehouses are visible regardless of plant assignment.
