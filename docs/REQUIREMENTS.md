# REQUIREMENTS.md — Configurable Manufacturing ERP Platform

**Version:** 2.0  **Date:** 2026-10-02  **Status:** Living specification
**Sources:** v1.1 (Spring Manufacturing IMS), SAP-class platform expansion (2026-10-02)

---

## 0. Document Map

| Document | Purpose |
|---|---|
| **REQUIREMENTS.md** | *What* to build — module catalogue, functional requirements with IDs, RBAC |
| ARCHITECTURE.md | *How* it is structured — principles, layering, key components |
| DESIGN.md | *Exactly how* each part works — schemas, algorithms, API contracts |
| PLAN.md | *When* it is built — releases R1–R20, task IDs, exit gates |

---

## 1. Platform Vision

The **Configurable Manufacturing ERP Platform** is a production-grade, modular ERP system targeting small-to-mid-sized manufacturers across multiple industries. It covers the complete business cycle from raw-material procurement through finished-goods dispatch, and extends to finance, cost accounting, HR, CRM, advanced analytics, and AI services.

**Design imperatives:**
- **Configurable, not customised.** Adding a new industry vertical (Automotive, Furniture, Electronics) requires only a seed data migration — zero code changes.
- **Modular adoption.** Customers activate only the modules they need; inactive modules do not appear in the UI and their tables carry no data.
- **Single codebase.** One Spring Boot application, one Angular SPA, one PostgreSQL schema. No separate deployments per vertical.
- **Ledger-first correctness.** Every business number flows from an append-only event ledger. No black-box totals.
- **Enterprise security.** Every endpoint is authorised; every sensitive action is audited; every secret is injected from the environment.

---

## 2. Module Catalogue

### 2.1 Platform Modules (always active)

| Code | Module | Status |
|---|---|---|
| ENT | Enterprise Structure | R9 |
| IAM | Identity and Access Management | **[Implemented R1–R8]** |
| CFG | System Configuration | **[Implemented R1–R8]** |
| MDM | Master Data Management | **[Implemented R1–R8]** |
| WKF | Workflow Engine | **[Implemented R1–R8]** |
| NTF | Notification Engine | R17 |
| DOC | Document Management | R17 |
| AUD | Audit Trail | **[Implemented R1–R8]** |
| SEC | Security and Compliance | **[Implemented R1–R8]** |

### 2.2 Operations Modules

| Code | Module | Status |
|---|---|---|
| INV | Inventory Management | **[Implemented R1–R8]** |
| WMS | Warehouse Management System | R14 |
| PUR | Procurement | **[Implemented R1–R8]** |
| MFG | Manufacturing | **[Implemented R1–R8]** |
| PLM | Product Lifecycle Management | R15 |
| MRP | Materials Requirements Planning | R11 |
| QMS | Quality Management System | **[Implemented R1–R8]** |
| MNT | Maintenance Management | **[Partial R1–R8]**, complete R18 |

### 2.3 Commercial Modules

| Code | Module | Status |
|---|---|---|
| SAL | Sales Management | **[Implemented R1–R8]** |
| CRM | Customer Relationship Management | R12 |

### 2.4 Financial Modules

| Code | Module | Status |
|---|---|---|
| FIN | Financial Accounting | R9 |
| CTR | Controlling / Cost Accounting | R10 |
| HRM | Human Resource Management | R13 |

### 2.5 Intelligence Modules

| Code | Module | Status |
|---|---|---|
| RPT | Reporting and Exports | **[Implemented R1–R8]** |
| ANA | Advanced Analytics and BI | R19 |
| INT | Integration Platform | R19 |

---

## 3. Industry Configuration Architecture

The platform uses a **3-layer architecture**:

```
┌──────────────────────────────────────────────────────────────────┐
│                  INDUSTRY CONFIGURATION LAYER                    │
│  Springs (v1)  │  Automotive  │  Furniture  │  Electronics  │ … │
│  (data-only seed migrations — no code changes between verticals) │
└──────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────┐
│                    UNIVERSAL PLATFORM LAYERS                     │
│  Core ERP: Inventory · Purchase · Sales · Warehouses · Master   │
│  Manufacturing: BOM · Production · WIP · Quality · MRP          │
│  Common Services: IAM · Audit · Workflow · Reports · Alerts     │
│  Enterprise: Finance · Controlling · HR · CRM · PLM · WMS      │
└──────────────────────────────────────────────────────────────────┘
```

**Industry Configuration artefacts (data, not code):**

