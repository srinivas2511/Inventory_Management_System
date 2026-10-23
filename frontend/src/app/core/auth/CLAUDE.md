# core/auth/ CLAUDE.md

Authentication services, guards, and helpers.

## Spec files
| File | What it covers |
|------|---------------|
| `auth.guard.spec.ts` | `AuthGuard`: redirects unauthenticated users to `/login`; allows authenticated users through; `canActivate` defers to `AuthService.isLoggedIn$` |
| `auth.service.spec.ts` | `AuthService`: `login()` stores access token in memory (not `localStorage`); `logout()` calls `POST /api/auth/logout` and clears state; `refreshToken()` uses the HttpOnly cookie; `isLoggedIn$` emits correctly after login/logout; `currentUser$` emits the decoded JWT claims |
| `landing.spec.ts` | `LandingResolver`: resolves the correct first route for each role (ADMIN → `/admin`, PRODUCTION_OPERATOR → `/production/my-work`, FINANCE_MANAGER → `/finance`, etc.); a plant-scoped user never resolves to a company-level route |
| `password-policy.spec.ts` | `PasswordPolicyValidator`: min 8 chars, at least one upper, one lower, one digit, one special character; rejects spaces; exact boundary cases |
| `return-url.spec.ts` | `ReturnUrlService`: stores the pre-login URL; clears it after a successful redirect; does not store `/login` or `/` as the return URL |

## Notes
- Access tokens are **memory-only** — `AuthService` never writes them to `localStorage` or `sessionStorage`. The refresh token travels only in an HttpOnly cookie.
- `AuthGuard` is used on every protected route; `PermissionGuard` (in `core/`) handles per-permission checks within authenticated routes.
- `landing.spec.ts` covers the D-04 fix: roles must land on their documented first screen, not a blank dashboard that returns 403.
