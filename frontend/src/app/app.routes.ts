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
    ],
  },
  { path: '**', redirectTo: '' },
];