| Artefact | Mechanism | Spring v1 example |
|---|---|---|
| Product attribute model | `product_attribute_definitions` JSONB metadata | `wire_diameter`, `spring_rate`, `end_type` |
| Material types | `materials.material_type` seed rows | SPRING_STEEL, MUSIC_WIRE |
| Standard operations | `operations` seed rows | Wire Drawing, Coiling, Grinding |
| Inspection parameters | `inspection_plan_parameters` seed rows | Diameter ± 0.03 mm, spring rate limits |
| Rejection reasons | `rejection_reasons` seed rows | Coiling defect, Surface defect |
| Chart of accounts | `gl_accounts` seed rows | Industry-standard account codes |
| Cost centres | `cost_centres` seed rows | Production, Quality, Sales |

---

## 4. Technology Stack

| Layer | Choice |
|---|---|
| Backend | Java 17+ (21 LTS recommended), Spring Boot 3.x, Spring Web, Spring Data JPA, Spring Security |
| Database | PostgreSQL 16+ (normalised; FKs, indexes, constraints; Flyway migrations) |
| Frontend | Angular 18+, TypeScript, Angular Material, Reactive Forms, RxJS, signals |
| API docs | OpenAPI 3 / Swagger UI |
| Packaging | Docker (multi-stage builds), Docker Compose |
| Testing | JUnit 5, Testcontainers PostgreSQL, ArchUnit, Playwright |

**Architecture:** Controller → Service → Repository → Database. DTOs (Java records) only across the API boundary. Entities never leave the service layer. MapStruct for mapping.

---

## 5. Roles and Access Control (RBAC)

### 5.1 Model [Implemented R1–R8]

- Permissions managed centrally by the backend, never hard-coded in the frontend.
- A user has one or more roles; a role has many permissions; permissions are the union.
- **Every backend API verifies authorization.** UI guards are UX only; backend returns 403.
- Tables: `users`, `roles`, `permissions`, `user_roles`, `role_permissions`.

### 5.2 Core Roles [Implemented R1–R8]

| Role | Key Capabilities |
|---|---|
| **ADMIN** | System configuration, user/role management, all modules, authorized adjustments, audit logs |
| **ENGINEER** | Products, BOMs, routing, drawings, specs, tolerances |
| **PRODUCTION_MANAGER** | Production orders, scheduling, WIP monitoring, production reports |
| **SUPERVISOR** | Assigned orders, operations, operator assignment, downtime, material requests |
| **OPERATOR** | Own work orders, operations (start/pause/complete), simple tablet UI |
| **QUALITY_MANAGER** | Inspections (incoming/in-process/final), approvals, rejections, QMS |
| **STORE_MANAGER** | Inventory receive/issue/transfer/adjust, locations, batches, stock counts |
| **PURCHASE_MANAGER** | Suppliers, requisitions, POs, goods receipt tracking |
| **SALES** | Customers, sales orders, FG availability, reservations, dispatch requests |
| **DISPATCH** | Approved sales orders, FG dispatch, transport, dispatch documents |
| **MAINTENANCE** | Machine master, breakdowns, maintenance requests, PM schedules |
| **STORE_OPERATOR** | Scoped to assigned warehouses: receive, scan, move, issue, count |
| **MANAGEMENT** | Read-only dashboards and reports across all modules |

### 5.3 Extended Roles (R9–R13)

| Role | Module | Key Capabilities |
|---|---|---|
| **ACCOUNTANT** | FIN | Journal entries, GL, AP/AR, bank reconciliation, period-end |
| **FINANCE_MANAGER** | FIN | Financial reports, period close, chart of accounts, approvals |
| **COST_CONTROLLER** | CTR | Cost centres, product costing, variance analysis, profit centre reporting |
| **HR_MANAGER** | HRM | Employee master, payroll, attendance, leave management |
| **CRM_AGENT** | CRM | Leads, opportunities, contacts, customer activities, quotations |
| **PLANNER** | MRP | Demand planning, MRP runs, purchase and production proposals |
| **WAREHOUSE_MANAGER** | WMS | Bin management, put-away, picking, wave planning, cycle counts |
| **PLM_ENGINEER** | PLM | ECOs, drawing revisions, specification changes, approval workflows |

### 5.4 Permission Codes

