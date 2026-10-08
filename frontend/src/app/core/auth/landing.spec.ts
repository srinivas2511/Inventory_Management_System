import { TestBed } from '@angular/core/testing';
import { landingRoute } from './landing';
import { SessionStore } from './session.store';

describe('landingRoute', () => {
  let session: SessionStore;

  beforeEach(() => {
    session = TestBed.inject(SessionStore);
  });

  const signIn = (roles: string[], permissions: string[]) =>
    session.setUser({ id: 1, username: 'asha', fullName: 'Asha Rao', roles, permissions, mustChangePassword: false });

  it('sends nobody to a screen when nobody is signed in', () => {
    expect(landingRoute(session)).toBe('/login');
  });

  it('sends a role with a dashboard to it', () => {
    signIn(['ENGINEER'], ['DASHBOARD_VIEW', 'PRODUCT_VIEW']);
    expect(landingRoute(session)).toBe('/dashboard');
  });

  it('sends an operator to My Work, since an operator has no dashboard', () => {
    signIn(['OPERATOR'], ['PRODUCTION_EXECUTE']);
    expect(landingRoute(session)).toBe('/production/my-work');
  });

  it('sends dispatch to the dispatch queue and maintenance to machine status', () => {
    signIn(['DISPATCH'], ['DISPATCH_VIEW', 'SALES_VIEW', 'INVENTORY_VIEW']);
    expect(landingRoute(session)).toBe('/sales/dispatches');

    signIn(['MAINTENANCE'], ['MAINTENANCE_VIEW', 'INVENTORY_VIEW']);
    expect(landingRoute(session)).toBe('/maintenance');
  });

  it('prefers the dashboard when a user with several roles holds the permission', () => {
    signIn(['ADMIN', 'OPERATOR'], ['DASHBOARD_VIEW', 'PRODUCTION_EXECUTE']);
    expect(landingRoute(session)).toBe('/dashboard');
  });

  it('falls back to the first menu entry the user may open, never to a screen they cannot use', () => {
    signIn(['ACCOUNTANT'], ['GL_VIEW']);
    expect(landingRoute(session)).toBe('/finance/journals');
  });

  it('keeps a plant-scoped user off the dashboard and the all-plant screens, which the server refuses them', () => {
    session.setUser({
      id: 1,
      username: 'asha',
      fullName: 'Asha Rao',
      roles: ['PURCHASE_MANAGER'],
      permissions: ['DASHBOARD_VIEW', 'REPORT_VIEW', 'PURCHASE_VIEW'],
      mustChangePassword: false,
      plantScoped: true,
    });
    const landing = landingRoute(session);
    expect(landing).not.toBe('/dashboard');
    expect(landing).not.toContain('/insight');
  });

  it('still sends a user without plant restrictions to the dashboard', () => {
    session.setUser({
      id: 1,
      username: 'asha',
      fullName: 'Asha Rao',
      roles: ['PURCHASE_MANAGER'],
      permissions: ['DASHBOARD_VIEW', 'PURCHASE_VIEW'],
      mustChangePassword: false,
      plantScoped: false,
    });
    expect(landingRoute(session)).toBe('/dashboard');
  });

  it('uses the notifications page, which needs no permission, when nothing else is allowed', () => {
    signIn(['NOBODY'], []);
    expect(landingRoute(session)).toBe('/notifications');
  });
});
