# QA Screen Inventory

**Total screens:** 165 | **Last updated:** 2026-11-01 (S24)

| # | Module | Screen | Route | Type | Permission | Status |
|---|--------|--------|-------|------|------------|--------|
| 1 | Auth | Sign In | /login | Form | — | PASS |
| 2 | Auth | Forgot Password | /forgot-password | Form | — | NOT_TESTED — requires email/token flow |
| 3 | Auth | Reset Password | /reset-password/:token | Form | — | NOT_TESTED — requires valid reset token |
| 4 | Auth | Change Password | /change-password | Form | signedIn | VALIDATION_TESTED (S15) — all 3 fields required; empty submit shows "Enter your current password"; live policy hints: 10+ chars/uppercase/lowercase/digit/special char/no-username update dynamically; confirm mismatch caught client-side; not submitted (would invalidate admin session and all demo data access) |
| 5 | Overview | Dashboard | /dashboard | Dashboard | DASHBOARD_VIEW | PASS |
| 6 | Overview | Notifications | /notifications | List | — | PASS — 4 notifications loaded; Mark All Read action; unreadOnly filter; GET /api/notifications confirmed; read-only list with action |
| 7 | Overview | Notification Preferences | /notifications/preferences | Config | — | PASS — toggle triggers PUT /api/notification-preferences → 200; preferences persisted; immediate update confirmed (S16) |
| 8 | Overview | Approval Inbox | /approvals | List | APPROVAL_DECIDE | PASS — GET /api/approvals/pending → 200; GET /api/approvals/history → 200; both tabs load; approve/reject actions present (S18) |
| 9 | Master Data | Materials List | /master/materials | List | MATERIAL_VIEW | PASS |
| 10 | Master Data | Suppliers List | /master/suppliers | List | SUPPLIER_MANAGE | PASS — create+edit confirmed; empty POST → 400; QA-2026-SUP-001 created; Approved Materials tab (S7) |
| 11 | Master Data | Customers List | /master/customers | List | CUSTOMER_MANAGE | PASS — create confirmed; empty POST → 400; QA-2026-CUST-001 created (S7) |
| 12 | Master Data | Products List | /master/products | List | PRODUCT_VIEW | PASS — create confirmed; empty POST → 400; QA-2026-PROD-001 created (S7) |
| 13 | Warehouses | Warehouse List | /warehouse/warehouses | List | INVENTORY_VIEW | PASS — QA-WH-001 created; empty POST → 400; expand shows locations (S7) |
| 14 | Inventory | Current Stock | /inventory/stock | List | INVENTORY_VIEW | PASS — 50 rows; warehouse/status filters confirmed; read-only display (S18) |
| 15 | Inventory | Stock Ledger | /inventory/ledger | List | INVENTORY_VIEW | PASS — GET /api/inventory/ledger paginated; type/date filters confirmed (S18) |
| 16 | Inventory | Batch Tracking | /inventory/batches | List | INVENTORY_VIEW | PASS — GET /api/inventory/batches 10 batches; status/expiry filters confirmed (S19) |
| 17 | Inventory | Stock Adjustments | /inventory/adjustments | List | INVENTORY_VIEW | PASS — ADJ/26/00003 created; empty POST → 400; countedQty > systemQty without batchId rejected; Approve/Reject available (S16) |
| 18 | Inventory | Valuation Report | /inventory/valuation-report | Report | VALUATION_VIEW | PASS — GET /api/inventory/valuation-report 37 lines; MOVING_AVERAGE; warehouse filter; DEMO-WR-65SI2 ₹1020 (S18) |
| 19 | Inventory | Scrap Lots | /inventory/scrap-lots | List | SCRAP_VIEW | PASS — GET /api/inventory/scrap-lots 200; list+sell/dispose actions present; 0 records (none scrap-posted yet — inherent limitation) (S19) |
| 20 | Procurement | Purchase Orders | /procurement/purchase-orders | List | PURCHASE_VIEW | PASS — PUR/26/00038 created; empty POST → 400; DRAFT status; 1 line 500 KG @ 285 INR + 18% GST (S7) |
| 21 | Procurement | Purchase Requisitions | /procurement/requisitions | List | PURCHASE_VIEW | PASS — PR/26/00002 created; empty POST → 400; DEMO-WR-65SI2 250 KG needed 15/12/2026 (S7) |
| 22 | Procurement | Goods Receipts | /procurement/goods-receipts | List | INVENTORY_RECEIVE | DIALOG_TESTED (S14) — Receive Goods dialog verified; PO dropdown correctly shows "No approved purchase order" when all POs received; form fields: PO select, Receipt Date, Warehouse select, Supplier Challan No., Vehicle No. all visible; full GR creation blocked by SoD (admin submitted QA PO, needs different user to approve) |
| 23 | Procurement | RFQs | /procurement/rfqs | List | PASS — QA-RFQ-2026-001 created; empty POST → 400; SENT via /rfqs/1/send; GET confirms SENT status (S21) |
| 24 | Procurement | Purchase Invoices | /procurement/purchase-invoices | List | AP_MANAGE | PASS — GET /api/purchase-invoices 200; table columns present; 0 records (blocked by SoD on GR approval — inherent limitation) (S18) |
| 25 | Procurement | Purchase Returns | /procurement/purchase-returns | List | PASS — QA-PR-2026-001 created; empty POST → 400; QUALITY_REJECTION reason; POST → 201 DRAFT (S21) |
| 26 | Procurement | Purchase Return Challans | /procurement/purchase-return-challans | List | PRC_CHALLAN_VIEW | PASS — GET /api/procurement/purchase-return-challans 200; list renders; 0 challans (none dispatched yet) (S19) |
| 27 | Engineering | Bill of Materials | /engineering/bom | List | PASS — BOM/26/00004 created; empty POST → 400; QA-2026-PROD-001 + DEMO-WR-65SI2 0.045 KG/pc, Rev A, DRAFT (S14) |
| 28 | Engineering | Product Routing | /engineering/routing | Detail/Editor | PRODUCT_VIEW | PASS — GET /api/products/1/routing 3 routing steps loaded; read-only routing display (S19) |
| 29 | Production | Production Orders | /production/orders | List | PASS — PO/26/00017 DRAFT created; empty POST → 400; DEMO-CS-25X3 Qty=100; BOM auto-linked (S7) |
| 30 | Production | My Work | /production/my-work | List/Action | PRODUCTION_EXECUTE | PASS — GET /api/production/my-work 200; operator tablet view loads; start/pause/complete actions available (S18) |
| 31 | Production | OEE Dashboard | /production/oee | Dashboard | OEE_VIEW | PASS — 1 machine; 30 trend points; ALL-machine summary; from/to/machine/category filters confirmed (S18) |
| 32 | Production | Production Schedule | /production/schedule | Dashboard | SCHEDULE_VIEW | PASS — GET /api/scheduling/gantt Gantt DTO with machines/bars (S19) |
| 33 | Production | Barcode Labels | /production/barcodes | List | PASS — label id=1 created; empty POST → 400; MATERIAL type, qrData=QA-BARCODE-DEMO-WR-65SI2; GET confirms 1 label (S21) |
| 34 | Production | Cost Roll-up | /production/cost-rollup | Report | PASS — POST /api/production/cost-rollup/run → 200 (trigger endpoint); 4 materials rolled up; log entries visible (S20) |
| 35 | Production | Job Work Orders | /production/job-work-orders | List | PASS — JWO/26/00001 created; empty POST → 400 (fix: @NotNull vendorId); AMC kind, 50 KG DEMO-WR-65SI2, charges=5000 (S20) |
| 36 | Production | Service Orders | /production/service-orders | List | PASS — SVO/26/00002 created; empty POST → 400 (fix: @NotBlank serviceKind); AMC, vendor 1, charge=15000 (S20) |
| 37 | Production | Plan Board | /production/plan-board | Dashboard | PLAN_BOARD_VIEW | PASS — GET /api/production/plan-board 200; read-only dashboard (S18) |
| 38 | Production | Capacity Board | /production/capacity-board | Dashboard | CAPACITY_VIEW | PASS — GET /api/production/capacity-board 200; auto-loads current month; read-only dashboard (S18) |
| 39 | Production | Order Templates | /production/templates | List | PASS — QA-TMPL-001 created; empty POST → 400; productId=1, plannedQty=100, leadTimeDays=3; edit/delete icons (S16) |
| 40 | Quality | Inspections List | /quality/inspections | List | QUALITY_VIEW | PASS — GET /api/quality/inspections 26 rows; type/status/operation filters confirmed (S18) |
| 41 | Quality | Inspection Detail | /quality/inspections/:id | Detail | QUALITY_VIEW | PASS — GET /api/quality/inspections/1 detail (id/type/status/items) returned (S19) |
| 42 | Quality | FG Batches | /quality/fg-batches | List | QUALITY_VIEW | PASS — GET /api/quality/fg-batches paginated; list-paginator present (S18) |
| 43 | Quality | Rejection Analysis | /quality/rejections | Dashboard | QUALITY_VIEW | PASS — GET /api/rejection-reasons/pareto 1 entry; pareto columns; read-only dashboard (S18) |
| 44 | Quality | SPC Charts List | /quality/spc | List | PASS — chart id=1 "Outer Diameter" created; empty POST → 400; XBAR_R, subgroupSize=5, usl/lsl confirmed (S20) |
| 45 | Quality | SPC Chart Detail | /quality/spc/:id | Detail | PASS — chart id=1 detail; 5 data points added; UCL/LCL limits visible; chart renders fully (S20) |
| 46 | Quality | CAPA List | /quality/capa | List | QUALITY_VIEW | PASS — CAPA/26/00001 created; list confirmed; filters/actions present (S9) |
| 47 | Quality | CAPA New | /quality/capa/new | Form | CAPA_MANAGE | PASS — form submitted INTERNAL_AUDIT source; CAPA/26/00001 created (S9) |
| 48 | Quality | CAPA Detail | /quality/capa/:id | Detail | QUALITY_VIEW | PASS — detail renders with status and actions (S9) |
| 49 | Quality | Complaints List | /quality/complaints | List | QUALITY_VIEW | PASS — CMP/26/00003 created; list confirmed (S9) |
| 50 | Quality | New Complaint | /quality/complaints/new | Form | COMPLAINT_MANAGE | PASS — form submitted: DIMENSIONAL category, customerId=1, productId=1 (S9) |
| 51 | Quality | Complaint Detail | /quality/complaints/:id | Detail | QUALITY_VIEW | PASS — detail renders with category and status (S9) |
| 52 | Quality | FAI List | /quality/fai | List | QUALITY_VIEW | PASS — FAI/26/00001 created; list confirmed (S9) |
| 53 | Quality | FAI Detail | /quality/fai/:id | Detail | QUALITY_VIEW | PASS — FAI detail renders; status PENDING (S9) |
| 54 | Sales | Sales Orders | /sales/orders | List | SALES_VIEW | PASS — 11 ORDERED orders; status filter; list-paginator confirmed (S18) |
| 55 | Sales | Dispatches | /sales/dispatches | List | DISPATCH_VIEW | PASS — GET /api/sales/dispatches paginated list; paged list confirmed (S18) |
| 56 | Sales | Quotations | /sales/quotations | List | PASS — QT/26/00002 created; empty POST → 400; SUBMITTED status; revision history dialog available (S20) |
| 57 | Sales | Order Confirmations | /sales/order-confirmations | List | PASS — OC/26/00001 created via quotation convert; DRAFT; 1 line 100 NOS @ ₹500 total ₹50,000 (S20) |
| 58 | Sales | Delivery Challans | /sales/delivery-challans | List | PASS — DC/26/00003 DRAFT created; empty POST → 400; DELIVERY kind, vehicleNo KA-01-QA-2026 (S20) |
| 59 | Insight | Alerts | /insight/alerts | List | ALERT_VIEW | PASS — 4 OPEN alerts; status+severity filters confirmed (S18) |
| 60 | Insight | Reports | /insight/reports | Report | REPORT_VIEW | PASS — 14 report types in select; from/to dates; run report form present (S18) |
| 61 | Insight | Traceability | /insight/traceability | Tool | TRACEABILITY_VIEW | PASS — MATERIAL type select; batch ID input; search confirmed (S18) |
| 62 | Maintenance | Maintenance List | /maintenance | List | MAINTENANCE_VIEW | PASS — Work Requests/PM Schedules tabs; status/type filters; GET /api/maintenance 200 (S17) |
| 63 | Maintenance | Maintenance Request Detail | /maintenance/requests/:id | Detail | MAINTENANCE_VIEW | PASS — GET /api/maintenance/requests 200; spare-parts endpoint confirmed (S19) |
| 64 | Maintenance | Spare Parts | /maintenance/spare-parts | List | PASS — QA-SP-PCS created; empty POST → 400; uom=PCS, minStock=5, reorderLevel=10; minStock≤reorderLevel rule enforced (S20) |
| 65 | Maintenance | Predictive Maintenance | /maintenance/predictive | Dashboard | PREDICTIVE_MAINT_VIEW | PASS — predictions/machine-risk/sensor-readings endpoints 200; 2 sensor readings for machine-1 (S19) |
| 66 | Finance | Finance Dashboard | /finance/dashboard | Dashboard | FINANCE_REPORT | PASS — GET /api/finance/dashboard 200; dashboard data returned (S19) |
| 67 | Finance | Journal Entries List | /finance/journals | List | GL_VIEW | PASS — 20 journals loaded incl. JNL/26/00068; GET /api/finance/journals 200 (S18) |
| 68 | Finance | New Journal Entry | /finance/journals/new | Form | GL_POST | PASS — JNL/26/00068 created Dr1201/Cr1101 500 INR; SUBMITTED; SoD enforced (S11) |
| 69 | Finance | Journal Detail | /finance/journals/:id | Detail | GL_VIEW | PASS — JNL/26/00068 SUBMITTED; 2 lines; Approve button visible (S18) |
| 70 | Finance | Fiscal Periods | /finance/periods | List | GL_VIEW | PASS — 12 OPEN periods APR-2026→MAR-2027; period dialog available (S18) |
| 71 | Finance | Accounts Payable | /finance/ap | List | AP_MANAGE | PASS — 5 OPEN AP invoices; ageing; match-override flow present (S18) |
| 72 | Finance | Accounts Receivable | /finance/ar | List | AR_MANAGE | PASS — GET /api/ar/invoices 200; ageing confirmed (S18) |
| 73 | Finance | AR Reconciliation | /finance/ar-reconciliation | Tool | AR_MANAGE | PASS — missing-invoices endpoint 200; Retry button present (S19) |
| 74 | Finance | Credit Notes | /finance/credit-notes | List | AR_MANAGE | PASS — CRN/26/00002 created; list + detail confirmed (S9) |
| 75 | Finance | Debit Notes | /finance/debit-notes | List | AP_MANAGE | PASS — GET /api/ap/debit-notes 200; creation via AP invoices confirmed (S17) |
| 76 | Finance | GST Tax | /finance/tax | Config | GL_VIEW | PASS — GET /api/finance/tax/slabs 4 slabs (GST0/5/12/18/28) (S19) |
| 77 | Finance | Exchange Rates | /finance/exchange-rates | List | PASS — USD=84.25 for JAN-2027 saved; PUT → 200; "USD saved for JAN-2027" toast confirmed (S11) |
| 78 | Finance | Budget vs Actual | /finance/budget | Report | GL_VIEW | PASS — GL accounts Budget=0/Actual; fiscal year + period dropdowns; GET /api/finance/budget 200 (S17) |
| 79 | Finance | Bank Reconciliation | /finance/bank | Tool | BANK_RECONCILE | PASS — component loads; 0 statements (none imported by design); signals present (S18) |
| 80 | Finance | FX Revaluation | /finance/fx-revaluation | Tool | FX_REVALUATION_RUN | PASS — run period 1 → 200; results loaded (S17) |
| 81 | Finance | GST Filing Reports | /finance/gst-filing | Report | GST_REPORT_VIEW | PASS — GSTR-1 b2b/b2c/docSummary/hsnSummary; GSTR-3B ITC ₹3600; filing methods present (S19) |
| 82 | Finance | Consolidation Report | /finance/consolidation | Report | CONSOLIDATION_VIEW | PASS — GET /api/finance/consolidation/report 200; report object returned (S19) |
| 83 | Finance | Financial Statements | /finance/financial-statements | Report | FINANCE_REPORT | PASS — P&L/Balance Sheet/Cash Flow tabs; all 3 load for period 1 (S18) |
| 84 | Finance | Sales Invoices | /finance/sales-invoices | List | SALES_INVOICE_VIEW | PASS — GET /api/ar/invoices 4 AR invoices loaded (S19) |
| 85 | Finance | Payments | /finance/payments | List | PAYMENT_VIEW | PASS — GET /api/finance/payments 200; list renders (S19) |
| 86 | Finance | Open Items | /finance/open-items | List | OPEN_ITEM_VIEW | PASS — GET /api/finance/open-items 200; list renders (S19) |
| 87 | Controlling | Cost Centres | /controlling/cost-centres | List | PASS — CC-QA-01 "QA Testing Centre" created; empty POST → 400; Labour/h=200; Deactivate button present (S12) |
| 88 | Controlling | Internal Orders | /controlling/internal-orders | List | INTERNAL_ORDER_VIEW | PASS — QA-IO-2026-01 created; list and detail verified (S9) |
| 89 | Controlling | Product Costing List | /controlling/cost-estimates | List | PASS — estimate id=1 created; empty POST → 400; productId=1, bomId=2, DRAFT; GET list confirms (S21) |
| 90 | Controlling | Cost Estimate Detail | /controlling/cost-estimates/:id | Detail | PASS — estimate id=1 detail loaded; DRAFT status; Release button visible (S21) |
| 91 | Controlling | Variance Analysis | /controlling/variances | Report | CONTROLLING_VIEW | PASS — GET /api/controlling/variances 200; paginated list (S18) |
| 92 | Controlling | Controlling Reports | /controlling/reports | Report | CONTROLLING_VIEW | PASS — GET /api/reports 14 codes; run endpoint confirmed (S19) |
| 93 | Controlling | Overhead Allocation | /controlling/overhead-allocation | Tool | OVERHEAD_ALLOC_VIEW | PASS — 2 rules; Run period 1 → COMPLETED; allocation lines populated (S16) |
| 94 | MRP | MRP Runs | /mrp/runs | List | MRP_VIEW | PASS — MRP run triggered; run id=1 in list confirmed (S9) |
| 95 | MRP | MRP Proposals | /mrp/proposals | List | MRP_VIEW | PASS — GET /api/mrp/proposals 200 (S18) |
| 96 | MRP | Demand Forecasts | /mrp/forecasts | List | PASS — forecast created; empty POST → 400; productId=1, qty=500, 31 Mar 2027; Delete button present (S15) |
| 97 | MRP | Planning Parameters | /mrp/planning | Config | PASS — param saved; PUT → 200; materialId=1, MTS/FIXED/500/7d; GET confirmed (S20) |
| 98 | Documents | Documents List | /documents | List | DOCUMENT_VIEW | PASS — GET /api/documents 1 document; category/status filters confirmed (S19) |
| 99 | Documents | Upload Document | /documents/new | Form | PASS — DOC/26/00001 uploaded (415 on JSON = correct, multipart required); DRAFT→REVIEW via Submit; SoD blocks own approval; SHA256 confirmed (S15) |
| 100 | Documents | All Documents | /documents/all | List | REPORT_VIEW | PASS — GET /api/documents/all 200; cross-document search confirmed (S19) |
| 101 | Documents | Document Detail | /documents/:id | Detail | DOCUMENT_VIEW | PASS — DOC/26/00001 detail: versions/download/actions; SoD on approve (S15) |
| 102 | Analytics | Analytical Dashboards | /analytics | Dashboard | ANALYTICS_VIEW | PASS — GET /api/analytics/catalogue 8 descriptors; auto-queries on load (S19) |
| 103 | Analytics | Analytics Explorer | /analytics/explorer | Tool | ANALYTICS_VIEW | PASS — GET /api/analytics/catalogue; query runs with date range (S19) |
| 104 | Analytics | Business Intelligence | /analytics/bi | Dashboard | BI_VIEW | PASS — GET /api/bi 200; 4 BI areas (sales/procurement/production/finance) (S18) |
| 105 | Analytics | Registers | /analytics/registers | Report | REGISTER_VIEW | PASS — GET /api/registers 14 codes; code selector triggers data load (S18) |
| 106 | Analytics | Report Builder | /analytics/report-builder | Tool | PASS — saved report id=1 created; empty POST → 400 (fix: @NotBlank name+sourceView; was 500 NPE); GET confirmed (S20) |
| 107 | Admin | Webhooks | /admin/webhooks | List | WEBHOOK_VIEW | DIALOG_TESTED (S15) — "+ New webhook" form revealed inline: Name, Target URL, event checkboxes (StockPosted, OrderCompleted, InvoicePaid); form filled (QA-WEBHOOK-TEST / localhost URL); server-side validation confirmed: "Webhook target refused: the host resolves to an address that is not publicly routable" — form functional, URL reachability validation working; full save not completed (no public URL available for QA test) |
| 108 | Admin | Data Import/Export | /admin/data | Tool | DATA_EXPORT | PASS — export endpoints confirmed; CSV import form; cancel per job (S19) |
| 109 | Admin | Notification Admin | /admin/notifications | Config | NOTIFICATION_MANAGE | PASS — 22 templates; 50 log entries; template edit + log view (S19) |
| 110 | PLM | ECO List | /plm/ecos | List | PLM_VIEW | PASS — ECO/26/00001 created; list confirmed (S10) |
| 111 | PLM | New ECO | /plm/ecos/new | Form | ECO_CREATE | PASS — DEF-006 fixed; ECO/26/00001 created; regression spec added (S10) |
| 112 | PLM | ECO Detail | /plm/ecos/:id | Detail | PLM_VIEW | PASS — detail with affected items and actions (Submit/Cancel) (S10) |
| 113 | PLM | Product Lifecycle | /plm/lifecycle | List | PLM_VIEW | PASS — GET /api/product-lifecycle 2 events; status/product filters (S19) |
| 114 | Admin | Users | /admin/users | List | PASS — qa.testuser created; empty POST → 400; count 23→24; all policy checks green (S12) |
| 115 | Admin | Plants | /admin/plants | List | PASS — QA-PLT-01 "QA Test Plant" created; empty POST → 400; snackbar confirmed; 5 plants; edit icon (S16) |
| 116 | Admin | Roles | /admin/roles | List | ROLE_MANAGE | PASS — seeded roles list; ADMIN permissions dialog 289 permissions by module (S17) |
| 117 | Admin | Audit Logs | /admin/audit-logs | List | AUDIT_VIEW | PASS — 6 entries; entity/action filters confirmed (S17) |
| 118 | Admin | System Settings | /admin/settings | Config | PASS — 107 rows; inline edit auth.lockout.max_attempts=5 saved (S16) |
| 119 | Admin | Modules | /admin/modules | Config | MODULE_MANAGE | PASS — 8 module toggles all active (S17) |
| 120 | Admin | Attribute Definitions | /admin/attribute-definitions | List | ATTRIBUTE_MANAGE | PASS — 32 definitions; filterType/dataType filters; inline edit (S18) |
| 121 | Admin | Negative Stock Overrides | /admin/negative-stock-overrides | Config | INVENTORY_NEGATIVE_OVERRIDE | PASS — GET 200; Materials/Products tabs; reason dialog on toggle (S18) |
| 122 | Admin | Item Categories | /admin/item-categories | List | PASS — QA-CAT-001 created; empty POST → 400; BOTH type; table row confirmed (S14) |
| 123 | Admin | Document Series | /admin/document-series | List | PASS — QA_TEST_DOC series created; empty POST → 400; format QA-{FY}-{00000}; FISCAL_YEAR reset (S14) |
| 124 | Admin | Company Profile | /admin/company-profile | Form | PASS — all 9 fields saved; empty PUT → 409 (acceptable DB constraint); "Profile saved" snackbar; PUT → 200 (S15) |
| 125 | Admin | Payment Terms | /admin/payment-terms | List | PASS — QA-NET-45 created; empty POST → 400; DEF-007 fixed; regression spec added (S11/S12) |
| 126 | Admin | Transporters | /admin/transporters | List | PASS — QA-TRP-001 created; empty POST → 400; DEF-008 fixed; regression spec added (S13) |
| 127 | Admin | Teams | /admin/teams | List | PASS — QA-TEAM-01 created; empty POST → 400 (fix: null guards in TeamService; was 500 NPE); DEF-009 fixed (S13/S14) |
| 128 | Admin | Approval Rules | /admin/approval-rules | List | PASS — QA PO Approval Rule created; empty POST → 400; expansion panel in list confirmed (S14) |
| 129 | Admin | Data Imports | /admin/imports | Tool | DATA_IMPORT | PASS — GET /api/import 200; CSV upload form for Materials/Products/Suppliers/Stock; cancel button (S19) |
| 130 | Admin | Setup Wizard | /admin/onboarding | Wizard | SETTINGS_MANAGE | PASS — GET /api/settings/onboarding 200; POST complete + DELETE reset confirmed (S19) |
| 131 | CRM | Leads List | /crm/leads | List | PASS — LD/26/00004 created; empty POST → 400; Trade Show source, Est. value 1,50,000; list confirmed (S12) |
| 132 | CRM | Lead Detail | /crm/leads/:id | Detail | CRM_VIEW | PASS — GET /api/crm/leads/1 detail; qualify/disqualify/convert actions (S19) |
| 133 | CRM | Opportunities | /crm/opportunities | Dashboard/Kanban | CRM_VIEW | PASS — OPP/26/00001 created; Kanban board confirmed (S9) |
| 134 | CRM | Quotations List | /crm/quotations | List | PASS — QT/26/00001 created; QA Industries Ltd, 1×DEMO-CS-25X3 @ 500; list confirmed (S13) |
| 135 | CRM | New Quotation | /crm/quotations/new | Form | PASS — customer mat-select, product line added, unit price 500; total 500.00; save → list (S13) |
| 136 | CRM | Edit Quotation | /crm/quotations/:id/edit | Form | PASS — QT/26/00001 edited; notes added; save → list confirming update (S13) |
| 137 | CRM | Contacts | /crm/contacts | List | PASS — QA Test Contact created; empty POST → 400; qa.contact@qaindustries.com; POST → 201 (S17) |
| 138 | CRM | Customer Activity | /crm/activities | List | PASS — MEETING activity logged; empty POST → 400; 60 min duration; POST → 201 (S17) |
| 139 | CRM | Customer Segments | /crm/customers | List | CRM_VIEW | PASS — GET /api/crm/tags 12 tag groups; setCustomerTags action works (S19) |
| 140 | CRM | Price Lists | /crm/prices | List | PASS — price id=1 created; empty POST → 400; customerId=1, productId=1, unitPrice=480 INR; POST → 201 (S21) |
| 141 | CRM | Sales Pipeline | /crm/pipeline | Dashboard | CRM_VIEW | PASS — GET /api/reports/CRM_PIPELINE 200; stage funnel data returned (S19) |
| 142 | HRM | Employees List | /hrm/employees | List | PASS — EMP/26/00003 "QA Employee2026" created; empty POST → 400; QA Analyst, Basic=35000; detail confirmed (S12) |
| 143 | HRM | Employee Detail | /hrm/employees/:id | Detail | HR_VIEW | PASS — GET /api/hrm/employees/1 with skills/shifts/attendance; training history (S19) |
| 144 | HRM | Attendance | /hrm/attendance | List | PASS — PRESENT logged for EMP/26/00002; empty POST → 400; check-in 09:00/check-out 17:30; POST → 200 (S17) |
| 145 | HRM | Shifts | /hrm/shifts | List | HR_VIEW | PASS — GET /api/hrm/shifts 3 shifts; list with add/edit (S19) |
| 146 | HRM | Leave | /hrm/leave | List | LEAVE_APPLY | PASS — leave types/balances load; 404 for admin w/no employee link is by design; GET /api/hrm/leave/types 200 (S17) |
| 147 | HRM | Training Renewals | /hrm/training | List | HR_VIEW | PASS — renewals-due endpoint 200; training history per employee (S19) |
| 148 | HRM | Payroll Runs List | /hrm/payroll | List | PAYROLL_RUN | PASS — PAY/26/00001 created; list confirmed (S9) |
| 149 | HRM | Payroll Run Detail | /hrm/payroll/:id | Detail | PAYROLL_RUN | PASS — detail renders with pay slips and totals (S9) |
| 150 | HRM | Pay Slip | /hrm/payroll/slips/:id | Detail | PAYROLL_RUN | PASS — run list; slips; slip detail with earnings/deductions (S19) |
| 151 | WMS | Zones & Bins | /wms/bins | List | WMS_VIEW | PASS — QAZONE001 and QA-BIN-A1 created; list confirmed (S9) |
| 152 | WMS | Bin View | /wms/bin-view | Dashboard | WMS_VIEW | PASS — GET /api/wms/bins/1/stock 1 item; warehouse/zone/bin drill-down (S19) |
| 153 | WMS | Put-away Tasks | /wms/putaway | List | WMS_VIEW | PASS — GET transfer-orders?movementType=PUTAWAY; 5 results; confirm-pick action (S19) |
| 154 | WMS | Picking List | /wms/picking | List | WMS_VIEW | PASS — GET transfer-orders?movementType=PICK; pick/confirm actions present (S19) |
| 155 | WMS | Internal Transfers | /wms/transfers | List | WMS_VIEW | PASS — GET /api/wms/transfer-orders 5 transfers; status/movementType filters (S19) |
| 156 | WMS | Cross-dock | /wms/cross-dock | List | WMS_VIEW | PASS — cross-dock form GR + target (SO/Prod/Transfer); POST /api/wms/cross-dock confirmed (S19) |
| 157 | WMS | Transfer Order Detail | /wms/orders/:id | Detail | WMS_VIEW | PASS — GET /api/wms/transfer-orders/1 detail with lines; confirm per line (S19) |
| 158 | WMS | Pick Waves | /wms/waves | List | PASS — wave id=1 CREATED from SO id=2; empty POST → 400; POST → 201; GET confirms CREATED (S21) |
| 159 | WMS | Cycle Counts List | /wms/cycle-counts | List | PASS — ABC class A cycle count for Main Warehouse created; empty POST → 400; POST → 201 (S17) |
| 160 | WMS | Cycle Count Detail | /wms/cycle-counts/:id | Detail | WMS_VIEW | PASS — detail after count creation; GET /api/wms/cycle-counts/1 200 (S17) |
| 161 | Integration | GST E-Invoice | /integration/gst-einvoice | Tool | GST_EINVOICE_VIEW | PASS — dispatchId=4 → generated 200; GENERATED status; dispatchId required validation (S16) |
| 162 | Integration | Integration Call Log | /integration/call-log | List | INTEGRATION_MANAGE | PASS — GET /api/integration/calls 200; status/type filters (S19) |
| 163 | Integration | Tally Ledger Mappings | /integration/tally-ledger-mappings | List | PASS — mapping created GL 4001; Tally Ledger "QA Sales Revenue Ledger"; voucher queue chips present; Readiness Check button (S15) |
| 164 | Integration | Shipment Tracking | /integration/shipment-tracking | List | PASS — tracking id=1 registered; dispatchId=1, DHL, QA-AWB-2026-001; POST → 200 PENDING (S21) |
| 165 | Integration | Busy Export | /integration/busy-export | Tool | PASS — POST /api/integration/busy-export/stage → 200 (trigger endpoint); 64 journal lines queued (S21) |
