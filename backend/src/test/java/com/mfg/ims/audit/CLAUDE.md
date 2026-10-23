# test/audit/ CLAUDE.md

Audit log tests.

## Test classes
| Class | Type | Tests | What it covers |
|-------|------|-------|----------------|
| `AuditLogSearchIT` | IT | 8 | All filter combinations for `GET /api/audit-logs`: entity type, entity ID, actor, date range, open-ended date ranges (null start / null end); verifies `QueryBounds` sentinels prevent null-pointer 500s |
| `AuditJsonTest` | Unit | — | `audit/service/` — verifies that old/new value JSON serialisation round-trips correctly for all audited entity types; no Spring context |

## Notes
- `AuditLogSearchIT` is the E2E guard for the null-safe date filter fix (see `docs/decisions.md` "Null-safe queries and smoke guards").
- Every IT that audits a sensitive action implicitly tests audit behaviour; `AuditLogSearchIT` focuses on the search/filter API itself.
