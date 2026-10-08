/* eslint-disable @typescript-eslint/no-explicit-any */
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { CreditNotesComponent } from './credit-notes.component';
import { CreditNoteDto } from './finance.api';

function note(over: Partial<CreditNoteDto>): CreditNoteDto {
  return {
    id: 9,
    creditNoteNumber: 'CN/26/00001',
    arInvoiceId: 5,
    invoiceNumber: 'ARINV/26/00005',
    currencyCode: 'INR',
    creditDate: '2026-10-07',
    reason: 'Damaged',
    subtotal: 10000,
    taxTotal: 1800,
    totalAmount: 11800,
    status: 'DRAFT',
    journalHeaderId: null,
    createdBy: 1,
    approvedBy: null,
    approvedAt: null,
    returnFgBatchId: null,
    returnQuantity: null,
    returnTransactionNumber: null,
    taxLines: [
      { taxCode: 'CGST9', taxType: 'CGST', ratePct: 9, taxableAmount: 10000, taxAmount: 900 },
      { taxCode: 'SGST9', taxType: 'SGST', ratePct: 9, taxableAmount: 10000, taxAmount: 900 },
    ],
    ...over,
  };
}

describe('CreditNotesComponent', () => {
  let fixture: ComponentFixture<CreditNotesComponent>;
  let backend: HttpTestingController;
  let instance: Record<string, any>;

  function setup(userId: number, notes: CreditNoteDto[]): void {
    TestBed.configureTestingModule({
      imports: [CreditNotesComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.inject(SessionStore).setUser({
      id: userId,
      username: 'u' + userId,
      fullName: 'User',
      email: 'u@example.com',
      roles: [],
      permissions: ['AR_MANAGE', 'CREDIT_NOTE_APPROVE'],
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(CreditNotesComponent);
    backend = TestBed.inject(HttpTestingController);
    instance = fixture.componentInstance as unknown as Record<string, any>;
    fixture.detectChanges();
    backend
      .expectOne((r) => r.url === '/api/ar/credit-notes')
      .flush({ content: notes, totalElements: notes.length, totalPages: 1, size: 20, number: 0 });
    fixture.detectChanges();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const button = (label: string) =>
    (Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')) as HTMLButtonElement[]).find((b) =>
      (b.textContent ?? '').includes(label),
    );

  afterEach(() => backend.verify());

  it('lists the tax reversed per component', () => {
    setup(2, [note({})]);

    expect(text()).toContain('CN/26/00001');
    expect(text()).toContain('CGST 900.00');
    expect(text()).toContain('SGST 900.00');
    expect(text()).toContain('11,800.00');
  });

  it('disables Approve for the user who created the credit note, because a different user has to approve it', () => {
    setup(1, [note({ createdBy: 1 })]);

    expect(button('Approve')?.disabled).toBe(true);
  });

  it('lets another user approve, and reloads the list', () => {
    setup(2, [note({ createdBy: 1 })]);
    expect(button('Approve')?.disabled).toBe(false);

    instance['approve'](note({ createdBy: 1 }));

    const post = backend.expectOne('/api/ar/credit-notes/9/approve');
    expect(post.request.method).toBe('POST');
    post.flush(note({ status: 'POSTED', journalHeaderId: 55, approvedBy: 2 }));
    backend.expectOne((r) => r.url === '/api/ar/credit-notes').flush({ content: [], totalElements: 0 });
  });

  it('offers no actions on a posted credit note, and links its journal', () => {
    setup(2, [note({ status: 'POSTED', journalHeaderId: 55 })]);

    expect(button('Approve')).toBeUndefined();
    expect(button('Cancel')).toBeUndefined();
    expect((fixture.nativeElement as HTMLElement).querySelector('a[href="/finance/journals/55"]')).not.toBeNull();
  });
});
