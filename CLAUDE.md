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
- **R1–R17 COMPLETE — gate M17 passed (2026-10-04). `mvn clean verify` in one run: 957 unit/ArchUnit/web-slice tests + 684 integration tests (24 IT classes) against real PostgreSQL, 0 skipped, 0 failed. Frontend: 231/231 unit tests, lint clean, format clean, prod build green. R17 Document Management and Notifications: documents with versions (drawing revisions), links to products, BOMs, ECOs, suppliers, machines, CAPAs and complaints (CAPA evidence), per-link read access, categories that demand DRAWING_MANAGE or SPECIFICATION_MANAGE, an approval state machine with segregation of duties, content behind a `DocumentStorage` seam (local volume now, object store later) with extension/signature/size checks and a checksum, every upload and download audited; a notification engine (templates, per-user channel preferences, in-app inbox with a toolbar bell, an e-mail outbox with retry/back-off and idempotent delivery) wired to new alerts, approved purchase orders and CAPA assignments. Decisions and deviations in docs/decisions.md section "R17 Documents and Notifications" and DESIGN section 23.3; deployment notes in docs/RUNBOOK.md (documents volume, SMTP variables). Earlier release summaries: see git log and docs/decisions.md.**
- **AR invoicing fix (2026-10-04):** no dispatch had ever produced an AR invoice: `ArService.computeDispatchValue` was a zero stub, and the AR/AP journals used GL account CODES (1101, 4001, 2401...) as primary keys, so every journal would also have failed on a foreign key. Fixed: value = sum of quantity x unit price per line, accounts resolved by code (`GlAccountResolver`), zero-value dispatches get a settled zero invoice without a journal, invoicing runs after the dispatch commits (`DispatchCreatedEvent`) in its own transaction, a failure raises a `MISSING_AR_INVOICE` alert (inventory journals raise `MISSING_JOURNAL`), and `POST /api/ar/reconciliation/missing-invoices/retry` (AR_RECONCILE) backfills dispatches made before the fix. Partial AP/AR payments also failed on a status typo (`PARTIAL` vs `PARTIALLY_PAID`). Run the retry once after deploying. Frontend: the admin settings route/nav required the literal `ADMIN` and now use `SETTINGS_MANAGE`; `FrontendPermissionCodesIT` keeps frontend permission codes in step with the seeded ones. Details in docs/decisions.md.
- **Permission alignment (2026-10-04):** 18 authorities used by controllers were never seeded (MASTER_*_VIEW/MANAGE, IAM_*, GOODS_RECEIVE, SALES_MANAGE, MAINTENANCE_CREATE, ALERT_*, SALES_APPROVE, plus a literal `ADMIN` authority in SystemSettingController), so no real user, ADMIN included, could use master data, user admin, settings, audit, alerts, goods receipt, sales approval or maintenance create. Controllers and frontend now use the canonical DESIGN section 7.1 codes; V29 seeds only the three codes with no catalogue equivalent. `PermissionCatalogueIT` (every `@PreAuthorize` authority must be seeded and granted) and `RoleAccessIT` (a real user per seeded role logs in and is probed for access and 403, no hand-supplied authorities) guard against a repeat. The SALES_APPROVE, ALERT_* grants and the machine/operation lookup reads are choices that need business review, see docs/decisions.md.
- **Docs: v2.0 — expanded to SAP-class ERP platform scope (R9–R20 planned, 2026-10-02); R11/R14/R15 plan contradictions resolved in docs/decisions.md (2026-10-03).**
- IT counts: ControllingIT 13, CrmIT 23, DocumentIT 17, FinanceIT 8, ArFromDispatchIT 8, FrontendPermissionCodesIT 1, UserAttributionIT 2, HrmIT 17, InventoryPostingIT 11, MrpIT 14, NotificationIT 18, ProcurementIT 2, ProductionIT 4, FinalInspectionIT 7, SalesDispatchIT 6, SearchIT 8, SystemControllerIT 3, WmsIT 18, PlmIT 11, PermissionCatalogueIT 2, RoleAccessIT 459, AdvancedQualityIT 21, AuditLogSearchIT 8, EndpointSmokeIT 3 (644 in total). Tests are heavy on a memory-constrained machine: run one build at a time; if InventoryPostingIT.parallelIssues_neverOversell times out on a Hikari pool, rerun it alone before suspecting code. Stale files in backend/target/surefire-reports from older runs inflate a hand-made sum: trust the Maven summary line. Test-clock note: tests that move a timestamp must use the JVM clock, not the database `now()` (the Docker container clock can drift from the host).
- **Null-safe queries and smoke guards (2026-10-04):** `GET /api/audit-logs` (+export), `/api/inventory/ledger`, `/api/sales/orders|dispatches` (one-sided date filters), `/api/dashboard/kpis`, `/api/quality/fg-batches` (duplicate open FINAL inspections) and three reports (`PURCH_ORDERS`, `SALES_ORDERS_RPT`, `QUAL_INSPECTIONS`) returned 500 and are fixed. New rule: open-ended Instant/LocalDate filters use `common.search.QueryBounds` sentinels via a default repository method; native SQL casts nullable parameters (`CAST(:p AS date)`). `EndpointSmokeIT` calls every `GET /api/**` endpoint (discovered from the MVC mappings) as ADMIN, once per optional filter, and every report; it fails the build on any 5xx. See docs/decisions.md.
- Migrations: V1–V32 (V32 AR reconciliation: MISSING_AR_INVOICE/MISSING_JOURNAL alert types and templates, unique AR invoice per dispatch, AR_RECONCILE permission, finance roles see alerts; V31 documents and notifications: document categories/documents/versions/links, notification templates/subscriptions/notifications (outbox), DOCUMENT_VIEW/UPLOAD/APPROVE/MANAGE and NOTIFICATION_MANAGE, document and notification settings and templates; V30 advanced quality: SPC charts and points, CAPA, customer complaints, first article inspections, FAI inspection type, SPC alert type, SPC_MANAGE/CAPA_MANAGE/CAPA_CLOSE/COMPLAINT_MANAGE; V29 permission alignment: seeds ALERT_VIEW, ALERT_MANAGE, SALES_APPROVE and their narrow grants; V28 PLM: ecos, eco_affected_items, product_lifecycle_events, products.lifecycle_status, PLM permissions and PLM_ENGINEER, drops the stray bom_number unique; V19 cost/profit centres + journal dimensions + controlling permissions, V20 product costing, V21 production variance, V22 MRP, V23 CRM, V24 HRM core: shifts/employees/skills/attendance/leave/training + HR permissions and HR_MANAGER, V25 payroll runs/slips + `hrm.payroll.*` settings + payroll GL accounts, V26 `hrm.payroll.unrecorded_day_policy` setting). V27 WMS zones/bins/transfer orders/waves/cycle counts + WMS permissions and WAREHOUSE_MANAGER. Next release: R18 Advanced Manufacturing -> V32+ (PLAN migration numbers for R18–R20 are behind; renumber when each release starts).
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
