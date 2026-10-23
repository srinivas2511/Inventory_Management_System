# config/ CLAUDE.md

Spring Boot application-level configuration beans and filters. Not a feature — no business logic here.

## Key files
| File | Purpose |
|------|---------|
| `SecurityConfig.java` | Spring Security filter chain: JWT filter, CORS, CSRF, permit-list for public endpoints, actuator exposure |
| `JwtAuthenticationFilter.java` | Extracts and validates the access token from the `Authorization` header |
| `SecurityHeadersFilter.java` | Adds `Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options` to every response (`SecurityHeadersTest` guards this) |
| `DemoDataLoader.java` | Seeds demo data in `dev`/`demo` profiles only — never runs in production |
| `OpenApiConfig.java` | Springdoc/OpenAPI configuration; Swagger UI available in `dev` profile only |
| `SchedulingConfig.java` | `@EnableScheduling` + ShedLock configuration |
| `RetryConfig.java` | Spring Retry template for use in services that need backoff |
| `ClockConfig.java` | Provides a `Clock` bean (`TestClock` in tests) for deterministic time in ITs |
| `VerticalFlywayConfig.java` | Configures Flyway to apply migrations from `classpath:db/migration` |
| `VerticalPacks.java` | Feature-pack registry (which ERP modules are compiled in) |
| `CorrelationIdFilter.java` | Adds `X-Correlation-ID` to every request for log tracing |
| `CacheConfig.java` | Caffeine cache configuration (user details, system settings) |
| `JpaConfig.java` | JPA auditing (`@EnableJpaAuditing`), `AuditorAware` bean |

## Rules
- Do not add business logic here.
- `DemoDataLoader` runs only in `dev`/`demo` — guard with `@Profile({"dev","demo"})`.
- CSP header is required on every response — `SecurityHeadersTest` fails the build if it is absent or null.
- Actuator: health and info are public; metrics/prometheus require `SYSTEM_MONITOR` (ADMIN only, V46). Swagger/OpenAPI returns 404 outside the dev profile (`ActuatorExposureIT` guards this).
