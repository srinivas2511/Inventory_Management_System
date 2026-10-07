import { NAV_GROUPS } from '../layout/nav.config';
import { SessionStore } from './session.store';

/**
 * DESIGN §8.6: roles whose landing screen is not a dashboard. They hold no dashboard permission, so they must never be
 * sent to /dashboard (the KPI call would be refused with a 403 on the first screen they see).
 */
const ROLE_LANDING: readonly { role: string; route: string; permission: string }[] = [
  { role: 'OPERATOR', route: '/production/my-work', permission: 'PRODUCTION_EXECUTE' },
  { role: 'DISPATCH', route: '/sales/dispatches', permission: 'DISPATCH_VIEW' },
  { role: 'MAINTENANCE', route: '/maintenance', permission: 'MAINTENANCE_VIEW' },
];

/**
 * The first screen this user may use: their dashboard when they hold the permission, the documented landing for the
 * non-dashboard roles, otherwise the first menu entry they are allowed to open. A missing grant can therefore never
 * produce a failed API call or a redirect loop on arrival. /notifications needs no permission and is the last resort.
 */
export function landingRoute(session: SessionStore): string {
  const user = session.user();
  if (!user) {
    return '/login';
  }
  if (session.hasPermission('DASHBOARD_VIEW')) {
    return '/dashboard';
  }
  for (const landing of ROLE_LANDING) {
    if (user.roles.includes(landing.role) && session.hasPermission(landing.permission)) {
      return landing.route;
    }
  }
  for (const group of NAV_GROUPS) {
    for (const item of group.items) {
      if (item.enabled && item.permission && session.hasPermission(item.permission)) {
        return item.route;
      }
    }
  }
  return '/notifications';
}
