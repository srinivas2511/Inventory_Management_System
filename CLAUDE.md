# CLAUDE.md — Spring Manufacturing Inventory Management System

Production-ready inventory/manufacturing system for a spring manufacturer:
raw material → inventory → production → WIP → quality → finished goods → customer order → dispatch.
Stack: Java 17+ / Spring Boot 3 / PostgreSQL / Angular 18+ (Angular Material) / Docker.

## Read first (in `docs/`)
- `REQUIREMENTS.md` — what to build
- `ARCHITECTURE.md` — structure, workflows, security, ADRs
- `DESIGN.md` — schemas, algorithms, API contracts, screens, seed data, tests
- `PLAN.md` — phases, task IDs, estimates, exit gates

Before any task: read the DESIGN/ARCHITECTURE sections the PLAN task references. If code and docs disagree, stop and flag it rather than guessing.

## Current status
- **Phase:** 0 — Foundations: skeleton delivered; **gate M0 pending** until `mvn verify` and `docker compose up` are confirmed green on a machine with Maven Central and Docker access
- Verified so far (frontend): lint, 19 unit tests, production build. Backend and Docker images are written but not yet built.
- Update this line at every milestone gate (M0…M8).

## Non-negotiable rules
1. **Stock changes only through `InventoryPostingService`.** Never write to `inventory_transactions` or `inventory_balances` anywhere else. The ledger is append-only; corrections are reversals.
2. **Every endpoint has `@PreAuthorize`** and a role test. Frontend permission checks are UX only; backend returns 403.
3. **Entities never cross the API boundary.** Use DTOs (records) and MapStruct. Controller → Service → Repository.
4. **Additive changes only.** Add new Flyway migrations (`V#__*.sql`); never edit an applied one. Extend existing code rather than rewriting it unless the task says so.
5. **Real PostgreSQL in tests** (Testcontainers). No H2.
6. **Business rules 1–15** (REQUIREMENTS §6) each need a test.
7. **Audit** every sensitive action (old value, new value, reason) in the same transaction.
8. Quantities `NUMERIC(18,3)` / `BigDecimal`; money `NUMERIC(18,4)`; timestamps `TIMESTAMPTZ` (UTC). Never floating point.
9. Document state changes go through state machines; no free-form status updates.
10. Passwords: BCrypt(12). Never log secrets or passwords. Secrets come from the environment.
11. No TODO/placeholder implementations inside a finished phase.
12. Run the build and relevant tests before saying a task is done; report failures honestly.

## Working style
- Work phase by phase, task by task (IDs in PLAN.md). Finish a phase completely (backend + migration + Angular + tests + seed + docs) before the next.
- One task ID per commit, conventional commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`), e.g. `feat(P2-2.3): inventory posting service`.
- Keep changes focused; do not touch unrelated code.
- Concise summaries: what changed, tests run, any deviation from DESIGN.md.

## Repository layout
```
backend/    Spring Boot (package com.springmfg.ims, package-by-feature)
frontend/   Angular (core / shared / features)
docker/     compose files, nginx.conf
docs/       REQUIREMENTS.md, ARCHITECTURE.md, DESIGN.md, PLAN.md, decisions.md, adr/
```

## Commands
```bash
docker compose -f docker/docker-compose.yml -f docker/docker-compose.dev.yml up --build   # whole stack; web on :8081
cd backend  && mvn verify                                   # unit + ArchUnit + integration tests (ITs need Docker)
cd backend  && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && npm ci && npm run lint && npm run format:check && npm run test:ci && npm run build:prod
cd frontend && npm start                                    # :4200, proxies /api to :8080
```

## Decisions in force (defaults until confirmed — see PLAN §14)
- BOM wire quantity: 0.045 kg per piece (`base_quantity` supported)
- Stock-adjustment Admin threshold: variance value ₹10,000 (setting `inventory.adjustment.admin_threshold`)
- PO approval: any user holding `PURCHASE_APPROVE`
- Belleville/Custom spring attributes: per DESIGN §14 item 11
- Traceability: order-level by default, batch-level optional
- Demo users and demo data load only in the `dev`/`demo` profile, never production
