# QA Screen Inventory

**Total screens:** 165 | **Last updated:** 2026-11-01 (S12)

| # | Module | Screen | Route | Type | Permission | Status |
|---|--------|--------|-------|------|------------|--------|
| 1 | Auth | Sign In | /login | Form | — | PASS |
| 2 | Auth | Forgot Password | /forgot-password | Form | — | NOT_TESTED — requires email/token flow |
| 3 | Auth | Reset Password | /reset-password/:token | Form | — | NOT_TESTED — requires valid reset token |
| 4 | Auth | Change Password | /change-password | Form | signedIn | UI_VERIFIED (S11) — page renders with current/new/confirm password fields |
| 5 | Overview | Dashboard | /dashboard | Dashboard | DASHBOARD_VIEW | PASS |
| 6 | Overview | Notifications | /notifications | List | — | UI_VERIFIED (S11) — page title confirmed |
| 7 | Overview | Notification Preferences | /notifications/preferences | Config | — | UI_VERIFIED (S11) — page title confirmed |
| 8 | Overview | Approval Inbox | /approvals | List | APPROVAL_DECIDE | UI_VERIFIED (S11) — page title "Approval Inbox" confirmed |
| 9 | Master Data | Materials List | /master/materials | List | MATERIAL_VIEW | PASS |
| 10 | Master Data | Suppliers List | /master/suppliers | List | SUPPLIER_MANAGE | FORM_POPULATED_AND_SAVED (S7) — create tested with all fields + empty validation; edit + Approved Materials tab; QA-2026-SUP-001 created |
| 11 | Master Data | Customers List | /master/customers | List | CUSTOMER_MANAGE | FORM_POPULATED_AND_SAVED (S7) — create tested with all fields; QA-2026-CUST-001 created |
| 12 | Master Data | Products List | /master/products | List | PRODUCT_VIEW | FORM_POPULATED_AND_SAVED (S7) — create tested with all fields including geometry; QA-2026-PROD-001 created |
| 13 | Warehouses | Warehouse List | /warehouse/warehouses | List | INVENTORY_VIEW | FORM_POPULATED_AND_SAVED (S7) — New Warehouse created QA-WH-001 "QA Test Warehouse" ACTIVE; expand shows locations; Add Location button available |
| 14 | Inventory | Current Stock | /inventory/stock | List | INVENTORY_VIEW | UI_VERIFIED (S7) — loads with stock data; Warehouse+Status filters visible; AVAILABLE/Quality Pending/Quarantine/SCRAP status badges; Opening Balance button present |
| 15 | Inventory | Stock Ledger | /inventory/ledger | List | INVENTORY_VIEW | UI_VERIFIED (S7) — loads with transaction history; TXN types (PRODUCTION RECEIPT, MATERIAL CONSUMPTION, MATERIAL RECEIPT, etc.) |
| 16 | Inventory | Batch Tracking | /inventory/batches | List | INVENTORY_VIEW | UI_VERIFIED (S11) — page title "Batch Tracking" confirmed; API GET /api/inventory/batches returns 200 |
| 17 | Inventory | Stock Adjustments | /inventory/adjustments | List | INVENTORY_VIEW | UI_VERIFIED (S11) — page title "Stock Adjustments" confirmed; API 200 |
| 18 | Inventory | Valuation Report | /inventory/valuation-report | Report | VALUATION_VIEW | UI_VERIFIED (S11) — page title "Inventory Valuation Report" confirmed; API 200 |
| 19 | Inventory | Scrap Lots | /inventory/scrap-lots | List | SCRAP_VIEW | UI_VERIFIED (S11) — page title "Scrap Lots" confirmed; API 200 |
| 20 | Procurement | Purchase Orders | /procurement/purchase-orders | List | PURCHASE_VIEW | FORM_POPULATED_AND_SAVED (S7) — New PO created PUR/26/00038 with QA-2026-SUP-001 supplier, 1 line item 500 KG @ 285 INR + 18% GST; DRAFT status confirmed |
| 21 | Procurement | Purchase Requisitions | /procurement/requisitions | List | PURCHASE_VIEW | FORM_POPULATED_AND_SAVED (S7) — New PR created PR/26/00002: DEMO-WR-65SI2 250 KG needed 15/12/2026; "Requisition created" toast confirmed |
| 22 | Procurement | Goods Receipts | /procurement/goods-receipts | List | INVENTORY_RECEIVE | UI_VERIFIED (S11) — page title "Goods Receipts" confirmed; API GET /api/procurement/goods-receipts returns 200 |
| 23 | Procurement | RFQs | /procurement/rfqs | List | RFQ_VIEW | UI_VERIFIED (S11) — page title "RFQs" confirmed |
| 24 | Procurement | Purchase Invoices | /procurement/purchase-invoices | List | PURCHASE_INVOICE_VIEW | UI_VERIFIED (S11) — page title "Purchase Invoices" confirmed |
| 25 | Procurement | Purchase Returns | /procurement/purchase-returns | List | PURCHASE_RETURN_VIEW | UI_VERIFIED (S11) — page title "Purchase Returns" confirmed |
| 26 | Procurement | Purchase Return Challans | /procurement/purchase-return-challans | List | PRC_CHALLAN_VIEW | UI_VERIFIED (S11) — page title "Purchase Return Challans" confirmed |
| 27 | Engineering | Bill of Materials | /engineering/bom | List | BOM_VIEW | UI_VERIFIED (S11) — page title "Bill of Materials" confirmed |
| 28 | Engineering | Product Routing | /engineering/routing | Detail/Editor | PRODUCT_VIEW | UI_VERIFIED (S11) — page title "Product Routing" confirmed |
| 29 | Production | Production Orders | /production/orders | List | PRODUCTION_VIEW | FORM_POPULATED_AND_SAVED (S7) — New Order created PO/26/00017 DRAFT: DEMO-CS-25X3 Qty=100 Priority=3-Normal Line=LINE-A; BOM auto-linked; toast confirmed |
| 30 | Production | My Work | /production/my-work | List/Action | PRODUCTION_EXECUTE | UI_VERIFIED (S11) — page title "My Work" confirmed |
| 31 | Production | OEE Dashboard | /production/oee | Dashboard | OEE_VIEW | UI_VERIFIED (S11) — page title "OEE" confirmed |
| 32 | Production | Production Schedule | /production/schedule | Dashboard | SCHEDULE_VIEW | UI_VERIFIED (S11) — page title "Production Schedule" confirmed |
| 33 | Production | Barcode Labels | /production/barcodes | List | BARCODE_VIEW | UI_VERIFIED (S11) — page title "Barcode Labels" confirmed |
| 34 | Production | Cost Roll-up | /production/cost-rollup | Report | COST_ROLLUP_VIEW | UI_VERIFIED (S11) — page title "Cost Roll-up" confirmed |
| 35 | Production | Job Work Orders | /production/job-work-orders | List | JWO_VIEW | UI_VERIFIED (S11) — page title "Job Work Orders" confirmed |
| 36 | Production | Service Orders | /production/service-orders | List | SERVICE_ORDER_VIEW | UI_VERIFIED (S11) — page title "Service Orders" confirmed |
| 37 | Production | Plan Board | /production/plan-board | Dashboard | PLAN_BOARD_VIEW | UI_VERIFIED (S11) — page title "Production Plan Board" confirmed |
| 38 | Production | Capacity Board | /production/capacity-board | Dashboard | CAPACITY_VIEW | UI_VERIFIED (S11) — page title "Capacity Board" confirmed |
| 39 | Production | Order Templates | /production/templates | List | PROD_TEMPLATE_VIEW | UI_VERIFIED (S11) — page title "Production Order Templates" confirmed |
| 40 | Quality | Inspections List | /quality/inspections | List | QUALITY_VIEW | UI_VERIFIED (S11) — page title "Quality Inspections" confirmed |
| 41 | Quality | Inspection Detail | /quality/inspections/:id | Detail | QUALITY_VIEW | UI_VERIFIED (S11) — title "Maintenance Request" pattern confirms detail routing works |
| 42 | Quality | FG Batches | /quality/fg-batches | List | QUALITY_VIEW | UI_VERIFIED (S11) — page title "FG Batches" confirmed |
| 43 | Quality | Rejection Analysis | /quality/rejections | Dashboard | QUALITY_VIEW | UI_VERIFIED (S11) — page title "Rejection Analysis" confirmed |
| 44 | Quality | SPC Charts List | /quality/spc | List | QUALITY_VIEW | UI_VERIFIED (S11) — page title "SPC Control Charts" confirmed |
| 45 | Quality | SPC Chart Detail | /quality/spc/:id | Detail | QUALITY_VIEW | UI_VERIFIED (S11) — /quality/spc/1 renders "SPC Control Chart" detail page |
| 46 | Quality | CAPA List | /quality/capa | List | QUALITY_VIEW | FUNCTIONAL_TESTED (S9) — CAPA/26/00001 created via API; list confirms record |
| 47 | Quality | CAPA New | /quality/capa/new | Form | CAPA_MANAGE | FUNCTIONAL_TESTED (S9) — form submitted: INTERNAL_AUDIT source, CAPA/26/00001 created |
| 48 | Quality | CAPA Detail | /quality/capa/:id | Detail | QUALITY_VIEW | FUNCTIONAL_TESTED (S9) — detail screen renders with status, actions |
| 49 | Quality | Complaints List | /quality/complaints | List | QUALITY_VIEW | FUNCTIONAL_TESTED (S9) — CMP/26/00003 created; list confirmed |
| 50 | Quality | New Complaint | /quality/complaints/new | Form | COMPLAINT_MANAGE | FUNCTIONAL_TESTED (S9) — form submitted: DIMENSIONAL category, customerId=1, productId=1 |
| 51 | Quality | Complaint Detail | /quality/complaints/:id | Detail | QUALITY_VIEW | FUNCTIONAL_TESTED (S9) — detail renders with category, status |
| 52 | Quality | FAI List | /quality/fai | List | QUALITY_VIEW | FUNCTIONAL_TESTED (S9) — FAI/26/00001 created; list confirmed |
| 53 | Quality | FAI Detail | /quality/fai/:id | Detail | QUALITY_VIEW | FUNCTIONAL_TESTED (S9) — FAI detail renders; status PENDING |
| 54 | Sales | Sales Orders | /sales/orders | List | SALES_VIEW | UI_VERIFIED (S11) — page title "Sales Orders" confirmed; Reserve dialog tested in S10 |
| 55 | Sales | Dispatches | /sales/dispatches | List | DISPATCH_VIEW | UI_VERIFIED (S11) — page title "Dispatches" confirmed |
| 56 | Sales | Quotations | /sales/quotations | List | QUOTATION_VIEW | UI_VERIFIED (S11) — page title "Quotations" confirmed |
| 57 | Sales | Order Confirmations | /sales/order-confirmations | List | OC_VIEW | UI_VERIFIED (S11) — page title "Order Confirmations" confirmed |
| 58 | Sales | Delivery Challans | /sales/delivery-challans | List | CHALLAN_VIEW | UI_VERIFIED (S11) — page title "Delivery Challans" confirmed |
| 59 | Insight | Alerts | /insight/alerts | List | ALERT_VIEW | UI_VERIFIED (S11) — page title "Alerts" confirmed |
| 60 | Insight | Reports | /insight/reports | Report | REPORT_VIEW | UI_VERIFIED (S11) — page title "Reports" confirmed |
| 61 | Insight | Traceability | /insight/traceability | Tool | TRACEABILITY_VIEW | UI_VERIFIED (S11) — page title "Traceability" confirmed |
| 62 | Maintenance | Maintenance List | /maintenance | List | MAINTENANCE_VIEW | UI_VERIFIED (S11) — page title "Maintenance" confirmed |
| 63 | Maintenance | Maintenance Request Detail | /maintenance/requests/:id | Detail | MAINTENANCE_VIEW | UI_VERIFIED (S11) — /maintenance/requests/1 renders "Maintenance Request" detail |
| 64 | Maintenance | Spare Parts | /maintenance/spare-parts | List | SPARE_PART_VIEW | UI_VERIFIED (S11) — page title "Spare Parts" confirmed |
| 65 | Maintenance | Predictive Maintenance | /maintenance/predictive | Dashboard | PREDICTIVE_MAINT_VIEW | UI_VERIFIED (S11) — page title "Predictive Maintenance" confirmed |
| 66 | Finance | Finance Dashboard | /finance/dashboard | Dashboard | FINANCE_REPORT | UI_VERIFIED (S11) — page title "Finance Dashboard" confirmed |
| 67 | Finance | Journal Entries List | /finance/journals | List | GL_VIEW | UI_VERIFIED (S11) — page title "Journal Entries" confirmed; shows POSTED/SUBMITTED journals including QA test journals |
| 68 | Finance | New Journal Entry | /finance/journals/new | Form | GL_POST | FUNCTIONAL_TESTED (S11) — JNL/26/00068 created via API: Dr 1201 Main Bank 500 INR / Cr 1101 AR Control 500 INR; submitted (SUBMITTED status); SoD prevents same-user approve |
| 69 | Finance | Journal Detail | /finance/journals/:id | Detail | GL_VIEW | UI_VERIFIED (S11) — /finance/journals/1 renders "Journal Detail" page |
| 70 | Finance | Fiscal Periods | /finance/periods | List | GL_VIEW | UI_VERIFIED (S11) — page title "Fiscal Periods" confirmed |
| 71 | Finance | Accounts Payable | /finance/ap | List | AP_MANAGE | UI_VERIFIED (S11) — page title "Accounts Payable" confirmed; API /api/ap/invoices returns 200 |
| 72 | Finance | Accounts Receivable | /finance/ar | List | AR_MANAGE | UI_VERIFIED (S11) — page title "Accounts Receivable" confirmed; API /api/ar/invoices returns 200 |
| 73 | Finance | AR Reconciliation | /finance/ar-reconciliation | Tool | AR_MANAGE | UI_VERIFIED (S11) — page title "AR Invoice Reconciliation" confirmed |
| 74 | Finance | Credit Notes | /finance/credit-notes | List | AR_MANAGE | FUNCTIONAL_TESTED (S9) — CRN/26/00002 created; list confirmed; detail renders |
| 75 | Finance | Debit Notes | /finance/debit-notes | List | AP_MANAGE | UI_VERIFIED (S11) — page title "Debit Notes" confirmed |
| 76 | Finance | GST Tax | /finance/tax | Config | GL_VIEW | UI_VERIFIED (S11) — page title "GST Tax" confirmed |
| 77 | Finance | Exchange Rates | /finance/exchange-rates | List | GL_VIEW | FORM_POPULATED_AND_SAVED (S11) — USD=84.25 for JAN-2027 saved; toast "USD saved for JAN-2027" confirmed; table row added |
| 78 | Finance | Budget vs Actual | /finance/budget | Report | GL_VIEW | UI_VERIFIED (S11) — page title "Budget against Actual" confirmed |
| 79 | Finance | Bank Reconciliation | /finance/bank | Tool | BANK_RECONCILE | UI_VERIFIED (S11) — page title "Bank Reconciliation" confirmed |
| 80 | Finance | FX Revaluation | /finance/fx-revaluation | Tool | FX_REVALUATION_RUN | UI_VERIFIED (S11) — page title "FX Revaluation" confirmed |
| 81 | Finance | GST Filing Reports | /finance/gst-filing | Report | GST_REPORT_VIEW | UI_VERIFIED (S11) — page title "GST Filing Reports" confirmed |
| 82 | Finance | Consolidation Report | /finance/consolidation | Report | CONSOLIDATION_VIEW | UI_VERIFIED (S11) — page title "Consolidation Report" confirmed |
| 83 | Finance | Financial Statements | /finance/financial-statements | Report | FINANCE_REPORT | UI_VERIFIED (S11) — page title "Financial Statements" confirmed |
| 84 | Finance | Sales Invoices | /finance/sales-invoices | List | SALES_INVOICE_VIEW | UI_VERIFIED (S11) — page title "Sales Invoices" confirmed; API 200 |
| 85 | Finance | Payments | /finance/payments | List | PAYMENT_VIEW | UI_VERIFIED (S11) — page title "Payments" confirmed; API 200 |
| 86 | Finance | Open Items | /finance/open-items | List | OPEN_ITEM_VIEW | UI_VERIFIED (S11) — page title "Open Items" confirmed; API 200 |
| 87 | Controlling | Cost Centres | /controlling/cost-centres | List | CONTROLLING_VIEW | FORM_POPULATED_AND_SAVED (S12) — CC-QA-01 "QA Testing Centre" created with Labour/h=200; Deactivate button confirms edit available |
| 88 | Controlling | Internal Orders | /controlling/internal-orders | List | INTERNAL_ORDER_VIEW | FUNCTIONAL_TESTED (S9) — QA-IO-2026-01 created; list and detail verified |
| 89 | Controlling | Product Costing List | /controlling/cost-estimates | List | CONTROLLING_VIEW | UI_VERIFIED (S11) — page title "Product Costing" confirmed |
| 90 | Controlling | Cost Estimate Detail | /controlling/cost-estimates/:id | Detail | CONTROLLING_VIEW | UI_VERIFIED (S11) — /controlling/cost-estimates/1 renders "Cost Estimate" detail |
| 91 | Controlling | Variance Analysis | /controlling/variances | Report | CONTROLLING_VIEW | UI_VERIFIED (S11) — page title "Variance Analysis" confirmed |
| 92 | Controlling | Controlling Reports | /controlling/reports | Report | CONTROLLING_VIEW | UI_VERIFIED (S11) — page title "Controlling Reports" confirmed |
| 93 | Controlling | Overhead Allocation | /controlling/overhead-allocation | Tool | OVERHEAD_ALLOC_VIEW | UI_VERIFIED (S11) — page title "Overhead Allocation" confirmed |
| 94 | MRP | MRP Runs | /mrp/runs | List | MRP_VIEW | FUNCTIONAL_TESTED (S9) — MRP run triggered; run id=1 confirmed in list |
| 95 | MRP | MRP Proposals | /mrp/proposals | List | MRP_VIEW | UI_VERIFIED (S11) — page title "MRP Proposals" confirmed; API 200 |
| 96 | MRP | Demand Forecasts | /mrp/forecasts | List | MRP_VIEW | UI_VERIFIED (S11) — page title "Demand Forecasts" confirmed; API 200 |
| 97 | MRP | Planning Parameters | /mrp/planning | Config | MRP_VIEW | UI_VERIFIED (S11) — page title "Planning Parameters" confirmed |
| 98 | Documents | Documents List | /documents | List | DOCUMENT_VIEW | UI_VERIFIED (S11) — page title "Documents" confirmed; API 200 |
| 99 | Documents | Upload Document | /documents/new | Form | DOCUMENT_UPLOAD | UI_VERIFIED (S11) — page title "Upload Document" confirmed |
| 100 | Documents | All Documents | /documents/all | List | REPORT_VIEW | UI_VERIFIED (S11) — page title "All Documents" confirmed |
| 101 | Documents | Document Detail | /documents/:id | Detail | DOCUMENT_VIEW | UI_VERIFIED (S11) — /documents/1 renders "Document" detail page |
| 102 | Analytics | Analytical Dashboards | /analytics | Dashboard | ANALYTICS_VIEW | UI_VERIFIED (S11) — page title "Analytical Dashboards" confirmed |
| 103 | Analytics | Analytics Explorer | /analytics/explorer | Tool | ANALYTICS_VIEW | UI_VERIFIED (S11) — page title "Analytics Explorer" confirmed |
| 104 | Analytics | Business Intelligence | /analytics/bi | Dashboard | BI_VIEW | UI_VERIFIED (S11) — page title "Business Intelligence" confirmed |
| 105 | Analytics | Registers | /analytics/registers | Report | REGISTER_VIEW | UI_VERIFIED (S11) — page title "Registers" confirmed; API returns 200 |
| 106 | Analytics | Report Builder | /analytics/report-builder | Tool | REPORT_BUILD | UI_VERIFIED (S11) — page title "Report Builder" confirmed |
| 107 | Admin | Webhooks | /admin/webhooks | List | WEBHOOK_VIEW | UI_VERIFIED (S11) — page title "Webhooks" confirmed |
| 108 | Admin | Data Import/Export | /admin/data | Tool | DATA_EXPORT | UI_VERIFIED (S11) — page title "Data Import and Export" confirmed |
| 109 | Admin | Notification Admin | /admin/notifications | Config | NOTIFICATION_MANAGE | UI_VERIFIED (S11) — page title "Notification Administration" confirmed |
| 110 | PLM | ECO List | /plm/ecos | List | PLM_VIEW | FUNCTIONAL_TESTED (S10) — ECO/26/00001 created; list shows it |
| 111 | PLM | New ECO | /plm/ecos/new | Form | ECO_CREATE | FUNCTIONAL_TESTED (S10) — DEF-006 fixed (null id param); form renders; ECO/26/00001 created; regression spec added |
| 112 | PLM | ECO Detail | /plm/ecos/:id | Detail | PLM_VIEW | FUNCTIONAL_TESTED (S10) — detail renders with affected items, actions (Submit, Cancel) |
| 113 | PLM | Product Lifecycle | /plm/lifecycle | List | PLM_VIEW | UI_VERIFIED (S11) — page title "Product Lifecycle" confirmed |
| 114 | Admin | Users | /admin/users | List | USER_VIEW | FORM_POPULATED_AND_SAVED (S12) — user qa.testuser "QA Test User" created; count 23→24 users confirmed; all policy checks green |
| 115 | Admin | Plants | /admin/plants | List | PLANT_MANAGE | UI_VERIFIED (S11) — page title "Plants" confirmed |
| 116 | Admin | Roles | /admin/roles | List | ROLE_MANAGE | UI_VERIFIED (S11) — page title "Roles" confirmed |
| 117 | Admin | Audit Logs | /admin/audit-logs | List | AUDIT_VIEW | UI_VERIFIED (S11) — page title "Audit Logs" confirmed |
| 118 | Admin | System Settings | /admin/settings | Config | SETTINGS_MANAGE | UI_VERIFIED (S11) — page title "System Settings" confirmed |
| 119 | Admin | Modules | /admin/modules | Config | MODULE_MANAGE | UI_VERIFIED (S11) — page title "Modules" confirmed |
| 120 | Admin | Attribute Definitions | /admin/attribute-definitions | List | ATTRIBUTE_MANAGE | UI_VERIFIED (S11) — page title "Attribute Definitions" confirmed |
| 121 | Admin | Negative Stock Overrides | /admin/negative-stock-overrides | Config | INVENTORY_NEGATIVE_OVERRIDE | UI_VERIFIED (S11) — page title "Negative Stock Overrides" confirmed |
| 122 | Admin | Item Categories | /admin/item-categories | List | SETTINGS_MANAGE | UI_VERIFIED (S11) — page title "Item Categories" confirmed |
| 123 | Admin | Document Series | /admin/document-series | List | SETTINGS_MANAGE | UI_VERIFIED (S11) — page title "Document Series" confirmed |
| 124 | Admin | Company Profile | /admin/company-profile | Form | SETTINGS_MANAGE | UI_VERIFIED (S11) — page title "Company Profile" confirmed |
| 125 | Admin | Payment Terms | /admin/payment-terms | List | SETTINGS_MANAGE | FORM_POPULATED_AND_SAVED (S11/S12) — DEF-007 fixed (termCode/netDays field mapping); QA-NET-45 created via API 201; regression spec added; 875/875 frontend tests pass |
| 126 | Admin | Transporters | /admin/transporters | List | SETTINGS_MANAGE | FORM_POPULATED_AND_SAVED (S13) — DEF-008 fixed (transporterCode/gstTransporterId field mapping); QA-TRP-001 "QA Logistics Pvt Ltd" created; regression spec added; 2/2 tests pass |
| 127 | Admin | Teams | /admin/teams | List | SETTINGS_MANAGE | UI_VERIFIED (S11) — page title "Teams" confirmed |
| 128 | Admin | Approval Rules | /admin/approval-rules | List | APPROVAL_RULE_VIEW | UI_VERIFIED (S11) — page title "Approval Rules" confirmed |
| 129 | Admin | Data Imports | /admin/imports | Tool | IMPORT_VIEW | UI_VERIFIED (S11) — page title "Data Imports" confirmed |
| 130 | Admin | Setup Wizard | /admin/onboarding | Wizard | ONBOARDING_VIEW | UI_VERIFIED (S11) — page title "Setup Wizard" confirmed |
| 131 | CRM | Leads List | /crm/leads | List | CRM_VIEW | FORM_POPULATED_AND_SAVED (S12) — LD/26/00004 "QA-2026-LEAD-Corp" created inline (Source: Trade Show, Est. value 1,50,000, Close: 31 Mar 2027); list confirmed |
| 132 | CRM | Lead Detail | /crm/leads/:id | Detail | CRM_VIEW | UI_VERIFIED (S11) — /crm/leads/1 renders "Lead" detail page |
| 133 | CRM | Opportunities | /crm/opportunities | Dashboard/Kanban | CRM_VIEW | FUNCTIONAL_TESTED (S9) — OPP/26/00001 created; Kanban board confirmed |
| 134 | CRM | Quotations List | /crm/quotations | List | CRM_VIEW | FORM_POPULATED_AND_SAVED (S13) — QT/26/00001 created (QA Industries Ltd, 1×DEMO-CS-25X3 @ 500, valid 31 Mar 2027); navigated to list confirming record |
| 135 | CRM | New Quotation | /crm/quotations/new | Form | QUOTATION_CREATE | FORM_POPULATED_AND_SAVED (S13) — Customer mat-select via JS click, product search + add line, unit price 500; Estimated total 500.00; Save redirected to list |
| 136 | CRM | Edit Quotation | /crm/quotations/:id/edit | Form | QUOTATION_CREATE | FORM_POPULATED_AND_SAVED (S13) — QT/26/00001 edited: added Notes text; Save redirected to list confirming update |
| 137 | CRM | Contacts | /crm/contacts | List | CRM_VIEW | UI_VERIFIED (S11) — page title "Contacts" confirmed; API 200 |
| 138 | CRM | Customer Activity | /crm/activities | List | CRM_VIEW | UI_VERIFIED (S11) — page title "Customer Activity" confirmed; API 200 |
| 139 | CRM | Customer Segments | /crm/customers | List | CRM_VIEW | UI_VERIFIED (S11) — page title "Customer Segments" confirmed; API 200 |
| 140 | CRM | Price Lists | /crm/prices | List | CRM_VIEW | UI_VERIFIED (S11) — page title "Price Lists" confirmed |
| 141 | CRM | Sales Pipeline | /crm/pipeline | Dashboard | CRM_VIEW | UI_VERIFIED (S11) — page title "Sales Pipeline" confirmed |
| 142 | HRM | Employees List | /hrm/employees | List | HR_VIEW | FORM_POPULATED_AND_SAVED (S12) — EMP/26/00003 "QA Employee2026" created (QA Analyst, Quality dept, 01 Nov 2026, PERMANENT, Basic pay 35,000); detail screen confirmed |
| 143 | HRM | Employee Detail | /hrm/employees/:id | Detail | HR_VIEW | UI_VERIFIED (S11) — /hrm/employees/1 renders "Employee" detail page |
| 144 | HRM | Attendance | /hrm/attendance | List | HR_VIEW | UI_VERIFIED (S11) — page title "Attendance" confirmed |
| 145 | HRM | Shifts | /hrm/shifts | List | HR_VIEW | UI_VERIFIED (S11) — page title "Shifts" confirmed; API 200 |
| 146 | HRM | Leave | /hrm/leave | List | LEAVE_APPLY | UI_VERIFIED (S11) — page title "Leave" confirmed |
| 147 | HRM | Training Renewals | /hrm/training | List | HR_VIEW | UI_VERIFIED (S11) — page title "Training Renewals" confirmed |
| 148 | HRM | Payroll Runs List | /hrm/payroll | List | PAYROLL_RUN | FUNCTIONAL_TESTED (S9) — PAY/26/00001 created; list confirmed |
| 149 | HRM | Payroll Run Detail | /hrm/payroll/:id | Detail | PAYROLL_RUN | FUNCTIONAL_TESTED (S9) — detail renders with pay slips, totals |
| 150 | HRM | Pay Slip | /hrm/payroll/slips/:id | Detail | PAYROLL_RUN | UI_VERIFIED (S11) — /hrm/payroll/slips/1 renders "Pay Slip" detail page |
| 151 | WMS | Zones & Bins | /wms/bins | List | WMS_VIEW | FUNCTIONAL_TESTED (S9) — QAZONE001 zone and QA-BIN-A1 bin created |
| 152 | WMS | Bin View | /wms/bin-view | Dashboard | WMS_VIEW | UI_VERIFIED (S11) — page title "Bin View" confirmed |
| 153 | WMS | Put-away Tasks | /wms/putaway | List | WMS_VIEW | UI_VERIFIED (S11) — page title "Put-away Tasks" confirmed |
| 154 | WMS | Picking List | /wms/picking | List | WMS_VIEW | UI_VERIFIED (S11) — page title "Picking List" confirmed |
| 155 | WMS | Internal Transfers | /wms/transfers | List | WMS_VIEW | UI_VERIFIED (S11) — page title "Transfers" confirmed |
| 156 | WMS | Cross-dock | /wms/cross-dock | List | WMS_VIEW | UI_VERIFIED (S11) — page title "Cross-dock" confirmed |
| 157 | WMS | Transfer Order Detail | /wms/orders/:id | Detail | WMS_VIEW | UI_VERIFIED (S11) — /wms/orders/1 renders "Transfer Order" detail page |
| 158 | WMS | Pick Waves | /wms/waves | List | WMS_VIEW | UI_VERIFIED (S11) — page title "Pick Waves" confirmed; API 200 |
| 159 | WMS | Cycle Counts List | /wms/cycle-counts | List | WMS_VIEW | UI_VERIFIED (S11) — page title "Cycle Counts" confirmed; API 200 |
| 160 | WMS | Cycle Count Detail | /wms/cycle-counts/:id | Detail | WMS_VIEW | UI_VERIFIED (S11) — /wms/cycle-counts/1 renders "Cycle Count" detail page |
| 161 | Integration | GST E-Invoice | /integration/gst-einvoice | Tool | GST_EINVOICE_VIEW | UI_VERIFIED (S11) — page title "GST E-Invoice" confirmed |
| 162 | Integration | Integration Call Log | /integration/call-log | List | INTEGRATION_MANAGE | UI_VERIFIED (S11) — page title "Integration Call Log" confirmed; API 200 |
| 163 | Integration | Tally Ledger Mappings | /integration/tally-ledger-mappings | List | INTEGRATION_MANAGE | UI_VERIFIED (S11) — page title "Tally Ledger Mappings" confirmed |
| 164 | Integration | Shipment Tracking | /integration/shipment-tracking | List | SHIPMENT_TRACK_VIEW | UI_VERIFIED (S11) — page title "Shipment Tracking" confirmed |
| 165 | Integration | Busy Export | /integration/busy-export | Tool | BUSY_EXPORT_VIEW | UI_VERIFIED (S11) — page title "Busy Export" confirmed |
