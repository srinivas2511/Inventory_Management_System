import { SessionStore } from '../auth/session.store';

export interface NavItem {
  label: string;
  icon: string;
  route: string;
  permission?: string;
  enabled: boolean;
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}

export const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Overview',
    items: [{ label: 'Dashboard', icon: 'dashboard', route: '/dashboard', enabled: true }],
  },
  {
    label: 'Master Data',
    items: [
      { label: 'Materials', icon: 'category', route: '/master/materials', permission: 'MATERIAL_VIEW', enabled: true },
      {
        label: 'Spring Products',
        icon: 'settings_input_component',
        route: '/master/products',
        permission: 'PRODUCT_VIEW',
        enabled: true,
      },
      {
        label: 'Suppliers',
        icon: 'local_shipping',
        route: '/master/suppliers',
        permission: 'SUPPLIER_MANAGE',
        enabled: true,
      },
      { label: 'Customers', icon: 'groups', route: '/master/customers', permission: 'CUSTOMER_MANAGE', enabled: true },
    ],
  },
  {
    label: 'Warehouses & Inventory',
    items: [
      {
        label: 'Warehouses',
        icon: 'warehouse',
        route: '/warehouse/warehouses',
        permission: 'INVENTORY_VIEW',
        enabled: true,
      },
      {
        label: 'Current Stock',
        icon: 'inventory_2',
        route: '/inventory/stock',
        permission: 'INVENTORY_VIEW',
        enabled: true,
      },
      {
        label: 'Stock Ledger',
        icon: 'receipt_long',
        route: '/inventory/ledger',
        permission: 'INVENTORY_VIEW',
        enabled: true,
      },
      {
        label: 'Batch Tracking',
        icon: 'qr_code',
        route: '/inventory/batches',
        permission: 'INVENTORY_VIEW',
        enabled: true,
      },
      {
        label: 'Adjustments',
        icon: 'tune',
        route: '/inventory/adjustments',
        permission: 'INVENTORY_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: 'Procurement',
    items: [
      {
        label: 'Purchase Orders',
        icon: 'shopping_cart',
        route: '/procurement/purchase-orders',
        permission: 'PURCHASE_VIEW',
        enabled: true,
      },
      {
        label: 'Requisitions',
        icon: 'request_quote',
        route: '/procurement/requisitions',
        permission: 'PURCHASE_VIEW',
        enabled: true,
      },
      {
        label: 'Goods Receipts',
        icon: 'local_shipping',
        route: '/procurement/goods-receipts',
        permission: 'GOODS_RECEIVE',
        enabled: true,
      },
    ],
  },
  {
    label: 'Engineering',
    items: [
      { label: 'Bill of Materials', icon: 'schema', route: '/engineering/bom', permission: 'BOM_VIEW', enabled: true },
      {
        label: 'Product Routing',
        icon: 'route',
        route: '/engineering/routing',
        permission: 'ROUTING_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: 'Production',
    items: [
      {
        label: 'Production Orders',
        icon: 'precision_manufacturing',
        route: '/production/orders',
        permission: 'PRODUCTION_VIEW',
        enabled: true,
      },
      {
        label: 'My Work',
        icon: 'assignment_ind',
        route: '/production/my-work',
        permission: 'PRODUCTION_EXECUTE',
        enabled: true,
      },
    ],
  },
  {
    label: 'Quality',
    items: [
      {
        label: 'Inspections',
        icon: 'fact_check',
        route: '/quality/inspections',
        permission: 'QUALITY_VIEW',
        enabled: true,
      },
      {
        label: 'FG Batches',
        icon: 'inventory_2',
        route: '/quality/fg-batches',
        permission: 'QUALITY_VIEW',
        enabled: true,
      },
      {
        label: 'Rejection Analysis',
        icon: 'bar_chart',
        route: '/quality/rejections',
        permission: 'QUALITY_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: 'Sales',
    items: [
      { label: 'Sales Orders', icon: 'sell', route: '/sales/orders', permission: 'SALES_VIEW', enabled: true },
      {
        label: 'Dispatches',
        icon: 'local_shipping',
        route: '/sales/dispatches',
        permission: 'DISPATCH_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: 'Insight',
    items: [
      {
        label: 'Alerts',
        icon: 'notifications_active',
        route: '/insight/alerts',
        permission: 'ALERT_VIEW',
        enabled: true,
      },
      { label: 'Reports', icon: 'assessment', route: '/insight/reports', permission: 'REPORT_VIEW', enabled: true },
      {
        label: 'Traceability',
        icon: 'track_changes',
        route: '/insight/traceability',
        permission: 'TRACEABILITY_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: 'Finance',
    items: [
      {
        label: 'Dashboard',
        icon: 'account_balance',
        route: '/finance/dashboard',
        permission: 'FINANCE_REPORT',
        enabled: true,
      },
      {
        label: 'Journal Entries',
        icon: 'receipt_long',
        route: '/finance/journals',
        permission: 'GL_VIEW',
        enabled: true,
      },
      {
        label: 'Fiscal Periods',
        icon: 'calendar_today',
        route: '/finance/periods',
        permission: 'GL_VIEW',
        enabled: true,
      },
      { label: 'Accounts Payable', icon: 'payments', route: '/finance/ap', permission: 'AP_MANAGE', enabled: true },
      {
        label: 'Accounts Receivable',
        icon: 'request_quote',
        route: '/finance/ar',
        permission: 'AR_MANAGE',
        enabled: true,
      },
      {
        label: 'Bank Reconciliation',
        icon: 'account_balance_wallet',
        route: '/finance/bank',
        permission: 'BANK_RECONCILE',
        enabled: true,
      },
    ],
  },
  {
    label: 'Controlling',
    items: [
      {
        label: 'Cost Centres',
        icon: 'account_tree',
        route: '/controlling/cost-centres',
        permission: 'CONTROLLING_VIEW',
        enabled: true,
      },
      {
        label: 'Product Costing',
        icon: 'calculate',
        route: '/controlling/cost-estimates',
        permission: 'CONTROLLING_VIEW',
        enabled: true,
      },
      {
        label: 'Variance Analysis',
        icon: 'compare_arrows',
        route: '/controlling/variances',
        permission: 'CONTROLLING_VIEW',
        enabled: true,
      },
      {
        label: 'Controlling Reports',
        icon: 'analytics',
        route: '/controlling/reports',
        permission: 'CONTROLLING_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: 'Planning (MRP)',
    items: [
      { label: 'MRP Runs', icon: 'event_repeat', route: '/mrp/runs', permission: 'MRP_VIEW', enabled: true },
      {
        label: 'MRP Proposals',
        icon: 'playlist_add_check',
        route: '/mrp/proposals',
        permission: 'MRP_VIEW',
        enabled: true,
      },
      {
        label: 'Demand Forecasts',
        icon: 'trending_up',
        route: '/mrp/forecasts',
        permission: 'MRP_VIEW',
        enabled: true,
      },
      { label: 'Planning Parameters', icon: 'tune', route: '/mrp/planning', permission: 'MRP_VIEW', enabled: true },
    ],
  },
  {
    label: 'Maintenance',
    items: [
      { label: 'Maintenance', icon: 'build', route: '/maintenance', permission: 'MAINTENANCE_VIEW', enabled: true },
    ],
  },
  {
    label: 'Administration',
    items: [
      { label: 'Users', icon: 'manage_accounts', route: '/admin/users', permission: 'IAM_USER_MANAGE', enabled: true },
      {
        label: 'Roles',
        icon: 'admin_panel_settings',
        route: '/admin/roles',
        permission: 'IAM_USER_MANAGE',
        enabled: true,
      },
      { label: 'Audit Logs', icon: 'history', route: '/admin/audit-logs', permission: 'IAM_AUDIT_VIEW', enabled: true },
      { label: 'System Settings', icon: 'settings', route: '/admin/settings', permission: 'ADMIN', enabled: true },
    ],
  },
];

export function visibleGroups(groups: NavGroup[] = NAV_GROUPS, session?: SessionStore): NavGroup[] {
  return groups
    .map((g) => ({
      ...g,
      items: g.items.filter((i) => {
        if (!i.enabled) return false;
        if (!i.permission) return true;
        return session ? session.hasPermission(i.permission) : false;
      }),
    }))
    .filter((g) => g.items.length > 0);
}
