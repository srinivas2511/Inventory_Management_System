# ARCHITECTURE.md — Configurable Manufacturing ERP Platform

**Version:** 2.0  **Date:** 2026-10-02
**Derived from:** REQUIREMENTS.md v2.0

---

## Table of Contents
0. Platform Architecture Overview
1. Architectural Goals and Principles
2. System Context
3. Architecture Style and Layering
4. Backend Architecture
5. Core Design: Inventory Engine [Implemented R1–R8]
6. Workflows and State Machines [Implemented R1–R8]
7. Key Sequence Flows
8. Database Architecture
9. Traceability Design [Implemented R1–R8]
10. Security Architecture [Implemented R1–R8]
11. Audit Architecture [Implemented R1–R8]
12. API Architecture
13. Frontend Architecture
14. Reporting, Dashboards and Alerts
15. Finance Architecture (R9)
16. Controlling Architecture (R10)
17. MRP Architecture (R11)
18. CRM Architecture (R12)
19. HRM Architecture (R13)
20. WMS Architecture (R14)
21. Cross-Cutting Concerns
22. Deployment Architecture
23. Testing Architecture
24. Extensibility Roadmap
25. Implementation Phasing
26. Architecture Decision Records

---

## 0. Platform Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                  INDUSTRY CONFIGURATION LAYER                    │
│  Springs (v1)  │  Automotive  │  Furniture  │  Electronics  │ … │
│  (seed data migrations only — zero code change per vertical)     │
└──────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────┐
│                    UNIVERSAL PLATFORM LAYERS                     │
│                                                                  │
│  Platform Services: IAM · Audit · Workflow · Notifications       │
│  Document Management · System Config · Numbering · Settings      │
│                                                                  │
│  Core ERP: Inventory · Purchase · Sales · Warehouses · Master   │
│                                                                  │
│  Manufacturing: BOM/PLM · Production/MFG · WIP · QMS · MRP     │
│                                                                  │
│  Enterprise: Finance/FIN · Controlling/CTR · HR/HRM · CRM      │
│              WMS · Maintenance/MNT                               │
│                                                                  │
│  Intelligence: Reports/RPT · Analytics/ANA · Integration/INT    │
└──────────────────────────────────────────────────────────────────┘
```

### 0.1 Industry Configuration layer (data only)

All industry-specific content is **data**, loaded via Flyway seed migrations:

| Artefact | Mechanism | Spring v1 example |
|---|---|---|
| Product attribute definitions | `product_attribute_definitions` rows + JSONB | `wire_diameter`, `spring_rate`, `end_type` |
| Material types | `materials.material_type` VARCHAR rows | SPRING_STEEL, MUSIC_WIRE |
| Standard operations | `operations` seed rows | Wire Drawing, Coiling, Grinding |
| Inspection parameters | `inspection_plan_parameters` rows | Diameter ± 0.03 mm, tensile strength |
| Rejection reasons | `rejection_reasons` seed rows | Coiling defect, Surface defect |
| Chart of accounts | `gl_accounts` seed rows | Industry-standard account codes |
| GL account assignments | `inventory_account_assignments` rows | RM stock → 1401, FG stock → 1410 |

### 0.2 Extensibility seams

Already in place (R1–R8):
- `product_attributes JSONB` — type-specific fields without schema changes
- `spring_attribute_definitions` — metadata catalogue drives backend validation and Angular form rendering
- `inspection_plan_parameters` — quality parameters are data rows, not code
- `materials.material_type` stored as `VARCHAR` (no hardcoded enum)
- `company_id` on every aggregate root table (multi-company / multi-plant hook)
- `plant_id` on transacting documents (enterprise structure hook, R9)
- Spring events + notification outbox seam for IoT/ERP integration

---

## 1. Architectural Goals and Principles

| # | Principle | Consequence |
|---|---|---|
| P1 | **Ledger is the source of truth** | Stock and financial balances derive from append-only ledgers; projection tables are transactional caches |
| P2 | **Every change has a business document** | No stock or financial mutation without a `reference_type` + `reference_id` |
| P3 | **State machines guard documents** | Status transitions are explicit, validated, and audited; no free-form status updates |
| P4 | **Backend is the security boundary** | Every endpoint authorises; frontend guards are UX only |
| P5 | **Traceability by construction** | Batch links are recorded at the moment of issue, production, inspection, dispatch, and financial posting |
| P6 | **History is immutable** | DB triggers prevent UPDATE/DELETE on ledger and audit tables; corrections are reversals |
| P7 | **Simple first, extensible later** | Modular monolith with seams for IoT, MRP, multi-company, finance |
| P8 | **Quality gates stock availability** | Quarantine/hold/rejected stock physically separated by status and location |
| P9 | **Finance closes the loop** | Operational transactions auto-generate journal entries; no manual re-entry |
| P10 | **Configuration over code** | Industry verticals, inspection plans, account assignments, approval rules = data rows, not code branches |

---

## 2. System Context

```
                              ┌───────────────────────────────┐
    Roles                     │  Configurable Manufacturing    │
    ─────                     │       ERP Platform            │
    Admin                     │                               │
    Engineer                  │  ┌──────────┐  ┌──────────┐  │
    Production Mgr/Supervisor │  │ Angular  │  │ Spring   │  │
    Operator (tablet)         │  │ 18+ SPA  │──│ Boot API │  │
    Quality                   │  └──────────┘  └────┬─────┘  │
    Store Mgr/Operator        │                     │         │
    Purchase                  │                ┌────▼─────┐   │
    Sales/Dispatch            │                │ PostgreSQL│   │
    Maintenance               │                │    16    │   │
    Finance/Accountant        │                └──────────┘   │
    Cost Controller           │                               │
    HR Manager                └───────────────────────────────┘
    Planner                           │         │
    Management                        │         │
                                      │         │
                               SMTP   │    Future integrations:
                               server │    Barcode/RFID scanners
                                      │    IoT machine gateway
                                      │    Tally/accounting export
                                      │    Kafka/RabbitMQ
                                      │    Bank statement import
