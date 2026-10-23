# features/ CLAUDE.md

One folder per ERP domain — each is a **lazy-loaded route group**.

## Folder → domain mapping
| Folder | ERP domain |
|--------|-----------|
| `admin/` | User & role administration, system settings, negative-stock overrides |
| `analytics/` | Materialized-view dashboards, trend charts |
| `auth/` | Login, change-password, forced first-login screen |
| `controlling/` | Cost/profit centres, variance reports |
| `crm/` | Customers, contacts, activities |
| `dashboard/` | KPI cards, alert inbox |
| `documents/` | Document categories, upload, approval |
| `engineering/` | BOMs, routing, product costing |
| `finance/` | Journals, period close, AP invoicing, AR receipts, GST, exchange rates |
| `hrm/` | Employees, attendance, leave, payroll runs |
| `insight/` | Alert management |
| `integration/` | Webhook configuration and outbox |
| `inventory/` | Stock balances, ledger, adjustments, valuations, opening balances |
| `maintenance/` | Machines, spare parts, work orders, OEE |
| `master/` | Materials, products, suppliers, UOM, attributes |
| `mrp/` | MRP run results, pegging |
| `notifications/` | User notification inbox and subscription settings |
| `plm/` | ECOs, product lifecycle events |
| `procurement/` | Purchase orders, goods receipt, AP invoices |
| `production/` | Production orders, My Work (operator tablet), material issue/return |
| `quality/` | Incoming / in-process / final inspections, FG batches, traceability |
| `sales/` | Sales orders, reservations, dispatch, AR invoices |
| `warehouse/` | Warehouse and location master |
| `wms/` | Zones, bins, transfer orders, wave picking, cycle counts |

## Feature structure (convention)
```
features/<domain>/
  <domain>.routes.ts       lazy route definitions
  <domain>-list.component.ts/.spec.ts
  <domain>-edit.component.ts/.spec.ts  (dialog)
  <domain>.api.ts          HTTP service for this domain
```

## Rules
- Each feature is loaded via `loadComponent` / `loadChildren` in `app.routes.ts` — never eagerly imported.
- HTTP calls go to `/api/<domain>/...` via `core/http/` interceptors.
- Use `PagedList<T>` + `<app-list-paginator>` for any list with >25 rows.
- Create/edit forms open as Angular Material dialogs (`MatDialog`) — no native `alert`/`confirm`/`prompt`.
- Plant-scoped users see a filtered subset of data; the `plantScoped` flag from sign-in drives `core/modules/` hide logic for company-level features (finance, controlling, MRP, analytics, etc.).
- `typeInto()` helper from `e2e/support/ui.ts` must be used in Playwright specs for every typed input inside a dialog.
