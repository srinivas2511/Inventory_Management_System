# features/dashboard/ CLAUDE.md

Landing KPI dashboard: operational metrics tiles, trend charts, and active alert count.

## Screens
| Component | Purpose |
|-----------|---------|
| `DashboardComponent` | Main dashboard: KPI stat tiles (orders, stock value, open alerts, production utilisation), trend line charts from `GET /api/dashboard/trends` |

## API
`GET /api/dashboard/kpis` — requires `DASHBOARD_VIEW`.
`GET /api/dashboard/trends` — live from the GL ledger; requires `DASHBOARD_VIEW`.

## Notes
- `DASHBOARD_VIEW` is granted to roles that should see the landing page (e.g. `FINANCE_MANAGER` gained it in V45).
- Plant-scoped users do not see this dashboard (hidden by `core/modules/` plant-scope logic); they land on their first permitted non-company-level screen instead.
- `DashboardComponent` is the default redirect after login for users with `DASHBOARD_VIEW`. Users without it are redirected to their first permitted route (resolved in `app.routes.ts` by `AuthService.firstPermittedRoute()`).
- The alert count badge on the dashboard links to `insight/alerts-list`.