```

---

## 3. Architecture Style and Layering

**Style:** Modular monolith, package-by-feature, strict layering inside each module. Modules communicate through **public service interfaces and domain events**, never by reaching into each other's repositories. Boundaries enforced by ArchUnit.

```
Web layer:        Controllers (REST, validation, DTO mapping)
                  Global exception handler
                  Security filter chain + method security

Application:      Services (use cases, transactions, state machines)
                  Domain event publishers/listeners
                  Saga coordinators (for multi-step cross-module workflows R9+)

Domain:           Entities + value objects
                  Business rules / policies

Infrastructure:   Spring Data JPA repositories
                  Export engines (POI / PDF / CSV)
                  Mail / notification
                  Schedulers (ShedLock)
                  Event outbox
```

**Layer rules:**
- Controller → Service → Repository → Database. No business logic in controllers.
- DTOs (Java records) cross the web boundary; entities never leave the service layer. MapStruct mapping.
- Services own transactions (`@Transactional`). Controllers never open transactions.
- Cross-module calls go through the other module's public API package only.

---

## 4. Backend Architecture

### 4.1 Module map

```
Platform
├── auth               JWT auth, refresh, password reset
├── iam                Users, roles, permissions
├── audit              Append-only audit log
└── common             Numbering, settings, approvals, exceptions, events

Master Data (MDM)
├── material           Material master + UOM conversions
├── product            Product master + attribute definitions
├── partner            Supplier + Customer
├── facility           Warehouse + machine + operation + rejection reasons
└── (R9) finance-md    Chart of accounts, cost centres, profit centres
└── (R13) employee     Employee master + skill matrix

Core ERP
├── inventory          Ledger + balances + batches + adjustment + count + transfer
├── purchase           Requisitions + purchase orders + supplier performance
├── receipt            Goods receipts
└── (R14) wms          Bins + put-away + picking + transfer orders + cycle counts

Manufacturing
├── engineering/bom    BOM + revisions
├── engineering/routing  Routing + operations
├── (R15) plm          ECOs + drawings + product lifecycle
├── production         Orders + material issue + operations + outputs + WIP + downtime
├── quality            Inspections + plans + rejections + FG release
│   └── (R16) spc      SPC charts + Cpk + CAPA + complaints
└── maintenance        Machine maintenance + PM + downtime
    └── (R18) oee      OEE calculation + spare parts

Planning
└── (R11) mrp          Demand planning + MRP engine + proposals

Commercial
├── sales              Sales orders + reservations + dispatch
└── (R12) crm          Leads + opportunities + quotations + activities

Enterprise
├── (R9) finance        GL + AP + AR + bank + periods + budgets
├── (R10) controlling   Cost centres + product costing + variance + profit centres
└── (R13) hrm           Employees + attendance + leave + payroll + training

Intelligence
├── insight/dashboard  Role dashboards + alerts + KPIs
├── insight/report     Report catalogue + export engines
├── insight/trace      Traceability
└── (R19) analytics    BI engine + AI forecasting + anomaly detection
└── (R19) integration  Webhooks + Kafka + import/export APIs
```

### 4.2 Package structure

```
backend/src/main/java/com/springmfg/ims/
├── ImsApplication.java
├── config/                  # Security, CORS, OpenAPI, Jackson, Async, Scheduling, Cache
├── common/
│   ├── api/                 # ApiError, PageResponse, filter/spec builders
│   ├── domain/              # BaseEntity, Money, Quantity, DocumentRef
│   ├── exception/           # BusinessRuleException, NotFoundException, ConflictException, GlobalExceptionHandler
│   ├── numbering/           # DocumentNumberService
│   ├── settings/            # SystemSettingService
│   ├── approval/            # ApprovalService (generic workflow engine)
│   └── idempotency/         # IdempotencyKeyFilter
├── iam/                     # users, roles, permissions, user_roles, role_permissions
├── auth/                    # login, refresh, password reset, JWT service
├── audit/                   # @Audited aspect, AuditService, AuditLog entity
├── masterdata/
│   ├── material/ product/ supplier/ customer/ warehouse/ machine/ operation/ rejectionreason/
│   └── (R9) glaccounts/ costcentre/ profitcentre/
│   └── (R13) employee/ skillmatrix/
├── engineering/
│   ├── bom/ routing/
│   └── (R15) plm/ eco/ drawing/
├── inventory/
│   ├── api/                 # InventoryPostingService (public), StockQueryService (public)
│   ├── ledger/ balance/ batch/ adjustment/ count/ transfer/
│   └── (R9) accountingbridge/  # auto-journal generation from inventory events
├── purchase/                # requisition, purchase order, supplier performance
├── receipt/                 # goods receipt
├── (R14) wms/               # bins, zones, putaway, picking, transfer orders, cycle count
├── production/              # order, materialissue, operation, output, wip, downtime
├── (R11) mrp/               # demand, run, proposals, capacity check
├── quality/                 # inspection, plan/parameters, rejection, fgbatch release
│   └── (R16) spc/ capa/ complaint/ fai/
├── sales/                   # sales order, reservation
├── dispatch/
├── (R12) crm/               # lead, opportunity, quotation, activity
├── (R9) finance/            # gl, journal, ap, ar, bank, period, budget, tax
├── (R10) controlling/       # costcentre accounting, product costing, variance, profitcentre
├── (R13) hrm/               # employee, attendance, leave, payroll, training
├── maintenance/             # machine maintenance, PM schedule
│   └── (R18) oee/ spareparts/
├── traceability/
├── report/                  # query services + exporters
├── dashboard/               # KPI queries, role dashboards, alerts
└── (R19) analytics/         # BI queries, AI forecasting seam
└── (R19) integration/       # webhook outbox, import/export APIs, Kafka adapter
(each feature: web/ service/ domain/ repository/ dto/ mapper/ event/)
```

### 4.3 Technology decisions

| Concern | Choice | Notes |
|---|---|---|
| Runtime | Java 17+ (21 LTS recommended), Spring Boot 3.3+ | |
| Persistence | Spring Data JPA / Hibernate 6, Flyway | `ddl-auto=validate` |
| Mapping | MapStruct | Compile-time, no reflection |
| Validation | Jakarta Bean Validation + class-level validators | Business rules in services |
| API docs | springdoc-openapi (Swagger UI) | Generated from controllers/DTOs |
| Security | Spring Security 6, JJWT/Nimbus, BCrypt(12) | |
| Cache | Caffeine (permissions, settings, master data) | Redis when scaling out |
| Export | Apache POI (SXSSF streaming), OpenCSV, OpenPDF | |
| Scheduling | `@Scheduled` + ShedLock | Alerts, MRP runs, reconciliation |
| Finance | Double-entry ledger in PostgreSQL | No external accounting library |
| Observability | Actuator, Micrometer, logback JSON + correlation id | |
| Build | Maven, Java toolchain | |

### 4.4 Entity base and conventions [Implemented R1–R8]
- Surrogate `BIGINT IDENTITY` keys (`id`) plus human-readable business keys (`code`, `*_number`).
- `BaseEntity`: `created_at`, `created_by`, `updated_at`, `updated_by`, `version` (optimistic lock).
- Enums stored as `VARCHAR` with `CHECK` constraints (not ordinals).
- Money: `NUMERIC(18,4)`; quantities: `NUMERIC(18,3)`; dimensions: `NUMERIC(10,3)`; never floating point.
- Master data uses soft deactivation (`active`/`status`); transactional documents are never hard-deleted.
- `company_id` on every aggregate root (default 1). `plant_id` on all transacting documents (added R9).

---

## 5. Core Design: Inventory Engine [Implemented R1–R8]

### 5.1 Ledger + balance model

```
Business document → InventoryPostingService → inventory_transactions (append-only ledger)
                                             → inventory_balances (projection)
                                             → material_batches / fg_batches (remaining qty)
                                             → StockPosted event (async, after commit)
