# test/support/ CLAUDE.md

Shared IT infrastructure. Import from here — never duplicate in individual ITs.

## Files
| File | Purpose |
|------|---------|
| `AbstractIntegrationTest` | Base class for every IT. Starts a single shared `postgres:16-alpine` Testcontainers container (reused across the whole test run via `@DynamicPropertySource`). Provides `protected static sharedPostgres()` accessor for cross-package container access (used by `BackupRestoreIT`). |
| `TestClock` | `Clock` bean override for tests. Allows ITs to fix or advance time deterministically. Injected via `@TestConfiguration`; production code depends on the `Clock` bean configured in `config/ClockConfig.java`. |
| `TestData` | Fluent builder for seeding test data (materials, products, warehouses, suppliers, customers, users). Keeps ITs short by hiding boilerplate. |
| `DockerSupport` | Utility methods for verifying Docker availability, starting/stopping auxiliary containers (e.g. a fresh Postgres for `BackupRestoreIT`), and reading container logs. |

## AbstractIntegrationTest contract
- Extend it in every `*IT.java` class.
- Do **not** extend it in `*Test.java` (unit/web-slice) classes — they run without Spring context.
- Flyway runs automatically against the Testcontainers database on the first startup; subsequent test classes reuse the same schema.
- Transactional ITs roll back after each test method. Non-transactional ITs (`@Transactional(propagation = NEVER)`) must clean up their own data in `@AfterEach` / `@AfterAll`.

## TestClock usage
```java
@Autowired TestClock clock;

clock.fixAt(Instant.parse("2026-10-01T00:00:00Z")); // freeze time
clock.advance(Duration.ofDays(1));                   // step forward
clock.reset();                                       // back to real time
```
Use it whenever a test asserts on timestamps, period dates, or time-based business rules.
