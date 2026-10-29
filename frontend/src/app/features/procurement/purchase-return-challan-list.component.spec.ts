import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { PurchaseReturnChallanListComponent } from './purchase-return-challan-list.component';
import { AuthService } from '../../core/auth/auth.service';

describe('PurchaseReturnChallanListComponent', () => {
  let fixture: ComponentFixture<PurchaseReturnChallanListComponent>;
  let http: HttpTestingController;

  const mockAuth = { hasPermission: () => true, currentUser: null };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PurchaseReturnChallanListComponent, HttpClientTestingModule, NoopAnimationsModule],
      providers: [{ provide: AuthService, useValue: mockAuth }],
    }).compileComponents();
    fixture = TestBed.createComponent(PurchaseReturnChallanListComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('creates component', () => {
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
    http.expectOne('/api/procurement/purchase-return-challans').flush([]);
  });

  it('renders loaded challans in table', () => {
    fixture.detectChanges();
    http.expectOne('/api/procurement/purchase-return-challans').flush([
      {
        id: 1,
        docNo: 'PRC-001',
        docDate: '2026-10-29',
        status: 'DRAFT',
        supplierId: 10,
        purchaseReturnId: null,
        vehicleNo: 'KA01AB1234',
        lrNo: null,
        remarks: null,
        grandTotal: 500,
        items: [],
      },
    ]);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('PRC-001');
    expect(text).toContain('KA01AB1234');
  });

  it('calls dispatch endpoint when dispatch() is invoked', () => {
    fixture.detectChanges();
    http.expectOne('/api/procurement/purchase-return-challans').flush([
      {
        id: 2,
        docNo: 'PRC-002',
        docDate: '2026-10-29',
        status: 'DRAFT',
        supplierId: 10,
        purchaseReturnId: null,
        vehicleNo: null,
        lrNo: null,
        remarks: null,
        grandTotal: 0,
        items: [],
      },
    ]);
    fixture.detectChanges();

    fixture.componentInstance.dispatch(2);

    http.expectOne('/api/procurement/purchase-return-challans/2/dispatch').flush({
      id: 2,
      docNo: 'PRC-002',
      docDate: '2026-10-29',
      status: 'DISPATCHED',
      supplierId: 10,
      purchaseReturnId: null,
      vehicleNo: null,
      lrNo: null,
      remarks: null,
      grandTotal: 0,
      items: [],
    });
  });
});
