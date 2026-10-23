# approval/ CLAUDE.md

Generic approval-request workflow used by procurement and other modules that need a separate approver.

## Responsibilities
- `domain/` — `ApprovalRequest` entity; statuses: `PENDING` → `APPROVED` / `REJECTED`.
- `service/` — `ApprovalService`: create, approve, reject; enforces segregation of duties (approver ≠ requester).
- `event/` — `ApprovalDecidedEvent` fired after a decision; consumed by the originating module.
- `repository/` — JPA repo for `approval_requests`.

## Rules
- Segregation of duties: the user who created the approval request cannot approve it. Use `common.security.SegregationOfDuties`.
- Approval decisions are audited (reason required for rejection).
- Do not add domain-specific business logic here; keep this package generic. Domain-specific checks belong in the originating service.
