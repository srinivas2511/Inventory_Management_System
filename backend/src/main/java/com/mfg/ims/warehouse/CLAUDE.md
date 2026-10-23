# warehouse/ CLAUDE.md

Warehouse and location master: warehouses, storage locations, and bin/zone definitions for WMS.

## Responsibilities
- `domain/` — `Warehouse`, `StorageLocation` (flat location for basic inventory), `WarehouseZone`, `WarehouseBin` (detailed for WMS).
- `service/` — warehouse and location CRUD; plant scoping (a warehouse belongs to a plant — plant-scoped users see only their plants' warehouses).
- `web/` — REST controllers: `/api/warehouses/**`; requires `MASTER_WAREHOUSE_VIEW` / `MASTER_WAREHOUSE_MANAGE`.

## Relationship to WMS
- Basic inventory uses `StorageLocation` (flat: warehouse → location).
- WMS uses `WarehouseZone` → `WarehouseBin` for bin-level tracking (V27).
- `InventoryPostingService` validates that a warehouse belongs to the user's plants when plant scoping is active.

## Notes
- Every stock posting references a warehouse/location — the location is the physical anchor of a stock movement.
- Opening-balance stock adjustments use a special `Opening Balance` location (created by `DemoDataLoader` in dev/demo).
