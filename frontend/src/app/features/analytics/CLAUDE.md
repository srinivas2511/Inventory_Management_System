# features/analytics/ CLAUDE.md

Materialized-view analytics: dashboards, charts, and ad-hoc explorer.

## Screens
| Component | Purpose |
|-----------|---------|
| `AnalyticsDashboardsComponent` | Pre-built dashboard tiles backed by `mv_production_monthly`, `mv_oee_daily`, etc. |
| `AnalyticsChartComponent` | Reusable chart component (Angular Material + SVG); used by dashboards |
| `AnalyticsExplorerComponent` | Ad-hoc query builder — select a view, apply filters, export CSV |

## API
`analytics.api.ts` → `GET /api/analytics/**`; requires `REPORT_VIEW`.

## Notes
- Data comes from PostgreSQL materialized views refreshed hourly. If the reporting health is `DEGRADED`, the UI should gracefully indicate stale data (the backend still returns the last-good result).
- Charts use the SVG helpers in `shared/` — not a third-party chart library.
- `AnalyticsChartComponent` is also imported by `production/oee-dashboard.component.ts`.
