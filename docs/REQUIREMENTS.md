# Inventory Management System — Spring Manufacturing Company
## REQUIREMENTS.md

**Version:** 1.0  **Date:** 2026-10-01
**Sources:** Attachment 1 (system specification), Attachment 2 (RBAC specification)

---

## 1. Purpose and Scope

A production-ready inventory and manufacturing-material system for a spring manufacturer. It covers the full lifecycle:

**Raw material procurement → Inventory → Production → WIP → Quality inspection → Finished goods → Customer order → Dispatch**

Spring types: Compression, Extension/Tension, Torsion, Conical, Disc/Belleville, Wire Forms, Custom.

**Objectives**
1. Raw-material inventory and spring/product master data
2. Raw-material usage tracking, production orders, material consumption, WIP, finished-goods output
3. Quality inspection; rejected/scrapped material tracking
4. Finished-goods inventory, customer orders, dispatch
5. Suppliers and purchase orders
6. Complete stock-movement history, dashboards, reports, audit trail
7. Architecture that can later grow into a full ERP/MRP

**Core principle:** this is a manufacturing inventory system, not simple CRUD. Every stock movement is tied to a business document and is fully traceable.

---

## 2. Technology Stack

| Layer | Choice |
|---|---|
| Backend | Java 17+, Spring Boot 3.x, Spring Web, Spring Data JPA, Hibernate, Spring Security, JWT, Bean Validation, Maven |
| Database | PostgreSQL (normalized; FKs, indexes, constraints; migration scripts) |
| Frontend | Angular 18+, TypeScript, Angular Material, Reactive Forms, RxJS |
| API docs | OpenAPI / Swagger |
| Packaging | Docker configuration |

**Architecture:** Controller → Service → Repository → Database. DTOs only; JPA entities are never exposed through REST.

---

## 3. Roles and Access Control (RBAC)

### 3.1 Model
- Permissions are managed **centrally by the backend**, never hard-coded in the frontend.
- A user has one or more roles; a role has many permissions.
- Tables: `users`, `roles`, `permissions`, `user_roles`, `role_permissions`.
- **Every backend API verifies authorization.** Hidden buttons, hidden menus and Angular route guards are not security. Example: an Operator calling `PUT /api/products/{id}` directly must receive **HTTP 403**.

### 3.2 Role reconciliation
Attachment 1 defines 7 roles; Attachment 2 defines 13 and is more detailed. **The 13-role model below is the baseline.** Attachment 1's roles map as follows:

| Attachment 1 role | Mapped to |
|---|---|
| ADMIN | ADMIN |
| INVENTORY_MANAGER | INVENTORY / STORE MANAGER |
| PRODUCTION_MANAGER | PRODUCTION MANAGER |
| QUALITY_MANAGER | QUALITY ENGINEER / QUALITY MANAGER |
| PURCHASE_MANAGER | PURCHASE MANAGER |
| SALES_MANAGER | SALES / CUSTOMER SERVICE |
| OPERATOR | OPERATOR |

New in Attachment 2: ENGINEER, SUPERVISOR, DISPATCH / LOGISTICS, MAINTENANCE ENGINEER, STORE OPERATOR, MANAGEMENT (read-only).

### 3.3 Roles and capabilities

