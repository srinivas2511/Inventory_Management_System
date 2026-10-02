# UAT Checklist — Spring Manufacturing IMS

Version: Phase 8 (M8).  
Stack: Java 17 / Spring Boot 3 / Angular 18 / PostgreSQL 16.  
Environment: `dev` profile with `ims.demo.load-data=true` (demo data pre-loaded by `DemoDataLoader`).

For each test, record: **Pass / Fail / N/A**, tester initials, date.

---

## Role 1 — Admin

| ID | Test | Pass/Fail |
|----|------|-----------|
| ADM-01 | Log in with `admin` / `Admin@123`, land on dashboard | |
| ADM-02 | Create a new user (`testuser`), assign role `STORE_KEEPER` | |
| ADM-03 | Reset `testuser`'s password | |
| ADM-04 | Disable and re-enable `testuser` | |
| ADM-05 | View audit log for a user change (old value / new value shown) | |
| ADM-06 | View and edit system settings (BOM base qty, adjustment threshold) | |
| ADM-07 | Verify accessing `/api/admin/**` with a non-admin JWT returns HTTP 403 | |

---

## Role 2 — Procurement Manager

| ID | Test | Pass/Fail |
|----|------|-----------|
| PRC-01 | Create supplier `TEST-SUP`, save | |
| PRC-02 | Create Purchase Order for `DEMO-WR-65SI2` (50 kg) against `TEST-SUP` | |
| PRC-03 | Submit PO for approval | |
| PRC-04 | Approve PO as a different user (verify segregation-of-duties: submitter cannot approve) | |
| PRC-05 | Send approved PO to supplier | |
| PRC-06 | Record GR against PO; confirm batch created in QUARANTINE status | |
| PRC-07 | Attempt to double-receive a closed PO — expect error | |

---

## Role 3 — Store Keeper / Warehouse

| ID | Test | Pass/Fail |
|----|------|-----------|
| STK-01 | View inventory balances for warehouse `MAIN-WH` | |
| STK-02 | Perform stock adjustment (< ₹10,000 variance) — no admin approval needed | |
| STK-03 | Perform stock adjustment (≥ ₹10,000 variance) — system requests admin approval | |
| STK-04 | Record opening balance for a new material (`TEST-RM-XX`, 100 kg AVAILABLE) | |
| STK-05 | Transfer stock between locations within MAIN-WH | |
| STK-06 | View material transaction ledger — entries are append-only (no edit button) | |

---

## Role 4 — QC Inspector (Incoming)

| ID | Test | Pass/Fail |
|----|------|-----------|
| QCI-01 | Open pending inspection list; find GR batch from PRC-06 | |
| QCI-02 | Accept batch (all quantity) — status → AVAILABLE, inventory_balances updated | |
| QCI-03 | Reject batch (partial) — rejected qty goes to scrap location | |
| QCI-04 | Put batch on HOLD — status → HOLD | |
| QCI-05 | Re-inspect a HOLD batch and accept it | |
| QCI-06 | Verify only QC-related endpoints accessible; production endpoints return 403 | |

---

## Role 5 — Production Supervisor

| ID | Test | Pass/Fail |
|----|------|-----------|
| PRD-01 | Create Production Order for `DEMO-CS-25X3` (50 pcs), select BOM | |
| PRD-02 | Issue material to production — batch allocated from AVAILABLE stock | |
| PRD-03 | Attempt to issue material with insufficient stock — expect error | |
| PRD-04 | Start operation COILING; record output qty | |
| PRD-05 | Complete all operations in sequence; verify WIP balance depleted | |
| PRD-06 | View production order history and operation audit trail | |

---

## Role 6 — QC Inspector (Final)

| ID | Test | Pass/Fail |
|----|------|-----------|
| QCF-01 | Open final inspection queue; find the production order from PRD-05 | |
| QCF-02 | Record inspection result: 48 pcs APPROVED, 2 pcs REJECTED | |
| QCF-03 | Verify FG batch created (48 pcs) in FG location | |
| QCF-04 | Verify rejected quantity posted to SCRAP location | |
| QCF-05 | Put an FG batch on HOLD; verify it cannot be reserved for a sales order | |
| QCF-06 | View FG batch material trace (source material batches shown) | |

---

## Role 7 — Sales Manager

| ID | Test | Pass/Fail |
|----|------|-----------|
| SAL-01 | Create customer `TEST-CUST`, save | |
| SAL-02 | Create Sales Order for `DEMO-CS-25X3` (30 pcs) against `TEST-CUST` | |
| SAL-03 | Confirm Sales Order — status → CONFIRMED | |
| SAL-04 | Reserve 30 pcs from available FG stock — reservation created | |
| SAL-05 | Attempt to reserve more than available FG stock — expect error | |
| SAL-06 | Create Dispatch against Sales Order; status → DISPATCHED | |
| SAL-07 | Verify dispatched items no longer appear in available FG balance | |

---

## Role 8 — Finance / Costing (read-only)

| ID | Test | Pass/Fail |
|----|------|-----------|
| FIN-01 | View inventory valuation report (FIFO cost per material) | |
| FIN-02 | View production cost summary for a closed order | |
| FIN-03 | Attempt to create a PO with the Finance role — expect 403 | |
| FIN-04 | Export inventory valuation to CSV or view in dashboard | |

---

## Role 9 — Insight / Reports (read-only)

| ID | Test | Pass/Fail |
|----|------|-----------|
| INS-01 | View Production Dashboard: monthly throughput chart loads | |
| INS-02 | View Inventory Value trend chart loads | |
| INS-03 | View active alerts list (LOW_STOCK, OVERDUE_PRODUCTION_ORDER) | |
| INS-04 | Dismiss an alert; verify it disappears from the active list | |
| INS-05 | Traceability search: enter FG batch number, see full trace tree | |
| INS-06 | Attempt to create an order with the Insight role — expect 403 | |

---

## Role 10 — Cross-cutting / Security

| ID | Test | Pass/Fail |
|----|------|-----------|
| SEC-01 | Access any API endpoint without Authorization header — expect 401 | |
| SEC-02 | Verify `Strict-Transport-Security` header present on all responses | |
| SEC-03 | Verify `X-XSS-Protection: 1; mode=block` header present | |
| SEC-04 | Verify `X-Content-Type-Options: nosniff` header present | |
| SEC-05 | Submit JWT signed with wrong secret — expect 401 | |
| SEC-06 | Use expired JWT — expect 401 | |
| SEC-07 | Confirm Swagger UI not accessible in production profile (`SWAGGER_ENABLED=false`) | |
| SEC-08 | Verify `/actuator/health` accessible without auth; `/actuator/prometheus` returns 401 from internet (internal only) | |
| SEC-09 | Confirm no passwords or secrets appear in any log output | |

---

## Completion sign-off

| Phase | Owner | Date | Signed |
|-------|-------|------|--------|
| Roles 1–3 | | | |
| Roles 4–6 | | | |
| Roles 7–9 | | | |
| Role 10 (Security) | | | |
| **Overall UAT PASSED** | | | |
