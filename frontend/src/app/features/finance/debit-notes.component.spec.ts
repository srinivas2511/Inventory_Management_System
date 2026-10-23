/* eslint-disable @typescript-eslint/no-explicit-any */
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { DebitNotesComponent } from './debit-notes.component';
import { ApDebitNoteDto } from './finance.api';

function note(over: Partial<ApDebitNoteDto>): ApDebitNoteDto {
  return {
    id: 11,
    debitNoteNumber: 'APDN/26/00001',
    apInvoiceId: 7,
    invoiceNumber: 'APINV/26/00007',
    currencyCode: 'INR',
    debitDate: '2026-10-23',
    reason: 'Overcharge on item X',
    subtotal: 1000,
    taxTotal: 180,
    totalAmount: 1180,
    status: 'DRAFT',
    journalHeaderId: null,
    createdBy: 1,
    approvedBy: null,
    approvedAt: null,
    ...over,
  };
}

describe('DebitNotesComponent', () => {
  let fixture: ComponentFixture<DebitNotesComponent>;
  let backend: HttpTestingController;
  let instance: Record<string, any>;

  function setup(userId: number, notes: ApDebitNoteDto[]): void {
    TestBed.configureTestingModule({
      imports: [DebitNotesComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.inject(SessionStore).setUser({
      id: userId,
      username: 'u' + userId,
      fullName: 'User',
      email: 'u@example.com',
      roles: [],
      permissions: ['AP_MANAGE', 'DEBIT_NOTE_APPROVE'],
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(DebitNotesComponent);
    backend = TestBed.inject(HttpTestingController);
    instance = fixture.componentInstance as unknown as Record<string, any>;
    fixture.detectChanges();
    backend
      .expectOne((r) => r.url === '/api/ap/debit-notes')
      .flush({ content: notes, totalElements: notes.length, totalPages: 1, size: 20, number: 0 });
    fixture.detectChanges();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const button = (label: string) =>
    (Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')) as HTMLButtonElement[]).find((b) =>
      (b.textContent ?? '').includes(label),
    );

  afterEach(() => backend.verify());

  it('lists debit note number, invoice number and total', () => {
    setup(2, [note({})]);

    expect(text()).toContain('APDN/26/00001');
    expect(text()).toContain('APINV/26/00007');
    expect(text()).toContain('1,180.00');
  });

  it('disables Approve for the user who created the debit note (SoD)', () => {
    setup(1, [note({ createdBy: 1 })]);

    expect(button('Approve')?.disabled).toBe(true);
  });

  it('lets another user approve and reloads the list', () => {
    setup(2, [note({ createdBy: 1 })]);
    expect(button('Approve')?.disabled).toBe(false);

    instance['approve'](note({ createdBy: 1 }));

    const post = backend.expectOne('/api/ap/debit-notes/11/approve');
    expect(post.request.method).toBe('POST');
    post.flush(note({ status: 'POSTED', journalHeaderId: 88, approvedBy: 2 }));
    backend.expectOne((r) => r.url === '/api/ap/debit-notes').flush({ content: [], totalElements: 0 });
  });

  it('offers no actions on a posted debit note, and links its journal', () => {
    setup(2, [note({ status: 'POSTED', journalHeaderId: 88 })]);

    expect(button('Approve')).toBeUndefined();
    expect(button('Cancel')).toBeUndefined();
    expect((fixture.nativeElement as HTMLElement).querySelector('a[href="/finance/journals/88"]')).not.toBeNull();
  });
});