| Role | Can | Must NOT |
|---|---|---|
| **ADMIN** | Manage users/roles/permissions, system config, master data, warehouses, machines; view all inventory, production, quality, purchase, sales; authorized stock adjustments; audit logs; all reports | Silently delete historical transactions |
| **ENGINEER** | Create/edit spring products, specs, dimensions, tolerances, drawing numbers/revisions; create/modify/revise/approve (if authorized) BOMs, view BOM history; define routing (operations, sequence, standard time, machines, inspection points); manage technical data (wire/spring diameter, free length, coils, rate, load, heat/surface treatment) | Modify inventory quantities |
| **PRODUCTION MANAGER** | Create/modify/release/close production orders; assign lines, machines, supervisors; schedule; view material availability; monitor WIP and progress; production reports | Modify stock except through approved production transactions |
| **SUPERVISOR** | View assigned orders; start/complete operations; assign operators and machines; record quantities, rejects, scrap, downtime, machine issues, WIP movement; request additional material; view instructions and specs | Modify product master data or BOMs |
| **OPERATOR** | View assigned work, order, instructions, required quantity; start/pause/complete operation; enter produced, rejected, scrap quantities; report machine and material problems. Interface must be extremely simple and fast | Modify inventory, BOM, product specs, production orders; approve inspections; modify historical production records |
| **QUALITY ENGINEER / MANAGER** | Incoming, in-process and final inspection; record measurements and deviations; approve/reject/HOLD raw-material batches; approve/reject WIP; approve/reject/release finished batches; manage rejection reasons | — |
| **INVENTORY / STORE MANAGER** | Receive material, create goods receipts, view stock and ledger and valuation, manage locations and batches, issue/return/transfer, stock counts, request adjustments, inventory reports | Post large adjustments alone: adjustments above a **configurable threshold require Admin approval** |
| **PURCHASE MANAGER** | Manage suppliers; purchase requisitions and orders; modify POs; submit for approval; track deliveries; purchase history; supplier performance; pending POs | Alter inventory directly (only via Goods Receipt) |
| **SALES / CUSTOMER SERVICE** | Manage customers; create/modify sales orders; view FG availability; reserve FG; track orders; create dispatch requests; view dispatch status | Reduce inventory directly (only via Dispatch) |
| **DISPATCH / LOGISTICS** | View approved sales orders and available FG; create dispatch; select FG batch; record quantity and transport; print dispatch documents; view history | Dispatch more than available stock |
| **MAINTENANCE ENGINEER** | Manage machine master; view status; record breakdowns, maintenance, spare-part use, downtime; create maintenance requests; schedule preventive maintenance; view machine history | — |
| **STORE OPERATOR** | Restricted to assigned warehouse: receive, scan material/batch, move between locations, issue against approved requests, record returns, stock counting | Access system configuration or modify master data |
| **MANAGEMENT** | Read-only dashboards and reports (inventory value, RM/WIP/FG, production, efficiency, rejection %, scrap %, pending orders and POs, low stock, machine downtime, sales/dispatch) | Modify any transaction |

### 3.4 Permission codes (minimum set)
```
USER_CREATE USER_UPDATE USER_DELETE USER_VIEW
PRODUCT_CREATE PRODUCT_UPDATE PRODUCT_VIEW
BOM_CREATE BOM_UPDATE BOM_APPROVE BOM_VIEW
INVENTORY_VIEW INVENTORY_RECEIVE INVENTORY_ISSUE INVENTORY_TRANSFER INVENTORY_ADJUST
PRODUCTION_CREATE PRODUCTION_UPDATE PRODUCTION_RELEASE PRODUCTION_EXECUTE PRODUCTION_CLOSE
QUALITY_INSPECT QUALITY_APPROVE QUALITY_REJECT
PURCHASE_CREATE PURCHASE_APPROVE PURCHASE_VIEW
SALES_CREATE SALES_UPDATE SALES_VIEW
DISPATCH_CREATE DISPATCH_APPROVE
REPORT_VIEW AUDIT_VIEW
```
Additional codes will be needed for the roles above (e.g. machine/maintenance, downtime, stock count, requisition); add them additively.

### 3.5 Role-based dashboards
Each role gets its own dashboard, not a shared one.

| Role | Content |
|---|---|
| Admin | System-wide information |
| Engineer | Products under development, BOM revisions, engineering changes, pending approvals |
| Production Manager | Today's production, orders, WIP, machine utilization, delays |
| Supervisor | Current shift, assigned machines/operators, targets, completed, rejections |
| Operator | My work orders, current operation, target/completed/rejected quantity, Start/Pause/Complete buttons only |
| Quality | Pending and failed inspections, rejection rate, quality trends |
| Store Manager | Current and low stock, incoming materials, material issues, stock movements |
| Management | High-level KPIs and reports |

