# QA Screen Inventory

**Total screens:** 165 | **Last updated:** 2026-10-30

| # | Module | Screen | Route | Type | Permission | Status |
|---|--------|--------|-------|------|------------|--------|
| 1 | Auth | Sign In | /login | Form | — | PASS |
| 2 | Auth | Forgot Password | /forgot-password | Form | — | NOT_TESTED |
| 3 | Auth | Reset Password | /reset-password/:token | Form | — | NOT_TESTED |
| 4 | Auth | Change Password | /change-password | Form | signedIn | NOT_TESTED |
| 5 | Overview | Dashboard | /dashboard | Dashboard | DASHBOARD_VIEW | PASS |
| 6 | Overview | Notifications | /notifications | List | — | NOT_TESTED |
| 7 | Overview | Notification Preferences | /notifications/preferences | Config | — | NOT_TESTED |
| 8 | Overview | Approval Inbox | /approvals | List | APPROVAL_DECIDE | NOT_TESTED |
| 9 | Master Data | Materials List | /master/materials | List | MATERIAL_VIEW | PASS |
| 10 | Master Data | Suppliers List | /master/suppliers | List | SUPPLIER_MANAGE | NOT_TESTED |
| 11 | Master Data | Customers List | /master/customers | List | CUSTOMER_MANAGE | NOT_TESTED |
| 12 | Master Data | Products List | /master/products | List | PRODUCT_VIEW | NOT_TESTED |
| 13 | Warehouses | Warehouse List | /warehouse/warehouses | List | INVENTORY_VIEW | NOT_TESTED |
| 14 | Inventory | Current Stock | /inventory/stock | List | INVENTORY_VIEW | NOT_TESTED |
| 15 | Inventory | Stock Ledger | /inventory/ledger | List | INVENTORY_VIEW | NOT_TESTED |
| 16 | Inventory | Batch Tracking | /inventory/batches | List | INVENTORY_VIEW | NOT_TESTED |
| 17 | Inventory | Stock Adjustments | /inventory/adjustments | List | INVENTORY_VIEW | NOT_TESTED |
| 18 | Inventory | Valuation Report | /inventory/valuation-report | Report | VALUATION_VIEW | NOT_TESTED |
| 19 | Inventory | Scrap Lots | /inventory/scrap-lots | List | SCRAP_VIEW | NOT_TESTED |
| 20 | Procurement | Purchase Orders | /procurement/purchase-orders | List | PURCHASE_VIEW | NOT_TESTED |
| 21 | Procurement | Purchase Requisitions | /procurement/requisitions | List | PURCHASE_VIEW | NOT_TESTED |
| 22 | Procurement | Goods Receipts | /procurement/goods-receipts | List | INVENTORY_RECEIVE | NOT_TESTED |
| 23 | Procurement | RFQs | /procurement/rfqs | List | RFQ_VIEW | NOT_TESTED |
| 24 | Procurement | Purchase Invoices | /procurement/purchase-invoices | List | PURCHASE_INVOICE_VIEW | NOT_TESTED |
| 25 | Procurement | Purchase Returns | /procurement/purchase-returns | List | PURCHASE_RETURN_VIEW | NOT_TESTED |
| 26 | Procurement | Purchase Return Challans | /procurement/purchase-return-challans | List | PRC_CHALLAN_VIEW | NOT_TESTED |
| 27 | Engineering | Bill of Materials | /engineering/bom | List | BOM_VIEW | NOT_TESTED |
| 28 | Engineering | Product Routing | /engineering/routing | Detail/Editor | PRODUCT_VIEW | NOT_TESTED |
| 29 | Production | Production Orders | /production/orders | List | PRODUCTION_VIEW | NOT_TESTED |
| 30 | Production | My Work | /production/my-work | List/Action | PRODUCTION_EXECUTE | NOT_TESTED |
| 31 | Production | OEE Dashboard | /production/oee | Dashboard | OEE_VIEW | NOT_TESTED |
| 32 | Production | Production Schedule | /production/schedule | Dashboard | SCHEDULE_VIEW | NOT_TESTED |
| 33 | Production | Barcode Labels | /production/barcodes | List | BARCODE_VIEW | NOT_TESTED |
| 34 | Production | Cost Roll-up | /production/cost-rollup | Report | COST_ROLLUP_VIEW | NOT_TESTED |
| 35 | Production | Job Work Orders | /production/job-work-orders | List | JWO_VIEW | NOT_TESTED |
| 36 | Production | Service Orders | /production/service-orders | List | SERVICE_ORDER_VIEW | NOT_TESTED |
| 37 | Production | Plan Board | /production/plan-board | Dashboard | PLAN_BOARD_VIEW | NOT_TESTED |
| 38 | Production | Capacity Board | /production/capacity-board | Dashboard | CAPACITY_VIEW | NOT_TESTED |
| 39 | Production | Order Templates | /production/templates | List | PROD_TEMPLATE_VIEW | NOT_TESTED |
| 40 | Quality | Inspections List | /quality/inspections | List | QUALITY_VIEW | NOT_TESTED |
| 41 | Quality | Inspection Detail | /quality/inspections/:id | Detail | QUALITY_VIEW | NOT_TESTED |
| 42 | Quality | FG Batches | /quality/fg-batches | List | QUALITY_VIEW | NOT_TESTED |
| 43 | Quality | Rejection Analysis | /quality/rejections | Dashboard | QUALITY_VIEW | NOT_TESTED |
| 44 | Quality | SPC Charts List | /quality/spc | List | QUALITY_VIEW | NOT_TESTED |
| 45 | Quality | SPC Chart Detail | /quality/spc/:id | Detail | QUALITY_VIEW | NOT_TESTED |
| 46 | Quality | CAPA List | /quality/capa | List | QUALITY_VIEW | NOT_TESTED |
| 47 | Quality | CAPA New | /quality/capa/new | Form | CAPA_MANAGE | NOT_TESTED |
| 48 | Quality | CAPA Detail | /quality/capa/:id | Detail | QUALITY_VIEW | NOT_TESTED |
| 49 | Quality | Complaints List | /quality/complaints | List | QUALITY_VIEW | NOT_TESTED |
| 50 | Quality | New Complaint | /quality/complaints/new | Form | COMPLAINT_MANAGE | NOT_TESTED |
| 51 | Quality | Complaint Detail | /quality/complaints/:id | Detail | QUALITY_VIEW | NOT_TESTED |
| 52 | Quality | FAI List | /quality/fai | List | QUALITY_VIEW | NOT_TESTED |
| 53 | Quality | FAI Detail | /quality/fai/:id | Detail | QUALITY_VIEW | NOT_TESTED |
| 54 | Sales | Sales Orders | /sales/orders | List | SALES_VIEW | NOT_TESTED |
| 55 | Sales | Dispatches | /sales/dispatches | List | DISPATCH_VIEW | NOT_TESTED |
| 56 | Sales | Quotations | /sales/quotations | List | QUOTATION_VIEW | NOT_TESTED |
| 57 | Sales | Order Confirmations | /sales/order-confirmations | List | OC_VIEW | NOT_TESTED |
| 58 | Sales | Delivery Challans | /sales/delivery-challans | List | CHALLAN_VIEW | NOT_TESTED |
| 59 | Insight | Alerts | /insight/alerts | List | ALERT_VIEW | NOT_TESTED |
| 60 | Insight | Reports | /insight/reports | Report | REPORT_VIEW | NOT_TESTED |
| 61 | Insight | Traceability | /insight/traceability | Tool | TRACEABILITY_VIEW | NOT_TESTED |
| 62 | Maintenance | Maintenance List | /maintenance | List | MAINTENANCE_VIEW | NOT_TESTED |
| 63 | Maintenance | Maintenance Request Detail | /maintenance/requests/:id | Detail | MAINTENANCE_VIEW | NOT_TESTED |
| 64 | Maintenance | Spare Parts | /maintenance/spare-parts | List | SPARE_PART_VIEW | NOT_TESTED |
| 65 | Maintenance | Predictive Maintenance | /maintenance/predictive | Dashboard | PREDICTIVE_MAINT_VIEW | NOT_TESTED |
| 66 | Finance | Finance Dashboard | /finance/dashboard | Dashboard | FINANCE_REPORT | NOT_TESTED |
| 67 | Finance | Journal Entries List | /finance/journals | List | GL_VIEW | NOT_TESTED |
| 68 | Finance | New Journal Entry | /finance/journals/new | Form | GL_POST | NOT_TESTED |
| 69 | Finance | Journal Detail | /finance/journals/:id | Detail | GL_VIEW | NOT_TESTED |
| 70 | Finance | Fiscal Periods | /finance/periods | List | GL_VIEW | NOT_TESTED |
| 71 | Finance | Accounts Payable | /finance/ap | List | AP_MANAGE | NOT_TESTED |
| 72 | Finance | Accounts Receivable | /finance/ar | List | AR_MANAGE | NOT_TESTED |
| 73 | Finance | AR Reconciliation | /finance/ar-reconciliation | Tool | AR_MANAGE | NOT_TESTED |
| 74 | Finance | Credit Notes | /finance/credit-notes | List | AR_MANAGE | NOT_TESTED |
| 75 | Finance | Debit Notes | /finance/debit-notes | List | AP_MANAGE | NOT_TESTED |
| 76 | Finance | GST Tax | /finance/tax | Config | GL_VIEW | NOT_TESTED |
| 77 | Finance | Exchange Rates | /finance/exchange-rates | List | GL_VIEW | NOT_TESTED |
| 78 | Finance | Budget vs Actual | /finance/budget | Report | GL_VIEW | NOT_TESTED |
| 79 | Finance | Bank Reconciliation | /finance/bank | Tool | BANK_RECONCILE | NOT_TESTED |
| 80 | Finance | FX Revaluation | /finance/fx-revaluation | Tool | FX_REVALUATION_RUN | NOT_TESTED |
| 81 | Finance | GST Filing Reports | /finance/gst-filing | Report | GST_REPORT_VIEW | NOT_TESTED |
| 82 | Finance | Consolidation Report | /finance/consolidation | Report | CONSOLIDATION_VIEW | NOT_TESTED |
| 83 | Finance | Financial Statements | /finance/financial-statements | Report | FINANCE_REPORT | NOT_TESTED |
| 84 | Finance | Sales Invoices | /finance/sales-invoices | List | SALES_INVOICE_VIEW | NOT_TESTED |
| 85 | Finance | Payments | /finance/payments | List | PAYMENT_VIEW | NOT_TESTED |
| 86 | Finance | Open Items | /finance/open-items | List | OPEN_ITEM_VIEW | NOT_TESTED |
| 87 | Controlling | Cost Centres | /controlling/cost-centres | List | CONTROLLING_VIEW | NOT_TESTED |
| 88 | Controlling | Internal Orders | /controlling/internal-orders | List | INTERNAL_ORDER_VIEW | NOT_TESTED |
| 89 | Controlling | Product Costing List | /controlling/cost-estimates | List | CONTROLLING_VIEW | NOT_TESTED |
| 90 | Controlling | Cost Estimate Detail | /controlling/cost-estimates/:id | Detail | CONTROLLING_VIEW | NOT_TESTED |
| 91 | Controlling | Variance Analysis | /controlling/variances | Report | CONTROLLING_VIEW | NOT_TESTED |
| 92 | Controlling | Controlling Reports | /controlling/reports | Report | CONTROLLING_VIEW | NOT_TESTED |
| 93 | Controlling | Overhead Allocation | /controlling/overhead-allocation | Tool | OVERHEAD_ALLOC_VIEW | NOT_TESTED |
| 94 | MRP | MRP Runs | /mrp/runs | List | MRP_VIEW | NOT_TESTED |
| 95 | MRP | MRP Proposals | /mrp/proposals | List | MRP_VIEW | NOT_TESTED |
| 96 | MRP | Demand Forecasts | /mrp/forecasts | List | MRP_VIEW | NOT_TESTED |
| 97 | MRP | Planning Parameters | /mrp/planning | Config | MRP_VIEW | NOT_TESTED |
| 98 | Documents | Documents List | /documents | List | DOCUMENT_VIEW | NOT_TESTED |
| 99 | Documents | Upload Document | /documents/new | Form | DOCUMENT_UPLOAD | NOT_TESTED |
| 100 | Documents | All Documents | /documents/all | List | REPORT_VIEW | NOT_TESTED |
| 101 | Documents | Document Detail | /documents/:id | Detail | DOCUMENT_VIEW | NOT_TESTED |
| 102 | Analytics | Analytical Dashboards | /analytics | Dashboard | ANALYTICS_VIEW | NOT_TESTED |
| 103 | Analytics | Analytics Explorer | /analytics/explorer | Tool | ANALYTICS_VIEW | NOT_TESTED |
| 104 | Analytics | Business Intelligence | /analytics/bi | Dashboard | BI_VIEW | NOT_TESTED |
| 105 | Analytics | Registers | /analytics/registers | Report | REGISTER_VIEW | NOT_TESTED |
| 106 | Analytics | Report Builder | /analytics/report-builder | Tool | REPORT_BUILD | NOT_TESTED |
| 107 | Admin | Webhooks | /admin/webhooks | List | WEBHOOK_VIEW | NOT_TESTED |
| 108 | Admin | Data Import/Export | /admin/data | Tool | DATA_EXPORT | NOT_TESTED |
| 109 | Admin | Notification Admin | /admin/notifications | Config | NOTIFICATION_MANAGE | NOT_TESTED |
| 110 | PLM | ECO List | /plm/ecos | List | PLM_VIEW | NOT_TESTED |
| 111 | PLM | New ECO | /plm/ecos/new | Form | ECO_CREATE | NOT_TESTED |
| 112 | PLM | ECO Detail | /plm/ecos/:id | Detail | PLM_VIEW | NOT_TESTED |
| 113 | PLM | Product Lifecycle | /plm/lifecycle | List | PLM_VIEW | NOT_TESTED |
| 114 | Admin | Users | /admin/users | List | USER_VIEW | NOT_TESTED |
| 115 | Admin | Plants | /admin/plants | List | PLANT_MANAGE | NOT_TESTED |
| 116 | Admin | Roles | /admin/roles | List | ROLE_MANAGE | NOT_TESTED |
| 117 | Admin | Audit Logs | /admin/audit-logs | List | AUDIT_VIEW | NOT_TESTED |
| 118 | Admin | System Settings | /admin/settings | Config | SETTINGS_MANAGE | NOT_TESTED |
| 119 | Admin | Modules | /admin/modules | Config | MODULE_MANAGE | NOT_TESTED |
| 120 | Admin | Attribute Definitions | /admin/attribute-definitions | List | ATTRIBUTE_MANAGE | NOT_TESTED |
| 121 | Admin | Negative Stock Overrides | /admin/negative-stock-overrides | Config | INVENTORY_NEGATIVE_OVERRIDE | NOT_TESTED |
| 122 | Admin | Item Categories | /admin/item-categories | List | SETTINGS_MANAGE | NOT_TESTED |
| 123 | Admin | Document Series | /admin/document-series | List | SETTINGS_MANAGE | NOT_TESTED |
| 124 | Admin | Company Profile | /admin/company-profile | Form | SETTINGS_MANAGE | NOT_TESTED |
| 125 | Admin | Payment Terms | /admin/payment-terms | List | SETTINGS_MANAGE | NOT_TESTED |
| 126 | Admin | Transporters | /admin/transporters | List | SETTINGS_MANAGE | NOT_TESTED |
| 127 | Admin | Teams | /admin/teams | List | SETTINGS_MANAGE | NOT_TESTED |
| 128 | Admin | Approval Rules | /admin/approval-rules | List | APPROVAL_RULE_VIEW | NOT_TESTED |
| 129 | Admin | Data Imports | /admin/imports | Tool | IMPORT_VIEW | NOT_TESTED |
| 130 | Admin | Setup Wizard | /admin/onboarding | Wizard | ONBOARDING_VIEW | NOT_TESTED |
| 131 | CRM | Leads List | /crm/leads | List | CRM_VIEW | NOT_TESTED |
| 132 | CRM | Lead Detail | /crm/leads/:id | Detail | CRM_VIEW | NOT_TESTED |
| 133 | CRM | Opportunities | /crm/opportunities | Dashboard/Kanban | CRM_VIEW | NOT_TESTED |
| 134 | CRM | Quotations List | /crm/quotations | List | CRM_VIEW | NOT_TESTED |
| 135 | CRM | New Quotation | /crm/quotations/new | Form | QUOTATION_CREATE | NOT_TESTED |
| 136 | CRM | Edit Quotation | /crm/quotations/:id/edit | Form | QUOTATION_CREATE | NOT_TESTED |
| 137 | CRM | Contacts | /crm/contacts | List | CRM_VIEW | NOT_TESTED |
| 138 | CRM | Customer Activity | /crm/activities | List | CRM_VIEW | NOT_TESTED |
| 139 | CRM | Customer Segments | /crm/customers | List | CRM_VIEW | NOT_TESTED |
| 140 | CRM | Price Lists | /crm/prices | List | CRM_VIEW | NOT_TESTED |
| 141 | CRM | Sales Pipeline | /crm/pipeline | Dashboard | CRM_VIEW | NOT_TESTED |
| 142 | HRM | Employees List | /hrm/employees | List | HR_VIEW | NOT_TESTED |
| 143 | HRM | Employee Detail | /hrm/employees/:id | Detail | HR_VIEW | NOT_TESTED |
| 144 | HRM | Attendance | /hrm/attendance | List | HR_VIEW | NOT_TESTED |
| 145 | HRM | Shifts | /hrm/shifts | List | HR_VIEW | NOT_TESTED |
| 146 | HRM | Leave | /hrm/leave | List | LEAVE_APPLY | NOT_TESTED |
| 147 | HRM | Training Renewals | /hrm/training | List | HR_VIEW | NOT_TESTED |
| 148 | HRM | Payroll Runs List | /hrm/payroll | List | PAYROLL_RUN | NOT_TESTED |
| 149 | HRM | Payroll Run Detail | /hrm/payroll/:id | Detail | PAYROLL_RUN | NOT_TESTED |
| 150 | HRM | Pay Slip | /hrm/payroll/slips/:id | Detail | PAYROLL_RUN | NOT_TESTED |
| 151 | WMS | Zones & Bins | /wms/bins | List | WMS_VIEW | NOT_TESTED |
| 152 | WMS | Bin View | /wms/bin-view | Dashboard | WMS_VIEW | NOT_TESTED |
| 153 | WMS | Put-away Tasks | /wms/putaway | List | WMS_VIEW | NOT_TESTED |
| 154 | WMS | Picking List | /wms/picking | List | WMS_VIEW | NOT_TESTED |
| 155 | WMS | Internal Transfers | /wms/transfers | List | WMS_VIEW | NOT_TESTED |
| 156 | WMS | Cross-dock | /wms/cross-dock | List | WMS_VIEW | NOT_TESTED |
| 157 | WMS | Transfer Order Detail | /wms/orders/:id | Detail | WMS_VIEW | NOT_TESTED |
| 158 | WMS | Pick Waves | /wms/waves | List | WMS_VIEW | NOT_TESTED |
| 159 | WMS | Cycle Counts List | /wms/cycle-counts | List | WMS_VIEW | NOT_TESTED |
| 160 | WMS | Cycle Count Detail | /wms/cycle-counts/:id | Detail | WMS_VIEW | NOT_TESTED |
| 161 | Integration | GST E-Invoice | /integration/gst-einvoice | Tool | GST_EINVOICE_VIEW | NOT_TESTED |
| 162 | Integration | Integration Call Log | /integration/call-log | List | INTEGRATION_MANAGE | NOT_TESTED |
| 163 | Integration | Tally Ledger Mappings | /integration/tally-ledger-mappings | List | INTEGRATION_MANAGE | NOT_TESTED |
| 164 | Integration | Shipment Tracking | /integration/shipment-tracking | List | SHIPMENT_TRACK_VIEW | NOT_TESTED |
| 165 | Integration | Busy Export | /integration/busy-export | Tool | BUSY_EXPORT_VIEW | NOT_TESTED |
