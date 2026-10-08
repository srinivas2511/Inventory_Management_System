/* eslint-disable @typescript-eslint/no-explicit-any */
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { CreditNoteDialogComponent } from './credit-note-dialog.component';

describe('CreditNoteDialogComponent', () => {
  let fixture: ComponentFixture<CreditNoteDialogComponent>;
  let backend: HttpTestingController;
  let close: jasmine.Spy;
  let instance: Record<string, any>;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(() => {
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [CreditNoteDialogComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: { close } },
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            invoice: {
              id: 5,
              invoiceNumber: 'ARINV/26/00005',
              subtotal: 20000,
              taxTotal: 3600,
              currencyCode: 'INR',
              counterparty: 'C4 · Spring Works',
            },
          },
        },
      ],
    });
    fixture = TestBed.createComponent(CreditNoteDialogComponent);
    backend = TestBed.inject(HttpTestingController);
    instance = fixture.componentInstance as unknown as Record<string, any>;
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('shows the tax that crediting 10,000 of a 20,000 invoice reverses: 1,800 of 3,600', () => {
    instance['form'].controls.subtotal.setValue(10000);
    fixture.detectChanges();

    const hint = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="tax-reversal"]');
    expect(hint?.textContent).toContain('Tax reversed 1,800.00');
    expect(hint?.textContent).toContain('credit total 11,800.00');
  });

  it('posts the CreateCreditNoteRequest shape without any return fields when no goods came back', () => {
    instance['form'].patchValue({ creditDate: '2026-10-07', subtotal: 10000, reason: '  Damaged in transit ' });

    instance['save']();

    const post = backend.expectOne('/api/ar/credit-notes');
    expect(post.request.body).toEqual({
      arInvoiceId: 5,
      creditDate: '2026-10-07',
      subtotal: 10000,
      reason: 'Damaged in transit',
    });
    post.flush({ id: 9, creditNoteNumber: 'CN/26/00001' });
    expect(close).toHaveBeenCalledWith(jasmine.objectContaining({ creditNoteNumber: 'CN/26/00001' }));
  });

  it('asks for the batch, quantity and place when goods came back, and sends them', () => {
    instance['form'].patchValue({ subtotal: 5000, reason: 'Returned', returned: true });
    backend.expectOne('/api/lookups/warehouses').flush([{ id: 2, code: 'W2', name: 'Main' }]);

    instance['save']();
    fixture.detectChanges();
    expect(text()).toContain('give the batch, the quantity, the warehouse and the location');
    backend.expectNone('/api/ar/credit-notes');

    instance['form'].patchValue({ batchId: 31, quantity: 10, warehouseId: 2 });
    backend
      .expectOne((r) => r.url === '/api/lookups/locations' && r.params.get('warehouseId') === '2')
      .flush([{ id: 8, warehouseId: 2, code: 'QR', name: 'Quarantine', locationType: 'QUARANTINE' }]);
    instance['form'].patchValue({ locationId: 8 });
    instance['save']();

    const body = backend.expectOne('/api/ar/credit-notes').request.body;
    expect(body.returnFgBatchId).toBe(31);
    expect(body.returnQuantity).toBe(10);
    expect(body.returnWarehouseId).toBe(2);
    expect(body.returnLocationId).toBe(8);
  });

  it('shows the server message and stays open when the credit is refused', () => {
    instance['form'].patchValue({ subtotal: 99999, reason: 'Too much' });

    instance['save']();
    backend
      .expectOne('/api/ar/credit-notes')
      .flush(
        { status: 422, code: 'VALIDATION_FAILED', detail: 'The credit is above the part not yet credited' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    fixture.detectChanges();

    expect(text()).toContain('above the part not yet credited');
    expect(close).not.toHaveBeenCalled();
  });
});
