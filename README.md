# Spring Manufacturing Inventory Management System

Inventory and manufacturing-material management for a spring manufacturer: raw material → inventory → production → WIP → quality → finished goods → customer order → dispatch, with full batch traceability.

**Stack:** Java 17 · Spring Boot 3.5 · PostgreSQL 16 · Angular 18 (Angular Material) · Docker

**Status:** Phase 0 (foundations) — runnable skeleton: API with security baseline, Angular shell, Docker stack, CI. Business modules arrive phase by phase (see `docs/PLAN.md`).

## Documentation
| Document | Purpose |
|---|---|
| [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) | what the system must do |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | structure, workflows, security, decisions |
| [docs/DESIGN.md](docs/DESIGN.md) | schemas, algorithms, API contracts, screens, seed data |
| [docs/PLAN.md](docs/PLAN.md) | phases, tasks, estimates, gates |
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
cd backend  && mvn verify                       # unit tests, ArchUnit rules, integration tests (need Docker)
cd frontend && npm run test:ci                  # Karma/Jasmine, headless Chrome, with coverage
cd frontend && npm run lint && npm run format:check
```
Integration tests (`*IT`) use a real PostgreSQL via Testcontainers and are skipped automatically when Docker is not available.

## Production-style run
```bash
cp docker/.env.example docker/.env      # then edit: strong DB_PASSWORD, real CORS_ORIGINS
docker compose -f docker/docker-compose.yml -f docker/docker-compose.prod.yml --env-file docker/.env up -d --build
```
The prod override refuses to start without secrets, exposes only the web port, disables Swagger, and loads no demo data.

## Repository layout
```
backend/    Spring Boot (com.springmfg.ims, package-by-feature)
frontend/   Angular (core / shared / features)
docker/     compose files, nginx.conf, .env.example
docs/       requirements, architecture, design, plan, decisions, ADRs
.github/    CI workflow, pull request template
```

## Sample login credentials
None yet — authentication and demo users arrive in Phase 1 and Phase 8. Demo users will load only under the `dev`/`demo` profile.

## Working on this project
See [CONTRIBUTING.md](CONTRIBUTING.md) and [CLAUDE.md](CLAUDE.md).
