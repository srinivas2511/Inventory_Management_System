# test/iam/ CLAUDE.md

Identity and access management ITs.

## Test classes
| Class | Type | Tests | What it covers |
|-------|------|-------|----------------|
| `AdminPasswordResetIT` | IT | 7 | Admin resets another user's password; one-time password shown once; other sessions invalidated; reset audited without the password value; cannot reset your own account; requires `USER_PASSWORD_RESET` |
| `LastAdminGuardIT` | IT | 6 | Cannot deactivate the last ADMIN (advisory lock prevents race); cannot demote the last ADMIN's role; a second ADMIN can be deactivated; advisory lock test uses concurrent threads |
| `UserCreationRoleGuardIT` | IT | 5 | Creating a user with roles requires `ROLE_MANAGE`; creating without roles requires only `IAM_MANAGE`; role assignment after creation requires `ROLE_MANAGE` |
| `InitialAdminBootstrapIT` | IT | — | `iam/service/` — first-run bootstrap: creates the ADMIN user from `IMS_BOOTSTRAP_ADMIN_USERNAME`/`_PASSWORD` only when no users exist; idempotent on re-run |
| `InitialAdminBootstrapTest` | Unit | — | `iam/service/` — bootstrap logic with no DB: correct BCrypt(12) hashing, never logs the password |

## Notes
- `LastAdminGuardIT` is non-transactional — the advisory-lock race test spins up two concurrent threads and verifies exactly one succeeds and one gets a conflict error.
- `AdminPasswordResetIT` is non-transactional — it verifies that the reset-target's sessions are actually invalidated by attempting a refresh with their old token after the reset.
