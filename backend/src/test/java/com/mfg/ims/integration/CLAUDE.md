# test/integration/ CLAUDE.md

External integration tests: export, import, Tally, and webhooks.

## Sub-folders and test classes
| Class | Type | Tests | What it covers |
|-------|------|-------|----------------|
| `ExportIT` | IT | — | `integration/export/` — CSV/Excel export of materials and transactions; correct column headers; row values match database |
| `ExportServiceTest` | Unit | — | `integration/export/` — export row formatting, number formatting, date formatting |
| `ImportIT` | IT | — | `integration/importer/` — bulk CSV import of materials and BOMs; idempotency (re-importing same rows is a no-op); invalid rows produce a row-by-row error report |
| `RowReaderTest` | Unit | — | `integration/importer/` — CSV row parsing: delimiter detection, encoding, blank lines, header row handling |
| `TallyIT` | IT | — | `integration/tally/` — Tally XML export contains correct voucher entries for posted journals; no entries for DRAFT journals |
| `TallyExportServiceTest` | Unit | — | `integration/tally/` — XML structure correctness, character escaping |
| `WebhookIT` | IT | — | `integration/webhook/` — webhook subscription CRUD; outbox delivery job sends payload to a mock HTTP server; retry on 5xx; signature in `X-Hub-Signature-256` header |
| `WebhookDeliveryServiceTest` | Unit | — | `integration/webhook/` — HTTP delivery logic, retry backoff |
| `WebhookSignerTest` | Unit | — | HMAC-SHA256 signature generation and verification |
| `SecretCipherTest` | Unit | — | Webhook secret encryption/decryption at rest |
| `WebhookTargetValidatorTest` | Unit | — | URL validation: allows HTTPS only; blocks private IP ranges |
| `WebhookAdminServiceTest` | Unit | — | `integration/webhook/service/` — admin service CRUD logic |

## Notes
- `WebhookIT` starts a mock HTTP server (WireMock or similar) inside the test to receive webhook deliveries.
- Webhook secrets are stored encrypted (`SecretCipherTest` guards the cipher); never logged.
- `WebhookTargetValidatorTest` is the guard for SSRF: delivery to `localhost`, `169.254.*`, or `10.*` must be rejected.
