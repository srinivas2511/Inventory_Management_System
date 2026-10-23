# admin/user/ CLAUDE.md

User administration dialogs and list.

## Spec files
| File | What it covers |
|------|---------------|
| `user-list.component.spec.ts` | `UserListComponent`: lists users via `GET /api/iam/users`; status badge per user (ACTIVE / LOCKED / INACTIVE); `IAM_MANAGE` shows create and deactivate controls; at least one active ADMIN must remain (`LastAdminGuard` — deactivate is disabled for the last active admin) |
| `user-form-dialog.component.spec.ts` | `UserFormDialogComponent`: create form — username, display name, email, initial password; password must meet the policy (min 8 chars, complexity); `ROLE_MANAGE` required to assign roles at create time; submits `POST /api/iam/users` |
| `user-plants-dialog.component.spec.ts` | `UserPlantsDialogComponent`: shows plant chip list; add/remove plant assignments; an empty assignment means unrestricted (global) access, not no-access |
| `user-reset-password-dialog.component.spec.ts` | `UserResetPasswordDialogComponent`: generates a one-time temporary password; shown once in the dialog and never again; `USER_PASSWORD_RESET` permission required; cannot reset your own password (server-enforced, 403 surfaced) |
| `user-roles-dialog.component.spec.ts` | `UserRolesDialogComponent`: checkbox list of available roles; `ROLE_MANAGE` required; creates user with roles on `POST /api/iam/users/{id}/roles` |

## Notes
- `UserResetPasswordDialogComponent` covers the auth-hardening story: the one-time password is shown once to the admin, then the user must change it on first login (`PASSWORD_CHANGE_REQUIRED` is set server-side).
- `user-plants-dialog.component.spec.ts` covers the plant-scoping assignment path; an empty plant list = unrestricted, not zero access (non-obvious invariant).