```
-- Platform
USER_CREATE USER_UPDATE USER_DELETE USER_VIEW
ROLE_MANAGE PERMISSION_MANAGE SETTINGS_MANAGE
AUDIT_VIEW REPORT_VIEW DASHBOARD_VIEW

-- Master Data
MASTERDATA_MANAGE PRODUCT_CREATE PRODUCT_UPDATE PRODUCT_VIEW
BOM_CREATE BOM_UPDATE BOM_APPROVE BOM_VIEW
SUPPLIER_MANAGE CUSTOMER_MANAGE MACHINE_MANAGE

-- Inventory / Warehouse
INVENTORY_VIEW INVENTORY_RECEIVE INVENTORY_ISSUE INVENTORY_TRANSFER INVENTORY_ADJUST
STOCK_COUNT LOCATION_MANAGE WAREHOUSE_MANAGE BATCH_MANAGE

-- Procurement
PURCHASE_CREATE PURCHASE_APPROVE PURCHASE_VIEW REQUISITION_CREATE

-- Manufacturing
PRODUCTION_CREATE PRODUCTION_UPDATE PRODUCTION_RELEASE PRODUCTION_EXECUTE PRODUCTION_CLOSE
PRODUCTION_SCHEDULE DOWNTIME_RECORD MATERIAL_REQUEST ROUTING_MANAGE

-- Quality
QUALITY_INSPECT QUALITY_APPROVE QUALITY_REJECT QUALITY_HOLD
REJECTION_REASON_MANAGE INSPECTION_PLAN_MANAGE

-- Sales / Dispatch
SALES_CREATE SALES_UPDATE SALES_VIEW RESERVE_FG DISPATCH_CREATE DISPATCH_APPROVE

-- Finance (R9)
GL_POST GL_VIEW JOURNAL_CREATE JOURNAL_APPROVE PERIOD_CLOSE
AP_MANAGE AR_MANAGE BANK_RECONCILE FINANCIAL_REPORT

-- Controlling (R10)
COST_CENTRE_MANAGE PRODUCT_COSTING VARIANCE_ANALYSIS PROFIT_CENTRE_REPORT

-- HR (R13)
EMPLOYEE_MANAGE PAYROLL_RUN ATTENDANCE_MANAGE LEAVE_APPROVE

-- CRM (R12)
LEAD_MANAGE OPPORTUNITY_MANAGE QUOTATION_CREATE CUSTOMER_ACTIVITY

-- Maintenance
MAINTENANCE_MANAGE PM_SCHEDULE SPARE_PART_MANAGE

-- MRP (R11)
MRP_RUN DEMAND_PLAN PRODUCTION_PROPOSAL PURCHASE_PROPOSAL

-- WMS (R14)
BIN_MANAGE PUTAWAY_MANAGE PICKING_MANAGE WAVE_MANAGE

-- PLM (R15)
ECO_CREATE ECO_APPROVE DRAWING_MANAGE SPECIFICATION_MANAGE
```

---

## 6. Enterprise Structure Requirements (ENT — R9)

**ENT-001:** The system shall support a hierarchical enterprise structure: Company → Plant → Storage Location → Cost Centre.

**ENT-002:** Every transacting document shall reference a Plant. All inventory, production, purchasing, and sales transactions shall be plant-scoped.

**ENT-003:** The system shall support multiple plants under a single company sharing master data (materials, products, suppliers, customers, chart of accounts).

**ENT-004:** Users shall be assignable to one or more plants; cross-plant data visibility shall be configurable per role.

**ENT-005:** Plant-level settings shall override company-level defaults for: default warehouse, timezone, production calendar, costing method.

---

## 7. Master Data Requirements (MDM)

### 7.1 Material Master [Implemented R1–R8]

**MDM-001:** Materials shall have a unique Material Code, Name, Type, Grade, UOM, standard cost, reorder/min/max levels, and active flag.

**MDM-002:** Material types shall be configurable via seed data (not hard-coded enums).

**MDM-003:** The system shall support alternative UOMs with conversion factors (e.g., kg ↔ pcs ↔ coils).

**MDM-004:** Material masters shall support purchasing info (preferred supplier, purchasing UOM, tax code) and costing data (standard cost, cost estimate).

### 7.2 Product Master [Implemented R1–R8]

**MDM-010:** Products shall have a unique Product Code and common fields (name, type, material, UOM, drawing number, revision, status).

**MDM-011:** Type-specific attributes shall be stored in a JSONB column driven by a metadata catalogue (`product_attribute_definitions`). Adding a new product type requires only a seed data row, not a code change.

**MDM-012:** Customer-specific product specifications shall be supported (tolerance overrides per customer).

### 7.3 Supplier and Customer Master [Implemented R1–R8]

**MDM-020:** Suppliers shall have: code, name, GST number, payment terms, contact, bank details, status, approved materials list.

**MDM-021:** Customers shall have: code, name, GST number, payment terms, contact, credit limit, approved products list.

**MDM-022:** Supplier performance metrics (on-time delivery %, quality pass rate %) shall be maintained automatically from transaction history.

### 7.4 Chart of Accounts (R9 — FIN)

**MDM-030:** The system shall maintain a hierarchical chart of accounts (company, account group, GL account).

**MDM-031:** Account types: Asset, Liability, Equity, Revenue, Expense, Cost of Goods Sold.

**MDM-032:** The chart of accounts shall be configurable per company; a default chart shall ship as seed data for manufacturing companies.

