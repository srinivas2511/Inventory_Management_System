# test/analytics/ CLAUDE.md

Analytics and anomaly detection tests.

## Test classes
| Class | Type | Tests | What it covers |
|-------|------|-------|----------------|
| `AnalyticsIT` | IT | — | Materialized view data correctness after a production/sales cycle; verifies `mv_production_monthly` and `mv_oee_daily` are populated after `AnalyticsViewRefresher.refreshAll()` |
| `AnomalyMathTest` | Unit | — | Pure math for anomaly detection thresholds (z-score, moving average, IQR); no Spring context |
| `AnomalyRulesTest` | Unit | — | Rule evaluation logic: a data point outside the configured bounds triggers an anomaly; boundary and off-by-one cases |

## Notes
- `AnalyticsIT` calls `AnalyticsViewRefresher.refreshAll()` directly after seeding data; it does not wait for the hourly job.
- Anomaly unit tests are in `analytics/anomaly/` and run with `mvn test` (no Testcontainers needed).