```

**Rules of the posting service** (single public entry point, one DB transaction):
1. Validate the command (item, batch, warehouse/location, quantity > 0, UOM).
2. Lock affected `inventory_balances` rows `SELECT … FOR UPDATE`, ordered by id (deadlock prevention).
3. Enforce stock rules (§5.3).
4. Insert one ledger row per movement leg (signed quantity; transfers = OUT + IN sharing `transfer_group_id`).
5. Update balance projection and batch remaining quantity in the same transaction.
6. Emit `StockPosted` event (after commit) for alerts, dashboards, and auto-journal generation (R9).

No other class may write to `inventory_balances` or `inventory_transactions`. Enforced by package-private repositories and an ArchUnit rule.

### 5.2 Ledger transaction types [Implemented R1–R8]

| Type | Direction | Source document |
|---|---|---|
| OPENING_BALANCE | IN | Opening stock load |
| PURCHASE_RECEIPT | IN (to QUARANTINE) | Goods Receipt |
| MATERIAL_ISSUE | OUT RM → IN WIP | Material issue to production |
| MATERIAL_RETURN | OUT WIP → IN RM | Return from production |
| MATERIAL_CONSUMPTION | OUT WIP | Consumption recorded against order |
| PRODUCTION_RECEIPT | IN to FG AVAILABLE | Final inspection approval |
| PRODUCTION_REJECTION | IN to SCRAP | Inspection/operation rejection |
| SALES_DISPATCH | OUT FG | Dispatch |
| STOCK_ADJUSTMENT | ± | Approved adjustment / count variance |
| STOCK_TRANSFER | OUT + IN | Transfer between locations |
| SCRAP | OUT | Scrap write-off |
| CUSTOMER_RETURN | IN | Customer return |

### 5.3 Stock rules enforced centrally [Implemented R1–R8]

| Rule | Enforcement |
|---|---|
| No negative stock | Balance check under row lock; DB `CHECK` as backstop |
| Cannot issue insufficient stock | Posting service pre-check under row lock |
| Quarantine/Hold/Rejected not issuable | Issue allocator selects only AVAILABLE |
| FG not available until approval | FG lands in FG-QP (QUALITY_PENDING); moves to AVAILABLE only via inspection approval |
| Consumption ≤ issued | `production_order_materials.consumed + returned + scrap <= issued` |
| Dispatch ≤ available FG | Dispatch posts under lock; available = on_hand − reserved |
| Completed order locked | Services reject mutations when `status IN (COMPLETED, CLOSED)` |
| Ledger immutable | DB trigger blocks UPDATE/DELETE on ledger |

### 5.4 Balance dimensions [Implemented R1–R8]

`inventory_balances` keyed by **(company, plant, warehouse, location, item_type, item_id, batch_id, stock_status)**. Stock status ∈ AVAILABLE | QUARANTINE | HOLD | REJECTED | QUALITY_PENDING.

### 5.5 Batch allocation [Implemented R1–R8]
- Default **FEFO/FIFO** by `received_date` / `expiry_date` among AVAILABLE batches.
- `BatchAllocator.allocate(item, qty, warehouse, explicitBatchId)` — pure function, result persisted as `material_issue_lines`.
- FG reservation (`sales_reservations`) increments `qty_reserved`, released at dispatch or cancellation.

### 5.6 Costing and valuation [Implemented R1–R8]
- Each ledger row stores `unit_cost` and `total_cost`.
- Default: **moving weighted average** per item+warehouse, updated on every cost-bearing receipt.
- Sits behind `CostingStrategy` interface — standard costing slots in here (R10).

---

## 6. Workflows and State Machines [Implemented R1–R8]

Each document has a `status` column and a validated transition table. Illegal transitions throw `BusinessRuleException` (HTTP 422). Every transition writes an audit record.

Key state machines: Purchase Order, Goods Receipt / Incoming Inspection, BOM, Production Order, Operation (operator level), Finished Goods Quality, Sales Order, Stock Adjustment, Alert lifecycle.

**New state machines (R9+):**
- **Journal Entry:** DRAFT → SUBMITTED → APPROVED → POSTED → REVERSED
- **Financial Period:** OPEN → CLOSING → CLOSED → LOCKED
- **ECO (R15):** DRAFT → REVIEW → APPROVED → IMPLEMENTED → CLOSED
- **MRP Run (R11):** INITIATED → RUNNING → COMPLETED → CONVERTED / DISCARDED
- **Customer Complaint (R16):** OPEN → INVESTIGATION → CORRECTIVE_ACTION → VERIFIED → CLOSED

---

## 7. Key Sequence Flows

### 7.1 Purchase → Receipt → Inspection → Stock [Implemented R1–R8]

Purchase Manager creates + submits PO → approved → Store receives against PO → PURCHASE_RECEIPT ledger entry (stock QUARANTINE) → incoming inspection created → Quality inspects → accepted: QUARANTINE → AVAILABLE status transfer; rejected: QUARANTINE → REJECTED.

### 7.2 Production → Finished Goods [Implemented R1–R8]

Production Manager creates/approves/releases order (BOM snapshot) → Supervisor issues material (MATERIAL_ISSUE, batch allocation) → Operator starts/completes each operation (good + reject + scrap quantities) → MATERIAL_CONSUMPTION posted → last operation complete → FG batch created (QUALITY_PENDING) → Quality final inspection → approved: PRODUCTION_RECEIPT (FG AVAILABLE); rejected: PRODUCTION_REJECTION (SCRAP).

### 7.3 Sales → Dispatch [Implemented R1–R8]

Sales creates order → reserves FG (qty_reserved++) → Dispatch creates dispatch (select FG batch, transport) → SALES_DISPATCH posted (fails if qty > available) → SO dispatched quantities updated.

### 7.4 Goods Receipt → Finance (R9)

```
StockPosted event (PURCHASE_RECEIPT)
  → AccountingBridgeListener
  → Journal entry:
      Dr Inventory (Raw Material) account
      Cr GR/IR Clearing account
    (unit_cost × quantity)
