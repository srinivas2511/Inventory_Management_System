# features/integration/ CLAUDE.md

External integration: webhook management and data exchange (import/export).

## Screens
| Component | Purpose |
|-----------|---------|
| `WebhookAdminComponent` | Webhook subscription list; create/edit/delete/test; view outbox delivery status |
| `DataExchangeComponent` | Bulk import (materials, BOMs, opening stock via CSV upload) and data export |

## API
`integration.api.ts` → `/api/integration/**`; requires `INTEGRATION_MANAGE`.

## Notes
- Hidden for plant-scoped users (company-level function).
- Webhook test button fires a test payload to the registered URL — warn the user before triggering in production.
- CSV import uses `multipart/form-data`; errors are returned as a row-by-row validation report.
