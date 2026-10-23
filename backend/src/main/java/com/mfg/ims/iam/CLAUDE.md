# iam/ CLAUDE.md

Identity and Access Management: users, roles, permissions, and role assignment.

## Responsibilities
- `domain/` — `User`, `Role`, `Permission`, `UserRole` join table.
- `service/` — user CRUD, role assignment, permission catalogue; `AdminGuard` (advisory lock — ensures at least one active, unlocked ADMIN always remains).
- `web/` — REST controllers: `/api/iam/**`; requires `IAM_VIEW` / `IAM_MANAGE` / `ROLE_MANAGE`.

## Key rules
- Creating a user with roles requires `ROLE_MANAGE`.
- Initial password must meet the password policy (BCrypt 12, minimum complexity).
- `deactivate` and `assignRoles` are serialised with an advisory lock via `AdminGuard` to prevent the last-admin race condition.
- `ADMIN` role must hold **all** seeded permission codes — `AdminHoldsEveryPermissionIT` fails the build otherwise. Every new permission added to a migration must also be granted to ADMIN in the same migration.
- `PermissionCatalogueIT` — every authority used in any `@PreAuthorize` must exist in the `permissions` table.
- `RoleAccessIT` — probes every endpoint with real users; every new endpoint must be covered or declared exempt.
- `FrontendPermissionCodesIT` — permission codes referenced in the Angular frontend must match the seeded set.

## User attribution
`UserAttributionIT` (2 tests) — verifies that `createdBy`/`updatedBy` are populated by JPA auditing (`@EnableJpaAuditing` in `config/JpaConfig`).
