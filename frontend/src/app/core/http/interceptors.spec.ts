import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { SessionStore } from '../auth/session.store';
import { TokenStore } from '../auth/token.store';
import { AppConfigService } from '../config/app-config.service';
import { ToastService } from '../notification/toast.service';
import { authInterceptor } from './auth.interceptor';
import { errorInterceptor } from './error.interceptor';
import { IDEMPOTENT, idempotencyInterceptor } from './idempotency.interceptor';
import { LoadingService } from './loading.service';
import { loadingInterceptor } from './loading.interceptor';

describe('HTTP interceptors', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let toast: jasmine.SpyObj<ToastService>;

  beforeEach(() => {
    toast = jasmine.createSpyObj<ToastService>('ToastService', ['error', 'success', 'info']);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(
          withInterceptors([loadingInterceptor, authInterceptor, idempotencyInterceptor, errorInterceptor]),
        ),
        provideHttpClientTesting(),
        { provide: ToastService, useValue: toast },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('adds the bearer token to API calls only', () => {
    TestBed.inject(TokenStore).set('abc.def.ghi');
    http.get('/api/materials').subscribe();
    http.get('https://other.example.com/data').subscribe();

    expect(backend.expectOne('/api/materials').request.headers.get('Authorization')).toBe('Bearer abc.def.ghi');
    expect(backend.expectOne('https://other.example.com/data').request.headers.has('Authorization')).toBeFalse();
  });

  it('does not add Authorization when signed out', () => {
    http.get('/api/materials').subscribe();
    expect(backend.expectOne('/api/materials').request.headers.has('Authorization')).toBeFalse();
  });

  it('adds an Idempotency-Key only to marked POST requests', () => {
    http.post('/api/production/issues', {}, { context: new HttpContext().set(IDEMPOTENT, true) }).subscribe();
    http.post('/api/materials', {}).subscribe();

    const marked = backend.expectOne('/api/production/issues');
    expect(marked.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    expect(backend.expectOne('/api/materials').request.headers.has('Idempotency-Key')).toBeFalse();
  });

  it('tracks in-flight requests', () => {
    const loading = TestBed.inject(LoadingService);
    http.get('/api/materials').subscribe();
    expect(loading.isLoading()).toBeTrue();
    backend.expectOne('/api/materials').flush([]);
    expect(loading.isLoading()).toBeFalse();
  });

  it('shows a permission message for 403', () => {
    http.get('/api/products').subscribe({ error: () => undefined });
    backend
      .expectOne('/api/products')
      .flush({ code: 'ACCESS_DENIED', status: 403 }, { status: 403, statusText: 'Forbidden' });
    expect(toast.error).toHaveBeenCalledWith('You do not have permission to perform this action.');
  });

  it('sends a user with a temporary password to the change-password page instead of showing a permission toast', () => {
    const session = TestBed.inject(SessionStore);
    session.setUser({
      id: 1,
      username: 'new.user',
      fullName: 'New User',
      roles: ['OPERATOR'],
      permissions: ['PRODUCT_VIEW'],
      mustChangePassword: false,
    });
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigate').and.resolveTo(true);

    let propagated = false;
    http.get('/api/products').subscribe({ error: () => (propagated = true) });
    backend
      .expectOne('/api/products')
      .flush({ code: 'PASSWORD_CHANGE_REQUIRED', status: 403 }, { status: 403, statusText: 'Forbidden' });

    expect(propagated).toBeTrue();
    expect(toast.error).not.toHaveBeenCalled();
    expect(session.mustChangePassword()).toBeTrue();
    expect(session.isLoggedIn()).toBeTrue();
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate.calls.mostRecent().args[0]).toEqual(['/change-password']);
  });

  it('shows the business-rule detail for 422 and still propagates the error', () => {
    let propagated = false;
    http.post('/api/dispatches', {}).subscribe({ error: () => (propagated = true) });
    backend
      .expectOne('/api/dispatches')
      .flush(
        { code: 'DISPATCH_EXCEEDS_STOCK', status: 422, detail: 'Requested 6000; available 4800' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    expect(toast.error).toHaveBeenCalledWith('Requested 6000; available 4800');
    expect(propagated).toBeTrue();
  });

  it('leaves 400 validation errors to the form (no toast)', () => {
    http.post('/api/materials', {}).subscribe({ error: () => undefined });
    backend
      .expectOne('/api/materials')
      .flush(
        { code: 'VALIDATION_FAILED', status: 400, fieldErrors: [{ field: 'name', message: 'required' }] },
        { status: 400, statusText: 'Bad Request' },
      );
    expect(toast.error).not.toHaveBeenCalled();
  });

  it('names the failing fields in the detail a form shows instead of a bare "Validation failed" (D-31)', () => {
    let detail = '';
    http.post('/api/materials', {}).subscribe({ error: (e) => (detail = e.error.detail) });
    backend.expectOne('/api/materials').flush(
      {
        code: 'VALIDATION_FAILED',
        status: 400,
        detail: 'Validation failed',
        fieldErrors: [
          { field: 'minStock', message: 'must not be null' },
          { field: 'standardCost', message: 'must not be null' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    expect(detail).toBe('Validation failed (minStock: must not be null; standardCost: must not be null)');
  });

  it('quotes the trace id for server errors', () => {
    http.get('/api/materials').subscribe({ error: () => undefined });
    backend
      .expectOne('/api/materials')
      .flush({ code: 'INTERNAL_ERROR', status: 500, traceId: 'abc123' }, { status: 500, statusText: 'Server Error' });
    expect(toast.error).toHaveBeenCalledWith('Something went wrong on the server. Reference: abc123');
  });

  it('treats the shipped default as same-origin, so API paths are /api/... and never /api/api/...', () => {
    expect(TestBed.inject(AppConfigService).apiBaseUrl).toBe('');
  });
  describe('session refresh', () => {
    const REFRESHED = {
      accessToken: 't2',
      expiresIn: 900,
      mustChangePassword: false,
      user: { id: 1, username: 'asha', fullName: 'Asha Rao', roles: ['ADMIN'], permissions: ['USER_VIEW'] },
    };
    const unauthorized = { status: 401, statusText: 'Unauthorized' };

    beforeEach(() => TestBed.inject(TokenStore).set('t1'));

    it('refreshes once on a 401 and retries the request with the new token', () => {
      let result: unknown;
      http.get('/api/materials').subscribe((r) => (result = r));
      backend.expectOne('/api/materials').flush({}, unauthorized);

      const refresh = backend.expectOne('/api/auth/refresh');
      expect(refresh.request.headers.has('Authorization')).toBeFalse();
      refresh.flush(REFRESHED);

      const retry = backend.expectOne('/api/materials');
      expect(retry.request.headers.get('Authorization')).toBe('Bearer t2');
      retry.flush({ ok: true });
      expect(result).toEqual({ ok: true });
      expect(TestBed.inject(SessionStore).user()?.username).toBe('asha');
    });

    it('shares one refresh between requests that fail at the same time', () => {
      http.get('/api/a').subscribe();
      http.get('/api/b').subscribe();
      backend.expectOne('/api/a').flush({}, unauthorized);
      backend.expectOne('/api/b').flush({}, unauthorized);

      backend.expectOne('/api/auth/refresh').flush(REFRESHED);

      backend.expectOne('/api/a').flush({});
      backend.expectOne('/api/b').flush({});
    });

    it('ends the session instead of looping when the retried request is refused again', () => {
      const navigate = spyOn(TestBed.inject(Router), 'navigate');
      let failed = false;
      http.get('/api/materials').subscribe({ error: () => (failed = true) });
      backend.expectOne('/api/materials').flush({}, unauthorized);
      backend.expectOne('/api/auth/refresh').flush(REFRESHED);
      backend.expectOne('/api/materials').flush({}, unauthorized);

      backend.expectNone('/api/auth/refresh');
      expect(failed).toBeTrue();
      expect(TestBed.inject(TokenStore).accessToken()).toBeNull();
      expect(navigate).toHaveBeenCalledWith(['/login']);
    });

    it('fails every waiting request and signs out once when the refresh is rejected', () => {
      const navigate = spyOn(TestBed.inject(Router), 'navigate');
      const failures: number[] = [];
      http.get('/api/a').subscribe({ error: (e) => failures.push(e.status) });
      http.get('/api/b').subscribe({ error: (e) => failures.push(e.status) });
      backend.expectOne('/api/a').flush({}, unauthorized);
      backend.expectOne('/api/b').flush({}, unauthorized);
      backend.expectOne('/api/auth/refresh').flush({ code: 'INVALID_TOKEN' }, unauthorized);

      expect(failures).toEqual([401, 401]);
      expect(navigate).toHaveBeenCalledTimes(1);
    });

    it('never tries to refresh around the sign-in endpoints themselves', () => {
      http.post('/api/auth/logout', {}).subscribe({ error: () => undefined });
      const logout = backend.expectOne('/api/auth/logout');
      expect(logout.request.headers.has('Authorization')).toBeFalse();
      logout.flush({}, unauthorized);

      backend.expectNone('/api/auth/refresh');
    });
  });
});
