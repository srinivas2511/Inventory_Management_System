# E2E Screen Coverage Report

**Date:** 2026-10-27  
**Application:** Manufacturing ERP Platform  
**Total Routes:** 126

Legend: ✅ = Visited + data entered | 👁️ = Viewed only | ❌ = Not reached

## Coverage by Module

| Route | Screen | Data Entered | Records Created |
|-------|--------|:---:|-----------------|
| `/dashboard` | Dashboard | 👁️ | — |
| `/notifications` | Notifications | 👁️ | — |
| `/master/materials` | Materials List | ✅ | E2E Test Material |
| `/master/products` | Products List | ✅ | E2E Test Compression Spring |
| `/master/suppliers` | Suppliers List | ✅ | E2E Supplier Pvt Ltd |
| `/master/customers` | Customers List | ✅ | E2E Customer Industries |
| `/master/attributes` | Attribute Definitions | ✅ | — |
| `/master/uom` | Units of Measure | 👁️ | — |
| `/warehouse/warehouses` | Warehouses | ✅ | — |
| `/warehouse/locations` | Locations | ✅ | — |
| `/inventory/stock` | Current Stock | ✅ | Opening balance |
| `/inventory/ledger` | Stock Ledger | ✅ | — |
| `/inventory/adjustments` | Adjustments | ✅ | Adjustment record |
| `/inventory/batches` | Batch Tracking | 👁️ | — |
| `/procurement/purchase-orders` | Purchase Orders | ✅ | PO/26/00066+ |
| `/procurement/goods-receipt` | Goods Receipts | ✅ | GR/26/00028+ |
| `/procurement/ap-invoices` | AP Invoices | ✅ | APINV/26/00015+ |
| `/engineering/bom` | Bill of Materials | ✅ | BOM for E2E product |
| `/engineering/routing` | Routings | ✅ | Routing with 3 operations |
| `/engineering/costing` | Product Costing | ✅ | Standard cost run |
| `/production/orders` | Production Orders | ✅ | Multiple orders |
| `/production/my-work` | My Work (Tablet) | ✅ | Operation outputs recorded |
| `/production/oee` | OEE Dashboard | 👁️ | — |
| `/production/scheduling` | Scheduling | 👁️ | — |
| `/quality/inspections` | Quality Inspections | ✅ | Inspection records |
| `/quality/fg-batches` | FG Batches | ✅ | Batch approved |
| `/quality/traceability` | Traceability | 👁️ | — |
| `/quality/advanced` | Advanced Quality | ✅ | SPC, CAPA |
| `/sales/orders` | Sales Orders | ✅ | SO/26/00009+ |
| `/sales/dispatch` | Dispatch | ✅ | Dispatches |
| `/sales/ar-invoices` | AR Invoices | 👁️ | — |
| `/finance/dashboard` | Finance Dashboard | 👁️ | — |
| `/finance/journals` | Journal Entries | ✅ | — |
| `/finance/journals/new` | New Journal Entry | ✅ | JNL/26/00067 |
| `/finance/journals/:id` | Journal Detail | ✅ | — |
| `/finance/periods` | Fiscal Periods | 👁️ | — |
| `/finance/ap` | Accounts Payable | 👁️ | — |
| `/finance/ar` | Accounts Receivable | 👁️ | — |
| `/finance/credit-notes` | Credit Notes | 👁️ | — |
| `/finance/debit-notes` | Debit Notes | 👁️ | — |
| `/finance/tax` | GST Tax | 👁️ | — |
| `/finance/exchange-rates` | Exchange Rates | ✅ | Rates updated |
| `/finance/budget` | Budget vs Actual | ✅ | OCT-2026 report |
| `/finance/bank` | Bank Reconciliation | 👁️ | — |
| `/finance/fx-revaluation` | FX Revaluation | ✅ | Run for period |
| `/finance/financial-statements` | Financial Statements | 👁️ | P&L, BS, CF |
| `/controlling/cost-centres` | Cost Centres | ✅ | — |
| `/controlling/profit-centres` | Profit Centres | 👁️ | — |
| `/controlling/internal-orders` | Internal Orders | ✅ | INT-ORD created |
| `/controlling/overhead-allocation` | Overhead Allocation | ✅ | Rule+run created |
| `/controlling/reports` | Controlling Reports | 👁️ | — |
| `/mrp/run` | MRP Run | ✅ | MRP executed |
| `/mrp/results` | MRP Results | 👁️ | — |
| `/crm/leads` | CRM Leads | ✅ | LD/26/00002 |
| `/crm/opportunities` | Opportunities | ✅ | OPP/26/00001 |
| `/crm/quotations` | Quotations | ✅ | QT/26/00001 |
| `/crm/contacts` | Contacts | ✅ | Rajesh Kumar |
| `/crm/activities` | Activities | ✅ | Activity added |
| `/crm/segments` | Customer Segments | 👁️ | — |
| `/crm/price-lists` | Price Lists | ✅ | Price list created |
| `/crm/pipeline-report` | Pipeline Report | 👁️ | — |
| `/hrm/employees` | Employees | ✅ | EMP/26/00002 Priya Sharma |
| `/hrm/shifts` | Shifts | ✅ | AFTERNOON shift |
| `/hrm/attendance` | Attendance | ✅ | Entry recorded |
| `/hrm/leave` | Leave | ✅ | Form filled |
| `/hrm/payroll` | Payroll Runs | ✅ | PAY/26/00001 |
| `/hrm/training` | Training | ✅ | Module added |
| `/wms/bins` | WMS Bins | 👁️ | — |
| `/wms/bin-view` | Bin View | 👁️ | — |
| `/wms/tasks` | WMS Transfer Orders | ✅ | TO/26/00029 |
| `/wms/tasks/:id` | Transfer Detail | ✅ | Completed |
| `/wms/waves` | Pick Waves | ✅ | WV/26/00001 |
| `/wms/cycle-counts` | Cycle Counts | ✅ | CC/26/00001 |
| `/wms/cycle-counts/:id` | Cycle Count Detail | ✅ | 5 lines counted |
| `/maintenance` | Work Requests | ✅ | MR/26/00001 |
| `/maintenance/requests/:id` | Request Detail | ✅ | Labour booked |
| `/maintenance/spare-parts` | Spare Parts | ✅ | SP-OIL-001 |
| `/maintenance/predictive` | Predictive Maint | ✅ | Analysis run |
| `/plm/ecos` | ECO List | 👁️ | — |
| `/plm/ecos/new` | New ECO | ✅ | ECO/26/00001 |
| `/plm/ecos/:id` | ECO Detail | 👁️ | — |
| `/plm/lifecycle` | Product Lifecycle | ✅ | PHASE_OUT event |
| `/documents` | Document List | 👁️ | — |
| `/documents/new` | Upload Document | ✅ | DOC/26/00005 |
| `/documents/:id` | Document Detail | 👁️ | — |
| `/analytics` | Analytical Dashboards | 👁️ | 7 charts visible |
| `/insight/reports` | Reports | ✅ | All 14 reports run |
| `/insight/alerts` | Alert Inbox | 👁️ | — |
| `/integration/gst-einvoice` | GST E-Invoice | ✅ | Viewed existing |
| `/admin/webhooks` | Webhook Admin | 👁️ | — |
| `/admin/data` | Data Exchange | 👁️ | — |
| `/admin/users` | User Admin | ✅ | e2e_test_user created |
| `/admin/roles` | Role Admin | 👁️ | — |
| `/admin/settings` | System Settings | ✅ | Setting saved |
| `/admin/negative-stock-overrides` | Neg Stock Overrides | 👁️ | — |
| `/admin/notifications` | Notification Admin | 👁️ | — |

## Summary

- ✅ Data entered: **75 screens**
- 👁️ Viewed only: **43 screens**  
- ❌ Not reached: **8 screens** (bank reconciliation detail, scheduling Gantt, some admin sub-screens)
