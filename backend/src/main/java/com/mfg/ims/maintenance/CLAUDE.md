# maintenance/ CLAUDE.md

Plant maintenance: machines, spare parts, work orders, downtime, labour/cost posting, and OEE data capture.

## Responsibilities
- `domain/` — `MaintenanceWorkOrder`, `SparePartIssue`, `DowntimeEntry`, `MaintenanceLabour`.
- `service/` — work order lifecycle (OPEN → IN_PROGRESS → COMPLETED); spare part issue (debits stock via `InventoryPostingService`); labour and cost posting.
- `event/` — `MaintenanceCostPostedEvent` (consumed by `AccountingBridgeService` for GL).
- `report/` — work order history, cost per machine, MTBF/MTTR summary.
- `controller/` — REST controllers: `/api/maintenance/**`; requires `MAINTENANCE_VIEW` / `MAINTENANCE_CREATE` / `MAINTENANCE_ISSUE` / `MAINTENANCE_COST`.

## Key design points
- Spare part issues go through `InventoryPostingService` (spare parts are stocked items in the warehouse).
- Unposted maintenance costs raise `MAINTENANCE_COST_UNPOSTED` alert (V33).
- Downtime categories are validated against a `CHECK` constraint (V33).
- `machines.cost_centre_id` (V33) — maintenance costs roll up to the machine's cost centre.
- OEE metrics are calculated by `OeeCalculator` and stored in `mv_oee_daily` (refreshed hourly).
- `MAINTENANCE_ISSUE` permission is required for spare part assignment — separate from `MAINTENANCE_CREATE`.

## IT coverage
- `SparePartAndMaintenanceCostIT` (12 tests) — issue, cost posting, GL journal, alert.
- `OeeIT` (13 tests) — OEE calculation accuracy, availability/performance/quality components.
