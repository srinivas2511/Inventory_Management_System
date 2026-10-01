/**
 * Navigation model. Each group appears only when it has at least one enabled item; from Phase 1 items are also
 * filtered by `permission` (UX only - the backend enforces access). Items are enabled phase by phase.
 */
export interface NavItem {
  label: string;
  icon: string;
  route: string;
  /** Permission code required to see the item (checked from Phase 1). */
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
      { label: 'Materials', icon: 'category', route: '/master/materials', permission: 'MATERIAL_VIEW', enabled: false },
      {
        label: 'Spring Products',
        icon: 'settings_input_component',
        route: '/master/products',
        permission: 'PRODUCT_VIEW',
        enabled: false,
      },
      {
        label: 'Suppliers',
        icon: 'local_shipping',
        route: '/master/suppliers',
        permission: 'SUPPLIER_MANAGE',
        enabled: false,
      },
      { label: 'Customers', icon: 'groups', route: '/master/customers', permission: 'CUSTOMER_MANAGE', enabled: false },
    ],
  },
  {
    label: 'Administration',
    items: [
      { label: 'Users', icon: 'manage_accounts', route: '/admin/users', permission: 'USER_VIEW', enabled: false },
      {
        label: 'Roles',
        icon: 'admin_panel_settings',
        route: '/admin/roles',
        permission: 'ROLE_MANAGE',
        enabled: false,
      },
      { label: 'Audit Logs', icon: 'history', route: '/admin/audit-logs', permission: 'AUDIT_VIEW', enabled: false },
    ],
  },
];

export function visibleGroups(groups: NavGroup[] = NAV_GROUPS): NavGroup[] {
  return groups.map((g) => ({ ...g, items: g.items.filter((i) => i.enabled) })).filter((g) => g.items.length > 0);
}
