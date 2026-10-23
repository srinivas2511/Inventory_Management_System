# features/maintenance/ CLAUDE.md

Plant maintenance: work orders, spare parts, and maintenance request detail.

## Screens
| Component | Purpose |
|-----------|---------|
| `MaintenanceListComponent` | Work order list with status filter |
| `MaintenanceRequestDetailComponent` | Work order detail: labour entries, spare part issues, downtime records, cost summary |
| `SparePartsComponent` | Spare part catalogue and stock levels |

## API
`maintenance.api.ts` → `/api/maintenance/**`; requires `MAINTENANCE_VIEW` / `MAINTENANCE_CREATE` / `MAINTENANCE_ISSUE` / `MAINTENANCE_COST`.

## Notes
- Spare part issues from a work order go through the inventory posting path — the UI calls the same `/api/inventory/issue` endpoint used by production.
- Downtime entries are linked to downtime categories validated by the backend (CHECK constraint).
- OEE dashboard is in `production/oee-dashboard.component.ts` (not here) because it uses `oee.api.ts` alongside production data.
