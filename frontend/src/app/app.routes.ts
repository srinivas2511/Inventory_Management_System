import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { authGuard, guestGuard, permissionGuard, signedInGuard } from './core/auth/auth.guard';
import { landingRoute } from './core/auth/landing';
import { SessionStore } from './core/auth/session.store';
import { ShellComponent } from './core/layout/shell.component';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'forgot-password',
    title: 'Reset Password',
    loadComponent: () => import('./features/auth/forgot-password.component').then((m) => m.ForgotPasswordComponent),
  },
  {
    path: 'reset-password/:token',
    title: 'Set New Password',
    loadComponent: () => import('./features/auth/reset-password.component').then((m) => m.ResetPasswordComponent),
  },
  {
    path: 'change-password',
    title: 'Change password',
    canActivate: [signedInGuard],
    loadComponent: () => import('./features/auth/change-password.component').then((m) => m.ChangePasswordComponent),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: () => landingRoute(inject(SessionStore)) },
      {
        path: 'dashboard',
        title: 'Dashboard',
        canActivate: [permissionGuard('DASHBOARD_VIEW')],
        loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'master/materials',
        title: 'Materials',
        canActivate: [permissionGuard('MATERIAL_VIEW')],
        loadComponent: () =>
          import('./features/master/material/material-list.component').then((m) => m.MaterialListComponent),
      },
      {
        path: 'master/suppliers',
        title: 'Suppliers',
        canActivate: [permissionGuard('SUPPLIER_MANAGE')],
        loadComponent: () =>
          import('./features/master/supplier/supplier-list.component').then((m) => m.SupplierListComponent),
      },
      {
        path: 'master/customers',
        title: 'Customers',
        canActivate: [permissionGuard('CUSTOMER_MANAGE')],
        loadComponent: () =>
          import('./features/master/customer/customer-list.component').then((m) => m.CustomerListComponent),
      },
      {
        path: 'master/products',
        title: 'Products',
        canActivate: [permissionGuard('PRODUCT_VIEW')],
        loadComponent: () =>
          import('./features/master/product/product-list.component').then((m) => m.ProductListComponent),
      },
      {
        path: 'warehouse/warehouses',
        title: 'Warehouses',
        canActivate: [permissionGuard('INVENTORY_VIEW')],
        loadComponent: () =>
          import('./features/warehouse/warehouse-list.component').then((m) => m.WarehouseListComponent),
      },
      {
        path: 'inventory/stock',
        title: 'Current Stock',
        canActivate: [permissionGuard('INVENTORY_VIEW')],
        loadComponent: () =>
          import('./features/inventory/stock-balance-list.component').then((m) => m.StockBalanceListComponent),
      },
      {
        path: 'inventory/ledger',
        title: 'Stock Ledger',
        canActivate: [permissionGuard('INVENTORY_VIEW')],
        loadComponent: () => import('./features/inventory/stock-ledger.component').then((m) => m.StockLedgerComponent),
      },
      {
        path: 'inventory/batches',
        title: 'Batch Tracking',
        canActivate: [permissionGuard('INVENTORY_VIEW')],
        loadComponent: () => import('./features/inventory/batch-list.component').then((m) => m.BatchListComponent),
      },
      {
        path: 'inventory/adjustments',
        title: 'Stock Adjustments',
        canActivate: [permissionGuard('INVENTORY_VIEW')],
        loadComponent: () =>
          import('./features/inventory/stock-adjustment-list.component').then((m) => m.StockAdjustmentListComponent),
      },
      {
        path: 'procurement/purchase-orders',
        title: 'Purchase Orders',
        canActivate: [permissionGuard('PURCHASE_VIEW')],
        loadComponent: () =>
          import('./features/procurement/purchase-order-list.component').then((m) => m.PurchaseOrderListComponent),
      },
      {
        path: 'procurement/requisitions',
        title: 'Purchase Requisitions',
        canActivate: [permissionGuard('PURCHASE_VIEW')],
        loadComponent: () =>
          import('./features/procurement/purchase-requisition-list.component').then(
            (m) => m.PurchaseRequisitionListComponent,
          ),
      },
      {
        path: 'procurement/goods-receipts',
        title: 'Goods Receipts',
        canActivate: [permissionGuard('INVENTORY_RECEIVE')],
        loadComponent: () =>
          import('./features/procurement/goods-receipt-list.component').then((m) => m.GoodsReceiptListComponent),
      },
      {
        path: 'engineering/bom',
        title: 'Bill of Materials',
        canActivate: [permissionGuard('BOM_VIEW')],
        loadComponent: () => import('./features/engineering/bom-list.component').then((m) => m.BomListComponent),
      },
      {
        path: 'engineering/routing',
        title: 'Product Routing',
        canActivate: [permissionGuard('PRODUCT_VIEW')],
        loadComponent: () =>
          import('./features/engineering/routing-editor.component').then((m) => m.RoutingEditorComponent),
      },
      {
        path: 'production/orders',
        title: 'Production Orders',
        canActivate: [permissionGuard('PRODUCTION_VIEW')],
        loadComponent: () =>
          import('./features/production/production-order-list.component').then((m) => m.ProductionOrderListComponent),
      },
      {
        path: 'production/my-work',
        title: 'My Work',
        canActivate: [permissionGuard('PRODUCTION_EXECUTE')],
        loadComponent: () => import('./features/production/my-work.component').then((m) => m.MyWorkComponent),
      },
      {
        path: 'quality/inspections',
        title: 'Quality Inspections',
        canActivate: [permissionGuard('QUALITY_VIEW')],
        loadComponent: () =>
          import('./features/quality/inspection-list.component').then((m) => m.InspectionListComponent),
      },
      {
        path: 'quality/inspections/:id',
        title: 'Inspection',
        canActivate: [permissionGuard('QUALITY_VIEW')],
        loadComponent: () =>
          import('./features/quality/inspection-detail.component').then((m) => m.InspectionDetailComponent),
      },
      {
        path: 'quality/fg-batches',
        title: 'FG Batches',
        canActivate: [permissionGuard('QUALITY_VIEW')],
        loadComponent: () => import('./features/quality/fg-batch-list.component').then((m) => m.FgBatchListComponent),
      },
      {
        path: 'quality/rejections',
        title: 'Rejection Analysis',
        canActivate: [permissionGuard('QUALITY_VIEW')],
        loadComponent: () =>
          import('./features/quality/rejection-dashboard.component').then((m) => m.RejectionDashboardComponent),
      },
      {
        path: 'quality/spc',
        title: 'SPC Control Charts',
        canActivate: [permissionGuard('QUALITY_VIEW')],
        loadComponent: () => import('./features/quality/spc-chart-list.component').then((m) => m.SpcChartListComponent),
      },
      {
        path: 'quality/spc/:id',
        title: 'SPC Control Chart',
        canActivate: [permissionGuard('QUALITY_VIEW')],
        loadComponent: () =>
          import('./features/quality/spc-chart-detail.component').then((m) => m.SpcChartDetailComponent),
      },
      {
        path: 'quality/capa',
        title: 'CAPA',
        canActivate: [permissionGuard('QUALITY_VIEW')],
        loadComponent: () => import('./features/quality/capa-list.component').then((m) => m.CapaListComponent),
      },
      {
        path: 'quality/capa/new',
        title: 'New CAPA',
        canActivate: [permissionGuard('CAPA_MANAGE')],
        loadComponent: () => import('./features/quality/capa-detail.component').then((m) => m.CapaDetailComponent),
      },
      {
        path: 'quality/capa/:id',
        title: 'CAPA',
        canActivate: [permissionGuard('QUALITY_VIEW')],
        loadComponent: () => import('./features/quality/capa-detail.component').then((m) => m.CapaDetailComponent),
      },
      {
        path: 'quality/complaints',
        title: 'Customer Complaints',
        canActivate: [permissionGuard('QUALITY_VIEW')],
        loadComponent: () =>
          import('./features/quality/complaint-list.component').then((m) => m.ComplaintListComponent),
      },
      {
        path: 'quality/complaints/new',
        title: 'Register Complaint',
        canActivate: [permissionGuard('COMPLAINT_MANAGE')],
        loadComponent: () =>
          import('./features/quality/complaint-detail.component').then((m) => m.ComplaintDetailComponent),
      },
      {
        path: 'quality/complaints/:id',
        title: 'Customer Complaint',
        canActivate: [permissionGuard('QUALITY_VIEW')],
        loadComponent: () =>
          import('./features/quality/complaint-detail.component').then((m) => m.ComplaintDetailComponent),
      },
      {
        path: 'quality/fai',
        title: 'First Article Inspections',
        canActivate: [permissionGuard('QUALITY_VIEW')],
        loadComponent: () => import('./features/quality/fai-list.component').then((m) => m.FaiListComponent),
      },
      {
        path: 'quality/fai/:id',
        title: 'First Article Inspection',
        canActivate: [permissionGuard('QUALITY_VIEW')],
        loadComponent: () => import('./features/quality/fai-detail.component').then((m) => m.FaiDetailComponent),
      },
      {
        path: 'sales/orders',
        title: 'Sales Orders',
        canActivate: [permissionGuard('SALES_VIEW')],
        loadComponent: () =>
          import('./features/sales/sales-order-list.component').then((m) => m.SalesOrderListComponent),
      },
      {
        path: 'sales/dispatches',
        title: 'Dispatches',
        canActivate: [permissionGuard('DISPATCH_VIEW')],
        loadComponent: () => import('./features/sales/dispatch-list.component').then((m) => m.DispatchListComponent),
      },
      {
        path: 'insight/alerts',
        title: 'Alerts',
        canActivate: [permissionGuard('ALERT_VIEW')],
        loadComponent: () => import('./features/insight/alerts-list.component').then((m) => m.AlertsListComponent),
      },
      {
        path: 'insight/reports',
        title: 'Reports',
        canActivate: [permissionGuard('REPORT_VIEW')],
        loadComponent: () => import('./features/insight/reports.component').then((m) => m.ReportsComponent),
      },
      {
        path: 'insight/traceability',
        title: 'Traceability',
        canActivate: [permissionGuard('TRACEABILITY_VIEW')],
        loadComponent: () => import('./features/insight/traceability.component').then((m) => m.TraceabilityComponent),
      },
      {
        path: 'maintenance',
        title: 'Maintenance',
        canActivate: [permissionGuard('MAINTENANCE_VIEW')],
        loadComponent: () =>
          import('./features/maintenance/maintenance-list.component').then((m) => m.MaintenanceListComponent),
      },
      {
        path: 'maintenance/requests/:id',
        title: 'Maintenance Request',
        canActivate: [permissionGuard('MAINTENANCE_VIEW')],
        loadComponent: () =>
          import('./features/maintenance/maintenance-request-detail.component').then(
            (m) => m.MaintenanceRequestDetailComponent,
          ),
      },
      {
        path: 'maintenance/spare-parts',
        title: 'Spare Parts',
        canActivate: [permissionGuard('SPARE_PART_VIEW')],
        loadComponent: () => import('./features/maintenance/spare-parts.component').then((m) => m.SparePartsComponent),
      },
      {
        path: 'maintenance/predictive',
        title: 'Predictive Maintenance',
        canActivate: [permissionGuard('PREDICTIVE_MAINT_VIEW')],
        loadComponent: () =>
          import('./features/maintenance/predictive-maintenance.component').then(
            (m) => m.PredictiveMaintenanceComponent,
          ),
      },
      {
        path: 'integration/gst-einvoice',
        title: 'GST E-Invoice',
        canActivate: [permissionGuard('GST_EINVOICE_VIEW')],
        loadComponent: () =>
          import('./features/integration/gst-einvoice.component').then((m) => m.GstEInvoiceComponent),
      },
      {
        path: 'production/oee',
        title: 'OEE',
        canActivate: [permissionGuard('OEE_VIEW')],
        loadComponent: () =>
          import('./features/production/oee-dashboard.component').then((m) => m.OeeDashboardComponent),
      },
      {
        path: 'production/schedule',
        title: 'Production Schedule',
        canActivate: [permissionGuard('SCHEDULE_VIEW')],
        loadComponent: () =>
          import('./features/production/schedule-gantt.component').then((m) => m.ScheduleGanttComponent),
      },
      {
        path: 'finance/dashboard',
        title: 'Finance Dashboard',
        canActivate: [permissionGuard('FINANCE_REPORT')],
        loadComponent: () =>
          import('./features/finance/finance-dashboard.component').then((m) => m.FinanceDashboardComponent),
      },
      {
        path: 'finance/journals',
        title: 'Journal Entries',
        canActivate: [permissionGuard('GL_VIEW')],
        loadComponent: () => import('./features/finance/journal-list.component').then((m) => m.JournalListComponent),
      },
      {
        path: 'finance/journals/new',
        title: 'New Journal Entry',
        canActivate: [permissionGuard('GL_POST')],
        loadComponent: () => import('./features/finance/journal-form.component').then((m) => m.JournalFormComponent),
      },
      {
        path: 'finance/journals/:id',
        title: 'Journal Detail',
        canActivate: [permissionGuard('GL_VIEW')],
        loadComponent: () =>
          import('./features/finance/journal-detail.component').then((m) => m.JournalDetailComponent),
      },
      {
        path: 'finance/periods',
        title: 'Fiscal Periods',
        canActivate: [permissionGuard('GL_VIEW')],
        loadComponent: () => import('./features/finance/period-list.component').then((m) => m.PeriodListComponent),
      },
      {
        path: 'finance/ap',
        title: 'Accounts Payable',
        canActivate: [permissionGuard('AP_MANAGE')],
        loadComponent: () => import('./features/finance/ap-list.component').then((m) => m.ApListComponent),
      },
      {
        path: 'finance/ar',
        title: 'Accounts Receivable',
        canActivate: [permissionGuard('AR_MANAGE')],
        loadComponent: () => import('./features/finance/ar-list.component').then((m) => m.ArListComponent),
      },
      {
        path: 'finance/ar-reconciliation',
        title: 'AR Invoice Reconciliation',
        canActivate: [permissionGuard('AR_MANAGE')],
        loadComponent: () =>
          import('./features/finance/ar-reconciliation.component').then((m) => m.ArReconciliationComponent),
      },
      {
        path: 'finance/credit-notes',
        title: 'Credit Notes',
        canActivate: [permissionGuard('AR_MANAGE')],
        loadComponent: () => import('./features/finance/credit-notes.component').then((m) => m.CreditNotesComponent),
      },
      {
        path: 'finance/debit-notes',
        title: 'Debit Notes',
        canActivate: [permissionGuard('AP_MANAGE')],
        loadComponent: () => import('./features/finance/debit-notes.component').then((m) => m.DebitNotesComponent),
      },
      {
        path: 'finance/tax',
        title: 'GST Tax',
        canActivate: [permissionGuard('GL_VIEW')],
        loadComponent: () => import('./features/finance/tax-admin.component').then((m) => m.TaxAdminComponent),
      },
      {
        path: 'finance/exchange-rates',
        title: 'Exchange Rates',
        canActivate: [permissionGuard('GL_VIEW')],
        loadComponent: () =>
          import('./features/finance/exchange-rates.component').then((m) => m.ExchangeRatesComponent),
      },
      {
        path: 'finance/budget',
        title: 'Budget against Actual',
        canActivate: [permissionGuard('GL_VIEW')],
        loadComponent: () =>
          import('./features/finance/budget-vs-actual.component').then((m) => m.BudgetVsActualComponent),
      },
      {
        path: 'finance/bank',
        title: 'Bank Reconciliation',
        canActivate: [permissionGuard('BANK_RECONCILE')],
        loadComponent: () =>
          import('./features/finance/bank-reconciliation.component').then((m) => m.BankReconciliationComponent),
      },
      {
        path: 'finance/fx-revaluation',
        title: 'FX Revaluation',
        canActivate: [permissionGuard('FX_REVALUATION_RUN')],
        loadComponent: () =>
          import('./features/finance/fx-revaluation.component').then((m) => m.FxRevaluationComponent),
      },
      {
        path: 'finance/financial-statements',
        title: 'Financial Statements',
        canActivate: [permissionGuard('FINANCE_REPORT')],
        loadComponent: () =>
          import('./features/finance/financial-statements.component').then((m) => m.FinancialStatementsComponent),
      },
      {
        path: 'controlling/cost-centres',
        title: 'Cost Centres',
        canActivate: [permissionGuard('CONTROLLING_VIEW')],
        loadComponent: () =>
          import('./features/controlling/cost-centre-list.component').then((m) => m.CostCentreListComponent),
      },
      {
        path: 'controlling/internal-orders',
        title: 'Internal Orders',
        canActivate: [permissionGuard('INTERNAL_ORDER_VIEW')],
        loadComponent: () =>
          import('./features/controlling/internal-order-list.component').then((m) => m.InternalOrderListComponent),
      },
      {
        path: 'controlling/cost-estimates',
        title: 'Product Costing',
        canActivate: [permissionGuard('CONTROLLING_VIEW')],
        loadComponent: () =>
          import('./features/controlling/product-costing-list.component').then((m) => m.ProductCostingListComponent),
      },
      {
        path: 'controlling/cost-estimates/:id',
        title: 'Cost Estimate',
        canActivate: [permissionGuard('CONTROLLING_VIEW')],
        loadComponent: () =>
          import('./features/controlling/product-costing-detail.component').then(
            (m) => m.ProductCostingDetailComponent,
          ),
      },
      {
        path: 'controlling/variances',
        title: 'Variance Analysis',
        canActivate: [permissionGuard('CONTROLLING_VIEW')],
        loadComponent: () =>
          import('./features/controlling/variance-list.component').then((m) => m.VarianceListComponent),
      },
      {
        path: 'controlling/reports',
        title: 'Controlling Reports',
        canActivate: [permissionGuard('CONTROLLING_VIEW')],
        loadComponent: () =>
          import('./features/controlling/controlling-reports.component').then((m) => m.ControllingReportsComponent),
      },
      {
        path: 'controlling/overhead-allocation',
        title: 'Overhead Allocation',
        canActivate: [permissionGuard('OVERHEAD_ALLOC_VIEW')],
        loadComponent: () =>
          import('./features/controlling/overhead-allocation.component').then((m) => m.OverheadAllocationComponent),
      },
      {
        path: 'mrp/runs',
        title: 'MRP Runs',
        canActivate: [permissionGuard('MRP_VIEW')],
        loadComponent: () => import('./features/mrp/mrp-run-list.component').then((m) => m.MrpRunListComponent),
      },
      {
        path: 'mrp/proposals',
        title: 'MRP Proposals',
        canActivate: [permissionGuard('MRP_VIEW')],
        loadComponent: () =>
          import('./features/mrp/mrp-proposal-list.component').then((m) => m.MrpProposalListComponent),
      },
      {
        path: 'mrp/forecasts',
        title: 'Demand Forecasts',
        canActivate: [permissionGuard('MRP_VIEW')],
        loadComponent: () =>
          import('./features/mrp/demand-forecast-list.component').then((m) => m.DemandForecastListComponent),
      },
      {
        path: 'mrp/planning',
        title: 'Planning Parameters',
        canActivate: [permissionGuard('MRP_VIEW')],
        loadComponent: () => import('./features/mrp/planning-params.component').then((m) => m.PlanningParamsComponent),
      },
      {
        path: 'documents',
        title: 'Documents',
        canActivate: [permissionGuard('DOCUMENT_VIEW')],
        loadComponent: () =>
          import('./features/documents/document-list.component').then((m) => m.DocumentListComponent),
      },
      {
        path: 'documents/new',
        title: 'Upload Document',
        canActivate: [permissionGuard('DOCUMENT_UPLOAD')],
        loadComponent: () =>
          import('./features/documents/document-upload.component').then((m) => m.DocumentUploadComponent),
      },
      {
        path: 'documents/:id',
        title: 'Document',
        canActivate: [permissionGuard('DOCUMENT_VIEW')],
        loadComponent: () =>
          import('./features/documents/document-detail.component').then((m) => m.DocumentDetailComponent),
      },
      {
        path: 'notifications',
        title: 'Notifications',
        loadComponent: () =>
          import('./features/notifications/notification-inbox.component').then((m) => m.NotificationInboxComponent),
      },
      {
        path: 'notifications/preferences',
        title: 'Notification Preferences',
        loadComponent: () =>
          import('./features/notifications/notification-preferences.component').then(
            (m) => m.NotificationPreferencesComponent,
          ),
      },
      {
        path: 'analytics',
        title: 'Analytical Dashboards',
        canActivate: [permissionGuard('ANALYTICS_VIEW')],
        loadComponent: () =>
          import('./features/analytics/analytics-dashboards.component').then((m) => m.AnalyticsDashboardsComponent),
      },
      {
        path: 'analytics/explorer',
        title: 'Analytics Explorer',
        canActivate: [permissionGuard('ANALYTICS_VIEW')],
        loadComponent: () =>
          import('./features/analytics/analytics-explorer.component').then((m) => m.AnalyticsExplorerComponent),
      },
      {
        path: 'admin/webhooks',
        title: 'Webhooks',
        canActivate: [permissionGuard('WEBHOOK_VIEW')],
        loadComponent: () =>
          import('./features/integration/webhook-admin.component').then((m) => m.WebhookAdminComponent),
      },
      {
        path: 'admin/data',
        title: 'Data Import and Export',
        canActivate: [permissionGuard('DATA_EXPORT')],
        loadComponent: () =>
          import('./features/integration/data-exchange.component').then((m) => m.DataExchangeComponent),
      },
      {
        path: 'admin/notifications',
        title: 'Notification Administration',
        canActivate: [permissionGuard('NOTIFICATION_MANAGE')],
        loadComponent: () =>
          import('./features/notifications/notification-admin.component').then((m) => m.NotificationAdminComponent),
      },
      {
        path: 'plm/ecos',
        title: 'Engineering Change Orders',
        canActivate: [permissionGuard('PLM_VIEW')],
        loadComponent: () => import('./features/plm/eco-list.component').then((m) => m.EcoListComponent),
      },
      {
        path: 'plm/ecos/new',
        title: 'New ECO',
        canActivate: [permissionGuard('ECO_CREATE')],
        loadComponent: () => import('./features/plm/eco-detail.component').then((m) => m.EcoDetailComponent),
      },
      {
        path: 'plm/ecos/:id',
        title: 'Engineering Change Order',
        canActivate: [permissionGuard('PLM_VIEW')],
        loadComponent: () => import('./features/plm/eco-detail.component').then((m) => m.EcoDetailComponent),
      },
      {
        path: 'plm/lifecycle',
        title: 'Product Lifecycle',
        canActivate: [permissionGuard('PLM_VIEW')],
        loadComponent: () => import('./features/plm/lifecycle-list.component').then((m) => m.LifecycleListComponent),
      },
      {
        path: 'crm/leads',
        title: 'Leads',
        canActivate: [permissionGuard('CRM_VIEW')],
        loadComponent: () => import('./features/crm/crm-lead-list.component').then((m) => m.CrmLeadListComponent),
      },
      {
        path: 'crm/leads/:id',
        title: 'Lead',
        canActivate: [permissionGuard('CRM_VIEW')],
        loadComponent: () => import('./features/crm/crm-lead-detail.component').then((m) => m.CrmLeadDetailComponent),
      },
      {
        path: 'crm/opportunities',
        title: 'Opportunities',
        canActivate: [permissionGuard('CRM_VIEW')],
        loadComponent: () =>
          import('./features/crm/crm-opportunity-board.component').then((m) => m.CrmOpportunityBoardComponent),
      },
      {
        path: 'crm/quotations',
        title: 'Quotations',
        canActivate: [permissionGuard('CRM_VIEW')],
        loadComponent: () =>
          import('./features/crm/crm-quotation-list.component').then((m) => m.CrmQuotationListComponent),
      },
      {
        path: 'crm/quotations/new',
        title: 'New Quotation',
        canActivate: [permissionGuard('QUOTATION_CREATE')],
        loadComponent: () =>
          import('./features/crm/crm-quotation-form.component').then((m) => m.CrmQuotationFormComponent),
      },
      {
        path: 'crm/quotations/:id/edit',
        title: 'Edit Quotation',
        canActivate: [permissionGuard('QUOTATION_CREATE')],
        loadComponent: () =>
          import('./features/crm/crm-quotation-form.component').then((m) => m.CrmQuotationFormComponent),
      },
      {
        path: 'crm/contacts',
        title: 'Contacts',
        canActivate: [permissionGuard('CRM_VIEW')],
        loadComponent: () => import('./features/crm/crm-contact-list.component').then((m) => m.CrmContactListComponent),
      },
      {
        path: 'crm/activities',
        title: 'Customer Activity',
        canActivate: [permissionGuard('CRM_VIEW')],
        loadComponent: () =>
          import('./features/crm/crm-activity-page.component').then((m) => m.CrmActivityPageComponent),
      },
      {
        path: 'crm/customers',
        title: 'Customer Segments',
        canActivate: [permissionGuard('CRM_VIEW')],
        loadComponent: () =>
          import('./features/crm/crm-customer-segments.component').then((m) => m.CrmCustomerSegmentsComponent),
      },
      {
        path: 'crm/prices',
        title: 'Price Lists',
        canActivate: [permissionGuard('CRM_VIEW')],
        loadComponent: () => import('./features/crm/crm-price-list.component').then((m) => m.CrmPriceListComponent),
      },
      {
        path: 'crm/pipeline',
        title: 'Sales Pipeline',
        canActivate: [permissionGuard('CRM_VIEW')],
        loadComponent: () =>
          import('./features/crm/crm-pipeline-report.component').then((m) => m.CrmPipelineReportComponent),
      },
      {
        path: 'wms/bins',
        title: 'Zones and Bins',
        canActivate: [permissionGuard('WMS_VIEW')],
        loadComponent: () => import('./features/wms/wms-bins.component').then((m) => m.WmsBinsComponent),
      },
      {
        path: 'wms/bin-view',
        title: 'Bin View',
        canActivate: [permissionGuard('WMS_VIEW')],
        loadComponent: () => import('./features/wms/wms-bin-view.component').then((m) => m.WmsBinViewComponent),
      },
      {
        path: 'wms/putaway',
        title: 'Put-away Tasks',
        data: { movementType: 'PUTAWAY', title: 'Put-away Tasks' },
        canActivate: [permissionGuard('WMS_VIEW')],
        loadComponent: () => import('./features/wms/wms-tasks.component').then((m) => m.WmsTasksComponent),
      },
      {
        path: 'wms/picking',
        title: 'Picking List',
        data: { movementType: 'PICKING', title: 'Picking List' },
        canActivate: [permissionGuard('WMS_VIEW')],
        loadComponent: () => import('./features/wms/wms-tasks.component').then((m) => m.WmsTasksComponent),
      },
      {
        path: 'wms/transfers',
        title: 'Transfers',
        data: { movementType: 'INTERNAL_TRANSFER', title: 'Internal Transfers' },
        canActivate: [permissionGuard('WMS_VIEW')],
        loadComponent: () => import('./features/wms/wms-tasks.component').then((m) => m.WmsTasksComponent),
      },
      {
        path: 'wms/cross-dock',
        title: 'Cross-dock',
        data: { movementType: 'CROSS_DOCK', title: 'Cross-dock Orders' },
        canActivate: [permissionGuard('WMS_VIEW')],
        loadComponent: () => import('./features/wms/wms-tasks.component').then((m) => m.WmsTasksComponent),
      },
      {
        path: 'wms/orders/:id',
        title: 'Transfer Order',
        canActivate: [permissionGuard('WMS_VIEW')],
        loadComponent: () => import('./features/wms/wms-task-detail.component').then((m) => m.WmsTaskDetailComponent),
      },
      {
        path: 'wms/waves',
        title: 'Pick Waves',
        canActivate: [permissionGuard('WMS_VIEW')],
        loadComponent: () => import('./features/wms/wms-waves.component').then((m) => m.WmsWavesComponent),
      },
      {
        path: 'wms/cycle-counts',
        title: 'Cycle Counts',
        canActivate: [permissionGuard('WMS_VIEW')],
        loadComponent: () => import('./features/wms/wms-cycle-counts.component').then((m) => m.WmsCycleCountsComponent),
      },
      {
        path: 'wms/cycle-counts/:id',
        title: 'Cycle Count',
        canActivate: [permissionGuard('WMS_VIEW')],
        loadComponent: () =>
          import('./features/wms/wms-cycle-count-detail.component').then((m) => m.WmsCycleCountDetailComponent),
      },
      {
        path: 'hrm/employees',
        title: 'Employees',
        canActivate: [permissionGuard('HR_VIEW')],
        loadComponent: () => import('./features/hrm/employee-list.component').then((m) => m.EmployeeListComponent),
      },
      {
        path: 'hrm/employees/:id',
        title: 'Employee',
        canActivate: [permissionGuard('HR_VIEW')],
        loadComponent: () => import('./features/hrm/employee-detail.component').then((m) => m.EmployeeDetailComponent),
      },
      {
        path: 'hrm/attendance',
        title: 'Attendance',
        canActivate: [permissionGuard('HR_VIEW')],
        loadComponent: () => import('./features/hrm/attendance-page.component').then((m) => m.AttendancePageComponent),
      },
      {
        path: 'hrm/shifts',
        title: 'Shifts',
        canActivate: [permissionGuard('HR_VIEW')],
        loadComponent: () => import('./features/hrm/shift-list.component').then((m) => m.ShiftListComponent),
      },
      {
        path: 'hrm/leave',
        title: 'Leave',
        canActivate: [permissionGuard('LEAVE_APPLY')],
        loadComponent: () => import('./features/hrm/leave-page.component').then((m) => m.LeavePageComponent),
      },
      {
        path: 'hrm/training',
        title: 'Training Renewals',
        canActivate: [permissionGuard('HR_VIEW')],
        loadComponent: () =>
          import('./features/hrm/training-renewals.component').then((m) => m.TrainingRenewalsComponent),
      },
      {
        path: 'hrm/payroll',
        title: 'Payroll',
        canActivate: [permissionGuard('PAYROLL_RUN')],
        loadComponent: () => import('./features/hrm/payroll-run-list.component').then((m) => m.PayrollRunListComponent),
      },
      {
        path: 'hrm/payroll/slips/:id',
        title: 'Pay Slip',
        canActivate: [permissionGuard('PAYROLL_RUN')],
        loadComponent: () => import('./features/hrm/payslip.component').then((m) => m.PayslipComponent),
      },
      {
        path: 'hrm/payroll/:id',
        title: 'Payroll Run',
        canActivate: [permissionGuard('PAYROLL_RUN')],
        loadComponent: () =>
          import('./features/hrm/payroll-run-detail.component').then((m) => m.PayrollRunDetailComponent),
      },
      {
        path: 'admin/users',
        title: 'Users',
        canActivate: [permissionGuard('USER_VIEW')],
        loadComponent: () => import('./features/admin/user/user-list.component').then((m) => m.UserListComponent),
      },
      {
        path: 'admin/plants',
        title: 'Plants',
        canActivate: [permissionGuard('PLANT_MANAGE')],
        loadComponent: () => import('./features/admin/plants/plants.component').then((m) => m.PlantsComponent),
      },
      {
        path: 'admin/roles',
        title: 'Roles',
        canActivate: [permissionGuard('ROLE_MANAGE')],
        loadComponent: () => import('./features/admin/role/role-list.component').then((m) => m.RoleListComponent),
      },
      {
        path: 'admin/audit-logs',
        title: 'Audit Logs',
        canActivate: [permissionGuard('AUDIT_VIEW')],
        loadComponent: () =>
          import('./features/admin/audit/audit-log-list.component').then((m) => m.AuditLogListComponent),
      },
      {
        path: 'admin/settings',
        title: 'System Settings',
        canActivate: [permissionGuard('SETTINGS_MANAGE')],
        loadComponent: () =>
          import('./features/admin/settings/system-settings.component').then((m) => m.SystemSettingsComponent),
      },
      {
        path: 'admin/modules',
        title: 'Modules',
        canActivate: [permissionGuard('MODULE_MANAGE')],
        loadComponent: () => import('./features/admin/modules/modules.component').then((m) => m.ModulesComponent),
      },
      {
        path: 'admin/attribute-definitions',
        title: 'Attribute Definitions',
        canActivate: [permissionGuard('ATTRIBUTE_MANAGE')],
        loadComponent: () =>
          import('./features/admin/attribute-definitions/attribute-definitions.component').then(
            (m) => m.AttributeDefinitionsComponent,
          ),
      },
      {
        path: 'admin/negative-stock-overrides',
        title: 'Negative Stock Overrides',
        canActivate: [permissionGuard('INVENTORY_NEGATIVE_OVERRIDE')],
        loadComponent: () =>
          import('./features/admin/negative-stock-overrides/negative-stock-overrides.component').then(
            (m) => m.NegativeStockOverridesComponent,
          ),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