**MDM-033:** Cost centres and profit centres shall be first-class master data entities.

### 7.5 Employee Master (R13 — HRM)

**MDM-040:** Employee master shall include: employee code, name, designation, department, plant, cost centre, date of joining, employment type, pay grade, bank details.

**MDM-041:** Operators and supervisors in IAM shall be linked to the Employee master.

---

## 8. Inventory Management Requirements (INV) [Implemented R1–R8]

**INV-001:** All stock changes shall be recorded through `InventoryPostingService` as append-only ledger transactions. No direct write to balances outside this service.

**INV-002:** Stock shall be tracked by Warehouse + Location + Item + Batch + Stock Status.

**INV-003:** Transaction types: OPENING_BALANCE, PURCHASE_RECEIPT, MATERIAL_ISSUE, MATERIAL_RETURN, MATERIAL_CONSUMPTION, PRODUCTION_RECEIPT, PRODUCTION_REJECTION, SALES_DISPATCH, STOCK_ADJUSTMENT, STOCK_TRANSFER, SCRAP, CUSTOMER_RETURN.

**INV-004:** Stock cannot go negative unless explicitly enabled by an administrator (configurable per item).

**INV-005:** Quarantine, Hold, and Rejected stock cannot be issued to production.

**INV-006:** Batch/lot management with FEFO/FIFO allocation. Batch fields: batch number, supplier, PO reference, heat number, certificate number, received date, expiry date, quantity, status.

**INV-007:** Stock adjustment workflow: Requested → Supervisor Review → Manager Approval → Admin Approval (above threshold) → Posted. Reason mandatory.

**INV-008:** Nightly reconciliation shall verify that the sum of ledger transactions equals the balance projection; mismatches raise a critical alert.

**INV-009:** Full traceability from raw material batch to finished goods batch and dispatch.

**INV-010:** The system shall support stock valuation using moving weighted average (default) or standard cost. The costing method shall be configurable per company.

---

## 9. Warehouse Management Requirements (WMS — R14)

**WMS-001:** The system shall support a bin-level warehouse structure: Warehouse → Zone → Aisle → Rack → Bin.

**WMS-002:** Bin capacity management: weight capacity, volume capacity, allowed material types.

**WMS-003:** Goods receipt process shall include: unloading, quality inspection, put-away task generation.

**WMS-004:** Put-away strategies: fixed bin (item-to-bin assignment), nearest empty bin, bulk zone first.

**WMS-005:** Picking strategies: FEFO/FIFO, zone-based picking, multi-order wave picking.

**WMS-006:** The system shall generate transfer orders (TO) for internal movements. TOs shall be confirmable via barcode scan or manual entry.

**WMS-007:** Cycle count management: frequency-based (ABC), random sample, zone-based. Count results shall feed into the stock adjustment workflow.

**WMS-008:** The system shall support cross-docking: goods received from a PO are directly staged for a production order or dispatch without putaway.

---

## 10. Procurement Requirements (PUR) [Implemented R1–R8]

**PUR-001:** Full PO lifecycle: DRAFT → SUBMITTED → APPROVED → SENT_TO_SUPPLIER → PARTIALLY_RECEIVED → RECEIVED → CANCELLED.

**PUR-002:** Purchase requisitions shall be raisable by Store Managers and Supervisors; requisitions require approval before becoming POs.

**PUR-003:** Goods receipts against POs shall create RM batches in QUARANTINE status pending incoming inspection.

**PUR-004:** Three-way matching: PO quantity, GR quantity, and supplier invoice quantity shall be reconciled before AP payment.

**PUR-005:** Supplier performance shall be tracked: promised delivery date vs actual, inspection pass rate, price variance.

**PUR-006** (R11): MRP-generated purchase proposals shall be convertable to PRs/POs in one click, referencing the MRP run that generated them.

---

## 11. Manufacturing Requirements (MFG) [Implemented R1–R8]

**MFG-001:** Bill of Materials with revisions. BOM workflow: DRAFT → ENGINEERING_REVIEW → APPROVED → ACTIVE. Only one ACTIVE revision per product.

**MFG-002:** On production order release, the active BOM and routing shall be snapshotted into the order. Later BOM revisions shall not affect running orders.

**MFG-003:** Production order lifecycle: DRAFT → APPROVED → RELEASED → IN_PROGRESS → COMPLETED → CLOSED.

**MFG-004:** Material issue against production orders with FEFO/FIFO batch allocation. Multiple issues per order supported.

**MFG-005:** Operation-level output recording: good quantity, rejected quantity, scrap quantity, operator, machine, start/end time.

**MFG-006:** WIP tracking: quantity at each operation shall be derivable from issue and output records.

