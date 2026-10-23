# analytics/ CLAUDE.md

Materialized-view reporting layer and anomaly detection.

## Responsibilities
- Manages PostgreSQL materialized views in the `reporting` schema (e.g. `mv_production_monthly`, `mv_oee_daily`).
- `AnalyticsViewRefresher.refreshAll()` — refreshes all views; called hourly by `MaterializedViewRefreshJob` (in `insight/`).
- `AnalyticsController` — serves report queries via `GET /api/analytics/**`; requires `REPORT_VIEW`.
- `ReportingHealthIndicator` — Spring Boot `HealthIndicator` (bean `reportingHealthIndicator`); tracks last refresh result; returns `UP` / `DEGRADED` / `UNKNOWN`; `DEGRADED` maps to HTTP 200 (non-critical, never blocks readiness).
- `anomaly/` — anomaly-detection rules run after each refresh.

## Key design points
- Views are refreshed `CONCURRENTLY` where a unique index exists; if that index is dropped/missing the refresh fails and `ReportingHealthIndicator` reports `DEGRADED` — this is graceful degradation (NFR-003).
- Refresh failures are captured, not thrown; inventory posting succeeds in a separate transaction regardless.
- Health group `reporting` (`/actuator/health/reporting`) is outside the readiness group — a degraded reporting subsystem never blocks traffic.

## IT coverage
- `GracefulDegradationIT` (4 tests) — drops the unique index, asserts `DEGRADED`, confirms inventory posting is unaffected, confirms `/actuator/health/readiness` stays `UP`.
- `AnalyticsIT` — end-to-end view data correctness.
