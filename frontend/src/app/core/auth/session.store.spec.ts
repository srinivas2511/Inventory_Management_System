import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ToastService } from '../notification/toast.service';
import { SessionStore } from './session.store';
import { TokenStore } from './token.store';

const user = {
  id: 1,
  username: 'asha',
  fullName: 'Asha',
  roles: ['ADMIN'],
  permissions: ['USER_VIEW'],
  primaryDashboard: 'ADMIN',
};
const session = { accessToken: 'tok', expiresIn: 900, mustChangePassword: false, user };

describe('SessionStore', () => {
  let store: SessionStore;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['info', 'error', 'success']) },
      ],
    });
    store = TestBed.inject(SessionStore);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('signs in and exposes the user, permissions and token', () => {
    let outcome: string | undefined;
    store.login('asha', 'pw').subscribe((o) => (outcome = o));
    const req = backend.expectOne('/api/auth/login');
    expect(req.request.withCredentials).toBeTrue();
    req.flush(session);
    expect(outcome).toBe('authenticated');
    expect(store.isAuthenticated()).toBeTrue();
    expect(store.permissions().has('USER_VIEW')).toBeTrue();
    expect(TestBed.inject(TokenStore).accessToken()).toBe('tok');
  });

  it('reports a forced password change without creating a session', () => {
    let outcome: string | undefined;
    store.login('asha', 'pw').subscribe((o) => (outcome = o));
    backend.expectOne('/api/auth/login').flush({ mustChangePassword: true });
    expect(outcome).toBe('mustChangePassword');
    expect(store.isAuthenticated()).toBeFalse();
    expect(TestBed.inject(TokenStore).accessToken()).toBeNull();
  });

  it('signs in after the forced change', () => {
    store.changePassword('asha', 'old', 'new').subscribe();
    const req = backend.expectOne('/api/auth/change-password');
    expect(req.request.body).toEqual({ username: 'asha', currentPassword: 'old', newPassword: 'new' });
    req.flush(session);
    expect(store.isAuthenticated()).toBeTrue();
  });

  it('shares one refresh among concurrent callers', () => {
    const results: boolean[] = [];
    store.refresh().subscribe((r) => results.push(r));
    store.refresh().subscribe((r) => results.push(r));
    backend.expectOne('/api/auth/refresh').flush(session);
    expect(results).toEqual([true, true]);
  });

  it('clears the session when the refresh fails', () => {
    store.login('asha', 'pw').subscribe();
    backend.expectOne('/api/auth/login').flush(session);
    let renewed: boolean | undefined;
    store.refresh().subscribe((r) => (renewed = r));
    backend.expectOne('/api/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(renewed).toBeFalse();
    expect(store.isAuthenticated()).toBeFalse();
    expect(TestBed.inject(TokenStore).accessToken()).toBeNull();
  });

  it('restores a session from the cookie at start-up', async () => {
    const done = store.restore();
    backend.expectOne('/api/auth/refresh').flush(session);
    await done;
    expect(store.status()).toBe('authenticated');
  });

  it('becomes anonymous when there is no session to restore', async () => {
    const done = store.restore();
    backend.expectOne('/api/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });
    await done;
    expect(store.status()).toBe('anonymous');
  });

  it('clears locally on logout even if the call fails', () => {
    store.login('asha', 'pw').subscribe();
    backend.expectOne('/api/auth/login').flush(session);
    store.logout().subscribe();
    backend.expectOne('/api/auth/logout').flush({}, { status: 500, statusText: 'Server Error' });
    expect(store.isAuthenticated()).toBeFalse();
  });
});
