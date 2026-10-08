import { SessionStore } from '../auth/session.store';
import { NAV_GROUPS, moduleOfRoute, visibleGroups } from './nav.config';

describe('navigation and optional modules', () => {
  const everything = (): SessionStore => {
    const session = new SessionStore();
    session.setUser({
      id: 1,
      username: 'admin',
      fullName: 'Admin',
      roles: ['ADMIN'],
      permissions: NAV_GROUPS.flatMap((g) => g.items.map((i) => i.permission ?? '')).filter(Boolean),
      mustChangePassword: false,
    });
    return session;
  };
  const routes = (inactive: string[]): string[] =>
    visibleGroups(NAV_GROUPS, everything(), new Set(inactive)).flatMap((g) => g.items.map((i) => i.route));

  it('maps a route to its optional module, and the core to none', () => {
    expect(moduleOfRoute('/finance/journals')).toBe('FINANCE');
    expect(moduleOfRoute('/quality/spc')).toBe('QUALITY_PLUS');
    expect(moduleOfRoute('/crm/leads')).toBe('CRM');
    expect(moduleOfRoute('/quality/inspections')).toBeNull();
    expect(moduleOfRoute('/production/orders')).toBeNull();
    expect(moduleOfRoute('/financeless')).toBeNull();
  });

  it('shows every screen while no module is switched off', () => {
    const all = routes([]);
    expect(all.some((r) => r.startsWith('/crm'))).toBeTrue();
    expect(all.some((r) => r.startsWith('/finance'))).toBeTrue();
  });

  it('hides the screens of a switched-off module and nothing else', () => {
    const without = routes(['CRM']);
    expect(without.some((r) => r.startsWith('/crm'))).toBeFalse();
    expect(without.some((r) => r.startsWith('/finance'))).toBeTrue();
    expect(without).toContain('/quality/inspections');
  });

  it('hides only the advanced quality screens when advanced quality is off, keeping inspections', () => {
    const without = routes(['QUALITY_PLUS']);
    expect(without).not.toContain('/quality/spc');
    expect(without).not.toContain('/quality/capa');
    expect(without).toContain('/quality/inspections');
    expect(without).toContain('/quality/fg-batches');
  });
});
