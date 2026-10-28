import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { PurchaseInvoiceListComponent } from './purchase-invoice-list.component';

describe('PurchaseInvoiceListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PurchaseInvoiceListComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(PurchaseInvoiceListComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
