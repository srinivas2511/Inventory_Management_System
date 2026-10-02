import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { SessionStore } from '../auth/session.store';
import { HasPermissionDirective } from './has-permission.directive';
import { authGuard, guestGuard, permissionGuard } from './permission.guard';
import { PermissionService } from './permission.service';

function fakeSession(permissions: string[], authenticated = true) {
  return {
    permissions: signal(new Set(permissions)),
    isAuthenticated: signal(authenticated),
  };
}

describe('PermissionService', () => {
  function service(permissions: string[]): PermissionService {
    TestBed.configureTestingModule({ providers: [{ provide: SessionStore, useValue: fakeSession(permissions) }] });
    return TestBed.inject(PermissionService);
  }

  it('answers has / any / all', () => {
    const s = service(['A', 'B']);
    expect(s.has('A')).toBeTrue();
    expect(s.has('C')).toBeFalse();
    expect(s.check(['C', 'B'])).toBeTrue();
    expect(s.check(['C', 'B'], 'all')).toBeFalse();
    expect(s.check(['A', 'B'], 'all')).toBeTrue();
  });

  it('allows when nothing is required', () => {
    const s = service([]);
    expect(s.check(undefined)).toBeTrue();
    expect(s.check(null)).toBeTrue();
    expect(s.check([])).toBeTrue();
  });
});

@Component({
  standalone: true,
  imports: [HasPermissionDirective],
  template: `<button *appHasPermission="'USER_CREATE'" id="create">New</button>`,
})
class HostComponent {}

describe('HasPermissionDirective', () => {
  it('shows the element only while the permission is held', () => {
    const fake = fakeSession([]);
    TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [{ provide: SessionStore, useValue: fake }],
    });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#create')).toBeNull();
    fake.permissions.set(new Set(['USER_CREATE']));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#create')).not.toBeNull();
    fake.permissions.set(new Set());
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#create')).toBeNull();
  });
});

describe('route guards', () => {
  function run(
    guard: typeof authGuard,
    session: ReturnType<typeof fakeSession>,
    data: object = {},
    url = '/admin/users',
  ) {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: SessionStore, useValue: session }],
    });
    return TestBed.runInInjectionContext(() =>
      guard({ data } as unknown as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );
  }

  it('authGuard sends anonymous users to login with the return url', () => {
    const result = run(authGuard, fakeSession([], false)) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/login?returnUrl=%2Fadmin%2Fusers');
  });

  it('authGuard lets signed-in users through', () => {
    expect(run(authGuard, fakeSession([]))).toBeTrue();
  });

  it('guestGuard sends signed-in users home', () => {
    const result = run(guestGuard, fakeSession([])) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/');
    TestBed.resetTestingModule();
    expect(run(guestGuard, fakeSession([], false))).toBeTrue();
  });

  it('permissionGuard passes with the permission and redirects to /forbidden without', () => {
    expect(run(permissionGuard, fakeSession(['USER_VIEW']), { permission: 'USER_VIEW' })).toBeTrue();
    TestBed.resetTestingModule();
    const result = run(permissionGuard, fakeSession([]), { permission: 'USER_VIEW' }) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/forbidden');
  });
});
