import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SalesInvoiceListComponent } from './sales-invoice-list.component';

describe('SalesInvoiceListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SalesInvoiceListComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(SalesInvoiceListComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