**MFG-007:** Operator UI shall be tablet-optimised: large touch targets, minimal navigation, single-screen quantity entry with numeric keypad.

**MFG-008:** Production downtime recording with reason codes. Machine problem and material problem reporting.

**MFG-009:** BOM quantity basis shall be configurable per BOM (via `bom.base_quantity` column; default from system setting `bom.default_base_quantity=1`).

### 11.1 MRP Requirements (R11)

**MRP-001:** The MRP engine shall calculate net material requirements from: sales order demand, production order demand, safety stock levels, existing POs, and on-hand stock.

**MRP-002:** MRP shall generate: purchase proposals (for raw materials) and production proposals (for semi-finished/finished items).

**MRP-003:** MRP shall respect: lead times (supplier, production), lot sizing rules (fixed lot, min/max, economic order quantity), and planning horizons.

**MRP-004:** MRP proposals shall be reviewable and filterable before conversion to PRs/production orders.

**MRP-005:** The system shall support make-to-stock (MTS) and make-to-order (MTO) planning strategies per product.

**MRP-006:** Capacity levelling: MRP proposals shall be checked against rough-cut capacity (machine hours, labour hours) and flagged when overloaded.

### 11.2 PLM Requirements (R15)

**PLM-001:** Engineering Change Orders (ECOs) shall govern changes to product specifications, BOMs, and routings. ECO workflow: DRAFT → REVIEW → APPROVED → IMPLEMENTED.

**PLM-002:** Each ECO shall record: change description, affected products/BOMs, reason, impact assessment, effectivity date.

**PLM-003:** On ECO implementation (after approval), the system shall automatically create a new DRAFT BOM revision for each affected product.

**PLM-004:** Drawing and document attachments shall be supported on products, BOMs, and ECOs (file references, not file storage — files stored externally via DOC module).

**PLM-005:** Product lifecycle states: DEVELOPMENT → PROTOTYPE → APPROVED → ACTIVE → PHASE_OUT → OBSOLETE. Status transitions shall be audited.

---

## 12. Quality Management Requirements (QMS) [Implemented R1–R8]

**QMS-001:** Three inspection types: INCOMING (raw material), IN_PROCESS (during production), FINAL (finished goods).

**QMS-002:** Inspection plans shall be data-driven (`inspection_plan_parameters`). Adding a new quality parameter requires only a seed row, not a code change.

**QMS-003:** Quality outcomes: PENDING → APPROVED / REJECTED / HOLD. Partial acceptance supported (accept x, reject y of a batch).

**QMS-004:** Finished goods shall not enter AVAILABLE stock until quality approval. FG lands in QUALITY_PENDING; moves to AVAILABLE only via inspection approval.

**QMS-005:** Rejection management: record reason, quantity, rejection %. Pareto analysis of rejection reasons.

**QMS-006:** Traceability: for any FG batch, the system shall trace back to raw material batch, supplier, and PO.

**QMS-007** (R16 — Advanced Quality): Statistical Process Control (SPC) charts (X-bar, R, P, C charts) for critical parameters.

**QMS-008** (R16): Capability indices (Cp, Cpk) calculated from inspection history per parameter.

**QMS-009** (R16): Customer complaint management: complaint receipt, root cause analysis, corrective action (CAPA), closure.

**QMS-010** (R16): First Article Inspection (FAI) reports for new products or after ECOs.

---

## 13. Sales Management Requirements (SAL) [Implemented R1–R8]

**SAL-001:** Sales order lifecycle: ORDERED → ALLOCATED → PACKED → PARTIALLY_DISPATCHED → DISPATCHED → CANCELLED.

**SAL-002:** FG reservation against available stock. Reservation shall reduce available-to-promise quantity.

**SAL-003:** Dispatch records: customer, sales order, product, FG batch, quantity, transport, invoice reference. Dispatch reduces FG inventory.

**SAL-004:** Dispatch quantity cannot exceed available (on-hand minus reserved for others) FG stock.

**SAL-005:** Printable dispatch documents (PDF delivery note, packing list).

**SAL-006:** Customer-specific product specifications shall override standard specs during quality approval.

### 13.1 CRM Requirements (R12)

**CRM-001:** Lead and opportunity management: track prospects from first contact through quote to order.

**CRM-002:** Quotation management: create price quotations from product master + customer-specific pricing. Accepted quotations shall convert to sales orders.

**CRM-003:** Customer activity log: calls, emails, meetings, follow-ups tracked per customer.

**CRM-004:** Customer segmentation and tagging (industry, region, tier, strategic status).

**CRM-005:** Pipeline reporting: opportunities by stage, value, expected close date.

**CRM-006:** CRM contacts shall link to the customer master in MDM; a contact may have multiple CRM activities.

---

## 14. Finance Requirements (FIN — R9)

