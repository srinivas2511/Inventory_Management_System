/* eslint-disable @typescript-eslint/no-explicit-any */
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ApInvoiceFormComponent } from './ap-invoice-form.component';
import { ApInvoiceDto } from './finance.api';

const receipt = {
  id: 3,
  grNumber: 'GR/26/00003',
  receiptDate: '2026-10-05',
  poNumber: 'PO/26/00009',
  supplierId: 7,
  receivedValue: 20000,
  receivedTax: 3600,
  invoicedValue: 0,
  openValue: 20000,
};

function invoice(over: Partial<ApInvoiceDto>): ApInvoiceDto {
  return {
    id: 11,
    invoiceNumber: 'APINV/26/00011',
    supplierInvoiceNo: 'S-100',
    supplierId: 7,
    invoiceDate: '2026-10-07',
    dueDate: '2026-11-06',
    subtotal: 20000,
    taxTotal: 3600,
    totalAmount: 23600,
    paidAmount: 0,
    balanceAmount: 23600,
    status: 'DRAFT',
    goodsReceiptId: 3,
    matchStatus: 'MATCHED',
    matchExpectedAmount: 20000,
    matchVarianceAmount: 0,
    matchMessage: null,
    ...over,
  };
}

describe('ApInvoiceFormComponent', () => {
  let fixture: ComponentFixture<ApInvoiceFormComponent>;
  let backend: HttpTestingController;
  let close: jasmine.Spy;
  let instance: Record<string, any>;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const buttons = () =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')) as HTMLButtonElement[];

  function receiptsRequest() {
    return backend.expectOne(
      (r) => r.url === '/api/lookups/goods-receipts/invoiceable' && r.params.get('supplierId') === '7',
    );
  }

  /** Chooses supplier 7 and its receipt, the way a user would, leaving the amounts to the suggestion. */
  function chooseReceipt(): void {
    instance['form'].controls.supplierId.setValue(7);
    receiptsRequest().flush([receipt]);
    instance['form'].controls.goodsReceiptId.setValue(3);
    fixture.detectChanges();
  }

  beforeEach(() => {
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [ApInvoiceFormComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MatDialogRef, useValue: { close } }],
    });
    fixture = TestBed.createComponent(ApInvoiceFormComponent);
    backend = TestBed.inject(HttpTestingController);
    instance = fixture.componentInstance as unknown as Record<string, any>;
    fixture.detectChanges();
    backend.expectOne('/api/lookups/suppliers').flush([{ id: 7, code: 'S7', name: 'Wire Co' }]);
    backend.expectOne('/api/finance/tax/slabs').flush([
      {
        slabCode: 'GST18',
        description: '18% goods',
        components: [
          { supplyType: 'INTRA', taxCode: 'CGST9', taxType: 'CGST', ratePct: 9 },
          { supplyType: 'INTRA', taxCode: 'SGST9', taxType: 'SGST', ratePct: 9 },
          { supplyType: 'INTER', taxCode: 'IGST18', taxType: 'IGST', ratePct: 18 },
        ],
      },
    ]);
  });

  afterEach(() => backend.verify());

  it('refuses an empty form and posts nothing', () => {
    instance['save']();

    backend.expectNone('/api/ap/invoices');
    expect(instance['form'].touched).toBe(true);
  });

  it('loads the supplier receipts that still have value open, and suggests the open value and its tax', () => {
    chooseReceipt();

    expect(instance['form'].value.subtotal).toBe(20000);
    expect(instance['form'].value.taxTotal).toBe(3600);
    expect(text()).toContain('Invoice total 23,600.00');
  });

  it('leaves an amount the user already typed alone when a receipt is chosen', () => {
    instance['form'].controls.subtotal.setValue(18000);
    instance['form'].controls.subtotal.markAsDirty();

    chooseReceipt();

    expect(instance['form'].value.subtotal).toBe(18000);
  });

  it('does not suggest the receipt tax once part of the receipt is already invoiced', () => {
    instance['form'].controls.supplierId.setValue(7);
    receiptsRequest().flush([{ ...receipt, invoicedValue: 12000, openValue: 8000 }]);

    instance['form'].controls.goodsReceiptId.setValue(3);

    expect(instance['form'].value.subtotal).toBe(8000);
    expect(instance['form'].value.taxTotal).toBeNull();
  });

  it('clears the chosen receipt when the supplier changes', () => {
    chooseReceipt();

    instance['form'].controls.supplierId.setValue(8);
    backend
      .expectOne((r) => r.url === '/api/lookups/goods-receipts/invoiceable' && r.params.get('supplierId') === '8')
      .flush([]);

    expect(instance['form'].value.goodsReceiptId).toBeNull();
  });

  it('posts the ApInvoiceCreateRequest shape and shows the match result for a matching invoice', () => {
    chooseReceipt();
    instance['form'].patchValue({ supplierInvoiceNo: ' S-100 ', invoiceDate: '2026-10-07', dueDate: '2026-11-06' });

    instance['save']();

    const req = backend.expectOne('/api/ap/invoices');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      supplierId: 7,
      supplierInvoiceNo: 'S-100',
      invoiceDate: '2026-10-07',
      dueDate: '2026-11-06',
      goodsReceiptId: 3,
      subtotal: 20000,
      taxTotal: 3600,
    });
    req.flush(invoice({}));
    fixture.detectChanges();

    expect(text()).toContain('Invoice APINV/26/00011 saved');
    expect(text()).toContain('It matches its goods receipt.');
    expect(buttons().some((b) => b.textContent?.includes('Open invoice'))).toBe(true);
    expect(close).not.toHaveBeenCalled();
  });

  it('sends no tax when the tax box is empty, and no invoice number when it is blank', () => {
    instance['form'].controls.supplierId.setValue(7);
    receiptsRequest().flush([{ ...receipt, invoicedValue: 12000, openValue: 8000 }]);
    instance['form'].controls.goodsReceiptId.setValue(3);
    instance['form'].patchValue({ supplierInvoiceNo: '   ' });

    instance['save']();

    const req = backend.expectOne('/api/ap/invoices');
    expect(req.request.body.taxTotal).toBeNull();
    expect(req.request.body.supplierInvoiceNo).toBeNull();
    req.flush(invoice({}));
  });

  it('opens a matching invoice from the result and closes the dialog', () => {
    chooseReceipt();
    instance['save']();
    backend.expectOne('/api/ap/invoices').flush(invoice({}));
    fixture.detectChanges();

    buttons()
      .find((b) => b.textContent?.includes('Open invoice'))!
      .click();

    const req = backend.expectOne('/api/ap/invoices/11/open');
    expect(req.request.method).toBe('PUT');
    req.flush(invoice({ status: 'OPEN' }));
    expect(close).toHaveBeenCalledWith(true);
  });

  it('shows the mismatch, withholds Open and explains who can approve it', () => {
    chooseReceipt();
    instance['form'].controls.subtotal.setValue(25000);
    instance['save']();
    backend.expectOne('/api/ap/invoices').flush(
      invoice({
        subtotal: 25000,
        totalAmount: 28600,
        matchStatus: 'EXCEPTION',
        matchVarianceAmount: 5000,
        matchMessage: 'Invoice subtotal 25000.00 exceeds the 20000.00 of the goods receipt still open for matching.',
      }),
    );
    fixture.detectChanges();

    expect(text()).toContain('It does not match its goods receipt.');
    expect(text()).toContain('exceeds the 20000.00 of the goods receipt');
    expect(text()).toContain('A finance manager other than the person who entered it');
    expect(text()).toContain('5,000.00');
    expect(buttons().some((b) => b.textContent?.includes('Open invoice'))).toBe(false);
  });

  it('cancels a mismatching draft from the result so a corrected invoice can be entered', () => {
    chooseReceipt();
    instance['save']();
    backend.expectOne('/api/ap/invoices').flush(invoice({ matchStatus: 'EXCEPTION', matchMessage: 'too high' }));
    fixture.detectChanges();

    buttons()
      .find((b) => b.textContent?.includes('Cancel this invoice'))!
      .click();

    const req = backend.expectOne('/api/ap/invoices/11/cancel');
    expect(req.request.method).toBe('PUT');
    req.flush(invoice({ status: 'CANCELLED' }));
    expect(close).toHaveBeenCalledWith(true);
  });

  it('shows the server error and stays on the form when saving fails', () => {
    chooseReceipt();
    instance['save']();

    backend
      .expectOne('/api/ap/invoices')
      .flush(
        { status: 422, code: 'PERIOD_CLOSED', detail: 'No open period for 2026-10-07' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    fixture.detectChanges();

    expect(text()).toContain('No open period for 2026-10-07');
    expect(text()).toContain('New supplier invoice');
    expect(instance['saving']()).toBe(false);
  });

  it('tells the dialog opener whether anything was created when it closes', () => {
    instance['close']();
    expect(close).toHaveBeenCalledWith(false);

    chooseReceipt();
    instance['save']();
    backend.expectOne('/api/ap/invoices').flush(invoice({}));
    instance['close']();
    expect(close).toHaveBeenCalledWith(true);
  });

  it('previews the tax of a GST slab by place of supply and takes the tax box out of play', fakeAsync(() => {
    instance['form'].controls.supplierId.setValue(7);
    receiptsRequest().flush([receipt]);
    instance['form'].controls.taxSlabCode.setValue('GST18');
    instance['form'].controls.subtotal.setValue(20000);
    tick(300);

    const call = backend.expectOne((r) => r.url === '/api/finance/tax/determine');
    expect(call.request.params.get('direction')).toBe('PURCHASE');
    expect(call.request.params.get('slab')).toBe('GST18');
    expect(call.request.params.get('partnerId')).toBe('7');
    expect(call.request.params.get('amount')).toBe('20000');
    call.flush({
      slabCode: 'GST18',
      supplyType: 'INTRA',
      companyStateCode: '27',
      partnerStateCode: '27',
      partnerStateAssumed: false,
      taxableAmount: 20000,
      taxTotal: 3600,
      lines: [
        { taxCode: 'CGST9', taxType: 'CGST', ratePct: 9, taxableAmount: 20000, taxAmount: 1800 },
        { taxCode: 'SGST9', taxType: 'SGST', ratePct: 9, taxableAmount: 20000, taxAmount: 1800 },
      ],
    });
    fixture.detectChanges();

    const preview = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="tax-preview"]');
    expect(preview?.textContent).toContain('Within the company state: CGST + SGST');
    expect(preview?.textContent).toContain('CGST 9% on 20,000.00 = 1,800.00');
    expect(preview?.textContent).toContain('Tax 3,600.00');
    expect(instance['form'].controls.taxTotal.disabled).toBe(true);
    expect(instance['total']()).toBe(23600);
    flush();
  }));

  it('sends the slab and an upper-case currency, and no typed tax amount, when a slab is chosen', fakeAsync(() => {
    chooseReceipt();
    instance['form'].controls.taxSlabCode.setValue('GST18');
    instance['form'].controls.currencyCode.setValue('usd');
    instance['form'].controls.subtotal.setValue(250);
    tick(300);
    backend
      .expectOne((r) => r.url === '/api/finance/tax/determine')
      .flush({
        slabCode: 'GST18',
        supplyType: 'INTER',
        companyStateCode: '27',
        partnerStateCode: '29',
        partnerStateAssumed: false,
        taxableAmount: 250,
        taxTotal: 45,
        lines: [{ taxCode: 'IGST18', taxType: 'IGST', ratePct: 18, taxableAmount: 250, taxAmount: 45 }],
      });

    instance['save']();

    const post = backend.expectOne('/api/ap/invoices');
    expect(post.request.body.taxSlabCode).toBe('GST18');
    expect(post.request.body.currencyCode).toBe('USD');
    expect(post.request.body.taxTotal).toBeNull();
    expect(post.request.body.subtotal).toBe(250);
    post.flush(
      invoice({
        subtotal: 250,
        taxTotal: 45,
        totalAmount: 295,
        currencyCode: 'USD',
        exchangeRate: 80,
        totalInr: 23600,
        taxLines: [{ taxCode: 'IGST18', taxType: 'IGST', ratePct: 18, taxableAmount: 250, taxAmount: 45 }],
      }),
    );
    fixture.detectChanges();

    expect(text()).toContain('IGST 18%');
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="result-inr"]')?.textContent).toContain(
      '23,600.00',
    );
  }));
});
