/* eslint-disable @typescript-eslint/no-explicit-any */
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { DebitNoteDialogComponent } from './debit-note-dialog.component';

describe('DebitNoteDialogComponent', () => {
  let fixture: ComponentFixture<DebitNoteDialogComponent>;
  let backend: HttpTestingController;
  let close: jasmine.Spy;
  let instance: Record<string, any>;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(() => {
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [DebitNoteDialogComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: { close } },
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            invoice: {
              id: 7,
              invoiceNumber: 'APINV/26/00007',
              subtotal: 20000,
              taxTotal: 3600,
              currencyCode: 'INR',
              counterparty: 'S3 · Steel Works',
            },
          },
        },
      ],
    });
    fixture = TestBed.createComponent(DebitNoteDialogComponent);
    backend = TestBed.inject(HttpTestingController);
    instance = fixture.componentInstance as unknown as Record<string, any>;
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('shows proportional tax reversal: debiting 10,000 of a 20,000 invoice reverses 1,800 of 3,600', () => {
    instance['form'].controls.subtotal.setValue(10000);
    fixture.detectChanges();

    const hint = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="tax-reversal"]');
    expect(hint?.textContent).toContain('Tax reversed 1,800.00');
    expect(hint?.textContent).toContain('debit total 11,800.00');
  });

  it('posts the CreateDebitNoteRequest with trimmed reason', () => {
    instance['form'].patchValue({ debitDate: '2026-10-23', subtotal: 5000, reason: '  Overcharge on SKU-001 ' });

    instance['save']();

    const post = backend.expectOne('/api/ap/debit-notes');
    expect(post.request.body).toEqual({
      apInvoiceId: 7,
      debitDate: '2026-10-23',
      subtotal: 5000,
      reason: 'Overcharge on SKU-001',
    });
    post.flush({ id: 11, debitNoteNumber: 'APDN/26/00001' });
    expect(close).toHaveBeenCalledWith(jasmine.objectContaining({ debitNoteNumber: 'APDN/26/00001' }));
  });

  it('shows server error and stays open when the debit is refused', () => {
    instance['form'].patchValue({ subtotal: 99999, reason: 'Too much' });

    instance['save']();
    backend
      .expectOne('/api/ap/debit-notes')
      .flush(
        { status: 422, code: 'VALIDATION_FAILED', detail: 'The debit exceeds the open balance' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    fixture.detectChanges();

    expect(text()).toContain('open balance');
    expect(close).not.toHaveBeenCalled();
  });

  it('does not submit when required fields are missing', () => {
    instance['save']();
    fixture.detectChanges();

    backend.expectNone('/api/ap/debit-notes');
  });
});
