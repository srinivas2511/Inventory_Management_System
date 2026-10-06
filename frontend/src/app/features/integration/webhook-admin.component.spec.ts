import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { Page, WebhookDeliveryDto, WebhookDto, WebhookSecretDto } from './integration.api';
import { WebhookAdminComponent } from './webhook-admin.component';

const hook: WebhookDto = {
  id: 3,
  webhookCode: 'wh-1a2b3c4d',
  name: 'ERP sink',
  targetUrl: 'https://erp.example.com/ims',
  secretHint: 'x9Qz',
  events: ['StockPosted', 'InvoicePaid'],
  active: true,
  createdAt: '2026-10-05T08:00:00Z',
  version: 2,
};

function delivery(id: number, status: WebhookDeliveryDto['status']): WebhookDeliveryDto {
  return {
    id,
    subscriptionId: 3,
    eventType: 'StockPosted',
    eventKey: `StockPosted:${id}`,
    status,
    retryCount: status === 'FAILED' ? 7 : 0,
    nextRetryAt: null,
    attemptedAt: '2026-10-05T08:01:00Z',
    deliveredAt: status === 'DELIVERED' ? '2026-10-05T08:01:01Z' : null,
    responseStatus: status === 'DELIVERED' ? 200 : 500,
    responseBody: null,
    lastError: status === 'FAILED' ? 'The target answered HTTP 500' : null,
    createdAt: '2026-10-05T08:00:30Z',
  };
}

function page<T>(content: T[]): Page<T> {
  return { content, totalElements: content.length, totalPages: 1, size: 20, number: 0 };
}

