# features/insight/ CLAUDE.md

Operational visibility: alerts, standard reports, and material/FG traceability.

## Screens
| Component | Purpose |
|-----------|---------|
| `AlertsListComponent` | System alert list (MISSING_JOURNAL, MISSING_AR_INVOICE, SPC, etc.) with acknowledge action |
| `ReportsComponent` | Standard report runner (purchase orders, sales orders, quality inspections, etc.) with date filter and CSV export |
| `TraceabilityComponent` | Forward and backward trace: enter a material batch or FG batch number; shows the full supply/dispatch chain |

## API
`insight.api.ts` → `/api/insight/alerts/**`, `/api/reports/**`, `/api/quality/traceability/**`.

## Permissions
`ALERT_VIEW` / `ALERT_MANAGE` for alerts; `REPORT_VIEW` for reports; `QUALITY_VIEW` for traceability.

## Notes
- Hidden for plant-scoped users (company-level function).
- Traceability granularity is controlled by the `quality.trace_granularity` system setting (`ORDER` / `BATCH`) — the UI shows whatever the backend returns; no client-side filtering.
- Reports use open-ended date filters; null dates are safe (backend uses `QueryBounds` sentinels).