**FIN-001:** General Ledger with double-entry bookkeeping. Every financial posting shall create balanced journal entries (Dr = Cr).

**FIN-002:** Automatic journal entry generation from operational transactions:
- Goods Receipt → Dr Inventory / Cr GR Clearing
- Invoice receipt → Dr GR Clearing / Cr AP
- Production completion → Dr FG Inventory / Cr WIP
- Sales dispatch → Dr COGS / Cr FG Inventory; Dr AR / Cr Revenue

**FIN-003:** Accounts Payable: supplier invoices, three-way match, payment processing, ageing analysis.

**FIN-004:** Accounts Receivable: customer invoices from dispatch, receipts, credit notes, ageing analysis.

**FIN-005:** Bank reconciliation: import bank statements, match with GL entries, flag unreconciled items.

**FIN-006:** Financial periods: monthly close process with period-open/period-close controls. Posting to a closed period shall require Finance Manager approval.

**FIN-007:** Tax management: GST (India) — CGST, SGST, IGST, UTGST — with configurable tax codes per material/supplier/customer combination.

**FIN-008:** Financial statements: Balance Sheet, P&L, Cash Flow Statement — as of any period.

**FIN-009:** Multi-currency support: exchange rates configurable per period; functional currency = INR default.

**FIN-010:** Budget management: enter annual budgets per GL account per cost centre; actual vs budget variance reporting.

---

## 15. Controlling Requirements (CTR — R10)

**CTR-001:** Cost centre accounting: all operational costs shall be assignable to cost centres. Production labour, machine hours, and overhead absorption recorded per cost centre.

**CTR-002:** Product costing: calculate standard cost per product from BOM (material cost), routing (labour + machine cost), and overhead rates.

**CTR-003:** Actual costing: capture actual material, labour, and overhead costs per production order. Calculate production order variance (actual vs standard).

**CTR-004:** Variance analysis: price variance (standard vs actual material price), quantity variance (standard vs actual consumption), efficiency variance (standard vs actual labour hours).

**CTR-005:** Profit centre reporting: revenue and cost aggregation per profit centre. Contribution margin analysis.

**CTR-006:** Internal orders for project-based cost tracking (capital expenditure, maintenance projects).

**CTR-007:** Period-end cost allocation: overhead cost distribution from cost centre to products using allocation bases (machine hours, labour hours, production quantity).

---

## 16. HR Management Requirements (HRM — R13)

**HRM-001:** Employee master (linked to IAM users for system access, separate for non-system employees).

**HRM-002:** Attendance management: shift definitions, attendance recording, late/absent tracking. Integration with production operations for automatic operator time capture.

**HRM-003:** Leave management: leave types (CL, SL, EL, LOP), leave application, approval workflow, balance tracking.

**HRM-004:** Payroll: basic pay, allowances (HRA, DA, travel), deductions (PF, ESI, PT, TDS), net pay. Pay slip generation.

**HRM-005:** Operator skill matrix: skills, certifications, machines authorised to operate. Referenced by production scheduling.

**HRM-006:** Training records: training programmes, attendance, certifications, renewal dates.

---

## 17. Maintenance Requirements (MNT) [Partial R1–R8]

**MNT-001:** Machine master with maintenance schedules (PM frequencies: daily, weekly, monthly, annual, by-meter). [Implemented R1–R8]

**MNT-002:** Preventive maintenance (PM) schedules with due-date alerts. [Implemented R1–R8]

**MNT-003:** Breakdown maintenance: record breakdown, assign technician, record repair activities, spare parts used, resolution time. [Implemented R1–R8]

**MNT-004** (R18): Spare parts inventory: maintain spare part stock, issue against maintenance work orders, minimum stock alerts.

**MNT-005** (R18): OEE (Overall Equipment Effectiveness) calculation: Availability × Performance × Quality per machine per period.

**MNT-006** (R18): Maintenance cost tracking per machine (spare parts + labour), integrated with CTR cost centres.

---

## 18. Reporting and Analytics Requirements (RPT / ANA)

### 18.1 Standard Reports [Implemented R1–R8]

**RPT-001:** All reports shall be exportable to Excel (XLSX), PDF, and CSV.

**RPT-002:** Inventory reports: current stock, stock ledger, batch-wise, warehouse-wise, valuation (by cost method), low stock, ageing.

**RPT-003:** Production reports: order status, material consumption, efficiency, machine-wise, operator-wise, rejection.

**RPT-004:** Quality reports: inspection results, rejection pareto, batch quality history, first-pass yield, customer complaint.

**RPT-005:** Purchase reports: PO history, supplier performance, pending POs, GR summary.

**RPT-006:** Sales reports: order backlog, dispatch summary, customer-wise, product-wise.