```

### 7.5 Production Completion → Finance (R9)

```
StockPosted event (PRODUCTION_RECEIPT)
  → Journal entry:
      Dr FG Inventory account
      Cr WIP account
    (standard cost × quantity, or actual cost from production order)
```

### 7.6 Sales Dispatch → Finance (R9)

```
StockPosted event (SALES_DISPATCH)
  → Journal entry 1 (COGS):
      Dr Cost of Goods Sold
      Cr FG Inventory
    (at costed value)
  → Journal entry 2 (Revenue):
      Dr Accounts Receivable
      Cr Revenue account
    (at selling price from sales order)
```

---

## 8. Database Architecture

### 8.1 Table groups

| Group | Tables | Status |
|---|---|---|
| Identity & access | `users`, `roles`, `permissions`, `user_roles`, `role_permissions`, `refresh_tokens`, `password_reset_tokens`, `user_warehouse_access` | R1–R8 |
| Platform | `system_settings`, `number_sequences`, `approval_requests`, `approval_steps`, `idempotency_keys`, `audit_logs`, `alerts`, `notification_outbox` | R1–R8 |
| Enterprise structure | `companies`, `plants`, `departments` | R9 |
| Master data | `materials`, `products`, `spring_specifications`, `product_attribute_definitions`, `suppliers`, `customers`, `customer_product_specs`, `warehouses`, `warehouse_locations`, `machines`, `operations`, `rejection_reasons`, `uoms` | R1–R8 |
| Finance master | `gl_accounts`, `gl_account_groups`, `cost_centres`, `profit_centres`, `tax_codes`, `fiscal_periods`, `budgets`, `exchange_rates` | R9 |
| Inventory accounting | `inventory_account_assignments`, `journal_headers`, `journal_lines`, `ap_invoices`, `ar_invoices`, `bank_statements`, `bank_statement_lines` | R9 |
| Engineering | `bom`, `bom_items`, `product_routing`, `routing_inspection_points` | R1–R8 |
| PLM | `ecos`, `eco_items`, `drawings`, `product_lifecycle_history` | R15 |
| Procurement | `purchase_requisitions`, `purchase_orders`, `purchase_order_items`, `goods_receipts`, `goods_receipt_items` | R1–R8 |
| WMS | `warehouse_zones`, `warehouse_bins`, `bin_assignments`, `transfer_orders`, `transfer_order_lines`, `cycle_counts`, `cycle_count_lines` | R14 |
| Inventory | `material_batches`, `fg_batches`, `fg_batch_material_sources`, `inventory_balances`, `inventory_transactions`, `stock_adjustments`, `stock_counts`, `stock_count_lines` | R1–R8 |
| Production | `production_orders`, `production_order_materials`, `material_issues`, `material_issue_lines`, `material_returns`, `production_operations`, `production_outputs`, `production_downtime`, `material_requests`, `rejections` | R1–R8 |
| Quality | `quality_inspections`, `quality_inspection_items`, `inspection_plans`, `inspection_plan_parameters` | R1–R8 |
| Quality R16 | `spc_data_points`, `control_charts`, `capa_records`, `customer_complaints`, `fai_reports` | R16 |
| MRP | `mrp_runs`, `demand_forecasts`, `mrp_proposals`, `capacity_plans` | R11 |
| Sales | `sales_orders`, `sales_order_items`, `sales_reservations`, `dispatches`, `dispatch_items` | R1–R8 |
| CRM | `crm_leads`, `crm_opportunities`, `crm_quotations`, `crm_quotation_items`, `crm_activities`, `crm_contacts` | R12 |
| HR | `employees`, `skill_matrix`, `attendance`, `leave_applications`, `leave_balances`, `payroll_runs`, `payroll_slips`, `training_records` | R13 |
| Maintenance | `maintenance_requests`, `maintenance_activities`, `spare_part_usage`, `pm_schedules` | R1–R8 |
| Reports | `report_definitions`, `report_schedules`, `report_exports` | R1–R8 |

### 8.2 Key DDL patterns [Implemented R1–R8]

```sql
-- One ACTIVE BOM per product
CREATE UNIQUE INDEX ux_bom_one_active ON bom (product_id) WHERE status = 'ACTIVE';