describe('WebhookAdminComponent', () => {
  let fixture: ComponentFixture<WebhookAdminComponent>;
  let backend: HttpTestingController;

  const q = (id: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
  const all = (id: string): HTMLElement[] =>
    Array.from(fixture.nativeElement.querySelectorAll(`[data-testid="${id}"]`));

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'admin',
      fullName: 'Admin',
      email: 'a@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(WebhookAdminComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/integration/webhooks').flush([hook]);
    backend.expectOne('/api/integration/webhooks/event-types').flush(['StockPosted', 'OrderCompleted', 'InvoicePaid']);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WebhookAdminComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists the subscriptions with only the last four characters of the secret', () => {
    setup(['WEBHOOK_VIEW']);
    const row = all('webhook-row')[0];
    expect(row.textContent).toContain('ERP sink');
    expect(row.textContent).toContain('https://erp.example.com/ims');
    expect(row.textContent).toContain('StockPosted, InvoicePaid');
    expect(row.textContent).toContain('••••x9Qz');
  });

  it('offers no management buttons to a view-only user', () => {
    setup(['WEBHOOK_VIEW']);
    expect(q('new')).toBeNull();
    expect(q('test')).toBeNull();
    expect(q('rotate')).toBeNull();
    expect(q('toggle')).toBeNull();
  });

  it('shows the secret once after creating a webhook, then drops it', () => {
    setup(['WEBHOOK_VIEW', 'WEBHOOK_MANAGE']);
    q('new')!.click();
    fixture.detectChanges();
    const c = fixture.componentInstance;
    c.form.setValue({ name: 'Warehouse feed', targetUrl: 'https://wh.example.com/hook' });
    c.toggle('StockPosted', true);
    fixture.detectChanges();
    (q('create') as HTMLButtonElement).click();

    const req = backend.expectOne((r) => r.method === 'POST' && r.url === '/api/integration/webhooks');
    expect(req.request.body).toEqual({
      name: 'Warehouse feed',
      targetUrl: 'https://wh.example.com/hook',
      events: ['StockPosted'],
      active: true,
      version: null,
    });
    const created: WebhookSecretDto = {
      webhook: { ...hook, id: 4, name: 'Warehouse feed', secretHint: 'abcd' },
      secret: 'whsec_exampleSecretValue1234abcd',
    };
    req.flush(created);
    backend.expectOne('/api/integration/webhooks').flush([hook, created.webhook]);
    fixture.detectChanges();

    expect(q('secret')!.textContent).toContain('shown once');
    expect(q('secret-value')!.textContent).toContain('whsec_exampleSecretValue1234abcd');

    q('secret-dismiss')!.click();
    fixture.detectChanges();
    expect(q('secret')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('whsec_exampleSecretValue1234abcd');
  });

  it('refuses a plain http target and a form with no event', () => {
    setup(['WEBHOOK_VIEW', 'WEBHOOK_MANAGE']);
    q('new')!.click();
    fixture.detectChanges();
    const c = fixture.componentInstance;
    c.form.setValue({ name: 'x', targetUrl: 'http://insecure.example.com' });
    c.toggle('StockPosted', true);
    fixture.detectChanges();
    expect((q('create') as HTMLButtonElement).disabled).toBeTrue();

    c.form.setValue({ name: 'x', targetUrl: 'https://ok.example.com' });
    c.toggle('StockPosted', false);
    fixture.detectChanges();
    expect((q('create') as HTMLButtonElement).disabled).toBeTrue();

    c.toggle('StockPosted', true);
    fixture.detectChanges();
    expect((q('create') as HTMLButtonElement).disabled).toBeFalse();
  });

  it('sends a test, rotates a secret and deactivates with the current version', () => {
    setup(['WEBHOOK_VIEW', 'WEBHOOK_MANAGE']);

    q('test')!.click();
    backend.expectOne('/api/integration/webhooks/3/test').flush(delivery(9, 'DELIVERED'));

    q('rotate')!.click();
    backend
      .expectOne('/api/integration/webhooks/3/rotate-secret')
      .flush({ webhook: hook, secret: 'whsec_rotatedSecretValue' } satisfies WebhookSecretDto);
    backend.expectOne('/api/integration/webhooks').flush([hook]);
    fixture.detectChanges();
    expect(q('secret-value')!.textContent).toContain('whsec_rotatedSecretValue');

    q('toggle')!.click();
    const put = backend.expectOne((r) => r.method === 'PUT' && r.url === '/api/integration/webhooks/3');
    expect(put.request.body).toEqual({
      name: 'ERP sink',
      targetUrl: 'https://erp.example.com/ims',
      events: ['StockPosted', 'InvoicePaid'],
      active: false,
      version: 2,
    });
    put.flush({ ...hook, active: false });
    backend.expectOne('/api/integration/webhooks').flush([{ ...hook, active: false }]);
  });

  it('lists deliveries and offers a retry for everything not yet delivered', async () => {
    setup(['WEBHOOK_VIEW', 'WEBHOOK_MANAGE']);
    (fixture.nativeElement.querySelectorAll('[role="tab"]')[1] as HTMLElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    backend
      .expectOne((r) => r.url === '/api/integration/webhook-deliveries' && r.params.get('page') === '0')
      .flush(page([delivery(11, 'FAILED'), delivery(12, 'DELIVERED'), delivery(13, 'RETRY')]));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(all('delivery-row')).toHaveSize(3);
    expect(all('retry')).toHaveSize(2);
    expect(all('delivery-row')[0].textContent).toContain('The target answered HTTP 500');

    all('retry')[0].click();
    backend.expectOne('/api/integration/webhook-deliveries/11/retry').flush(delivery(11, 'DELIVERED'));
    backend.expectOne((r) => r.url === '/api/integration/webhook-deliveries').flush(page([delivery(11, 'DELIVERED')]));
  });

  it('filters deliveries by status', () => {
    setup(['WEBHOOK_VIEW']);
    fixture.componentInstance.statusCtrl.setValue('FAILED');
    fixture.componentInstance.loadDeliveries();
    const req = backend.expectOne((r) => r.url === '/api/integration/webhook-deliveries');
    expect(req.request.params.get('status')).toBe('FAILED');
    expect(req.request.params.has('subscriptionId')).toBeFalse();
    req.flush(page([]));
  });
});
