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
    items: [
      { label: 'Dashboard', icon: 'dashboard', route: '/dashboard', permission: 'DASHBOARD_VIEW', enabled: true },
      { label: 'Notifications', icon: 'notifications', route: '/notifications', enabled: true },
    ],
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
        permission: 'INVENTORY_RECEIVE',
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
        permission: 'PRODUCT_VIEW',
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
      {
        label: 'Schedule',
        icon: 'view_timeline',
        route: '/production/schedule',
        permission: 'SCHEDULE_VIEW',
        enabled: true,
      },
      { label: 'OEE', icon: 'speed', route: '/production/oee', permission: 'OEE_VIEW', enabled: true },
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
      { label: 'SPC Charts', icon: 'show_chart', route: '/quality/spc', permission: 'QUALITY_VIEW', enabled: true },
      { label: 'CAPA', icon: 'assignment_late', route: '/quality/capa', permission: 'QUALITY_VIEW', enabled: true },
      {
        label: 'Complaints',
        icon: 'report_problem',
        route: '/quality/complaints',
        permission: 'QUALITY_VIEW',
        enabled: true,
      },
      { label: 'First Article', icon: 'rule', route: '/quality/fai', permission: 'QUALITY_VIEW', enabled: true },
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
        label: 'Analytics',
        icon: 'insights',
        route: '/analytics',
        permission: 'ANALYTICS_VIEW',
        enabled: true,
      },
      {
        label: 'Analytics Explorer',
        icon: 'query_stats',
        route: '/analytics/explorer',
        permission: 'ANALYTICS_VIEW',
        enabled: true,
      },
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
        label: 'AR Reconciliation',
        icon: 'sync_problem',
        route: '/finance/ar-reconciliation',
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
    label: 'Documents',
    items: [
      { label: 'Documents', icon: 'folder_open', route: '/documents', permission: 'DOCUMENT_VIEW', enabled: true },
    ],
  },
  {
    label: 'PLM',
    items: [
      {
        label: 'Change Orders',
        icon: 'published_with_changes',
        route: '/plm/ecos',
        permission: 'PLM_VIEW',
        enabled: true,
      },
      {
        label: 'Product Lifecycle',
        icon: 'timeline',
        route: '/plm/lifecycle',
        permission: 'PLM_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: 'CRM',
    items: [
      { label: 'Leads', icon: 'person_search', route: '/crm/leads', permission: 'CRM_VIEW', enabled: true },
      {
        label: 'Opportunities',
        icon: 'view_kanban',
        route: '/crm/opportunities',
        permission: 'CRM_VIEW',
        enabled: true,
      },
      { label: 'Quotations', icon: 'request_quote', route: '/crm/quotations', permission: 'CRM_VIEW', enabled: true },
      { label: 'Contacts', icon: 'contacts', route: '/crm/contacts', permission: 'CRM_VIEW', enabled: true },
      { label: 'Customer Activity', icon: 'history', route: '/crm/activities', permission: 'CRM_VIEW', enabled: true },
      { label: 'Customer Segments', icon: 'label', route: '/crm/customers', permission: 'CRM_VIEW', enabled: true },
      { label: 'Price Lists', icon: 'sell', route: '/crm/prices', permission: 'CRM_VIEW', enabled: true },
      { label: 'Sales Pipeline', icon: 'filter_alt', route: '/crm/pipeline', permission: 'CRM_VIEW', enabled: true },
    ],
  },
  {
    label: 'HR',
    items: [
      { label: 'Employees', icon: 'badge', route: '/hrm/employees', permission: 'HR_VIEW', enabled: true },
      { label: 'Attendance', icon: 'event_available', route: '/hrm/attendance', permission: 'HR_VIEW', enabled: true },
      { label: 'Shifts', icon: 'schedule', route: '/hrm/shifts', permission: 'HR_VIEW', enabled: true },
      { label: 'Leave', icon: 'beach_access', route: '/hrm/leave', permission: 'LEAVE_APPLY', enabled: true },
      { label: 'Training', icon: 'school', route: '/hrm/training', permission: 'HR_VIEW', enabled: true },
      { label: 'Payroll', icon: 'payments', route: '/hrm/payroll', permission: 'PAYROLL_RUN', enabled: true },
    ],
  },
  {
    label: 'Warehouse Operations',
    items: [
      { label: 'Zones & Bins', icon: 'grid_view', route: '/wms/bins', permission: 'WMS_VIEW', enabled: true },
      { label: 'Bin View', icon: 'inventory', route: '/wms/bin-view', permission: 'WMS_VIEW', enabled: true },
      { label: 'Put-away', icon: 'move_to_inbox', route: '/wms/putaway', permission: 'WMS_VIEW', enabled: true },
      {
        label: 'Picking',
        icon: 'shopping_cart_checkout',
        route: '/wms/picking',
        permission: 'WMS_VIEW',
        enabled: true,
      },
      { label: 'Pick Waves', icon: 'waves', route: '/wms/waves', permission: 'WMS_VIEW', enabled: true },
      { label: 'Transfers', icon: 'swap_horiz', route: '/wms/transfers', permission: 'WMS_VIEW', enabled: true },
      { label: 'Cross-dock', icon: 'sync_alt', route: '/wms/cross-dock', permission: 'WMS_VIEW', enabled: true },
      { label: 'Cycle Counts', icon: 'fact_check', route: '/wms/cycle-counts', permission: 'WMS_VIEW', enabled: true },
    ],
  },
  {
    label: 'Maintenance',
    items: [
      { label: 'Maintenance', icon: 'build', route: '/maintenance', permission: 'MAINTENANCE_VIEW', enabled: true },
      {
        label: 'Spare Parts',
        icon: 'handyman',
        route: '/maintenance/spare-parts',
        permission: 'SPARE_PART_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: 'Administration',
    items: [
      { label: 'Users', icon: 'manage_accounts', route: '/admin/users', permission: 'USER_VIEW', enabled: true },
      {
        label: 'Roles',
        icon: 'admin_panel_settings',
        route: '/admin/roles',
        permission: 'ROLE_MANAGE',
        enabled: true,
      },
      { label: 'Audit Logs', icon: 'history', route: '/admin/audit-logs', permission: 'AUDIT_VIEW', enabled: true },
      {
        label: 'Notification Admin',
        icon: 'mark_email_read',
        route: '/admin/notifications',
        permission: 'NOTIFICATION_MANAGE',
        enabled: true,
      },
      {
        label: 'Webhooks',
        icon: 'webhook',
        route: '/admin/webhooks',
        permission: 'WEBHOOK_VIEW',
        enabled: true,
      },
      {
        label: 'Data Import / Export',
        icon: 'import_export',
        route: '/admin/data',
        permission: 'DATA_EXPORT',
        enabled: true,
      },
      {
        label: 'System Settings',
        icon: 'settings',
        route: '/admin/settings',
        permission: 'SETTINGS_MANAGE',
        enabled: true,
      },
      {
        label: 'Attribute Definitions',
        icon: 'tune',
        route: '/admin/attribute-definitions',
        permission: 'ATTRIBUTE_MANAGE',
        enabled: true,
      },
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
