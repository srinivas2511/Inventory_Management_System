# core/http/ CLAUDE.md

HTTP interceptors applied to every outgoing request.

## Spec files
| File | What it covers |
|------|---------------|
| `interceptors.spec.ts` | `ApiInterceptor`: adds `Authorization: Bearer <token>` header when a token is in memory; omits the header on `/api/auth/login` and `/api/auth/refresh`; `RefreshInterceptor`: a 401 triggers exactly one refresh call even when multiple requests are in-flight concurrently; after refresh, the original request is retried with the new token; a failed refresh routes to `/login`; `ErrorInterceptor`: 4xx/5xx responses surface a snackbar with the problem detail message; 401 handled by refresh interceptor first (not shown as an error toast) |
| `step-up.interceptor.spec.ts` | `StepUpInterceptor`: a `428 Precondition Required` response from the server opens the step-up dialog; on dialog confirm the original request is retried with the step-up token in the header; on dialog cancel the request is rejected with the original 428 |

## Notes
- `RefreshInterceptor` uses a shared `BehaviorSubject` to serialise concurrent refresh calls — only one `POST /api/auth/refresh` is sent regardless of how many requests fail with 401 simultaneously.
- The interceptor chain order matters: `StepUpInterceptor` → `ApiInterceptor` → `RefreshInterceptor` → `ErrorInterceptor`.
- `interceptors.spec.ts` is a regression guard for D-20 (browser Back was dropping the session because a concurrent refresh was being treated as token theft; the grace window fix is in the backend, but the interceptor must not double-fire refresh).
