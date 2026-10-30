import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ServiceOrderListComponent } from './service-order-list.component';

describe('ServiceOrderListComponent', () => {
  let fixture: ComponentFixture<ServiceOrderListComponent>;
  let component: ServiceOrderListComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ServiceOrderListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(ServiceOrderListComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have loading state initially true', () => {
    expect(component.loading()).toBeTrue();
  });

  it('should render table columns', () => {
    expect(component.columns).toContain('docNo');
    expect(component.columns).toContain('status');
    expect(component.columns).toContain('serviceKind');
  });

  it('should initialise inward dialog state', () => {
    component.openInwardDialog({ id: 1, soId: 1, confirmedAt: '', confirmedBy: 1, remarks: null });
    expect(component.inwardDialogOpen()).toBeTrue();
    expect(component.inwardItems.length).toBe(1);
  });

  it('should initialise invoice dialog state', () => {
    component.openInvoiceDialog({ id: 1, soId: 1, confirmedAt: '', confirmedBy: 1, remarks: null });
    expect(component.invoiceDialogOpen()).toBeTrue();
    expect(component.invoiceLines.length).toBe(1);
  });

  it('should add inward item', () => {
    component.openInwardDialog({ id: 1, soId: 1, confirmedAt: '', confirmedBy: 1, remarks: null });
    component.addInwardItem();
    expect(component.inwardItems.length).toBe(2);
  });

  it('should add invoice line', () => {
    component.openInvoiceDialog({ id: 1, soId: 1, confirmedAt: '', confirmedBy: 1, remarks: null });
    component.addInvoiceLine();
    expect(component.invoiceLines.length).toBe(2);
  });
});
