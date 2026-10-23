# auth/ CLAUDE.md

JWT-based authentication, session management, step-up auth, and password lifecycle.

## Responsibilities
- `service/AuthService` — login, logout, token refresh, account lockout, password change, forced first-login change, password reset (admin-initiated).
- `web/AuthController` — `/api/auth/**` endpoints (public, no `@PreAuthorize`).
- `stepup/` — step-up authentication challenge/response for sensitive operations (e.g. period reopen).
- `dto/` — `LoginRequest`, `TokenResponse`, `ChangePasswordRequest`, etc.

## Token model
- **Access token**: short-lived JWT (memory-only on the client, never `localStorage`).
- **Refresh token**: long-lived, stored as an HttpOnly cookie; rotated on every use.
- **Reuse grace window**: `ims.security.jwt.refresh-reuse-grace-seconds` (default 10 s) — concurrent requests within the grace window are not treated as token theft; prevents Back-button session loss.

## Key rules
- Login and refresh run in their own transactions; account lockout and refresh-token reuse detection both depend on the transaction committing.
- A `PASSWORD_CHANGE_REQUIRED` flag in the cached user causes every `/api/**` call (except `/api/auth/**`) to return 403 until the password is changed.
- A password change or admin reset ends all other sessions for that user.
- Admin password reset: `USER_PASSWORD_RESET` permission (ADMIN only); one-time generated password shown once, never logged; audited without the password value; cannot reset your own account.
- Account unlock: ADMIN only; audited.
- At least one active, unlocked ADMIN must always remain (`AdminGuard` uses an advisory lock).

## IT coverage
- `AuthSessionIT` — login, refresh, reuse grace, logout, concurrent requests.
- `ForcedPasswordChangeIT` — forced first-login change flow.
- `AdminPasswordResetIT` — admin reset, session invalidation.
- `LastAdminGuardIT` — cannot deactivate/demote the last ADMIN.
- `NoDefaultUserIT` — Spring Boot auto-configured default user must not appear in logs.
