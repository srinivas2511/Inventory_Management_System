# insight/ CLAUDE.md

Operational visibility: alerts, KPI dashboard, reports, and traceability.

## Sub-packages
| Package | Responsibility |
|---------|---------------|
| `alert/` | `AlertSweepJob` (scheduled), `AlertService`, `AlertController`; alert types, templates, subscriptions |
| `dashboard/` | KPI endpoint (`GET /api/dashboard/kpis`, `GET /api/dashboard/trends`); requires `DASHBOARD_VIEW` |
| `report/` | Standard reports (purchase orders, sales orders, quality inspections, etc.); `REPORT_VIEW` |
| `traceability/` | Forward and backward trace for materials and FG batches; `GET /api/quality/traceability/**` |
| `MaterializedViewRefreshJob.java` | Hourly job that calls `AnalyticsViewRefresher.refreshAll()`; updates `ReportingHealthIndicator` with the result |

## Alert types (V31/V32/V33)
`MISSING_JOURNAL`, `MISSING_AR_INVOICE`, `MAINTENANCE_COST_UNPOSTED`, `SPC` (out-of-control chart), `MISSING_JOURNAL` (zero-value stock movement). Alert sweep runs on a schedule and on `AlertSweepJob.runSweep()` (direct call from `DemoDataLoader` bypasses ShedLock which holds for 5 min).

## Traceability granularity
Controlled by system setting `quality.trace_granularity` (`ORDER` / `BATCH`). Default: `ORDER`. At `BATCH` level the FG trace names the material batches, suppliers, POs and heat numbers behind a batch; the material trace reaches the FG batch, dispatch and customer.

## Report null-safety
All open-ended `Instant`/`LocalDate` filter parameters use `QueryBounds` sentinels. Native SQL casts nullable params: `CAST(:p AS date)`. `EndpointSmokeIT` calls every report endpoint and fails the build on any 5xx.

## IT coverage
- `AuditLogSearchIT` (8 tests) — search filter edge cases.
- `NotificationIT` (18 tests) — alert creation, subscription, delivery.