**RPT-007:** Financial reports (R9): GL trial balance, P&L, Balance Sheet, AP ageing, AR ageing, bank reconciliation.

### 18.2 Advanced Analytics (R19 — ANA)

**ANA-001:** An embedded BI engine shall provide drag-and-drop ad-hoc report building across all operational and financial data.

**ANA-002:** Pre-built analytical dashboards: Production OEE, Inventory turns, Supplier scorecards, Customer profitability, Cost variance.

**ANA-003:** AI-powered demand forecasting: use historical sales and production data to forecast material requirements. Forecasts feed into MRP.

**ANA-004:** Anomaly detection: automatically flag unusual patterns in stock consumption, supplier quality, production efficiency.

**ANA-005:** Predictive maintenance: use machine downtime history and sensor data (IoT integration) to predict maintenance needs.

---

## 19. Integration Requirements (INT — R19)

**INT-001:** REST API with OpenAPI 3.0 documentation for all modules. External systems shall integrate via the same API as the Angular SPA.

**INT-002:** Webhook outbox: the system shall emit domain events (StockPosted, OrderCompleted, InvoicePaid) to configurable HTTP endpoints.

**INT-003:** Import APIs: bulk import of master data (materials, products, customers, suppliers) via CSV/Excel upload.

**INT-004:** Export APIs: full data export per entity for data warehousing.

**INT-005:** The system shall provide a read-only reporting schema (PostgreSQL views) that external BI tools can query directly.

**INT-006** (R19): Message broker integration: publish domain events to Kafka/RabbitMQ for IoT integration (machine data ingestion) and downstream systems.

**INT-007** (R19): ERP connector: standard integration with tally (popular in Indian SME market) for financial data export.

---

## 20. Security Requirements (SEC) [Implemented R1–R8]

**SEC-001:** JWT authentication; BCrypt(12) password hashing; no plain-text passwords.

**SEC-002:** Every endpoint shall be authorised (`@PreAuthorize`). An ArchUnit rule shall verify no unprotected endpoints exist.

**SEC-003:** Refresh tokens stored hashed in the database; token reuse detection and family revocation.

**SEC-004:** Security headers: HSTS (2 years), X-Frame-Options, X-Content-Type-Options, Referrer-Policy, Permissions-Policy, CSP.

**SEC-005:** Rate limiting on authentication endpoints.

**SEC-006:** OWASP dependency scanning in CI.

**SEC-007:** All secrets from environment variables; never committed to source control.

**SEC-008:** Segregation of duties: PO requester ≠ approver; stock adjustment requester ≠ approver; journal entry preparer ≠ approver.

**SEC-009** (R9): Financial controls — period close requires Finance Manager; posting to closed periods requires Finance Manager override (audited).

**SEC-010** (R9): Sensitive financial reports (trial balance, payment runs) shall require re-authentication or MFA (configurable).

---

## 21. Non-Functional Requirements (NFR)

**NFR-001 Performance:** Ledger posting P95 < 150 ms; stock availability query P95 < 100 ms; dashboard load P95 < 500 ms; report generation (up to 10,000 rows) < 3 s.

**NFR-002 Scalability:** Backend stateless (JWT); horizontal scaling with ShedLock for distributed scheduling; read replicas for reporting.

**NFR-003 Availability:** 99.5% uptime target (excluding planned maintenance). Graceful degradation: reporting and analytics can be delayed but inventory posting must remain available.

**NFR-004 Data integrity:** Ledger and audit log immutability enforced by DB triggers. Referential integrity via FK constraints.

**NFR-005 Backup:** Automated daily pg_dump + WAL archiving. Restore procedures tested monthly. RTO < 4 hours, RPO < 1 hour.

**NFR-006 Internationalisation:** UI strings externalised (Angular i18n). Date/time display in user's timezone (default Asia/Kolkata). Currency display in INR. Kannada/Hindi locale support in a later release.

**NFR-007 Accessibility:** Angular Material components; keyboard navigation; WCAG 2.1 AA for operator-facing screens.

**NFR-008 Mobile:** Operator tablet UI optimised for 10" tablet. Future: Progressive Web App (PWA) for offline capability.

**NFR-009 Auditability:** Every sensitive action records user, role, action, entity, old value, new value, reason, timestamp, IP address. Audit records retained permanently.

**NFR-010 Compliance:** GST compliance for Indian businesses. Future: GAAP/IFRS compliance for financial reporting.

---

## 22. Audit Trail Requirements [Implemented R1–R8]

**AUD-001:** Every sensitive action shall be audited: User, Role(s), Action, Entity, Entity ID, Old Value, New Value, Reason (mandatory where applicable), Timestamp, IP address.

**AUD-002:** Audit records are append-only. DB triggers prevent UPDATE/DELETE on `audit_logs`.