-- Ledger item is exactly one of material / product
ALTER TABLE inventory_transactions ADD CONSTRAINT ck_item_exclusive CHECK (
  (item_type='MATERIAL' AND material_id IS NOT NULL AND product_id IS NULL) OR
  (item_type='PRODUCT'  AND product_id  IS NOT NULL AND material_id IS NULL));

-- Balance key uniqueness
CREATE UNIQUE INDEX ux_balance_key ON inventory_balances
  (warehouse_id, location_id, item_type, COALESCE(material_id,0), COALESCE(product_id,0),
   COALESCE(batch_id,0), stock_status);

-- Double-entry balance check on journals (R9)
ALTER TABLE journal_headers ADD CONSTRAINT ck_journal_balanced CHECK (
  (SELECT COALESCE(SUM(debit_amount),0) - COALESCE(SUM(credit_amount),0) FROM journal_lines WHERE header_id = id) = 0
);

-- Ledger and audit immutability
CREATE FUNCTION forbid_mutation() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION '% is append-only', TG_TABLE_NAME; END; $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_ledger_immutable BEFORE UPDATE OR DELETE ON inventory_transactions FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_audit_immutable  BEFORE UPDATE OR DELETE ON audit_logs             FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_journal_immutable BEFORE UPDATE OR DELETE ON journal_lines          FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
```

### 8.3 Flyway migration versions

| Range | Phase | Modules |
|---|---|---|
| V1–V3 | R1 Foundation | IAM, master data, inventory schema |
| V4–V5 | R2 Inventory | Inventory transactions, balances |
| V6 | R3 Procurement | Purchase, goods receipt |
| V7–V9 | R4–R5 Manufacturing | BOM, production, quality |
| V10–V11 | R5–R6 Quality/Sales | Quality R16 additions, sales |
| V12–V13 | R7–R8 Insight | Alerts, materialized views, performance indexes |
| V14–V16 | R9 Finance | Enterprise structure, GL, AP/AR, accounts |
| V17–V18 | R10 Controlling | Cost centres, product costing |
| V19–V20 | R11 MRP | MRP tables, demand, proposals |
| V21 | R12 CRM | CRM tables |
| V22 | R13 HRM | Employee, payroll, attendance |
| V23–V24 | R14 WMS | Bins, zones, transfer orders |
| V25 | R15 PLM | ECO, drawings, lifecycle |
| V26 | R16 Advanced Quality | SPC, CAPA, complaints |
| V27 | R17 Documents | Document management, notifications |
| V28 | R18 Advanced MFG | OEE, spare parts |
| V29 | R19 AI/Integration | Analytics, integration tables |

---

## 9. Traceability Design [Implemented R1–R8]

Traceability is a by-product of the linkage columns required by the workflows.

```
Supplier → PO → GR → RM Batch (heat no., cert no.)
                         → Material Issue Line
                              → Production Order
                                   → Production Operations / Outputs
                                        → Quality Inspection
                                             → FG Batch
                                                  → Dispatch Item → Sales Order → Customer
