# admin/role/ CLAUDE.md

Role list and permission assignment screen.

## Spec files
| File | What it covers |
|------|---------------|
| `role-list.component.spec.ts` | `RoleListComponent`: lists all roles; clicking a role opens a detail dialog with its permission codes grouped by module; `ROLE_MANAGE` permission required for assign/unassign; a permission already held by a user in the role cannot be removed if it is the last one holding a critical code (server-enforced, but the spec verifies the error is surfaced in the UI) |

## Notes
- Roles map to the seeded set from Flyway migrations. The frontend never creates new roles — it only assigns/unassigns permissions from existing ones.
- `FrontendPermissionCodesIT` (backend) fails the build if a permission code used in a frontend `hasPermission()` call does not exist in the seeded set.