**AUD-003:** Coverage: user/role changes, logins, master data edits, BOM approvals, document state transitions, stock adjustments, threshold approvals, production corrections, setting changes, financial postings, period close/open, report exports.

**AUD-004:** Audit log is accessible only to users with `AUDIT_VIEW` permission. Filterable by user, entity, action, date range. Exportable.

---

## 23. Demo Data Requirements [Implemented R1–R8]

**DEMO-001:** Demo data shall load only when profile is `dev` or `demo` (`ims.demo.load-data=true`). Never in production.

**DEMO-002:** Demo data shall be generated through production services (not raw SQL), so ledger and balances agree.

**DEMO-003:** Demo workflow shall cover the complete Spring Manufacturing lifecycle: PO → GR → Inspection → Production → Quality → Dispatch.

**DEMO-004:** Sample users for every role with documented credentials. At least 10 production orders, realistic inventory transactions, 5+ suppliers, 5+ customers.

---

## 24. Implementation Phases

See PLAN.md for detailed task IDs, estimates, and exit gates.

| Release | Modules | Status |
|---|---|---|
| R1 Foundation | IAM, MDM (partial), AUD, SEC | **Complete** |
| R2 Inventory Core | INV (ledger, balances, batches, adjustment) | **Complete** |
| R3 Procurement | PUR, incoming QMS | **Complete** |
| R4 Manufacturing | MFG (BOM, production, WIP) | **Complete** |
| R5 Quality | QMS (in-process, final inspection, FG) | **Complete** |
| R6 Sales | SAL (orders, dispatch) | **Complete** |
| R7 Insight | RPT, dashboards, alerts, traceability, MNT | **Complete** |
| R8 Hardening | SEC headers, DemoData, load tests, README | **Complete** |
| R9 Finance | FIN, ENT, extended MDM | Planned |
| R10 Controlling | CTR, product costing, variance analysis | Planned |
| R11 MRP | MRP engine, demand planning, proposals | Planned |
| R12 CRM | CRM (leads, opportunities, quotations) | Planned |
| R13 HR | HRM (employee, attendance, payroll, skills) | Planned |
| R14 WMS | Bin-level WMS, advanced picking/put-away | Planned |
| R15 PLM | ECOs, drawing management, product lifecycle | Planned |
| R16 Advanced Quality | SPC, CAPA, FAI, customer complaints | Planned |
| R17 Document Management | DOC module, NTF engine, template management | Planned |
| R18 Advanced Manufacturing | OEE, spare parts, advanced scheduling | Planned |
| R19 AI and Integration | ANA, INT, AI forecasting, message broker | Planned |
| R20 Industry Verticals | Automotive, Furniture, Electronics verticals | Planned |

---

## 25. Business Rules

### Existing Rules [Implemented R1–R8]
1. Stock cannot go negative unless explicitly enabled by an administrator.
2. Material cannot be issued without sufficient available stock.
3. Quarantine material cannot be issued to production.
4. Rejected material cannot be used for production.
5. Finished goods cannot enter available stock until quality approval.
6. Production cannot consume more material than issued.
7. Dispatch cannot exceed available FG stock.
8. A completed production order cannot be modified without authorized permission.
9. Every stock change generates an inventory transaction.
10. Every important transaction is auditable.
11. Batch traceability is maintained end-to-end.
12. BOM revisions are preserved; only one active revision per product.
13. Historical transactions are never silently deleted; corrections are reversals.
14. Adjustments above the configured threshold require Admin approval.
15. Engineers, Purchase, and Sales roles cannot change stock directly.

### Financial Rules (R9)
16. Every financial posting must balance (Dr = Cr).
17. Posting to a closed financial period requires Finance Manager approval.
18. Three-way match (PO/GR/Invoice) is required before AP payment.
19. Journal preparer and approver must be different users (segregation of duties).

### MRP Rules (R11)
20. MRP proposals are advisory only; they require human review before conversion to orders.
21. MRP shall not generate proposals that would violate supplier lead times.

---

## 26. Open Questions and Assumptions

- **Multi-currency (R9):** Functional currency is INR; foreign currency invoices need exchange rate at posting time. Method (transaction date rate vs closing rate) to be confirmed.
- **Payroll method (R13):** Monthly payroll with Indian statutory deductions (PF/ESI/PT). International payroll rules deferred to R20.
- **Barcode / RFID:** Store Operator workflow supports batch-code scan; dedicated scanner hardware integration is a future phase.
- **IoT machine data:** Machine downtime tables exist; ingestion pipeline deferred to R19.
- **Production targets:** Either from `system_settings` or a `production_targets` table — to be decided in R11.
- **GST filing:** Reports provide GST-ready data; e-filing integration is deferred to R19.
