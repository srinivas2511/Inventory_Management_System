import { SessionStore } from '../auth/session.store';

export interface NavItem {
  label: string;
  icon: string;
  route: string;
  permission?: string;
  enabled: boolean;
  /** Covers all plants (aggregates, finance, planning, audit, integration): hidden from a plant-scoped user. */
  companyLevel?: boolean;
}

export interface NavGroup {
  label: string;
  items: NavItem[];
  /** Every item covers all plants. */
  companyLevel?: boolean;
}

export const NAV_GROUPS: NavGroup[] = [
  {
    label: $localize`:@@nav.group-overview:Overview`,
    items: [
      {
        label: $localize`:@@nav.item-dashboard:Dashboard`,
        icon: 'dashboard',
        route: '/dashboard',
        permission: 'DASHBOARD_VIEW',
        enabled: true,
        companyLevel: true,
      },
      {
        label: $localize`:@@nav.item-notifications:Notifications`,
        icon: 'notifications',
        route: '/notifications',
        enabled: true,
      },
    ],
  },
  {
    label: $localize`:@@nav.group-master-data:Master Data`,
    items: [
      {
        label: $localize`:@@nav.item-materials:Materials`,
        icon: 'category',
        route: '/master/materials',
        permission: 'MATERIAL_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-products:Products`,
        icon: 'settings_input_component',
        route: '/master/products',
        permission: 'PRODUCT_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-suppliers:Suppliers`,
        icon: 'local_shipping',
        route: '/master/suppliers',
        permission: 'SUPPLIER_MANAGE',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-customers:Customers`,
        icon: 'groups',
        route: '/master/customers',
        permission: 'CUSTOMER_MANAGE',
        enabled: true,
      },
    ],
  },
  {
    label: $localize`:@@nav.group-warehouses-inventory:Warehouses & Inventory`,
    items: [
      {
        label: $localize`:@@nav.item-warehouses:Warehouses`,
        icon: 'warehouse',
        route: '/warehouse/warehouses',
        permission: 'INVENTORY_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-current-stock:Current Stock`,
        icon: 'inventory_2',
        route: '/inventory/stock',
        permission: 'INVENTORY_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-stock-ledger:Stock Ledger`,
        icon: 'receipt_long',
        route: '/inventory/ledger',
        permission: 'INVENTORY_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-batch-tracking:Batch Tracking`,
        icon: 'qr_code',
        route: '/inventory/batches',
        permission: 'INVENTORY_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-adjustments:Adjustments`,
        icon: 'tune',
        route: '/inventory/adjustments',
        permission: 'INVENTORY_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: $localize`:@@nav.group-procurement:Procurement`,
    items: [
      {
        label: $localize`:@@nav.item-purchase-orders:Purchase Orders`,
        icon: 'shopping_cart',
        route: '/procurement/purchase-orders',
        permission: 'PURCHASE_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-requisitions:Requisitions`,
        icon: 'request_quote',
        route: '/procurement/requisitions',
        permission: 'PURCHASE_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-goods-receipts:Goods Receipts`,
        icon: 'local_shipping',
        route: '/procurement/goods-receipts',
        permission: 'INVENTORY_RECEIVE',
        enabled: true,
      },
    ],
  },
  {
    label: $localize`:@@nav.group-engineering:Engineering`,
    items: [
      {
        label: $localize`:@@nav.item-bill-of-materials:Bill of Materials`,
        icon: 'schema',
        route: '/engineering/bom',
        permission: 'BOM_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-product-routing:Product Routing`,
        icon: 'route',
        route: '/engineering/routing',
        permission: 'PRODUCT_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: $localize`:@@nav.group-production:Production`,
    items: [
      {
        label: $localize`:@@nav.item-production-orders:Production Orders`,
        icon: 'precision_manufacturing',
        route: '/production/orders',
        permission: 'PRODUCTION_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-my-work:My Work`,
        icon: 'assignment_ind',
        route: '/production/my-work',
        permission: 'PRODUCTION_EXECUTE',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-schedule:Schedule`,
        icon: 'view_timeline',
        route: '/production/schedule',
        permission: 'SCHEDULE_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-oee:OEE`,
        icon: 'speed',
        route: '/production/oee',
        permission: 'OEE_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: $localize`:@@nav.group-quality:Quality`,
    items: [
      {
        label: $localize`:@@nav.item-inspections:Inspections`,
        icon: 'fact_check',
        route: '/quality/inspections',
        permission: 'QUALITY_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-fg-batches:FG Batches`,
        icon: 'inventory_2',
        route: '/quality/fg-batches',
        permission: 'QUALITY_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-rejection-analysis:Rejection Analysis`,
        icon: 'bar_chart',
        route: '/quality/rejections',
        permission: 'QUALITY_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-spc-charts:SPC Charts`,
        icon: 'show_chart',
        route: '/quality/spc',
        permission: 'QUALITY_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-capa:CAPA`,
        icon: 'assignment_late',
        route: '/quality/capa',
        permission: 'QUALITY_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-complaints:Complaints`,
        icon: 'report_problem',
        route: '/quality/complaints',
        permission: 'QUALITY_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-first-article:First Article`,
        icon: 'rule',
        route: '/quality/fai',
        permission: 'QUALITY_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: $localize`:@@nav.group-sales:Sales`,
    items: [
      {
        label: $localize`:@@nav.item-sales-orders:Sales Orders`,
        icon: 'sell',
        route: '/sales/orders',
        permission: 'SALES_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-dispatches:Dispatches`,
        icon: 'local_shipping',
        route: '/sales/dispatches',
        permission: 'DISPATCH_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: $localize`:@@nav.group-insight:Insight`,
    companyLevel: true,
    items: [
      {
        label: $localize`:@@nav.item-alerts:Alerts`,
        icon: 'notifications_active',
        route: '/insight/alerts',
        permission: 'ALERT_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-reports:Reports`,
        icon: 'assessment',
        route: '/insight/reports',
        permission: 'REPORT_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-analytics:Analytics`,
        icon: 'insights',
        route: '/analytics',
        permission: 'ANALYTICS_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-analytics-explorer:Analytics Explorer`,
        icon: 'query_stats',
        route: '/analytics/explorer',
        permission: 'ANALYTICS_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-traceability:Traceability`,
        icon: 'track_changes',
        route: '/insight/traceability',
        permission: 'TRACEABILITY_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: $localize`:@@nav.group-finance:Finance`,
    companyLevel: true,
    items: [
      {
        label: $localize`:@@nav.item-dashboard-2:Dashboard`,
        icon: 'account_balance',
        route: '/finance/dashboard',
        permission: 'FINANCE_REPORT',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-journal-entries:Journal Entries`,
        icon: 'receipt_long',
        route: '/finance/journals',
        permission: 'GL_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-fiscal-periods:Fiscal Periods`,
        icon: 'calendar_today',
        route: '/finance/periods',
        permission: 'GL_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-accounts-payable:Accounts Payable`,
        icon: 'payments',
        route: '/finance/ap',
        permission: 'AP_MANAGE',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-accounts-receivable:Accounts Receivable`,
        icon: 'request_quote',
        route: '/finance/ar',
        permission: 'AR_MANAGE',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-ar-reconciliation:AR Reconciliation`,
        icon: 'sync_problem',
        route: '/finance/ar-reconciliation',
        permission: 'AR_MANAGE',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-credit-notes:Credit Notes`,
        icon: 'receipt_long',
        route: '/finance/credit-notes',
        permission: 'AR_MANAGE',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-debit-notes:Debit Notes`,
        icon: 'remove_circle_outline',
        route: '/finance/debit-notes',
        permission: 'AP_MANAGE',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-gst-tax:GST Tax`,
        icon: 'percent',
        route: '/finance/tax',
        permission: 'GL_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-exchange-rates:Exchange Rates`,
        icon: 'currency_exchange',
        route: '/finance/exchange-rates',
        permission: 'GL_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-budget-vs-actual:Budget vs Actual`,
        icon: 'balance',
        route: '/finance/budget',
        permission: 'GL_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-bank-reconciliation:Bank Reconciliation`,
        icon: 'account_balance_wallet',
        route: '/finance/bank',
        permission: 'BANK_RECONCILE',
        enabled: true,
      },
    ],
  },
  {
    label: $localize`:@@nav.group-controlling:Controlling`,
    companyLevel: true,
    items: [
      {
        label: $localize`:@@nav.item-cost-centres:Cost Centres`,
        icon: 'account_tree',
        route: '/controlling/cost-centres',
        permission: 'CONTROLLING_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-product-costing:Product Costing`,
        icon: 'calculate',
        route: '/controlling/cost-estimates',
        permission: 'CONTROLLING_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-variance-analysis:Variance Analysis`,
        icon: 'compare_arrows',
        route: '/controlling/variances',
        permission: 'CONTROLLING_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-controlling-reports:Controlling Reports`,
        icon: 'analytics',
        route: '/controlling/reports',
        permission: 'CONTROLLING_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: $localize`:@@nav.group-planning-mrp:Planning (MRP)`,
    companyLevel: true,
    items: [
      {
        label: $localize`:@@nav.item-mrp-runs:MRP Runs`,
        icon: 'event_repeat',
        route: '/mrp/runs',
        permission: 'MRP_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-mrp-proposals:MRP Proposals`,
        icon: 'playlist_add_check',
        route: '/mrp/proposals',
        permission: 'MRP_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-demand-forecasts:Demand Forecasts`,
        icon: 'trending_up',
        route: '/mrp/forecasts',
        permission: 'MRP_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-planning-parameters:Planning Parameters`,
        icon: 'tune',
        route: '/mrp/planning',
        permission: 'MRP_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: $localize`:@@nav.group-documents:Documents`,
    items: [
      {
        label: $localize`:@@nav.item-documents:Documents`,
        icon: 'folder_open',
        route: '/documents',
        permission: 'DOCUMENT_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: $localize`:@@nav.group-plm:PLM`,
    items: [
      {
        label: $localize`:@@nav.item-change-orders:Change Orders`,
        icon: 'published_with_changes',
        route: '/plm/ecos',
        permission: 'PLM_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-product-lifecycle:Product Lifecycle`,
        icon: 'timeline',
        route: '/plm/lifecycle',
        permission: 'PLM_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: $localize`:@@nav.group-crm:CRM`,
    items: [
      {
        label: $localize`:@@nav.item-leads:Leads`,
        icon: 'person_search',
        route: '/crm/leads',
        permission: 'CRM_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-opportunities:Opportunities`,
        icon: 'view_kanban',
        route: '/crm/opportunities',
        permission: 'CRM_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-quotations:Quotations`,
        icon: 'request_quote',
        route: '/crm/quotations',
        permission: 'CRM_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-contacts:Contacts`,
        icon: 'contacts',
        route: '/crm/contacts',
        permission: 'CRM_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-customer-activity:Customer Activity`,
        icon: 'history',
        route: '/crm/activities',
        permission: 'CRM_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-customer-segments:Customer Segments`,
        icon: 'label',
        route: '/crm/customers',
        permission: 'CRM_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-price-lists:Price Lists`,
        icon: 'sell',
        route: '/crm/prices',
        permission: 'CRM_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-sales-pipeline:Sales Pipeline`,
        icon: 'filter_alt',
        route: '/crm/pipeline',
        permission: 'CRM_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: $localize`:@@nav.group-hr:HR`,
    items: [
      {
        label: $localize`:@@nav.item-employees:Employees`,
        icon: 'badge',
        route: '/hrm/employees',
        permission: 'HR_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-attendance:Attendance`,
        icon: 'event_available',
        route: '/hrm/attendance',
        permission: 'HR_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-shifts:Shifts`,
        icon: 'schedule',
        route: '/hrm/shifts',
        permission: 'HR_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-leave:Leave`,
        icon: 'beach_access',
        route: '/hrm/leave',
        permission: 'LEAVE_APPLY',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-training:Training`,
        icon: 'school',
        route: '/hrm/training',
        permission: 'HR_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-payroll:Payroll`,
        icon: 'payments',
        route: '/hrm/payroll',
        permission: 'PAYROLL_RUN',
        enabled: true,
      },
    ],
  },
  {
    label: $localize`:@@nav.group-warehouse-operations:Warehouse Operations`,
    items: [
      {
        label: $localize`:@@nav.item-zones-bins:Zones & Bins`,
        icon: 'grid_view',
        route: '/wms/bins',
        permission: 'WMS_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-bin-view:Bin View`,
        icon: 'inventory',
        route: '/wms/bin-view',
        permission: 'WMS_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-put-away:Put-away`,
        icon: 'move_to_inbox',
        route: '/wms/putaway',
        permission: 'WMS_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-picking:Picking`,
        icon: 'shopping_cart_checkout',
        route: '/wms/picking',
        permission: 'WMS_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-pick-waves:Pick Waves`,
        icon: 'waves',
        route: '/wms/waves',
        permission: 'WMS_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-transfers:Transfers`,
        icon: 'swap_horiz',
        route: '/wms/transfers',
        permission: 'WMS_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-cross-dock:Cross-dock`,
        icon: 'sync_alt',
        route: '/wms/cross-dock',
        permission: 'WMS_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-cycle-counts:Cycle Counts`,
        icon: 'fact_check',
        route: '/wms/cycle-counts',
        permission: 'WMS_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: $localize`:@@nav.group-maintenance:Maintenance`,
    items: [
      {
        label: $localize`:@@nav.item-maintenance:Maintenance`,
        icon: 'build',
        route: '/maintenance',
        permission: 'MAINTENANCE_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-spare-parts:Spare Parts`,
        icon: 'handyman',
        route: '/maintenance/spare-parts',
        permission: 'SPARE_PART_VIEW',
        enabled: true,
      },
    ],
  },
  {
    label: $localize`:@@nav.group-administration:Administration`,
    items: [
      {
        label: $localize`:@@nav.item-users:Users`,
        icon: 'manage_accounts',
        route: '/admin/users',
        permission: 'USER_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-roles:Roles`,
        icon: 'admin_panel_settings',
        route: '/admin/roles',
        permission: 'ROLE_MANAGE',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-audit-logs:Audit Logs`,
        icon: 'history',
        route: '/admin/audit-logs',
        permission: 'AUDIT_VIEW',
        enabled: true,
        companyLevel: true,
      },
      {
        label: $localize`:@@nav.item-modules:Modules`,
        icon: 'extension',
        route: '/admin/modules',
        permission: 'MODULE_MANAGE',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-plants:Plants`,
        icon: 'factory',
        route: '/admin/plants',
        permission: 'PLANT_MANAGE',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-notification-admin:Notification Admin`,
        icon: 'mark_email_read',
        route: '/admin/notifications',
        permission: 'NOTIFICATION_MANAGE',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-webhooks:Webhooks`,
        icon: 'webhook',
        route: '/admin/webhooks',
        companyLevel: true,
        permission: 'WEBHOOK_VIEW',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-data-import-export:Data Import / Export`,
        icon: 'import_export',
        route: '/admin/data',
        companyLevel: true,
        permission: 'DATA_EXPORT',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-system-settings:System Settings`,
        icon: 'settings',
        route: '/admin/settings',
        permission: 'SETTINGS_MANAGE',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-attribute-definitions:Attribute Definitions`,
        icon: 'tune',
        route: '/admin/attribute-definitions',
        permission: 'ATTRIBUTE_MANAGE',
        enabled: true,
      },
      {
        label: $localize`:@@nav.item-negative-stock:Negative Stock`,
        icon: 'remove_circle_outline',
        route: '/admin/negative-stock-overrides',
        permission: 'INVENTORY_NEGATIVE_OVERRIDE',
        enabled: true,
      },
    ],
  },
];

/** The optional module a screen belongs to, by route; the always-active core belongs to none. */
const MODULE_ROUTES: [string, string][] = [
  ['/finance', 'FINANCE'],
  ['/controlling', 'CONTROLLING'],
  ['/mrp', 'MRP'],
  ['/crm', 'CRM'],
  ['/hrm', 'HRM'],
  ['/wms', 'WMS'],
  ['/plm', 'PLM'],
  ['/quality/spc', 'QUALITY_PLUS'],
  ['/quality/capa', 'QUALITY_PLUS'],
  ['/quality/complaints', 'QUALITY_PLUS'],
  ['/quality/fai', 'QUALITY_PLUS'],
  ['/documents', 'DOCUMENTS'],
  ['/analytics', 'ANALYTICS'],
];

export function moduleOfRoute(route: string): string | null {
  const match = MODULE_ROUTES.find(([prefix]) => route === prefix || route.startsWith(prefix + '/'));
  return match ? match[1] : null;
}

export function visibleGroups(
  groups: NavGroup[] = NAV_GROUPS,
  session?: SessionStore,
  inactiveModules: ReadonlySet<string> = new Set(),
): NavGroup[] {
  return groups
    .map((g) => ({
      ...g,
      items: g.items.filter((i) => {
        if (!i.enabled) return false;
        if (session?.user()?.plantScoped && (g.companyLevel || i.companyLevel)) return false;
        const module = moduleOfRoute(i.route);
        if (module && inactiveModules.has(module)) return false;
        if (!i.permission) return true;
        return session ? session.hasPermission(i.permission) : false;
      }),
    }))
    .filter((g) => g.items.length > 0);
}
