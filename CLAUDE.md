# CLAUDE.md — Configurable Manufacturing ERP Platform

Production-ready ERP platform for manufacturers, starting with Spring Manufacturing (v1).
Full lifecycle: raw material → inventory → production → WIP → quality → finished goods → customer order → dispatch.
Extended modules: Finance, Controlling, MRP, CRM, HR, WMS, PLM, Advanced Quality, Analytics.
Stack: Java 17+ / Spring Boot 3 / PostgreSQL / Angular 18+ (Angular Material) / Docker.

## Read first (in `docs/`)
- `REQUIREMENTS.md` v2.0 — what to build (24+ modules, R1–R20)
- `ARCHITECTURE.md` v2.0 — structure, workflows, security, ADRs
- `DESIGN.md` v2.0 — schemas, algorithms, API contracts, screens, seed data, tests
- `PLAN.md` v2.0 — releases R1–R20, task IDs, estimates, exit gates

Before any task: read the DESIGN/ARCHITECTURE sections the PLAN task references. If code and docs disagree, stop and flag it rather than guessing.

## Current status
- **R1–R11 COMPLETE — gate M11 passed (2026-10-03). `mvn verify`: 172 unit/ArchUnit/slice tests + 62 integration tests (9 IT classes) against real PostgreSQL, 0 skipped, 0 failed. Frontend: 48/48 unit tests, lint clean, format clean, prod build green.**
- **Docs: v2.0 — expanded to SAP-class ERP platform scope (R9–R20 planned, 2026-10-02); R11/R14/R15 plan contradictions resolved in docs/decisions.md (2026-10-03).**
- IT counts: ControllingIT 13, FinanceIT 4, InventoryPostingIT 11, MrpIT 14, ProcurementIT 2, ProductionIT 4, FinalInspectionIT 5, SalesDispatchIT 6, SystemControllerIT 3.
- Migrations: V1–V22 (V19 cost/profit centres + journal dimensions + controlling permissions, V20 product costing, V21 production variance, V22 MRP runs/proposals/forecasts/planning params + MRP permissions). Next release: R12 CRM → V23+ (PLAN's migration numbers for R12–R20 are one behind; renumber when each release starts).
- Update this line at every milestone gate (M0…M20).

## Non-negotiable rules
1. **Stock changes only through `InventoryPostingService`.** Never write to `inventory_transactions` or `inventory_balances` anywhere else. The ledger is append-only; corrections are reversals.
2. **Every endpoint has `@PreAuthorize`** and a role test. Frontend permission checks are UX only; backend returns 403.
3. **Entities never cross the API boundary.** Use DTOs (records) and MapStruct. Controller → Service → Repository.
4. **Additive changes only.** Add new Flyway migrations (`V#__*.sql`); never edit an applied one. Extend existing code rather than rewriting it unless the task says so.
5. **Real PostgreSQL in tests** (Testcontainers). No H2.
6. **Business rules 1–19** (REQUIREMENTS §25) each need a test; rules 16–19 added in R9.
7. **Audit** every sensitive action (old value, new value, reason) in the same transaction.
8. Quantities `NUMERIC(18,3)` / `BigDecimal`; money `NUMERIC(18,4)`; timestamps `TIMESTAMPTZ` (UTC). Never floating point.
9. Document state changes go through state machines; no free-form status updates.
10. Passwords: BCrypt(12). Never log secrets or passwords. Secrets come from the environment.
11. No TODO/placeholder implementations inside a finished release.
12. Run the build and relevant tests before saying a task is done; report failures honestly.
13. **Journal entries must balance (Dr = Cr).** `JournalService` enforces this before posting; DB trigger enforces immutability after posting. (R9+)
14. **Financial periods gate all postings.** Never post to a CLOSED period without Finance Manager override (audited). (R9+)
15. **Auto-journals from `AccountingBridgeService` only.** No direct writes to `journal_headers`/`journal_lines` from inventory or production services. (R9+)

## Working style
- Work release by release, task by task (IDs in PLAN.md). Finish a release completely (backend + migration + Angular + tests + seed + docs) before the next.
- One task ID per commit, conventional commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`), e.g. `feat(R9-1.3): journal engine`.
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

## Decisions in force (defaults until confirmed)
- BOM quantity basis: **configurable per BOM** via `bom.base_quantity` column (exposed in UI); system setting `bom.default_base_quantity=1` is the default for new BOMs; demo seed uses 0.045 kg/pc for spring products
- Traceability granularity: **configurable via system setting** `quality.trace_granularity` (ORDER / BATCH); default ORDER
- Stock-adjustment Admin threshold: variance value ₹10,000 (setting `inventory.adjustment.admin_threshold`)
- PO approval: any user holding `PURCHASE_APPROVE`
- Belleville/Custom spring attributes: per DESIGN §14 item 11
- Demo users and demo data load only in the `dev`/`demo` profile, never production
- **Finance (R9):** functional currency = INR; costing method = moving weighted average (switchable per company); fiscal year = April–March (Indian standard); auto-journals generated from StockPosted events via AccountingBridgeService
- **MRP (R11):** batch run (not real-time); max BOM explosion depth = 10 levels; lot sizing = EXACT by default (configurable per material)
- **Payroll (R13):** Indian statutory deductions (PF 12% employer + 12% employee on basic up to ₹15,000; ESI 3.25% employer + 0.75% employee on gross ≤ ₹21,000); rates in system_settings
- **Next migration versions:** R9 starts at V14; never reuse V1–V13
