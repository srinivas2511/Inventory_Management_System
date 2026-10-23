# integration/ CLAUDE.md

External integration: webhook outbox, data export, import, and Tally connector.

## Sub-packages
| Package | Responsibility |
|---------|---------------|
| `webhook/` | Outbox pattern — domain events serialised to `webhook_outbox`, delivered by a scheduled job; `WebhookController` for subscription management |
| `export/` | CSV/Excel export of master data and transactions |
| `importer/` | Bulk import of materials, BOMs, opening stock via CSV |
| `tally/` | Tally ERP XML export (GL journals for Tally sync) |

## Webhook outbox
- Domain events are written to `webhook_outbox` in the same transaction as the business operation (transactional outbox pattern).
- A scheduled job polls the outbox, delivers payloads via HTTP POST, and marks rows `DELIVERED` or retries on failure.
- `IntegrationHealthIndicator` exposes outbox queue depth to the Spring Boot health endpoint.
- Webhook subscriptions: `POST /api/integration/webhooks`; requires `INTEGRATION_MANAGE`.

## Rules
- Never call external HTTP endpoints directly from a service method — write to the outbox and let the delivery job handle it. This keeps the business transaction fast and decoupled from external availability.
- Import jobs must be idempotent (use `common/idempotency/`).
