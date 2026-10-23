# test/architecture/ CLAUDE.md

ArchUnit structural tests — enforced at `mvn test` time (no Spring context needed).

## Test classes
| Class | What it enforces |
|-------|-----------------|
| `ArchitectureTest` | Core rules: no feature package imports `common/`; controllers depend only on services; services depend only on repositories and other services; entities never cross the API boundary (no entity in a controller method signature); every controller method has `@PreAuthorize` |
| `AdvancedMfgArchTest` | Advanced manufacturing packages (`maintenance/`, `oee/`, `scheduling/`) follow the same layering rules |
| `ControllingArchTest` | `controlling/` follows layering; `AccountingBridgeService` is the only writer of journal entries |
| `CrmArchTest` | `crm/` follows layering; no direct access to `partner/` domain bypassing the service layer |
| `DocumentNotificationArchTest` | `document/` and `notification/` have no circular dependencies; outbox pattern is used for delivery |
| `FinanceArchTest` | No direct writes to `journal_headers`/`journal_lines` from outside `finance/gl/`; `AccountingBridgeService` is the only external writer |
| `HrmArchTest` | `hrm/` layering; payroll rules are stateless |
| `IntegrationArchTest` | `integration/` never calls external HTTP directly from a service; all outbound calls go through the outbox or a dedicated delivery service |
| `MrpArchTest` | `mrp/engine/` has no Spring dependencies (pure domain logic) |
| `PlmArchTest` | `plm/` follows layering; `SegregationOfDuties` is called in the approval service |
| `QualityPlusArchTest` | `qualityplus/` follows layering; SPC point evaluation is stateless |
| `WmsArchTest` | `wms/` all stock movements go through `InventoryPostingService` |
| `PagedEndpointOrderingTest` | Every paged `@GetMapping` controller method declares a default sort (required to prevent non-deterministic pagination in `RoleAccessIT` and e2e tests) |

## Adding a new ArchUnit rule
Add it to the relevant existing `*ArchTest` class or create a new one (name it `<Domain>ArchTest`). Rules run as part of `mvn test` and block the build immediately — no Testcontainers needed.
