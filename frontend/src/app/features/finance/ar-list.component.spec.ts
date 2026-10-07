import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { SessionStore } from '../../core/auth/session.store';
import { ArListComponent } from './ar-list.component';
import { ArInvoiceDto } from './finance.api';

function invoice(over: Partial<ArInvoiceDto>): ArInvoiceDto {
  return {
    id: 1,
    invoiceNumber: 'ARINV/26/00001',
    customerId: 4,
    dispatchId: 9,
    invoiceDate: '2026-10-05',
    dueDate: '2026-11-04',
    subtotal: 6000,
    taxTotal: 0,
    totalAmount: 6000,
    receivedAmount: 0,
    balanceAmount: 6000,
    status: 'OPEN',
    periodId: 7,
    journalHeaderId: 31,
    ...over,
  };
}

describe('ArListComponent receipts', () => {
  let fixture: ComponentFixture<ArListComponent>;
  let backend: HttpTestingController;
  let listed: ArInvoiceDto[];

  function setup(permissions: string[], invoices: ArInvoiceDto[] = [invoice({})]): void {
    listed = invoices;
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'acc',
      fullName: 'Accountant',
      email: 'a@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(ArListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/lookups/customers').flush([{ id: 4, code: 'C4', name: 'Spring Works' }]);
    flushLists();
  }

  function flushLists(): void {
    backend
      .expectOne((r) => r.url === '/api/ar/invoices')
      .flush({ content: listed, totalElements: listed.length, totalPages: 1, size: 20, number: 0 });
    backend.expectOne('/api/ar/ageing').flush([]);
    fixture.detectChanges();
  }

  /** Each open() call answers with the next value, the way successive closes of a dialog would. */
  function dialogReturning(...values: unknown[]) {
    return spyOn(fixture.componentInstance['dialog'], 'open').and.returnValues(
      ...values.map((value) => ({ afterClosed: () => of(value) }) as unknown as MatDialogRef<unknown>),
    );
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const buttons = () =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')) as HTMLButtonElement[];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ArListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('names the customer from the lookup and links the invoice journal', () => {
    setup(['AR_MANAGE']);

    expect(text()).toContain('C4 · Spring Works');
    expect((fixture.nativeElement as HTMLElement).querySelector('a[href="/finance/journals/31"]')).not.toBeNull();
  });

  it('falls back to the customer id when the lookup has no such customer', () => {
    setup(['AR_MANAGE'], [invoice({ customerId: 99 })]);

    expect(text()).toContain('#99');
  });

  it('offers Record receipt on an open or part-paid invoice to AR_MANAGE only', () => {
    setup(
      ['AR_MANAGE'],
      [
        invoice({ id: 1, status: 'OPEN' }),
        invoice({ id: 2, status: 'PARTIALLY_PAID' }),
        invoice({ id: 3, status: 'PAID' }),
        invoice({ id: 4, status: 'CANCELLED' }),
      ],
    );

    expect(buttons().filter((b) => b.textContent?.includes('Record receipt')).length).toBe(2);
  });

  it('hides Record receipt from a user without AR_MANAGE', () => {
    setup(['FINANCE_REPORT']);

    expect(buttons().some((b) => b.textContent?.includes('Record receipt'))).toBe(false);
  });

  it('opens the receipt dialog with the balance and the customer, then reloads and shows the new status', () => {
    setup(['AR_MANAGE'], [invoice({ balanceAmount: 6000 })]);
    const dialog = dialogReturning(invoice({ status: 'PAID', balanceAmount: 0, receivedAmount: 6000 }));
    const snack = spyOn(fixture.componentInstance['snack'], 'open');

    fixture.componentInstance.recordReceipt(invoice({ balanceAmount: 6000 }));

    expect(dialog.calls.mostRecent().args[1]?.data).toEqual({
      kind: 'AR',
      invoice: { id: 1, invoiceNumber: 'ARINV/26/00001', balanceAmount: 6000, counterparty: 'C4 · Spring Works' },
    });
    expect(snack).toHaveBeenCalledWith('Receipt recorded on ARINV/26/00001: paid, 0 outstanding', 'OK', {
      duration: 5000,
    });
    listed = [invoice({ status: 'PAID', balanceAmount: 0, receivedAmount: 6000 })];
    flushLists();
    expect(fixture.componentInstance.invoices()[0].status).toBe('PAID');
    expect(buttons().some((b) => b.textContent?.includes('Record receipt'))).toBe(false);
  });

  it('does not reload when the receipt dialog is dismissed', () => {
    setup(['AR_MANAGE']);
    const dialog = dialogReturning(undefined);

    fixture.componentInstance.recordReceipt(invoice({}));

    expect(dialog).toHaveBeenCalledTimes(1);
    backend.expectNone((r) => r.url === '/api/ar/invoices');
  });
});
