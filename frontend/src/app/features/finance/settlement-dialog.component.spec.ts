/* eslint-disable @typescript-eslint/no-explicit-any */
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SettlementDialogComponent, SettlementDialogData } from './settlement-dialog.component';

describe('SettlementDialogComponent', () => {
  let fixture: ComponentFixture<SettlementDialogComponent>;
  let backend: HttpTestingController;
  let close: jasmine.Spy;
  let instance: Record<string, any>;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  function setup(kind: 'AP' | 'AR', balance = 12000): void {
    close = jasmine.createSpy('close');
    const data: SettlementDialogData = {
      kind,
      invoice: {
        id: 5,
        invoiceNumber: kind === 'AP' ? 'APINV/26/00005' : 'ARINV/26/00005',
        balanceAmount: balance,
        counterparty: 'S7 · Wire Co',
      },
    };
    TestBed.configureTestingModule({
      imports: [SettlementDialogComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    });
    fixture = TestBed.createComponent(SettlementDialogComponent);
    backend = TestBed.inject(HttpTestingController);
    instance = fixture.componentInstance as unknown as Record<string, any>;
    fixture.detectChanges();
  }

  afterEach(() => backend.verify());

  describe('paying a supplier', () => {
    beforeEach(() => setup('AP'));

    it('starts with the whole outstanding balance and names the invoice and supplier', () => {
      expect(instance['form'].value.amount).toBe(12000);
      expect(text()).toContain('Record payment');
      expect(text()).toContain('APINV/26/00005 · S7 · Wire Co');
      expect(text()).toContain('Outstanding 12,000.00');
    });

    it('does not offer UPI to a supplier', () => {
      expect(instance['modes'].map((m: { value: string }) => m.value)).toEqual(['BANK_TRANSFER', 'CHEQUE', 'CASH']);
    });

    it('refuses a payment above the balance, at four decimals', () => {
      instance['form'].controls.amount.setValue(12000.0001);
      expect(instance['form'].controls.amount.hasError('overBalance')).toBe(true);

      instance['save']();
      backend.expectNone('/api/ap/invoices/5/payments');

      instance['form'].controls.amount.setValue(12000);
      expect(instance['form'].controls.amount.hasError('overBalance')).toBe(false);
    });

    it('refuses zero, negative and empty amounts', () => {
      for (const bad of [0, -5, null]) {
        instance['form'].controls.amount.setValue(bad);
        instance['save']();
        expect(instance['form'].invalid).toBe(true);
      }
      backend.expectNone('/api/ap/invoices/5/payments');
    });

    it('posts the ApPaymentRequest shape and closes with the updated invoice', () => {
      instance['form'].patchValue({
        amount: 5000,
        date: '2026-10-07',
        mode: 'CHEQUE',
        reference: '  CHQ-0042 ',
      });

      instance['save']();

      const req = backend.expectOne('/api/ap/invoices/5/payments');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        paymentDate: '2026-10-07',
        amount: 5000,
        paymentMode: 'CHEQUE',
        referenceNumber: 'CHQ-0042',
      });
      const updated = { id: 5, status: 'PARTIALLY_PAID', balanceAmount: 7000 };
      req.flush(updated);
      expect(close).toHaveBeenCalledWith(updated);
    });

    it('sends no reference when it is blank', () => {
      instance['form'].patchValue({ reference: '   ' });

      instance['save']();

      const req = backend.expectOne('/api/ap/invoices/5/payments');
      expect(req.request.body.referenceNumber).toBeNull();
      req.flush({ id: 5 });
    });

    it('shows the server explanation and stays open when the payment is refused', () => {
      instance['save']();

      backend
        .expectOne('/api/ap/invoices/5/payments')
        .flush(
          { status: 422, code: 'ILLEGAL_STATE_TRANSITION', detail: 'Invoice APINV/26/00005 is not payable' },
          { status: 422, statusText: 'Unprocessable Entity' },
        );
      fixture.detectChanges();

      expect(text()).toContain('Invoice APINV/26/00005 is not payable');
      expect(close).not.toHaveBeenCalled();
      expect(instance['saving']()).toBe(false);
    });
  });

  describe('at the limit', () => {
    beforeEach(() => setup('AP', 0.3));

    it('is not fooled by float noise: 0.1 + 0.2 is within a balance of 0.3 at four decimals', () => {
      instance['form'].controls.amount.setValue(0.1 + 0.2);

      expect(0.1 + 0.2).not.toBe(0.3);
      expect(instance['form'].controls.amount.hasError('overBalance')).toBe(false);
    });
  });

  describe('recording a customer receipt', () => {
    beforeEach(() => setup('AR', 8000));

    it('is worded as a receipt and offers UPI', () => {
      expect(text()).toContain('Record receipt');
      expect(text()).toContain('Receipt date');
      expect(instance['modes'].map((m: { value: string }) => m.value)).toContain('UPI');
    });

    it('posts the ArReceiptRequest shape to the receipts endpoint', () => {
      instance['form'].patchValue({ amount: 8000, date: '2026-10-07', mode: 'UPI', reference: 'UTR-77' });

      instance['save']();

      const req = backend.expectOne('/api/ar/invoices/5/receipts');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        receiptDate: '2026-10-07',
        amount: 8000,
        paymentMode: 'UPI',
        referenceNumber: 'UTR-77',
      });
      req.flush({ id: 5, status: 'PAID', balanceAmount: 0 });
      expect(close).toHaveBeenCalledWith({ id: 5, status: 'PAID', balanceAmount: 0 });
    });

    it('refuses a receipt above the balance', () => {
      instance['form'].controls.amount.setValue(8000.01);

      instance['save']();

      backend.expectNone('/api/ar/invoices/5/receipts');
      expect(instance['form'].controls.amount.hasError('overBalance')).toBe(true);
    });
  });
});
