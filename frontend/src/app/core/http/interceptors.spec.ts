import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
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

  it('quotes the trace id for server errors', () => {
    http.get('/api/materials').subscribe({ error: () => undefined });
    backend
      .expectOne('/api/materials')
      .flush({ code: 'INTERNAL_ERROR', status: 500, traceId: 'abc123' }, { status: 500, statusText: 'Server Error' });
    expect(toast.error).toHaveBeenCalledWith('Something went wrong on the server. Reference: abc123');
  });

  it('uses the configured API base for matching', () => {
    expect(TestBed.inject(AppConfigService).apiBaseUrl).toBe('/api');
  });
});
