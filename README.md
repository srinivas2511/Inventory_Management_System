# Spring Manufacturing Inventory Management System

Inventory and manufacturing-material management for a spring manufacturer: raw material → inventory → production → WIP → quality → finished goods → customer order → dispatch, with full batch traceability.

**Stack:** Java 17 · Spring Boot 3.5 · PostgreSQL 16 · Angular 18 (Angular Material) · Docker

**Status:** Phase 8 complete (M8 passed) — full end-to-end manufacturing ERP: procurement → inventory → production → quality → sales → dispatch, with insight dashboards, batch traceability, scheduled alerts, and hardened for production (security headers, performance indexes, Playwright E2E, runbook, UAT checklist).

## Documentation
| Document | Purpose |
|---|---|
| [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) | what the system must do |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | structure, workflows, security, decisions |
| [docs/DESIGN.md](docs/DESIGN.md) | schemas, algorithms, API contracts, screens, seed data |
| [docs/PLAN.md](docs/PLAN.md) | phases, tasks, estimates, gates |
| [docs/RUNBOOK.md](docs/RUNBOOK.md) | deploy, upgrade, rollback, backup/restore |
| [docs/UAT_CHECKLIST.md](docs/UAT_CHECKLIST.md) | 10-role UAT checklist |
| [docs/env.example](docs/env.example) | environment variable template |
| [docs/decisions.md](docs/decisions.md) | open business decisions and the defaults in force |
| [docs/adr/](docs/adr/) | architecture decision records |

## Prerequisites
| Tool | Version |
|---|---|
| JDK | 17+ |
| Maven | 3.9+ |
| Node.js | 20+ (npm 10+) |
| Angular CLI | 18 (`npm i -g @angular/cli@18`) — optional, `npx ng` works too |
| Docker Desktop | recent, with Compose v2 |
| Git | any recent |

## Quick start (everything in Docker)
```bash
docker compose -f docker/docker-compose.yml -f docker/docker-compose.dev.yml up --build
```
| URL | What |
|---|---|
| http://localhost:8081 | web app (nginx serving Angular, proxying `/api`) |
| http://localhost:8080/swagger-ui.html | API docs (dev profile only) |
| http://localhost:8080/actuator/health | health |
| http://localhost:8025 | MailHog (captured e-mail, dev) |
| localhost:5432 | PostgreSQL (user/password/db: `ims`) |

Stop with `Ctrl+C`; remove containers and data with `docker compose -f docker/docker-compose.yml -f docker/docker-compose.dev.yml down -v`.

## Run without Docker (development)

### 1. Create the database
```sql
-- as a PostgreSQL superuser (psql -U postgres)
CREATE USER ims WITH PASSWORD 'ims';
CREATE DATABASE ims OWNER ims;
```
Flyway creates the `ims` schema and applies migrations automatically when the backend starts.

### 2. Backend
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
Override connection settings with `DB_URL`, `DB_USER`, `DB_PASSWORD` if needed.

### 3. Frontend
```bash
cd frontend
npm ci
npm start            # http://localhost:4200, proxies /api to http://localhost:8080
```

## Tests
```bash
cd backend  && mvn verify                        # unit tests, ArchUnit rules, integration tests (need Docker)
cd frontend && npm run test:ci                   # Karma/Jasmine, headless Chrome, with coverage
cd frontend && npm run lint && npm run format:check
cd frontend && npm run e2e                       # Playwright E2E (stack must be running at E2E_BASE_URL)
```
Integration tests (`*IT`) use a real PostgreSQL via Testcontainers and are skipped automatically when Docker is not available.
Playwright E2E tests require the full Docker stack running with `ims.demo.load-data=true`.

## Production-style run
```bash
cp docs/env.example docker/.env         # then fill in: DB_PASSWORD, JWT_SECRET, CORS_ORIGINS, etc.
docker compose -f docker/docker-compose.yml -f docker/docker-compose.prod.yml --env-file docker/.env up -d --build
```
The prod override refuses to start without secrets, exposes only the web port, disables Swagger, and loads no demo data.
See [docs/RUNBOOK.md](docs/RUNBOOK.md) for upgrade, rollback, and backup procedures.

## Repository layout
```
backend/    Spring Boot (com.springmfg.ims, package-by-feature)
frontend/   Angular (core / shared / features), Playwright E2E in e2e/
docker/     compose files, nginx.conf
docs/       requirements, architecture, design, plan, runbook, UAT checklist, env template, ADRs
.github/    CI workflow, pull request template
```

## Demo login credentials

Created by the demo loader in the `dev`/`demo` profile only (never production; set `ims.demo.load-data=true`):

| Username | Default password | Role |
|----------|-----------------|------|
| `admin` | `Admin@123` | ADMIN (all permissions) |

A production installation has no default user. Set `IMS_BOOTSTRAP_ADMIN_USERNAME` and `IMS_BOOTSTRAP_ADMIN_PASSWORD` for the first start (see `docs/RUNBOOK.md`, "First administrator").

Set `ims.demo.load-data=true` in your docker `.env` (or `application-dev.yml`) to auto-seed a complete end-to-end workflow on startup: supplier → raw-material stock → purchase order → production order (3 operations) → final inspection (48 pcs approved) → sales order → dispatch.

## Adding an industry vertical

The platform ships as a spring manufacturer. Other industries are data packs: a folder of SQL, no Java. Automotive, furniture and electronics are included as examples under `backend/src/main/resources/db/vertical/`. To add one, for example `medical`:

1. **Create the folder** `backend/src/main/resources/db/vertical/medical/` (the name is lower case, letters, digits and underscores).
2. **Add the seed files**, each a repeatable Flyway script that is safe to run twice (`ON CONFLICT DO NOTHING` or `WHERE NOT EXISTS`):
   - `R__medical_v1_types_attributes.sql`: one row in `product_types` (code up to 20 characters, an `industry` label) and its rows in `spring_attribute_definitions` (type, code, label, `NUMBER`/`TEXT`/`ENUM`/`BOOLEAN`, unit, required, limits, enum values as JSONB, display order).
   - `R__medical_v1_operations.sql`: rows in `operations`; prefix the codes (for example `MED-`) so they never clash with another vertical.
   - `R__medical_v1_rejection_reasons.sql`: rows in `rejection_reasons`, prefixed the same way, scope `INCOMING`, `PRODUCTION` or `BOTH`.
3. **Test it.** Copy the pattern in `IndustryVerticalIT`: apply the folder to a fresh database and check the rows, then create a product of the new type with its attributes through `POST /api/products`.
4. **Activate it** per deployment with `IMS_VERTICALS=medical` (comma separated for several) and restart; see `docs/RUNBOOK.md`, "Industry verticals". A deployment that does not name it never receives it.
5. **Adjust in the UI.** Attributes can then be added, edited and deactivated without a migration under Admin, Attribute Definitions. The sample standard times in operations are illustrative; set your own.

## Working on this project
See [CONTRIBUTING.md](CONTRIBUTING.md) and [CLAUDE.md](CLAUDE.md).