---

## 4. Master Data

### 4.1 Raw Material Master
Examples: Spring Steel, Stainless Steel, High Carbon Steel, Alloy Steel, Phosphor Bronze, Music Wire.
Fields: Material Code (e.g. `RM-SS-001`), Name, Type, Grade (e.g. SS304), Diameter, UOM, Supplier, Minimum / Reorder / Maximum Stock Level, Standard Cost, Active flag, Description.

### 4.2 Spring Product Master
Unique product code (e.g. `SPR-COMP-001`).
Common fields: Product Code, Name, Spring Type, Material, Wire Diameter, Outer Diameter, Inner Diameter, Free Length, Number of Coils, Active Coils, Spring Rate, Max/Min Load, Working Length, Solid Height, End Type, Surface Treatment, Heat Treatment, Tolerance, Unit Weight, UOM, Drawing Number, Revision Number, Customer, Status.

**Type-specific attributes** — do not force one field set on every type:
- **Compression:** wire diameter, outer diameter, free length, coil count, spring rate, load, solid height, end type
- **Extension:** wire diameter, body diameter, free length, coil count, hook type, hook length, spring rate
- **Torsion:** wire diameter, coil diameter, number of coils, leg length, leg angle, torque

Schema must allow new spring-specific specifications to be added later (`spring_specifications`, extensible design).

### 4.3 Other master data
- **Suppliers:** code, name, contact person, phone, email, address, GST number, payment terms, status
- **Customers:** code, name, contact person, email, phone, address, GST number, payment terms, status; customer-specific product specifications supported
- **Operations:** code, name, description, machine, standard time, setup time, active flag
- **Warehouses/Locations:** e.g. `MAIN-WH` with `RM-01` Raw Material, `RM-02` Stainless Steel, `WIP-01`, `FG-01`, `SCRAP-01`
- **Machines:** machine master (also managed by Maintenance Engineer)
- **Rejection reasons:** Incorrect Dimension, Wrong Wire Diameter, Surface Defect, Crack, Incorrect Spring Rate, Heat Treatment Failure, Coiling Defect, Grinding Defect, Customer Specification Failure

---

## 5. Functional Requirements

### 5.1 Bill of Materials (BOM)
- Defines raw materials and processes per product (e.g. SPR-COMP-001: wire 2.5 mm 0.45 kg, heat treatment, zinc plating, packaging 100 pcs/carton).
- Fields: BOM Number, Product, Revision, Material, Required Quantity, Scrap %, UOM, Operation, Effective Date, Status.
- Revisions (BOM-001 Rev A, Rev B) are preserved; **only one revision active for production at a time**.
- Workflow: Draft → Engineering Review → Approved → Active.

### 5.2 Inventory and stock ledger
- Track raw-material inventory (opening, purchased, consumed, rejected, available) and finished-goods inventory (opening, produced, dispatched, rejected, available).
- Inventory is tracked by **Warehouse + Location + Material/Product + Batch**.
- **No single "current stock" value.** Every movement creates a ledger transaction, and stock is calculable from the ledger.
- Transaction types: `OPENING_BALANCE`, `PURCHASE_RECEIPT`, `MATERIAL_ISSUE`, `MATERIAL_RETURN`, `PRODUCTION_RECEIPT`, `PRODUCTION_REJECTION`, `SALES_DISPATCH`, `STOCK_ADJUSTMENT`, `STOCK_TRANSFER`, `SCRAP`, `CUSTOMER_RETURN`.
- Ledger fields: Transaction ID, Number, Date, Material/Product, Batch/Lot, Type, Quantity, UOM, Unit Cost, Total Cost, Warehouse, Location, Reference Document, User, Remarks, Created Date.

