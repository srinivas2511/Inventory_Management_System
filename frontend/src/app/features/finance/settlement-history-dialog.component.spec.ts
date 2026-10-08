import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SettlementDto } from './finance.api';
import { SettlementHistoryDialogComponent } from './settlement-history-dialog.component';

function settlement(over: Partial<SettlementDto>): SettlementDto {
  return {
    id: 1,
    reference: 'APPMT/26/00001',
    date: '2012-02-10',
    amount: 250,
    paymentMode: 'BANK_TRANSFER',
    referenceNumber: null,
    exchangeRate: 82,
    carryingInr: 20000,
    fxDifference: -500,
    journalHeaderId: 44,
    ...over,
  };
}

describe('SettlementHistoryDialogComponent', () => {
  let fixture: ComponentFixture<SettlementHistoryDialogComponent>;
  let backend: HttpTestingController;

  function open(kind: 'AP' | 'AR', id: number): void {
    TestBed.configureTestingModule({
      imports: [SettlementHistoryDialogComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: MAT_DIALOG_DATA, useValue: { kind, invoice: { id, invoiceNumber: 'INV-7', currencyCode: 'USD' } } },
      ],
    });
    fixture = TestBed.createComponent(SettlementHistoryDialogComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => backend.verify());

  it('shows a supplier payment at 82 against a payable carried at 80 as a loss of 500', () => {
    open('AP', 7);
    backend.expectOne('/api/ap/invoices/7/payments').flush([settlement({})]);
    fixture.detectChanges();

    expect(text()).toContain('Payments on INV-7');
    const cell = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="fx-difference"]');
    expect(cell?.textContent).toContain('Loss 500.00');
    expect(text()).toContain('20,000.00');
    expect(text()).toContain('82.00');
  });

  it('shows a customer receipt that settles for more rupees than carried as a gain', () => {
    open('AR', 9);
    backend
      .expectOne('/api/ar/invoices/9/receipts')
      .flush([settlement({ reference: 'ARRCT/26/00001', fxDifference: 300, exchangeRate: 81 })]);
    fixture.detectChanges();

    expect(text()).toContain('Receipts on INV-7');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="fx-difference"]')?.textContent,
    ).toContain('Gain 300.00');
  });

  it('says so when nothing was paid yet', () => {
    open('AP', 7);
    backend.expectOne('/api/ap/invoices/7/payments').flush([]);
    fixture.detectChanges();
    expect(text()).toContain('Nothing has been paid on this invoice yet');
  });
});
