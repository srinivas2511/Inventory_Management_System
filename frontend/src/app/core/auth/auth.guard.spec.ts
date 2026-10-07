import { TestBed } from '@angular/core/testing';
import { Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { authGuard, guestGuard, permissionGuard } from './auth.guard';
import { SessionStore } from './session.store';

const state = (url: string) => ({ url }) as RouterStateSnapshot;

describe('auth guards', () => {
  let session: SessionStore;
  let router: Router;

  const run = (guard: typeof authGuard, url = '/inventory/stock') =>
    TestBed.runInInjectionContext(() => guard({} as never, state(url)));

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    session = TestBed.inject(SessionStore);
    router = TestBed.inject(Router);
  });

  const signIn = (permissions: string[]) =>
    session.setUser({
      id: 1,
      username: 'asha',
      fullName: 'Asha Rao',
      roles: [],
      permissions,
      mustChangePassword: false,
    });

  it('lets a signed-in user through', () => {
    signIn([]);
    expect(run(authGuard)).toBeTrue();
  });

  it('sends a signed-out user to login and remembers the page they asked for', () => {
    const result = run(authGuard, '/inventory/stock?warehouse=2') as UrlTree;
    expect(router.serializeUrl(result)).toBe('/login?returnUrl=%2Finventory%2Fstock%3Fwarehouse%3D2');
  });

  it('does not remember a root visit or a sign-in page', () => {
    expect(router.serializeUrl(run(authGuard, '/') as UrlTree)).toBe('/login');
    expect(router.serializeUrl(run(authGuard, '/login') as UrlTree)).toBe('/login');
  });

  it('checks the permission after the sign-in', () => {
    const guard = permissionGuard('USER_VIEW');
    expect(router.serializeUrl(run(guard, '/admin/users') as UrlTree)).toBe('/login?returnUrl=%2Fadmin%2Fusers');

    signIn(['PRODUCT_VIEW']);
    expect(router.serializeUrl(run(guard, '/admin/users') as UrlTree)).toBe('/dashboard');

    signIn(['USER_VIEW']);
    expect(run(guard, '/admin/users')).toBeTrue();
  });

  it('keeps the login page for people who are not signed in', () => {
    expect(run(guestGuard)).toBeTrue();
    signIn([]);
    expect(router.serializeUrl(run(guestGuard) as UrlTree)).toBe('/dashboard');
  });
});