### 5.3 Batch / lot management
- Fields: Batch Number (e.g. `RM-BATCH-2026-001`), Material, Supplier, Purchase Order, Manufacturing Date, Received Date, Quantity, Remaining Quantity, Heat Number, Certificate Number, Status.
- Statuses: `AVAILABLE`, `QUARANTINE`, `REJECTED`, `CONSUMED`, `EXPIRED` (plus `HOLD` from the quality workflow).

### 5.4 Purchasing
- **Purchase Order** fields: PO Number, Supplier, PO Date, Expected Delivery, Material, Quantity, Rate, Tax, Total, Status.
- PO status: `DRAFT`, `APPROVED`, `PARTIALLY_RECEIVED`, `RECEIVED`, `CANCELLED`. Approval flow: Draft → Submitted → Approved → Sent to Supplier.
- Purchase requisitions supported.
- **Goods Receipt:** PO → Goods Receipt → Quality Inspection → Accepted / Rejected stock. Received material sits in `QUARANTINE` until inspection completes; inventory updates automatically on acceptance.

### 5.5 Production
- **Production Order** fields: Number (e.g. `PO-2026-00125`), Product, BOM, Planned Quantity, Start Date, Expected Completion, Actual Start/Completion, Production Line, Machine, Supervisor, Status.
- Status flow: `DRAFT → APPROVED → RELEASED → IN_PROGRESS → COMPLETED → CLOSED` (or `CANCELLED`).
- **Material issue** against a production order, in multiple issues (e.g. 400 kg then 50 kg). Track planned, issued, consumed, returned, scrap and remaining quantities.
- **Operations and routing:** operation master plus per-product routing; not every spring uses every operation (wire drawing, coiling, cutting, grinding, heat treatment, shot peening, surface treatment, inspection, packaging).
- **Production output per operation:** production order, operation, input / good / rejected / scrap quantity, operator, machine, start/end time, remarks. **WIP updates automatically** and shows where the remaining quantity is.
- Operator actions: start, pause, complete.
- Downtime and machine/material problem reporting.

### 5.6 Quality control
- Inspection types: **Incoming** (raw material), **In-Process**, **Final**.
- Parameters per inspection: Specification, Lower Limit, Upper Limit, Actual Value, Pass/Fail. Examples: wire diameter, outer diameter, free length, coil count, spring rate, load, surface condition/finish, hardness, dimensional tolerance (e.g. Free Length 50 ± 0.5 mm, actual 49.8 → PASS).
- Quality outcomes: Pending Inspection → Approved / Rejected / Hold.
- FG flow: `PRODUCTION_COMPLETED → QUALITY_PENDING → APPROVED` or `REJECTED`. Approved quantity moves to FG inventory; rejected quantity moves to rejection/scrap inventory.
- **Rejection management:** record reasons, quantity and rejection %. Dashboard shows total production, good, rejected, rejection %, scrap.

### 5.7 Sales and dispatch
- **Sales Order:** Ordered → Allocated → Packed → Dispatched; FG reservation supported.
- **Dispatch** fields: Number, Customer, Sales Order, Product, Batch, Quantity, Date, Vehicle/Transport, Delivery Address, Invoice Reference. Dispatch automatically reduces FG inventory; printable dispatch documents.
- Returns: customer returns via `CUSTOMER_RETURN`.

### 5.8 Stock adjustment workflow
Requested → Supervisor Review → Manager Approval → Posted. Adjustments above the configurable threshold require Admin approval. Reason is mandatory.

### 5.9 Traceability
The system must answer:
- "For finished batch `FG-2026-0050`, which raw-material batch was used?"
- "For raw-material batch `RM-2026-0035`, which production orders and FG batches used it?"

Traceability screen chain: Raw Material Batch → Material Issue → Production Order → Production Operations → Quality Inspection → Finished Goods Batch → Sales Order → Customer Dispatch (with Supplier at the head of the chain).

### 5.10 Alerts
Low stock (stock < reorder level), overstock, pending inspection, production delay, material shortage, expiring/aging batches, pending purchase orders.

