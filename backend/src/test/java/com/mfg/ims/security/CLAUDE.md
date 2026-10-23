# test/security/ CLAUDE.md

Cross-cutting security guards. **These are the most important ITs in the project** — they fail the build when any new endpoint, permission, or role is misconfigured.

## Test classes
| Class | Tests | What it guards |
|-------|-------|----------------|
| `RoleAccessIT` | ~1,876 | Every `@PreAuthorize`-protected endpoint is probed with a real user per role. Permitted callers must get 2xx (or a declared non-2xx); forbidden callers must get exactly 403. A new endpoint not covered here (or not declared exempt) fails the build. |
| `PermissionCatalogueIT` | 2 | Every authority string used in any `@PreAuthorize` annotation must exist in the `permissions` table. Prevents phantom authorities that nobody can hold. |
| `AdminHoldsEveryPermissionIT` | 2 | The ADMIN role must hold all seeded permission codes. Every new migration that adds a permission code must also grant it to ADMIN in the same migration. |
| `EndpointSmokeIT` | 3 | Every `GET /api/**` endpoint (discovered from MVC mappings) must return non-5xx for ADMIN, once per optional filter, and every report endpoint. Catches null-pointer / unhandled-null-filter 500s before they reach production. |
| `SegregationOfDutiesIT` | 5 | Core SoD rules: journal approver ≠ preparer; sales order approver ≠ creator/submitter; PO approver ≠ submitter. Non-transactional; uses real principals. |
| `AdminSegregationOfDutiesIT` | — | Even the ADMIN role cannot bypass SoD rules (approving their own document returns 403). |
| `FrontendPermissionCodesIT` | 1 | Permission code strings used in the Angular frontend (`hasPermission('...')` calls) must match the seeded set. Keeps frontend and backend in sync. |

## Rules for new endpoints
1. Add the new endpoint URL pattern and expected status per role to `RoleAccessIT` (or add a `@DeclaredExempt` annotation if it is genuinely public).
2. Add any new permission codes to a Flyway migration AND grant them to ADMIN in the same migration.
3. If the endpoint enforces SoD, add a non-transactional probe in `SegregationOfDutiesIT`.

## RoleAccessIT probe format
Each probe is a row: `(method, path, body, permittedRoles[], expectedStatusForForbidden)`. The test logs in as each seeded role user in turn and asserts the status. Declared non-2xx (e.g. 404 for a missing entity) must be listed explicitly so the test does not interpret them as access failures.
