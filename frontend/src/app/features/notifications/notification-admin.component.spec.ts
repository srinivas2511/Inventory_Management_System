import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { NotificationAdminComponent } from './notification-admin.component';
import { DeliveryLogDto, Page, TemplateDto } from './notifications.api';

const template: TemplateDto = {
  id: 9,
  templateCode: 'LOW_STOCK',
  channel: 'BOTH',
  description: 'Alert: stock below its reorder level',
  subjectTemplate: '[{{severity}}] Low stock',
  bodyTemplate: '{{message}}',
  active: true,
};

const failed: DeliveryLogDto = {
  id: 1,
  userId: null,
  recipientEmail: 'buyer@acme.test',
  templateCode: 'PO_APPROVED_SUPPLIER',
  channel: 'EMAIL',
  subject: 'Purchase order PO/26/00007 approved',
  status: 'FAILED',
  attempts: 5,
  nextAttemptAt: null,
  lastError: 'MailSendException: connection refused',
  createdAt: '2026-10-04T08:00:00Z',
  sentAt: null,
};

describe('NotificationAdminComponent', () => {
  let fixture: ComponentFixture<NotificationAdminComponent>;
  let backend: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationAdminComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(NotificationAdminComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/notification-templates').flush([template]);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('lists the templates', () => {
    expect(fixture.nativeElement.textContent).toContain('LOW_STOCK');
    expect(fixture.nativeElement.querySelectorAll('[data-testid="template-row"]').length).toBe(1);
  });

  it('loads a template into the form for editing', () => {
    (fixture.nativeElement.querySelector('[data-testid="template-row"]') as HTMLElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="edit-form"]')).not.toBeNull();
    const v = fixture.componentInstance.form.getRawValue();
    expect(v.channel).toBe('BOTH');
    expect(v.bodyTemplate).toBe('{{message}}');
    expect(v.active).toBe(true);
  });

  it('cannot save an empty body', () => {
    fixture.componentInstance.edit(template);
    fixture.componentInstance.form.patchValue({ bodyTemplate: '' });
    expect(fixture.componentInstance.form.invalid).toBe(true);
    fixture.componentInstance.save();
    backend.expectNone((r) => r.method === 'PUT');
  });

  it('saves the edited template and updates the list', () => {
    fixture.componentInstance.edit(template);
    fixture.componentInstance.form.patchValue({
      channel: 'IN_APP',
      bodyTemplate: 'Custom: {{message}}',
      active: false,
    });
    fixture.componentInstance.save();
    const req = backend.expectOne('/api/notification-templates/9');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      channel: 'IN_APP',
      description: 'Alert: stock below its reorder level',
      subjectTemplate: '[{{severity}}] Low stock',
      bodyTemplate: 'Custom: {{message}}',
      active: false,
    });
    req.flush({ ...template, channel: 'IN_APP', bodyTemplate: 'Custom: {{message}}', active: false });
    expect(fixture.componentInstance.editing()).toBeNull();
    expect(fixture.componentInstance.templates()[0].channel).toBe('IN_APP');
  });

  it('shows the delivery log with failures and their last error, and filters it', () => {
    fixture.componentInstance.onTab(1);
    backend
      .expectOne((r) => r.url === '/api/notification-log')
      .flush({
        content: [failed],
        totalElements: 1,
        totalPages: 1,
        size: 50,
        number: 0,
      } as Page<DeliveryLogDto>);
    const row = fixture.componentInstance.log()[0];
    expect(row.recipientEmail).toBe('buyer@acme.test');
    expect(row.status).toBe('FAILED');
    expect(row.lastError).toBe('MailSendException: connection refused');

    fixture.componentInstance.statusCtrl.setValue('FAILED');
    fixture.componentInstance.channelCtrl.setValue('EMAIL');
    fixture.componentInstance.loadLog();
    const req = backend.expectOne((r) => r.url === '/api/notification-log');
    expect(req.request.params.get('status')).toBe('FAILED');
    expect(req.request.params.get('channel')).toBe('EMAIL');
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 50, number: 0 });
  });
});
