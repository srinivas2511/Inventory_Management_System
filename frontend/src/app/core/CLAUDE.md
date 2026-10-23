# core/ CLAUDE.md

App-wide singletons — provided at the root level, never re-provided in feature modules.

## Sub-folders
| Folder | What lives here |
|--------|----------------|
| `auth/` | `AuthService`, `AuthGuard`, `PermissionGuard`, token interceptor, forced-password-change interceptor |
| `http/` | `ApiInterceptor` (auth headers), `RefreshInterceptor` (401 → refresh + retry), `ErrorInterceptor` (toast on 4xx/5xx) |
| `layout/` | `ShellComponent` (toolbar, sidenav, locale switcher), `NavConfig`, route layout wrappers |
| `config/` | `AppConfigService` — loads `assets/config.json` at startup via `APP_INITIALIZER` |
| `models/` | Shared TypeScript interfaces for auth state, user profile, permissions |
| `modules/` | `ModuleActivationService` — reads active ERP modules from system settings |
| `notification/` | `NotificationService` — polls `/api/notifications` and shows snackbars |
| `stepup/` | Step-up authentication dialog for sensitive operations |
| `api/` | Generated or hand-written API client services (`ProcurementApi`, etc.) |

## Auth flow
1. `APP_INITIALIZER` in `app.config.ts` calls `AuthService.restore()` — reads the HttpOnly refresh cookie and restores the in-memory access token before the router runs.
2. Access token is memory-only (never `localStorage`).
3. `RefreshInterceptor` serialises concurrent 401s into one refresh call; on failure it routes to `/login`.
4. A `PASSWORD_CHANGE_REQUIRED` 403 from any `/api/**` call routes to `/auth/change-password`.

## Permission checks
`hasPermission(code: string): boolean` on `AuthService` — used in templates as `*ngIf="auth.hasPermission('INVENTORY_ADJUST')"`.
These are **UX guards only** — the server returns 403 for unauthorised calls regardless.

## Rules
- Do **not** lazy-load this folder — it provides root-level singletons.
- Do **not** add feature-specific UI here; feature screens live in `features/`.
- The `ShellComponent` toolbar contains the `LocaleSwitcherComponent` — edit locale behaviour there.
