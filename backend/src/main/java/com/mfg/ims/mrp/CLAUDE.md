# mrp/ CLAUDE.md

Material Requirements Planning: batch MRP run, BOM explosion, demand netting, and planned order generation.

## Responsibilities
- `domain/` — `MrpRun`, `MrpDemand`, `MrpPlannedOrder`, `MrpPeg` (demand-to-supply pegging).
- `engine/` — BOM explosion (up to 10 levels), lot sizing (`EXACT` by default, configurable per material), lead-time offset.
- `forecast/` — sales forecast integration (demand-side input to MRP).
- `service/MrpService` — triggers a batch run; persists planned orders.
- `web/` — REST controllers: `/api/mrp/**`; requires `MRP_VIEW` / `MRP_RUN`.

## Design decisions
- MRP is **batch**, not real-time. Triggered manually or on a schedule.
- Max BOM explosion depth: **10 levels** (throws `BomExplosionDepthException` beyond this).
- Default lot sizing: **EXACT** (order exactly what is needed); configurable per material (`material.lot_sizing_policy`).
- Demand sources: confirmed sales orders, manual demand entries, forecasts.
- Safety stock and reorder points are read from `material.safety_stock_qty` and `material.reorder_point`.

## IT coverage
- `MrpIT` (14 tests) — multi-level BOM explosion, netting, lot sizing policies, pegging correctness.