### 5.11 Dashboard KPIs
- **Inventory:** total RM stock, WIP, FG, low-stock items, quarantine, rejected, total inventory value
- **Production:** today's and monthly production, target, achievement %, WIP quantity, pending orders, efficiency, machine downtime
- **Quality:** first pass yield, rejection %, scrap %, inspections pending, failed inspections
- **Purchase:** open POs, pending receipts, supplier deliveries
- **Sales:** open customer orders, pending dispatches, FG available, dispatch summary

Charts and graphs required; recent transactions and alerts shown on the main dashboard.

### 5.12 Reports (export to Excel, PDF, CSV)
- **Inventory:** current stock, stock ledger, valuation, low stock, batch-wise, warehouse-wise
- **Production:** summary, order status, material consumption, efficiency, machine-wise, operator-wise
- **Quality:** rejection, defect analysis, inspection, batch quality history, customer complaint analysis
- **Purchase:** history, supplier-wise, pending POs (plus supplier performance)
- **Sales:** customer orders, dispatch, product-wise sales

### 5.13 Search and filtering
All major screens: search, sorting, pagination, and filters for date range, status, product, batch, supplier, customer.

---

## 6. Business Rules

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
11. Batch traceability is maintained.
12. BOM revisions are preserved; only one active revision per product.
13. Historical transactions are never silently deleted (corrections are made by reversing/adjusting entries).
14. Adjustments above the configured threshold need Admin approval.
15. Engineers, Purchase and Sales roles cannot change stock directly; stock changes only through Goods Receipt, Production, Dispatch and approved Adjustment transactions.

---

## 7. Audit Trail

For every sensitive action record: **User, Role, Action, Entity, Entity ID, Old Value, New Value, Reason (where applicable), Timestamp, IP address (if available)**. Records remain available permanently.

Examples:
- `STOCK_ADJUSTMENT` by admin: 500 KG → 480 KG, reason "Physical stock verification"
- `PRODUCTION_QUANTITY_UPDATE` by supervisor S001: 8,500 → 8,450, reason "50 pieces rejected during final inspection"

---

## 8. Data Design

**Tables (minimum):** users, roles, permissions, user_roles, role_permissions, suppliers, customers, materials, material_batches, products, spring_specifications, bom, bom_items, operations, product_routing, warehouses, warehouse_locations, inventory, inventory_transactions, purchase_orders, purchase_order_items, goods_receipts, goods_receipt_items, quality_inspections, quality_inspection_items, production_orders, material_issues, production_operations, production_outputs, rejections, sales_orders, sales_order_items, dispatches, dispatch_items, stock_adjustments, audit_logs.
Additions implied by Attachment 2: machines, downtime/maintenance records, purchase requisitions, approval records.

**Conventions:** primary and foreign keys, unique constraints, indexes, created/updated timestamps, optimistic locking where appropriate, Flyway/Liquibase-style migration scripts.

---

## 9. REST API

Standard conventions and HTTP status codes, centralized exception handling, OpenAPI documentation. Example: `GET/POST /api/products`, `GET/PUT/DELETE /api/products/{id}`.

Base resources: `/api/materials`, `/api/products`, `/api/inventory`, `/api/suppliers`, `/api/customers`, `/api/purchase-orders`, `/api/goods-receipts`, `/api/production-orders`, `/api/production`, `/api/quality`, `/api/sales-orders`, `/api/dispatches`, `/api/reports`.

---

## 10. Security

- JWT authentication; BCrypt password hashing; no plain-text passwords; secure password policy
- Role/permission authorization enforced on every endpoint (403 on violation)
- Input validation, global exception handling, CORS configuration
- Audit of sensitive actions (section 7)

---

## 11. Frontend

**Modules:** Login (login, forgot password); Dashboard (KPI cards, charts, alerts, recent transactions); Master Data (materials, spring products, suppliers, customers, operations, warehouses, machines); Procurement (POs, goods receipts, incoming inspection); Inventory (current stock, ledger, adjustment, transfer, batch tracking); Production (orders, material issue, operations, WIP, output); Quality (inspections, rejections, quality dashboard); Sales (customer orders, finished goods, dispatch); Reports (inventory, production, quality, purchase, sales); Administration (users, roles, audit logs, system settings); Traceability screen.

