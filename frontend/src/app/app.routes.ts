import { Routes } from '@angular/router';
import { authGuard, guestGuard, permissionGuard } from './core/permission/permission.guard';
import { ShellComponent } from './core/layout/shell.component';

export const routes: Routes = [
  // Public pages (no shell). Someone already signed in is sent on to the application.
  {
    path: 'login',
    title: 'Sign in',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'forgot-password',
    title: 'Reset your password',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/forgot-password.component').then((m) => m.ForgotPasswordComponent),
  },
  {
    path: 'reset-password/:token',
    title: 'Choose a new password',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/reset-password.component').then((m) => m.ResetPasswordComponent),
  },
  // Everything else needs a session; each page may also need a permission (UX only, the API enforces access).
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
        path: 'forbidden',
        title: 'Not permitted',
        loadComponent: () => import('./features/forbidden/forbidden.component').then((m) => m.ForbiddenComponent),
      },
      {
        path: 'master/materials',
        title: 'Materials',
        canActivate: [permissionGuard],
        data: { permission: ['MATERIAL_VIEW', 'MASTERDATA_MANAGE'] },
        loadComponent: () =>
          import('./features/master/pages/materials-page.component').then((m) => m.MaterialsPageComponent),
      },
      {
        path: 'master/suppliers',
        title: 'Suppliers',
        canActivate: [permissionGuard],
        data: { permission: ['SUPPLIER_MANAGE', 'PURCHASE_VIEW'], kind: 'suppliers' },
        loadComponent: () =>
          import('./features/master/pages/partners-page.component').then((m) => m.PartnersPageComponent),
      },
      {
        path: 'master/customers',
        title: 'Customers',
        canActivate: [permissionGuard],
        data: { permission: ['CUSTOMER_MANAGE', 'SALES_VIEW'], kind: 'customers' },
        loadComponent: () =>
          import('./features/master/pages/partners-page.component').then((m) => m.PartnersPageComponent),
      },
      {
        path: 'master/products',
        title: 'Spring products',
        canActivate: [permissionGuard],
        data: { permission: 'PRODUCT_VIEW' },
        loadComponent: () =>
          import('./features/master/pages/products-page.component').then((m) => m.ProductsPageComponent),
      },
      {
        path: 'master/products/new',
        title: 'New product',
        canActivate: [permissionGuard],
        data: { permission: 'PRODUCT_CREATE' },
        loadComponent: () =>
          import('./features/master/pages/product-form-page.component').then((m) => m.ProductFormPageComponent),
      },
      {
        path: 'master/products/:id/edit',
        title: 'Edit product',
        canActivate: [permissionGuard],
        data: { permission: 'PRODUCT_VIEW' },
        loadComponent: () =>
          import('./features/master/pages/product-form-page.component').then((m) => m.ProductFormPageComponent),
      },
      {
        path: 'admin/users',
        title: 'Users',
        canActivate: [permissionGuard],
        data: { permission: 'USER_VIEW' },
        loadComponent: () => import('./features/admin/pages/users-page.component').then((m) => m.UsersPageComponent),
      },
      {
        path: 'admin/roles',
        title: 'Roles',
        canActivate: [permissionGuard],
        data: { permission: 'ROLE_MANAGE' },
        loadComponent: () => import('./features/admin/pages/roles-page.component').then((m) => m.RolesPageComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
