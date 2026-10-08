import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { StepUpPromptService } from '../stepup/step-up-prompt.service';
import { stepUpInterceptor } from './step-up.interceptor';

describe('stepUpInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let prompt: jasmine.SpyObj<StepUpPromptService>;
  let answer: Subject<boolean>;

  const refusal = { code: 'STEP_UP_REQUIRED', status: 403, title: 'Re-authentication required' };

  beforeEach(() => {
    answer = new Subject<boolean>();
    prompt = jasmine.createSpyObj<StepUpPromptService>('StepUpPromptService', ['prompt']);
    prompt.prompt.and.returnValue(answer.asObservable());
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([stepUpInterceptor])),
        provideHttpClientTesting(),
        { provide: StepUpPromptService, useValue: prompt },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('asks for the password and sends the same request again when the user confirms', () => {
    let result: unknown;
    http.get('/api/gl/trial-balance').subscribe((r) => (result = r));

    backend.expectOne('/api/gl/trial-balance').flush(refusal, { status: 403, statusText: 'Forbidden' });
    expect(prompt.prompt).toHaveBeenCalledTimes(1);
    answer.next(true);

    backend.expectOne('/api/gl/trial-balance').flush([{ accountCode: '1401' }]);
    expect(result).toEqual([{ accountCode: '1401' }]);
  });

  it('passes the original refusal on when the user cancels, and sends nothing again', () => {
    let failure: HttpErrorResponse | undefined;
    http.get('/api/gl/trial-balance').subscribe({ error: (e: HttpErrorResponse) => (failure = e) });

    backend.expectOne('/api/gl/trial-balance').flush(refusal, { status: 403, statusText: 'Forbidden' });
    answer.next(false);

    expect(failure?.status).toBe(403);
    expect(failure?.error.code).toBe('STEP_UP_REQUIRED');
    backend.expectNone('/api/gl/trial-balance');
  });

  it('does not ask for anything on other refusals or errors', () => {
    let status = 0;
    http.get('/api/materials').subscribe({ error: (e: HttpErrorResponse) => (status = e.status) });

    backend
      .expectOne('/api/materials')
      .flush({ code: 'ACCESS_DENIED', status: 403, title: 'Access denied' }, { status: 403, statusText: 'Forbidden' });

    expect(status).toBe(403);
    expect(prompt.prompt).not.toHaveBeenCalled();
  });
});
