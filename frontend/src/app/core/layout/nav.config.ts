/**
 * Navigation model. An item is shown when it is enabled and the user holds its `permission` (any of a list);
 * a group appears only when it has at least one shown item (DESIGN.md section 8.2). UX only: the backend enforces
 * access. Items are enabled phase by phase.
 */
export interface NavItem {
  label: string;
  icon: string;
  route: string;
  /** Permission code(s) required to see the item; with several, any one is enough. */
  permission?: string | string[];
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
      { label: 'Users', icon: 'manage_accounts', route: '/admin/users', permission: 'USER_VIEW', enabled: true },
      {
        label: 'Roles',
        icon: 'admin_panel_settings',
        route: '/admin/roles',
        permission: 'ROLE_MANAGE',
        enabled: true,
      },
      { label: 'Audit Logs', icon: 'history', route: '/admin/audit-logs', permission: 'AUDIT_VIEW', enabled: false },
    ],
  },
];

/** Decides whether the user may see an item; the shell passes {@code PermissionService.check}. */
export type PermissionCheck = (required: string | string[] | undefined) => boolean;

export function visibleGroups(groups: NavGroup[] = NAV_GROUPS, allowed: PermissionCheck = () => true): NavGroup[] {
  return groups
    .map((g) => ({ ...g, items: g.items.filter((i) => i.enabled && allowed(i.permission)) }))
    .filter((g) => g.items.length > 0);
}
