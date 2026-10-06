import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { ArReconciliationComponent } from './ar-reconciliation.component';
import { ArReconcileResultDto, FinanceAlertDto, MissingArInvoiceDto } from './finance.api';

const missing: MissingArInvoiceDto[] = [
  { dispatchId: 31, dispatchNumber: 'DSP/26/00031', customerId: 4, dispatchDate: '2026-09-28' },
  { dispatchId: 32, dispatchNumber: 'DSP/26/00032', customerId: 5, dispatchDate: '2026-09-29' },
];

const alert: FinanceAlertDto = {
  id: 9,
  alertType: 'MISSING_AR_INVOICE',
  severity: 'HIGH',
  entityType: 'DISPATCH',
  entityId: 31,
  message: 'No open fiscal period for 2026-09-28',
  status: 'OPEN',
  lastSeenAt: '2026-10-05T09:00:00Z',
  resolvedAt: null,
};

describe('ArReconciliationComponent', () => {
  let fixture: ComponentFixture<ArReconciliationComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[], alertsAllowed = true): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'fm',
      fullName: 'Finance Manager',
      email: 'f@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(ArReconciliationComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/ar/reconciliation/missing-invoices').flush(missing);
    const alerts = backend.expectOne((r) => r.url === '/api/alerts');
    expect(alerts.request.params.get('alertType')).toBe('MISSING_AR_INVOICE');
    if (alertsAllowed) {
      alerts.flush({ content: [alert], totalElements: 1, totalPages: 1, size: 200, number: 0 });
    } else {
      alerts.flush({ detail: 'forbidden' }, { status: 403, statusText: 'Forbidden' });
    }
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ArReconciliationComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  function retryButton(): HTMLButtonElement | undefined {
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    return buttons.find((b) => (b.textContent ?? '').includes('Retry all'));
  }

  it('lists dispatches without an invoice and shows the open alert against its dispatch', () => {
    setup(['AR_MANAGE', 'ALERT_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('DSP/26/00031');
    expect(text).toContain('DSP/26/00032');
    expect(text).toContain('No open fiscal period for 2026-09-28');
    expect(text).toContain('No open alert');
  });

  it('hides retry from holders of only AR_MANAGE', () => {
    setup(['AR_MANAGE']);
    expect(retryButton()).toBeUndefined();
  });

  it('still lists dispatches when the alerts call is forbidden', () => {
    setup(['AR_MANAGE'], false);
    expect(fixture.nativeElement.textContent).toContain('DSP/26/00031');
  });

  it('retries as an AR_RECONCILE holder and shows the outcome with per-dispatch failures', () => {
    setup(['AR_MANAGE', 'AR_RECONCILE', 'ALERT_VIEW']);
    const button = retryButton();
    expect(button).toBeDefined();
    button?.click();
    const result: ArReconcileResultDto = {
      attempted: 2,
      created: 1,
      alreadyInvoiced: 0,
      failed: 1,
      failures: [{ dispatchId: 32, dispatchNumber: 'DSP/26/00032', reason: 'Fiscal period CLOSED' }],
    };
    backend
      .expectOne((r) => r.method === 'POST' && r.url === '/api/ar/reconciliation/missing-invoices/retry')
      .flush(result);
    // The screen reloads after a retry.
    backend.expectOne('/api/ar/reconciliation/missing-invoices').flush([missing[1]]);
    backend
      .expectOne((r) => r.url === '/api/alerts')
      .flush({ content: [], totalElements: 0, totalPages: 0, size: 200, number: 0 });
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Attempted 2');
    expect(text).toContain('created 1');
    expect(text).toContain('Fiscal period CLOSED');
    expect(text).not.toContain('DSP/26/00031');
  });
});
