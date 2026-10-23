# oee/ CLAUDE.md

Overall Equipment Effectiveness: calculation, storage, and reporting.

## Responsibilities
- `OeeCalculator` — computes OEE = Availability × Performance × Quality from downtime, production time, ideal cycle time, and defect counts.
- `OeeService` — aggregates raw machine events into OEE metrics per machine per shift/day.
- `OeeRepository` — reads from and writes to `mv_oee_daily` (materialized view, refreshed hourly by `OeeViewRefreshJob`).
- `OeeController` — `GET /api/oee/**`; requires `MAINTENANCE_VIEW`.
- `OeeViewRefreshJob` — scheduled hourly refresh of `mv_oee_daily`.

## Key metrics
| Metric | Formula |
|--------|---------|
| Availability | (Planned time − Downtime) / Planned time |
| Performance | (Ideal cycle time × Total count) / Run time |
| Quality | Good count / Total count |
| OEE | Availability × Performance × Quality |

## IT coverage
- `OeeIT` (13 tests) — OEE calculation across all three components, edge cases (zero run time, 100% quality).
