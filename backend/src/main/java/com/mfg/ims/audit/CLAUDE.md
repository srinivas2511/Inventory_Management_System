# audit/ CLAUDE.md

Append-only audit log for all sensitive actions across the platform.

## Responsibilities
- `domain/` — `AuditEntry` entity; fields: actor, action, entity type, entity id, old value (JSON), new value (JSON), reason, timestamp.
- `service/AuditService` — `record(action, entity, oldValue, newValue, reason)` called inside the same transaction as the business operation.
- `web/AuditController` — `GET /api/audit-logs` with filters (entity type, entity id, actor, date range); requires `AUDIT_VIEW`.
- `job/` — scheduled export job (CSV).
- `repository/` — JPA repo; open-ended date filters use `QueryBounds` sentinels from `common/search/`.

## Rules
- **Every sensitive action must be audited in the same transaction** — if the transaction rolls back, the audit entry disappears with it (correct behaviour).
- Old/new values are stored as JSON snapshots, not references — the audit trail is immutable even if the entity is deleted.
- Never log passwords, tokens, or PII beyond what the DESIGN §20 audit matrix specifies.
- `AuditLogSearchIT` (8 tests) — covers all filter combinations and verifies null-safe date ranges.
