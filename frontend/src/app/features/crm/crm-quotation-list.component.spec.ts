import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { CrmQuotationListComponent } from './crm-quotation-list.component';
import { Page, QuotationDto } from './crm.api';

function quotation(over: Partial<QuotationDto>): QuotationDto {
  return {
    id: 1,
    quotationNumber: 'QT/26/00001',
    opportunityId: null,
    customerId: 5,
    customerName: 'Acme Springs',
    validUntil: '2026-12-31',
    status: 'DRAFT',
    totalAmount: 2125,
    paymentTerms: 'Net 30',
    notes: null,
    sentAt: null,
    respondedAt: null,
    rejectReason: null,
    convertedSalesOrderId: null,
    convertedSalesOrderNumber: null,
    convertedAt: null,
    items: [],
    version: 0,
    allowedActions: ['EDIT', 'SEND'],
    ...over,
  };
}

const draft = quotation({});
const sent = quotation({ id: 2, quotationNumber: 'QT/26/00002', status: 'SENT', allowedActions: ['ACCEPT', 'REJECT'] });
const accepted = quotation({ id: 3, quotationNumber: 'QT/26/00003', status: 'ACCEPTED', allowedActions: ['CONVERT'] });
const converted = quotation({
  id: 4,
  quotationNumber: 'QT/26/00004',
  status: 'CONVERTED',
  convertedSalesOrderId: 9,
  convertedSalesOrderNumber: 'SO/26/00009',
  allowedActions: [],
});

const page: Page<QuotationDto> = {
  content: [draft, sent, accepted, converted],
  totalElements: 4,
  totalPages: 1,
  size: 50,
  number: 0,
};

describe('CrmQuotationListComponent', () => {
  let fixture: ComponentFixture<CrmQuotationListComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'agent',
      fullName: 'Agent',
      email: 'a@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(CrmQuotationListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/crm/quotations').flush(page);
    fixture.detectChanges();
  }

  function buttonLabels(): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll('td button') as NodeListOf<HTMLElement>).map((b) =>
      (b.textContent ?? '').trim(),
    );
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CrmQuotationListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists quotations with the converted sales order number and no actions for read-only users', () => {
    setup(['CRM_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('QT/26/00001');
    expect(text).toContain('SO/26/00009');
    expect(buttonLabels()).toEqual([]);
    expect(fixture.nativeElement.querySelector('a[href="/crm/quotations/new"]')).toBeNull();
  });

  it('shows only the actions each status allows', () => {
    setup(['CRM_VIEW', 'QUOTATION_CREATE']);
    expect(buttonLabels()).toEqual(['Edit', 'Send', 'Accept', 'Reject', 'Convert to sales order']);
    expect(fixture.nativeElement.querySelector('a[href="/crm/quotations/new"]')).toBeTruthy();
  });

  it('sends a draft quotation and reloads', () => {
    setup(['CRM_VIEW', 'QUOTATION_CREATE']);
    fixture.componentInstance.run(draft, 'send');
    const req = backend.expectOne((r) => r.url === '/api/crm/quotations/1/send');
    expect(req.request.method).toBe('POST');
    req.flush({ ...draft, status: 'SENT' });
    backend.expectOne((r) => r.url === '/api/crm/quotations' && r.method === 'GET').flush(page);
  });

  it('converts an accepted quotation and reloads', () => {
    setup(['CRM_VIEW', 'QUOTATION_CREATE']);
    fixture.componentInstance.run(accepted, 'convert');
    const req = backend.expectOne((r) => r.url === '/api/crm/quotations/3/convert-to-sales-order');
    req.flush({ ...accepted, status: 'CONVERTED', convertedSalesOrderNumber: 'SO/26/00010' });
    backend.expectOne((r) => r.url === '/api/crm/quotations' && r.method === 'GET').flush(page);
  });

  it('needs a reason to reject a sent quotation', () => {
    setup(['CRM_VIEW', 'QUOTATION_CREATE']);
    fixture.componentInstance.startReject(sent);
    expect(fixture.componentInstance.rejecting()?.id).toBe(2);
    fixture.componentInstance.reasonCtrl.setValue('price too high');
    fixture.componentInstance.confirmReject();
    const req = backend.expectOne((r) => r.url === '/api/crm/quotations/2/reject');
    expect(req.request.body).toEqual({ reason: 'price too high' });
    req.flush({ ...sent, status: 'REJECTED' });
    backend.expectOne((r) => r.url === '/api/crm/quotations' && r.method === 'GET').flush(page);
    expect(fixture.componentInstance.rejecting()).toBeNull();
  });

  it('filters by status', () => {
    setup(['CRM_VIEW']);
    fixture.componentInstance.statusCtrl.setValue('SENT');
    fixture.componentInstance.load();
    const req = backend.expectOne((r) => r.url === '/api/crm/quotations');
    expect(req.request.params.get('status')).toBe('SENT');
    req.flush(page);
  });
});
