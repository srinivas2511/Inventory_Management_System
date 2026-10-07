import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
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

  let listed: ApInvoiceDto[] = [invoice({}), exception];

  function setup(permissions: string[], invoices: ApInvoiceDto[] = [invoice({}), exception]): void {
    listed = invoices;
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
    backend.expectOne('/api/lookups/suppliers').flush([{ id: 7, code: 'S7', name: 'Wire Co' }]);
    flushLists();
  }

  function flushLists(): void {
    backend
      .expectOne((r) => r.url === '/api/ap/invoices')
      .flush({ content: listed, totalElements: listed.length, totalPages: 1, size: 20, number: 0 });
    backend.expectOne('/api/ap/ageing').flush([]);
    fixture.detectChanges();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const buttons = () =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')) as HTMLButtonElement[];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ApListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
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
    backend.expectOne('/api/ap/invoices/2/match-override').flush(
      {
        status: 422,
        code: 'SEGREGATION_OF_DUTIES',
        detail: 'You entered invoice APINV/26/00002; a different finance manager must approve its variance',
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    expect(snack).toHaveBeenCalledWith(
      'You entered invoice APINV/26/00002; a different finance manager must approve its variance',
      'OK',
      { duration: 6000 },
    );
    expect(fixture.componentInstance.saving()).toBe(false);
  });

  describe('entry, opening, paying and cancelling', () => {
    /** Each open() call answers with the next value, the way successive closes of a dialog would. */
    function dialogReturning(...values: unknown[]) {
      return spyOn(fixture.componentInstance['dialog'], 'open').and.returnValues(
        ...values.map((value) => ({ afterClosed: () => of(value) }) as unknown as MatDialogRef<unknown>),
      );
    }

    it('names the supplier from the lookup and links the invoice journal', () => {
      setup(['AP_MANAGE'], [invoice({ journalHeaderId: 55, status: 'OPEN' })]);

      expect(text()).toContain('S7 · Wire Co');
      const link = (fixture.nativeElement as HTMLElement).querySelector('a[href="/finance/journals/55"]');
      expect(link).not.toBeNull();
    });

    it('shows New invoice, Open and Cancel to AP_MANAGE, but never Open on a match exception', () => {
      setup(['AP_MANAGE']);

      expect(buttons().some((b) => b.textContent?.includes('New invoice'))).toBe(true);
      const openButtons = buttons().filter((b) => b.textContent?.trim() === 'Open');
      expect(openButtons.length).toBe(1); // the matched draft only, not the exception
      expect(buttons().filter((b) => b.textContent?.trim() === 'Cancel').length).toBe(2);
    });

    it('hides every action from a user without AP_MANAGE', () => {
      setup(['AP_MATCH_OVERRIDE']);

      expect(buttons().some((b) => b.textContent?.includes('New invoice'))).toBe(false);
      expect(buttons().some((b) => b.textContent?.trim() === 'Open')).toBe(false);
      expect(buttons().some((b) => b.textContent?.trim() === 'Cancel')).toBe(false);
    });

    it('offers Pay on an open or part-paid invoice only', () => {
      setup(
        ['AP_MANAGE'],
        [
          invoice({ id: 1, status: 'OPEN' }),
          invoice({ id: 2, status: 'PARTIALLY_PAID' }),
          invoice({ id: 3, status: 'PAID' }),
          invoice({ id: 4, status: 'DRAFT' }),
        ],
      );

      expect(buttons().filter((b) => b.textContent?.trim() === 'Pay').length).toBe(2);
    });

    it('opens a matched draft through the real endpoint and reloads the list', () => {
      setup(['AP_MANAGE']);

      fixture.componentInstance.open(invoice({}));

      const req = backend.expectOne('/api/ap/invoices/1/open');
      expect(req.request.method).toBe('PUT');
      req.flush(invoice({ status: 'OPEN' }));
      flushLists();
    });

    it('shows the server explanation when opening is refused', () => {
      setup(['AP_MANAGE']);
      const snack = spyOn(fixture.componentInstance['snack'], 'open');

      fixture.componentInstance.open(exception);
      backend.expectOne('/api/ap/invoices/2/open').flush(
        {
          status: 422,
          code: 'ILLEGAL_STATE_TRANSITION',
          detail: 'Invoice APINV/26/00002 does not match its goods receipt',
        },
        { status: 422, statusText: 'Unprocessable Entity' },
      );

      expect(snack).toHaveBeenCalledWith('Invoice APINV/26/00002 does not match its goods receipt', 'OK', {
        duration: 6000,
      });
      expect(fixture.componentInstance.saving()).toBe(false);
    });

    it('opens the payment dialog with the balance and the supplier, then reloads on success', () => {
      setup(['AP_MANAGE'], [invoice({ status: 'OPEN', balanceAmount: 12000 })]);
      const dialog = dialogReturning(invoice({ status: 'PARTIALLY_PAID', balanceAmount: 2000 }));

      fixture.componentInstance.pay(invoice({ status: 'OPEN', balanceAmount: 12000 }));

      expect(dialog.calls.mostRecent().args[1]?.data).toEqual({
        kind: 'AP',
        invoice: {
          id: 1,
          invoiceNumber: 'APINV/26/00001',
          balanceAmount: 12000,
          counterparty: 'S7 · Wire Co',
        },
      });
      flushLists();
    });

    it('does not reload when the payment dialog is dismissed', () => {
      setup(['AP_MANAGE']);
      dialogReturning(undefined);

      fixture.componentInstance.pay(invoice({ status: 'OPEN' }));

      backend.expectNone((r) => r.url === '/api/ap/invoices');
    });

    it('cancels a draft only after confirmation', () => {
      setup(['AP_MANAGE']);
      dialogReturning(true);

      fixture.componentInstance.cancel(exception);

      const req = backend.expectOne('/api/ap/invoices/2/cancel');
      expect(req.request.method).toBe('PUT');
      req.flush(invoice({ id: 2, status: 'CANCELLED' }));
      flushLists();
    });

    it('does nothing when the cancellation is not confirmed', () => {
      setup(['AP_MANAGE']);
      const dialog = dialogReturning(undefined);

      fixture.componentInstance.cancel(exception);

      expect(dialog).toHaveBeenCalledTimes(1);
      backend.expectNone('/api/ap/invoices/2/cancel');
    });

    it('reloads after the entry dialog saved something, and not when it was dismissed', () => {
      setup(['AP_MANAGE']);
      dialogReturning(true, false);
      fixture.componentInstance.newInvoice();
      flushLists();

      fixture.componentInstance.newInvoice();
      backend.expectNone((r) => r.url === '/api/ap/invoices');
    });
  });
});