**UI/UX:** professional industrial ERP style; left sidebar, top bar, dashboard cards, data tables, modal dialogs, validated forms, status badges, confirmation dialogs, toast notifications. Responsive for desktop and tablet. Simple enough for non-technical factory operators; the operator view is optimized for quick entry.
Consistent status colors for: AVAILABLE, LOW STOCK, QUARANTINE, REJECTED, IN PRODUCTION, COMPLETED, PENDING.

---

## 12. Seed / Sample Data

- ≥ 10 raw materials
- Spring products: ≥ 5 compression, 3 extension, 3 torsion, 2 conical, 2 wire forms
- ≥ 5 suppliers, ≥ 5 customers
- ≥ 10 production orders
- Realistic inventory transactions
- Sample users for every role, with documented login credentials

---

## 13. Testing

- **Backend:** unit, service, repository, controller/API, security (including 403 checks per role)
- **Frontend:** component, service, form-validation
- **Integration workflows:**
  1. Purchase Order → Goods Receipt → Quality Inspection → Inventory
  2. Production Order → Material Issue → Production → Quality Inspection → Finished Goods
  3. Sales Order → Dispatch → Inventory Reduction

---

## 14. Implementation Phases

Implement each phase completely (no placeholders or TODOs) before the next.

| Phase | Scope |
|---|---|
| 1 | Authentication, users, roles/permissions, materials, products, suppliers, customers |
| 2 | Warehouses, inventory, stock ledger, batch management |
| 3 | Purchase orders, goods receipts, incoming quality inspection |
| 4 | BOM, routing, production orders, material issue, WIP, production output |
| 5 | Quality management, final inspection, rejection, finished goods |
| 6 | Sales orders, dispatch, customer management |
| 7 | Reports, dashboards, analytics, audit logs |

**Per-module checklist:** entities → repositories → DTOs → services → controllers → validation → exception handling → security → Angular service → Angular component → UI validation → workflow test.

**Before coding:** analyze requirements; define architecture, ER diagram, entities and relationships, REST APIs, frontend screens, business rules, and development plan.

*Note:* Attachment 2 adds roles (Maintenance, Supervisor, Dispatch, etc.) that touch machines, downtime and approvals. Place these in the phase where their dependent module is built (e.g. machines/downtime with Phase 4, approval workflows alongside each module).

---

## 15. Deliverables

1. System architecture
2. ER / database design
3. Spring Boot backend project
4. PostgreSQL schema and migration scripts
5. REST APIs with OpenAPI docs
6. Angular frontend
7. Authentication and authorization
8. Seed/sample data and sample login credentials
9. Unit and integration tests
10. README with full setup (dependencies, database creation, migrations, starting backend and frontend, login, loading sample data, running the full manufacturing workflow)
11. Docker configuration
12. End-to-end demonstration workflow

Must run locally with Spring Boot + PostgreSQL + Angular.

---

## 16. Future Extensibility (design for, do not over-engineer now)

Barcode/QR scanning, RFID, IoT machines and machine data, predictive maintenance, AI demand forecasting and inventory optimization, production scheduling, cost accounting, OEE, supplier performance analytics, mobile app, multi-company, multi-location.

---

## 17. Open Questions / Assumptions

- **Role set:** the 13-role model from Attachment 2 is treated as authoritative over Attachment 1's 7 roles.
- **PO status:** Attachment 1 lists DRAFT/APPROVED/…; Attachment 2 adds Submitted and Sent to Supplier. The system should support both sets of states.
- **Production order status:** Attachment 2 adds an APPROVED step before RELEASED.
- **Adjustment threshold:** value and unit (quantity or currency) to be defined in System Settings.
- **Roles per user:** multiple roles allowed; permissions are the union.
- **Currency/tax:** GST is mentioned; tax and currency handling details to be confirmed.
