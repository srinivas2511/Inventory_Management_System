# backend/CLAUDE.md

Spring Boot 3 / Java 17+ backend for the ERP platform.
Root package: `com.mfg.ims` — package-by-feature layout.
Database: PostgreSQL 16 (real Testcontainers in every test — no H2).

## Commands
```bash
mvn verify                          # unit + ArchUnit + integration tests
mvn spring-boot:run -Dspring-boot.run.profiles=dev   # dev server on :8080
mvn test -pl . -Dtest=SomeIT        # single IT class
```

## Package map
| Package | Responsibility |
|---------|---------------|
| `common/` | Cross-cutting: security, search, settings, numbering, events, exceptions |
| `auth/` | JWT auth, refresh tokens, session management |
| `iam/` | Users, roles, permissions |
| `inventory/` | Stock postings, balances, ledger, adjustments |
| `procurement/` | PO, goods receipt, AP invoicing, three-way match |
| `production/` | Production orders, operations, BOM explosion, WIP |
| `quality/` / `qualityplus/` | Inspections, SPC, CAPA, FAI, customer complaints |
| `sales/` | Sales orders, dispatch, AR invoicing |
| `finance/` | Journals, period close, GST, multi-currency |
| `controlling/` | Cost/profit centres, variance reporting |
| `mrp/` | MRP batch run, lot sizing |
| `hrm/` | Employees, payroll, leave |
| `wms/` | Zones, bins, transfer orders, wave picking |
| `maintenance/` | Machines, spare parts, work orders, OEE |
| `analytics/` | Materialized views, refresh job |
| `crm/` | Customers, contacts, activities |
| `plm/` | ECOs, product lifecycle |
| `masterdata/` / `material/` / `product/` | Core master data |
| `notification/` | Outbox, templates, subscriptions |
| `document/` | Document categories, versions, links |
| `integration/` | Webhook outbox |

## Non-negotiable rules (enforced by ITs)
1. **Stock only via `InventoryPostingService`** — never write `inventory_transactions`/`inventory_balances` directly.
2. **Every endpoint has `@PreAuthorize`** — backend returns 403; frontend checks are UX only.
3. **Entities never cross the API boundary** — DTOs (records) + MapStruct only.
4. **Flyway migrations are additive** — `V#__*.sql` only; never edit an applied migration.
5. **Auto-journals via `AccountingBridgeService`** — no direct writes to `journal_headers`/`journal_lines` from inventory/production.
6. **Journals must balance (Dr = Cr)** — enforced by `JournalService` and a DB trigger.
7. **Financial periods gate all postings** — never post to a CLOSED period without Finance Manager override (audited).
8. **Segregation of duties** — use `common.security.SegregationOfDuties`; the matrix is in ARCHITECTURE §10.2a.
9. **Audit every sensitive action** — old value, new value, reason, in the same transaction.
10. **Quantities** `NUMERIC(18,3)` / `BigDecimal`; **money** `NUMERIC(18,4)`; **timestamps** `TIMESTAMPTZ` (UTC).

## Test conventions
- IT classes extend `AbstractIntegrationTest` (Testcontainers PostgreSQL shared container).
- `RoleAccessIT` probes every `@PreAuthorize` — a new endpoint must be covered or declared.
- `PermissionCatalogueIT` — every authority used in `@PreAuthorize` must be seeded.
- `ApiContractIT` + `FrontendSourcesTest` — frontend HTTP calls must match backend endpoints.
- `EndpointSmokeIT` — every `GET /api/**` must return non-5xx for ADMIN.
- `AdminHoldsEveryPermissionIT` — ADMIN role must hold all seeded permission codes.
- Segregation of duties rules need a non-transactional IT with real principals.

## Migrations in force
Current head: **V56** (`Manufacturing Co.` company rename).
Next free number: **V57**.
Never reuse V1–V56. Add a comment in the SQL header naming the release task.

## Current Flyway head: V56
Notable recent migrations:
- V55 — GC-1..GC-6 (alt UOMs, purchasing info, negative-stock overrides, customer tolerance overrides, CSP)
- V54 — plant scoping (`user_plant_access`)
- V46 — `SYSTEM_MONITOR` permission
- V41 — `USER_PASSWORD_RESET`
- V40 — AP three-way match columns and accounts
