# features/auth/ CLAUDE.md

Authentication screens: login, change password, forced first-login change, and password reset.

## Screens
| Component | Route | Notes |
|-----------|-------|-------|
| `LoginComponent` | `/login` | Email + password; calls `AuthService.login()`; redirects to the user's first permitted screen |
| `ChangePasswordComponent` | `/auth/change-password` | Current + new + confirm; also handles forced first-login change (`PASSWORD_CHANGE_REQUIRED` flag) |
| `ForgotPasswordComponent` | `/auth/forgot-password` | Initiates a reset email |
| `ResetPasswordComponent` | `/auth/reset-password` | Consumes reset token from query params |

## Notes
- `ChangePasswordComponent` is reached automatically when the backend returns `403 PASSWORD_CHANGE_REQUIRED` (intercepted by `core/http/RefreshInterceptor`).
- These routes are public — no `AuthGuard` applied.
- After a successful password change the user is redirected to login (all sessions are invalidated server-side).
- No nav shell wraps these screens; they use a standalone centred layout.
