import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { SessionStore } from './session.store';
import { TokenStore } from './token.store';

/** The backend answers login/refresh with { accessToken, expiresIn, mustChangePassword, user } and sets the refresh token as an HttpOnly cookie. */
const BACKEND_RESPONSE = {
  accessToken: 'jwt-1',
  expiresIn: 900,
  mustChangePassword: true,
  user: { id: 7, username: 'asha', fullName: 'Asha Rao', roles: ['ADMIN'], permissions: ['USER_VIEW'] },
};

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.removeItem('ims_rt');
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('logs in against /api/auth/login (never /api/api/...) and keeps the response mustChangePassword on the session user', () => {
    service.login({ username: 'asha', password: 'secret' }).subscribe();
    const req = http.expectOne('/api/auth/login');
    req.flush(BACKEND_RESPONSE);

    expect(TestBed.inject(TokenStore).accessToken()).toBe('jwt-1');
    const user = TestBed.inject(SessionStore).user();
    expect(user?.username).toBe('asha');
    expect(user?.mustChangePassword).toBeTrue();
  });

  it('does not store a refresh token in the browser: the backend keeps it in an HttpOnly cookie', () => {
    service.login({ username: 'asha', password: 'secret' }).subscribe();
    http.expectOne('/api/auth/login').flush(BACKEND_RESPONSE);

    expect(localStorage.getItem('ims_rt')).toBeNull();
  });

  it('refreshes by posting to /api/auth/refresh without needing a stored token', () => {
    service.refresh().subscribe();
    const req = http.expectOne('/api/auth/refresh');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ ...BACKEND_RESPONSE, accessToken: 'jwt-2', mustChangePassword: false });

    expect(TestBed.inject(TokenStore).accessToken()).toBe('jwt-2');
    expect(TestBed.inject(SessionStore).user()?.mustChangePassword).toBeFalse();
  });

  it('logs out and returns to the login page when the refresh is rejected', () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigate');
    service.refresh().subscribe({ error: () => undefined });
    http.expectOne('/api/auth/refresh').flush({ code: 'INVALID_TOKEN' }, { status: 401, statusText: 'Unauthorized' });

    expect(TestBed.inject(SessionStore).user()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
  it('restores the session from the refresh cookie at start-up without navigating', () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigate');
    let restored: boolean | undefined;
    service.restoreSession().subscribe((r) => (restored = r));
    http.expectOne('/api/auth/refresh').flush({ ...BACKEND_RESPONSE, mustChangePassword: false });

    expect(restored).toBeTrue();
    expect(TestBed.inject(SessionStore).user()?.fullName).toBe('Asha Rao');
    expect(TestBed.inject(TokenStore).accessToken()).toBe('jwt-1');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('reports no session, quietly, when start-up finds nobody signed in', () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigate');
    let restored: boolean | undefined;
    service.restoreSession().subscribe((r) => (restored = r));
    http.expectOne('/api/auth/refresh').flush({ code: 'INVALID_TOKEN' }, { status: 401, statusText: 'Unauthorized' });

    expect(restored).toBeFalse();
    expect(TestBed.inject(SessionStore).user()).toBeNull();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('shares one refresh request between callers that ask at the same time', () => {
    service.refresh().subscribe();
    service.refresh().subscribe();
    http.expectOne('/api/auth/refresh').flush({ ...BACKEND_RESPONSE, accessToken: 'jwt-3' });

    expect(TestBed.inject(TokenStore).accessToken()).toBe('jwt-3');
  });

  it('asks the server to end the session on logout, then clears the browser state', () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigate');
    service.login({ username: 'asha', password: 'secret' }).subscribe();
    http.expectOne('/api/auth/login').flush(BACKEND_RESPONSE);

    service.logout();
    const req = http.expectOne('/api/auth/logout');
    expect(req.request.method).toBe('POST');
    expect(TestBed.inject(SessionStore).user()).not.toBeNull();
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(TestBed.inject(SessionStore).user()).toBeNull();
    expect(TestBed.inject(TokenStore).accessToken()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('still clears the browser state when the server cannot be reached on logout', () => {
    service.login({ username: 'asha', password: 'secret' }).subscribe();
    http.expectOne('/api/auth/login').flush(BACKEND_RESPONSE);

    service.logout();
    http.expectOne('/api/auth/logout').error(new ProgressEvent('error'));

    expect(TestBed.inject(SessionStore).user()).toBeNull();
  });

  it('sends an expired session to login remembering the page', () => {
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigate');
    Object.defineProperty(router, 'url', { get: () => '/inventory/stock?warehouse=2' });

    service.expireSession();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/inventory/stock?warehouse=2' } });
  });

  it('refreshes the session after a password change, because the server ended the old access token', () => {
    service.changePassword('Old#Pass-2026', 'New#Pass-2027').subscribe();
    const change = http.expectOne('/api/auth/change-password');
    expect(change.request.body).toEqual({ currentPassword: 'Old#Pass-2026', newPassword: 'New#Pass-2027' });
    change.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/auth/refresh').flush({ ...BACKEND_RESPONSE, accessToken: 'jwt-4', mustChangePassword: false });

    expect(TestBed.inject(TokenStore).accessToken()).toBe('jwt-4');
    expect(TestBed.inject(SessionStore).user()?.mustChangePassword).toBeFalse();
  });
});
