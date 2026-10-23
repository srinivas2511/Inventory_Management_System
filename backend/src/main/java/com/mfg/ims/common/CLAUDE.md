# common/ CLAUDE.md

Cross-cutting backend utilities shared by every feature package.

## Sub-packages
| Package | What lives here |
|---------|----------------|
| `security/` | `SegregationOfDuties` helper, JWT filter, `@PreAuthorize` support |
| `settings/` | `SystemSettingService` — typed access to `system_settings` rows |
| `search/` | `QueryBounds` sentinels for open-ended date/instant filters in repositories |
| `numbering/` | Document number sequences (PO-, GR-, SO-, etc.) |
| `event/` | Spring application events (e.g. `StockPosted`, `DispatchCreated`) |
| `exception/` | Domain exceptions mapped to HTTP status codes |
| `idempotency/` | Idempotency key support for POST endpoints |
| `net/` | Outbound HTTP (webhook delivery) |
| `document/` | Base document state-machine support |
| `api/` | Shared DTOs, `PagedResponse`, error envelope |

## Key classes to know
- **`SegregationOfDuties`** — call in every approval service; the matrix is in ARCHITECTURE §10.2a.
- **`QueryBounds`** — use `QueryBounds.start(instant)` / `.end(instant)` instead of raw null checks in JPQL/native SQL.
- **`SystemSettingService.get(key, type, default)`** — typed system-setting lookup with caching.
- **`AccountingBridgeService`** (in `finance/`) listens to `StockPosted` and other domain events — do not call it directly from inventory/production services; fire the event instead.

## Rules
- Do **not** add feature-specific logic here — `common/` must remain dependency-free of feature packages.
- `ArchUnit` enforces that no feature package is imported by `common/`.
- Every new domain exception must extend the base exception class and map to an HTTP status in `GlobalExceptionHandler`.
