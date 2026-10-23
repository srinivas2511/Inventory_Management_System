# test/java/com/mfg/ims/ CLAUDE.md

Root of the backend test tree. Two test kinds live here:

- **Unit / web-slice tests** (`*Test.java`) — no Spring context or only a slice; run with `mvn test`.
- **Integration tests** (`*IT.java`) — full Spring context + real PostgreSQL via Testcontainers; run with `mvn verify` (Failsafe).

Total: **1,262 unit/ArchUnit/web-slice tests + 2,426 integration tests** (current HEAD).

## Commands
```bash
mvn verify                               # everything
mvn test                                 # unit tests only (no ITs)
mvn failsafe:integration-test -Dit.test=SomeIT   # single IT class
mvn test -Dtest=SomeTest                 # single unit test class
```

## Top-level folder map
| Folder | Content |
|--------|---------|
| `support/` | Shared IT infrastructure: `AbstractIntegrationTest`, `TestClock`, `TestData`, `DockerSupport` |
| `architecture/` | ArchUnit structural rules (package dependencies, naming, ordering) |
| `security/` | Cross-cutting security guards: `RoleAccessIT`, `PermissionCatalogueIT`, `AdminHoldsEveryPermissionIT`, `EndpointSmokeIT`, SoD ITs |
| `contract/` | API contract guards: `ApiContractIT`, `FrontendSourcesTest`, `UatTraceabilityTest` |
| `system/` | System-level ITs: actuator exposure, backup/restore, graceful degradation, security baseline |
| `config/` | `DemoDataLoaderIT`, `VerticalPacksTest` |
| `gc/` | GC-1…GC-6 gap rows: `GcGapRowsIT` |
| `search/` | `SearchIT`, `PagedListOrderingIT` |
| `vertical/` | `IndustryVerticalIT` — industry-vertical feature flags |
| `common/` | `ProblemsTest` — error response shape |
| *feature/* | One folder per domain (audit, auth, finance, inventory, production, quality, …) |

## Rules that apply to every IT
1. Extend `AbstractIntegrationTest` — it provides the shared Testcontainers PostgreSQL container.
2. Non-transactional ITs (those that test real commit/rollback behaviour) must annotate the class `@Transactional(propagation = NEVER)` and manage data cleanup themselves.
3. New `@PreAuthorize` authorities must be covered in `RoleAccessIT` (or declared exempt) and exist in the permission catalogue.
4. New permissions must be granted to the ADMIN role in the same Flyway migration; `AdminHoldsEveryPermissionIT` fails the build otherwise.
5. New segregation-of-duties rules need a non-transactional IT with real principals.
