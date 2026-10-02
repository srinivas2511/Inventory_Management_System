import { Routes } from '@angular/router';
import { authGuard, permissionGuard } from './core/auth/auth.guard';
import { ShellComponent } from './core/layout/shell.component';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in',
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
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Dashboard',
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
        title: 'Spring Products',
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
        canActivate: [permissionGuard('GOODS_RECEIVE')],
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
        canActivate: [permissionGuard('ROUTING_VIEW')],
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
        path: 'finance/bank',
        title: 'Bank Reconciliation',
        canActivate: [permissionGuard('BANK_RECONCILE')],
        loadComponent: () =>
          import('./features/finance/bank-reconciliation.component').then((m) => m.BankReconciliationComponent),
      },
      {
        path: 'controlling/cost-centres',
        title: 'Cost Centres',
        canActivate: [permissionGuard('CONTROLLING_VIEW')],
        loadComponent: () =>
          import('./features/controlling/cost-centre-list.component').then((m) => m.CostCentreListComponent),
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
        path: 'admin/users',
        title: 'Users',
        canActivate: [permissionGuard('IAM_USER_MANAGE')],
        loadComponent: () => import('./features/admin/user/user-list.component').then((m) => m.UserListComponent),
      },
      {
        path: 'admin/roles',
        title: 'Roles',
        canActivate: [permissionGuard('IAM_USER_MANAGE')],
        loadComponent: () => import('./features/admin/role/role-list.component').then((m) => m.RoleListComponent),
      },
      {
        path: 'admin/audit-logs',
        title: 'Audit Logs',
        canActivate: [permissionGuard('IAM_AUDIT_VIEW')],
        loadComponent: () =>
          import('./features/admin/audit/audit-log-list.component').then((m) => m.AuditLogListComponent),
      },
      {
        path: 'admin/settings',
        title: 'System Settings',
        canActivate: [permissionGuard('ADMIN')],
        loadComponent: () =>
          import('./features/admin/settings/system-settings.component').then((m) => m.SystemSettingsComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
