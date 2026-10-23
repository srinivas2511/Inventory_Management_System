# test/advmfg/ CLAUDE.md

Advanced manufacturing ITs: OEE, scheduling, spare parts, and maintenance cost.

## Test classes
| Class | Tests | What it covers |
|-------|-------|----------------|
| `OeeIT` | 13 | OEE calculation accuracy across all three components (availability, performance, quality); edge cases: zero run time, 100% quality, multiple machines in a shift |
| `SchedulingIT` | 12 | Forward and backward finite-capacity scheduling; calendar skip of non-working days; capacity conflict detection; Gantt output structure |
| `SparePartAndMaintenanceCostIT` | 12 | Spare part issue from stock (via `InventoryPostingService`); maintenance cost posting; GL journal (`Dr 5301/5302 / Cr 1401`); `MAINTENANCE_COST_UNPOSTED` alert raised on failure |
| `AdvancedMfgSecurityMatrixTest` | — | ArchUnit/security matrix: every maintenance, OEE, and scheduling endpoint has the correct `@PreAuthorize` annotation |

## Notes
- `SparePartAndMaintenanceCostIT` is non-transactional — it tests that costs posted in one transaction are visible in a separate GL query transaction.
- `SchedulingIT` uses `TestClock` to fix `now` so scheduled windows are deterministic.
- OEE metrics are stored in `mv_oee_daily` (refreshed hourly in production); `OeeIT` writes directly to the raw event tables and calls `OeeViewRefreshJob.refresh()` before asserting.
