# e2e/security/ CLAUDE.md

E2E tests for security: RBAC, role sweep, HTTP security headers, and segregation of duties.

## Spec files (22 tests total)
| File | Tests | What it covers |
|------|-------|---------------|
| `security.spec.ts` | 11 | HTTP security headers (CSP, X-Frame-Options, X-Content-Type-Options); actuator 401/403 for anonymous and non-admin; no default Spring Boot password in log; Swagger 404 outside dev (SEC-09) |
| `rbac.spec.ts` | 4 | Permission boundaries: a role can reach its permitted screens; a forbidden screen returns 403 (not a redirect) |
| `role-sweep.spec.ts` | 4 | Every seeded role user logs in successfully and reaches their first permitted route |
| `segregation-of-duties.spec.ts` | 3 | PO submitter cannot approve (PRC-04); SO submitter cannot approve (SEC-08b); journal preparer cannot approve (FIN-JNL-2) — UI disabled + direct API returns 403 |

## Notes
- `security.spec.ts` SEC-09 check: `UserDetailsServiceAutoConfiguration` is excluded from Spring Boot autoconfiguration, so no default user line appears in the backend log; `NoDefaultUserIT` on the backend guards the same rule.
- `role-sweep.spec.ts` uses all 14 `ROLE_USERS` from `support/users.ts`.
- `segregation-of-duties.spec.ts` tests both the UI disabled state and the direct API 403 — the UI check alone is insufficient because a user could call the API directly.
- CSP header test is the E2E guard for `SecurityHeadersFilter` + `SecurityHeadersTest` (backend).
