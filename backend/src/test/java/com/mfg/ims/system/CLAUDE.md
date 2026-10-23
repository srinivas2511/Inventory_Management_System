# test/system/ CLAUDE.md

System-level ITs covering infrastructure, observability, and platform hardening.

## Test classes
| Class | Tests | What it covers |
|-------|-------|----------------|
| `ActuatorExposureIT` | 4 | Actuator endpoint access control: anonymous → 401, non-admin → 403, admin → 200 for metrics/prometheus; Swagger/OpenAPI returns 404 outside the `dev` profile |
| `BackupRestoreIT` | 1 | Dumps the live Testcontainers PostgreSQL (`pg_dump -n ims -Fc`), copies the dump to the host, starts a fresh `postgres:16-alpine` container via `DockerSupport`, restores with `pg_restore`, asserts table count ≥ 50 matches the source |
| `GracefulDegradationIT` | 4 | Non-transactional; drops the unique index on `mv_production_monthly` to force `REFRESH MATERIALIZED VIEW CONCURRENTLY` to fail; asserts `refreshAll()` captures the failure (not throws); asserts inventory posting succeeds in a separate transaction; asserts `/actuator/health/readiness` stays `UP`; asserts `/actuator/health/reporting` shows `DEGRADED`. `@AfterAll` recreates the index. |
| `NoDefaultUserIT` | 1 | Verifies `UserDetailsServiceAutoConfiguration` is excluded and no default-password line appears in the Spring Boot startup log (SEC-09) |
| `SecurityBaselineTest` | — | Checks that the `SecurityConfig` bean rejects anonymous access to `/api/**` (unit-level, no Testcontainers) |
| `SecurityHeadersTest` | — | Verifies that `SecurityHeadersFilter` adds a non-null CSP header on every response (web-slice test) |
| `SystemControllerIT` | 3 | `GET /api/system/ping` returns 200 for anonymous; `GET /api/system/info` requires auth; version info fields are present |

## Notes
- `BackupRestoreIT` is the E2E guard for NFR-005 (backup automation). It uses `DockerSupport.startFreshPostgres()` to start a disposable container, so Docker must be available on the CI agent.
- `GracefulDegradationIT` is the E2E guard for NFR-003 (graceful degradation). It must be non-transactional because it tests real commit boundaries.
- `SecurityHeadersTest` is a web-slice (`@WebMvcTest`) — it runs without Testcontainers and fails fast if the CSP filter is accidentally removed.