```

**New traceability chain (R9):**
- FG Batch → Dispatch Item → AR Invoice → Customer Payment
- RM Batch → GR → AP Invoice → Supplier Payment

---

## 10. Security Architecture [Implemented R1–R8]

### 10.1 Authentication
- JWT (access 15 min + rotating HttpOnly refresh cookie 7 days)
- BCrypt(12) passwords; account lockout after failures; login audited
- Refresh tokens stored hashed; reuse detection revokes family
- Forgot-password: single-use hashed token via email; generic response (no user enumeration)
- Token carries `sub`, `roles`, `permission_version`; permissions resolved from cache

### 10.2 Authorization
Three enforcement layers, all server-side:
1. **Endpoint level:** `@PreAuthorize("hasAuthority('…')")` on every controller/service method. Missing annotation = ArchUnit test failure.
2. **State/transition level:** per-transition permission checks (e.g. `PURCHASE_APPROVE` for SUBMITTED→APPROVED).
3. **Data-scope level** (`AccessScopeService`): operator/supervisor see only assigned production orders; store operator scoped to assigned warehouses; segregation of duties (requester ≠ approver).

**The Administrator role holds every permission in the catalogue** (V37, kept complete by `AdminHoldsEveryPermissionIT`: a release that seeds new permission codes grants them to `ADMIN` in the same migration). Holding a permission does not bypass the code-level segregation-of-duties rules: an administrator still cannot approve their own purchase order or approval request (`AdminSegregationOfDutiesIT`).

### 10.3 Financial access controls (R9)
- Journal entry preparer ≠ approver (hard-coded `AccessScopeService` check)
- Period close requires `PERIOD_CLOSE` permission (Finance Manager)
- Posting to a closed period requires Finance Manager override (creates override audit entry)
- Sensitive reports (`FINANCIAL_REPORT`) audit every export

### 10.4 Other controls
- CORS: explicit allow-list per profile
- Input: Bean Validation on all DTOs, parameterised queries (JPA), request size limits
- Headers: `SecurityHeadersFilter` — HSTS 2yr, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, Permissions-Policy, CSP
- Rate limiting on `/api/auth/**`
- OWASP dependency-check and `npm audit` in CI
- Secrets from environment; never committed

---

## 11. Audit Architecture [Implemented R1–R8]

- Declarative: `@Audited` aspect captures user, role(s), action, entity, entity id, old value, new value, reason, timestamp, IP, correlation id.
- Same transaction as the business change: audit insert failure rolls back the business change.
- `audit_logs` is append-only (DB trigger); JSONB for old/new values; partitioned monthly.
- **Finance additions (R9):** every journal posting, period close, AP payment, AR receipt audited with full before/after snapshot.
- Access: `AUDIT_VIEW` only; filterable; exportable.

---

## 12. API Architecture

### 12.1 Conventions [Implemented R1–R8]
- Base path `/api`, JSON, plural nouns, standard verbs, `201 + Location` on create, `204` on deactivate, `409` on version conflict, `422` on business rule violation.
- Errors: RFC 7807 `ProblemDetail` — `type, title, status, detail, code, fieldErrors[], traceId`.
- Paging/sorting/filtering: `?page=0&size=20&sort=createdAt,desc&q=text&status=…&from=…&to=…`
- Actions as sub-resources: `POST /api/purchase-orders/{id}/approve`, `/release`, `/close`. No arbitrary PATCH status.
- OpenAPI at `/swagger-ui.html` (dev/staging; restricted in prod).

### 12.2 New endpoint namespaces (R9+)

| Namespace | Module | Key endpoints |
|---|---|---|
| `/api/gl` | FIN | `GET /accounts`, `POST /journals`, `GET /journals/{id}/approve` |
| `/api/ap` | FIN | `POST /invoices`, `POST /{id}/pay`, `GET /ageing` |
| `/api/ar` | FIN | `GET /invoices`, `POST /{id}/receive-payment`, `GET /ageing` |
| `/api/periods` | FIN | `GET`, `POST /{id}/close`, `POST /{id}/open` |
| `/api/cost-centres` | CTR | CRUD, `GET /{id}/costs` |
| `/api/product-costing` | CTR | `POST /estimate/{productId}`, `GET /variances` |
| `/api/mrp/runs` | MRP | `POST` (initiate), `GET /{id}/proposals`, `POST /{id}/convert` |
| `/api/crm/leads` | CRM | CRUD, `POST /{id}/convert-to-opportunity` |
| `/api/crm/quotations` | CRM | `POST`, `POST /{id}/convert-to-sales-order` |
| `/api/employees` | HRM | CRUD, `GET /{id}/attendance`, `POST /payroll-runs` |
| `/api/wms/bins` | WMS | CRUD, `POST /transfer-orders`, `GET /picking-tasks` |
| `/api/ecos` | PLM | CRUD, `POST /{id}/approve`, `POST /{id}/implement` |

---

## 13. Frontend Architecture

### 13.1 Technology [Implemented R1–R8]
Angular 18+ standalone components, signals for local state, RxJS for async, Angular Material + CDK, Reactive Forms, Chart.js, ESLint + Prettier, Jest/Karma + Testing Library, Playwright E2E.

### 13.2 Feature modules

```
features/
├── login/             [R1]
├── dashboard/         [R7]  role-aware KPI cards, charts, alerts
├── masterdata/        [R1]  materials, products (dynamic attribute form), suppliers, customers, operations, warehouses, machines
├── procurement/       [R3]  POs, goods receipts, incoming inspection
├── inventory/         [R2]  stock, ledger, adjustment, transfer, batches, counts
├── production/        [R4]  orders, issue, operations, WIP, output, my-work (tablet)
├── quality/           [R5]  inspections, rejections, quality dashboard
│   └── spc/          [R16] control charts, CAPA, complaints
├── sales/             [R6]  orders, finished goods, dispatch
├── crm/              [R12] leads, opportunities, quotations, activities
├── finance/          [R9]  GL accounts, journal entries, AP, AR, bank, periods, budgets
├── controlling/      [R10] cost centres, product costing, variances, profit centres
├── planning/         [R11] MRP run, proposals, capacity
├── wms/              [R14] bin management, put-away, picking, cycle counts
├── plm/              [R15] ECOs, drawings, product lifecycle
├── hrm/              [R13] employees, attendance, leave, payroll, training
├── traceability/      [R7]
├── reports/           [R7]
├── maintenance/       [R7]
└── admin/             [R1]  users, roles, audit logs, settings
```

### 13.3 Key mechanisms [Implemented R1–R8]
- **Permissions:** `GET /api/auth/me` → permission signal → `permissionGuard` + `*hasPermission` directive (UX only; backend authoritative).
- **Dynamic product form:** loads `product_attribute_definitions` for chosen product type → builds Reactive Form group at runtime.
- **Operator experience:** `my-work` page with large touch targets, numeric keypad, Start/Pause/Complete, idempotency key retries.
- **Data tables:** server-side pagination, sort, filter, URL-synced.
- **Status badges:** consistent palette — AVAILABLE=green, QUARANTINE=orange, REJECTED=red, IN_PROGRESS=blue, COMPLETED=teal.
- **Finance UI (R9):** journal entry form with line-by-line Dr/Cr, running totals, balance indicator; period picker with lock status; AP/AR ageing grids.

---

## 14. Reporting, Dashboards and Alerts [Implemented R1–R8]

- Each report is a `ReportDefinition` (code, parameters, query, columns, required permission). Streaming export via POI SXSSF (Excel), OpenCSV, OpenPDF.
- KPI queries: SQL aggregates, Caffeine-cached 30–60 s; heavy trends from materialized views.
- Alert engine: `AlertSweepJob` every 15 min; LOW_STOCK, PENDING_INSPECTION, OVERDUE_PO, PRODUCTION_DELAY, OVERDUE_PM.
- **Finance reports (R9):** Trial Balance, P&L, Balance Sheet, Cash Flow added to `ReportDefinition` catalogue. AP/AR ageing as KPI cards.

---

## 15. Finance Architecture (R9)

### 15.1 Double-entry ledger design

```
journal_headers
  id, journal_number, journal_type, posting_date, period_id, status, description, created_by
  
journal_lines
  id, header_id FK, line_number, gl_account_id FK, cost_centre_id FK,
  debit_amount NUMERIC(18,4), credit_amount NUMERIC(18,4), description
  CHECK: exactly one of debit/credit > 0

CHECK on header: SUM(debit) = SUM(credit)
TRIGGER: journal_lines is append-only once header.status = POSTED
```

### 15.2 Automatic journal generation

The `AccountingBridgeService` listens to `StockPosted` events and generates journal entries automatically. Account codes come from configurable `inventory_account_assignments` (seed data per industry vertical):

```
inventory_account_assignments
  id, company_id, transaction_type, item_type, gl_account_debit, gl_account_credit
```

### 15.3 Period management

Financial periods (fiscal months) gate all journal postings:
- OPEN: normal postings allowed
- CLOSING: no new business transactions; finance-only adjustments
- CLOSED: no postings; Finance Manager override required and audited
- LOCKED: no postings at all

---

## 16. Controlling Architecture (R10)

### 16.1 Product costing flow

```
Product Costing Estimate:
  Material cost = Σ(BOM material qty × standard material cost)
  Labour cost   = Σ(routing operation time × labour rate per cost centre)
  Machine cost  = Σ(routing machine time × machine rate)
  Overhead      = absorbed via overhead rates (% of labour or machine cost)
  Standard cost = Material + Labour + Machine + Overhead
```

### 16.2 Production order variance

```
Actual vs Standard:
  Price variance   = (actual material price - standard price) × actual qty
  Quantity variance = (actual material qty - standard qty) × standard price
  Efficiency variance = (actual hours - standard hours) × labour rate
  Total variance   = Actual production order cost - Standard cost × produced qty
```

---

## 17. MRP Architecture (R11)

### 17.1 MRP calculation flow

```
1. Demand collection:
   - Confirmed sales orders (make-to-order items)
   - Forecast demand (make-to-stock items)
   - Safety stock requirements

2. Gross requirements = demand within planning horizon

3. Net requirements:
   Net = Gross − on-hand (AVAILABLE) − open PO qty − open production order qty

4. Planned orders:
   - Net requirement > 0 → purchase proposal (raw materials)
                          → production proposal (semi-finished/finished)
   - Apply lot-sizing rules (fixed lot, min/max, EOQ)
   - Offset by lead time (supplier lead time or production routing time)

5. Capacity check:
   - Production proposals checked against rough-cut capacity (machine/labour hours/period)
   - Overloads flagged; proposals not automatically rescheduled

6. Output: MRP proposals list (filterable, reviewable, convertable to PRs / production orders)
```

---

## 18. CRM Architecture (R12)

CRM uses the existing Customer master from MDM as the foundation. New tables layer on top:

```
customers (MDM) ←── crm_contacts (M:1 customer)
customers        ←── crm_leads (M:1 customer, nullable until qualified)
crm_leads        ←── crm_opportunities (M:1 lead)
crm_opportunities ←── crm_quotations (M:1 opportunity) ──► sales_orders (MDM, on conversion)
crm_contacts     ←── crm_activities (call/email/meeting log)
```

CRM quotations, when accepted, are converted to `sales_orders` in the SAL module — no data re-entry.

---

## 19. HRM Architecture (R13)

```
users (IAM) ←── employees (optional link; not all employees have system access)
employees ←── attendance (daily record)
employees ←── leave_applications
employees ←── skill_matrix (many skills, machine authorisations)
employees ←── payroll_slips (per payroll run)
payroll_runs (monthly) → payroll_slips (per employee)
```

Production operators in IAM are linked to employees to enable automatic attendance capture from operation start/complete events.

---

## 20. WMS Architecture (R14)

WMS adds a bin-level dimension below the existing warehouse/location structure:

```
warehouse → zone → aisle → rack → bin
                                   └── inventory_balances (bin is a specific location)
```

Transfer orders (TOs) govern all internal movements in a WMS-managed warehouse. The existing `STOCK_TRANSFER` ledger type is used; WMS adds TO creation, task assignment, and scan-based confirmation.

---

## 21. Cross-Cutting Concerns

| Concern | Design |
|---|---|
| **Transactions** | One `@Transactional` use case per business operation; posting and audit in the same transaction; events published `AFTER_COMMIT` |
| **Document numbering** | `DocumentNumberService` using `number_sequences` row lock; per-year reset, gap-free |
| **Settings** | `system_settings` key/value with typed accessors and Caffeine cache |
| **Approvals** | Generic `ApprovalService` reused by PO, BOM, production order, stock adjustment, journal entry, ECO |
| **Events** | Spring `ApplicationEventPublisher` + `@TransactionalEventListener`; outbox seam for broker |
| **Error handling** | `GlobalExceptionHandler` → ProblemDetail with stable `code` values |
| **Time** | `TIMESTAMPTZ` UTC; display in user's zone (default Asia/Kolkata); injectable `Clock` for tests |
| **UOM** | `uoms` table with conversion factors; quantities in item's base UOM |
| **Logging** | JSON logs, correlation id, user id; no PII or secrets; audit separate |
| **Performance** | Mandatory pagination; DTO projections; `@EntityGraph` for list queries; Caffeine; HikariCP |
| **i18n** | Angular i18n from the start; Kannada/Hindi in a later release |

---

## 22. Deployment Architecture [Implemented R1–R8]

```
Browser → HTTPS 443 → nginx (serves Angular build, reverse proxy /api)
                         → Spring Boot :8080 → PostgreSQL 16 (pgdata volume)
                                              → SMTP (mailhog in dev)
```

| Item | Detail |
|---|---|
| Containers | `frontend` (Node build → nginx), `backend` (Maven build → JRE slim, non-root), `postgres`, optional `mailhog`, `pgadmin` |
| Compose | `docker-compose.yml` (base), `docker-compose.dev.yml` (dev), `docker-compose.prod.yml` (prod resource limits) |
| Profiles | `dev` (seed + Swagger), `test` (Testcontainers), `prod` (secure) |
| Config | 12-factor env vars: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS`, `SMTP_*` |
| Scaling | Stateless backend (JWT); ShedLock for distributed scheduling; Postgres read replica for reporting |
| CI/CD | Build → unit tests → Testcontainers ITs → ArchUnit → OWASP check → image build → compose smoke test |

---

## 23. Testing Architecture [Implemented R1–R8]

| Level | Scope | Tooling |
|---|---|---|
| Unit | Domain rules, state machines, allocators, costing, MRP calculations | JUnit 5, Mockito, AssertJ |
| Repository | Queries, constraints, partial indexes, triggers | `@DataJpaTest` + Testcontainers PostgreSQL |
| Service | Posting rules, approval flows, journal balancing | Spring Boot slice + Testcontainers |
| Controller/API | Validation, status codes, ProblemDetail, pagination | `@WebMvcTest`, MockMvc |
| Security | Per-role 403 matrix for every endpoint × role | `@SpringBootTest`, parameterized tests |
| Concurrency | Parallel stock postings, idempotency replay | Multi-threaded integration test |
| Architecture | Layering, module boundaries, unprotected endpoints, ledger writer | ArchUnit |
| Integration workflows | Purchase → GR → inspection → stock; production → FG; sales → dispatch; finance auto-journals | Testcontainers, full context |
| Frontend | Component, service, form-validation, permission directive | Jest/Karma + Testing Library |
| E2E | Demo workflow through the UI | Playwright against Compose |
| Load | Ledger posting, dashboard, financial report queries | k6 (P95 thresholds) |

---

## 24. Extensibility Roadmap

| Capability | Seam in place |
|---|---|
| Barcode / QR / RFID | Batch numbers and location codes are first-class keys |
| IoT machine data / OEE | `machines`, `production_operations`, `production_downtime`; event outbox |
| AI demand forecasting | Complete ledger history; `CostingStrategy` interface; read-only reporting schema |
| Additional industry verticals | `product_attribute_definitions`, `operations`, `inspection_plan_parameters` all data-driven; `company_id` on aggregate roots |
| Multi-company | `company_id` on aggregate roots; tenant resolver stub; unique business keys scoped per company |
| External ERP / accounting | Domain events + notification outbox; `AccountingBridgeService` interface |
| Message broker | Spring events `@TransactionalEventListener` → outbox → broker adapter (R19) |
| Advanced BI | Read-only reporting views; materialized views already published |
| Mobile PWA | Stateless JWT API; operator endpoints designed for small payloads |

---

## 25. Implementation Phasing

See PLAN.md for detailed task IDs and exit gates. Summary:

| Release | Backend modules | Frontend | Gate |
|---|---|---|---|
| R1–R8 | auth, iam, masterdata, inventory, purchase, receipt, BOM, production, quality, sales, dispatch, insight, maintenance, config | All features through admin | M0–M8 **complete** |
| R9 | finance (GL, AP, AR, bank, periods, budgets), ENT (company, plant) | Finance feature module | M9 |
| R10 | controlling (cost centres, product costing, variance) | Controlling screens | M10 |
| R11 | mrp (engine, demand, proposals) | Planning screens | M11 |
| R12 | crm (leads, opportunities, quotations) | CRM feature module | M12 |
| R13 | hrm (employee, attendance, leave, payroll) | HR feature module | M13 |
| R14 | wms (bins, transfer orders, cycle counts) | WMS screens | M14 |
| R15 | plm (ECOs, drawings, lifecycle) | PLM screens | M15 |
| R16 | quality/spc, capa, complaints, FAI | SPC charts, CAPA screens | M16 |
| R17 | document management, notification engine | Notifications, doc viewer | M17 |
| R18 | OEE, spare parts, advanced scheduling | OEE dashboard | M18 |
| R19 | analytics, AI forecasting, integration (Kafka, webhooks) | BI builder | M19 |
| R20 | Industry vertical seeds (Automotive, Furniture, Electronics) | Vertical-specific forms | M20 |

---

## 26. Architecture Decision Records

| ADR | Decision | Rationale |
|---|---|---|
| 01 | Modular monolith | One deployable; ACID across stock + documents; easy local run; option to split later |
| 02 | Ledger + transactional balance projection | Requirement mandates ledger-derived stock; projection gives O(1) availability checks |
| 03 | Pessimistic locks for stock, optimistic for documents | Stock contention is correctness-critical |
| 04 | Add `MATERIAL_CONSUMPTION` ledger type | Without it consumed material stays in WIP stock forever |
| 05 | Stock status as a balance dimension; status changes are transfers | Quarantine/hold/rejected fully auditable; partial acceptance supported |
| 06 | Relational columns + JSONB for product specs, with metadata catalogue | Extensible without schema change; validated; common fields indexed |
| 07 | Permissions as data; authorities checked server-side | "Managed centrally by backend"; roles editable without code change |
| 08 | 13-role RBAC model as baseline | Attachment 2 supersedes Attachment 1 |
| 09 | Short-lived JWT + rotating HttpOnly refresh cookie | Limits XSS exposure; revocation timely |
| 10 | Snapshot BOM + routing on production order release | Revisions never change running orders |
| 11 | Corrections by reversal; DB triggers enforce immutability | History never silently deleted |
| 12 | Flyway + Testcontainers PostgreSQL | Test same engine and constraints as production |
| 13 | Order-level traceability by default, batch-level optional | Meets trace questions; batch-level available where needed |
| 14 | Spring events now, outbox later | Simplicity today with defined path to IoT/ERP integration |
| 15 | Multi-industry through configuration data, not code branching | New vertical = seed migration only |
| 16 | Double-entry GL in PostgreSQL; no external accounting library | Keeps the stack simple; DB trigger enforces Dr=Cr balance |
| 17 | Auto-journal from `StockPosted` events via `AccountingBridgeService` | Finance closes the loop on inventory without manual re-entry |
| 18 | MRP as a batch calculation run, not real-time | Deterministic, auditable, restartable; real-time MRP is R19+ |
| 19 | CRM layers on top of existing Customer master, does not replace it | Avoids duplication; sales orders flow from CRM quotations directly |

---

*End of ARCHITECTURE.md v2.0*
