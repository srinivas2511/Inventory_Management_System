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
