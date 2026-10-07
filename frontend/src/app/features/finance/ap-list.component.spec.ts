import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { ApListComponent } from './ap-list.component';
import { ApInvoiceDto } from './finance.api';

function invoice(over: Partial<ApInvoiceDto>): ApInvoiceDto {
  return {
    id: 1,
    invoiceNumber: 'APINV/26/00001',
    supplierId: 7,
    invoiceDate: '2026-10-05',
    dueDate: '2026-11-04',
    totalAmount: 20000,
    paidAmount: 0,
    balanceAmount: 20000,
    status: 'DRAFT',
    goodsReceiptId: 3,
    matchStatus: 'MATCHED',
    matchExpectedAmount: 20000,
    matchVarianceAmount: 0,
    matchMessage: null,
    ...over,
  };
}

const exception = invoice({
  id: 2,
  invoiceNumber: 'APINV/26/00002',
  totalAmount: 25000,
  balanceAmount: 25000,
  matchStatus: 'EXCEPTION',
  matchMessage: 'Invoice subtotal 25000.00 exceeds the 20000.00 of the goods receipt still open for matching.',
});

describe('ApListComponent three-way match', () => {
  let fixture: ComponentFixture<ApListComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'fm',
      fullName: 'Finance Manager',
      email: 'f@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(ApListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    flushLists();
  }

  function flushLists(): void {
    backend
      .expectOne((r) => r.url === '/api/ap/invoices')
      .flush({ content: [invoice({}), exception], totalElements: 2, totalPages: 1, size: 20, number: 0 });
    backend.expectOne('/api/ap/ageing').flush([]);
    fixture.detectChanges();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const buttons = () =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')) as HTMLButtonElement[];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ApListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('shows each invoice match status and the reason an exception does not match', () => {
    setup(['AP_MANAGE']);

    expect(text()).toContain('Match exception');
    expect(text()).toContain('exceeds the 20000.00 of the goods receipt');
  });

  it('offers Approve variance only to a holder of AP_MATCH_OVERRIDE', () => {
    setup(['AP_MANAGE']);
    expect(buttons().some((b) => b.textContent?.includes('Approve variance'))).toBe(false);
  });

  it('requires a reason, then posts it to the real override endpoint and reloads', () => {
    setup(['AP_MANAGE', 'AP_MATCH_OVERRIDE']);
    buttons()
      .find((b) => b.textContent?.includes('Approve variance'))!
      .click();
    fixture.detectChanges();
    // The panel sits above the table, so its submit is the first Approve variance button; it needs a reason.
    const submit = buttons().find((b) => b.textContent?.includes('Approve variance'))!;
    expect(submit.disabled).toBe(true);

    fixture.componentInstance.reason = 'Supplier confirmed a price increase on the PO';
    fixture.detectChanges();
    fixture.componentInstance.approveVariance(exception);

    const req = backend.expectOne('/api/ap/invoices/2/match-override');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Supplier confirmed a price increase on the PO' });
    req.flush(invoice({ id: 2, matchStatus: 'OVERRIDDEN', matchVarianceAmount: 5000 }));
    flushLists();
    expect(fixture.componentInstance.overrideTarget()).toBeNull();
  });

  it('shows the server explanation when the override is refused', () => {
    setup(['AP_MATCH_OVERRIDE']);
    // The standalone component imports MatSnackBarModule, so it holds its own MatSnackBar instance.
    const snack = spyOn(fixture.componentInstance['snack'], 'open');
    fixture.componentInstance.openOverride(exception);
    fixture.componentInstance.reason = 'ok';

    fixture.componentInstance.approveVariance(exception);
    backend
      .expectOne('/api/ap/invoices/2/match-override')
      .flush(
        { detail: 'You entered invoice APINV/26/00002; a different finance manager must approve its variance' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );

    expect(snack).toHaveBeenCalledWith(
      'You entered invoice APINV/26/00002; a different finance manager must approve its variance',
      'OK',
      { duration: 6000 },
    );
    expect(fixture.componentInstance.saving()).toBe(false);
  });
});
