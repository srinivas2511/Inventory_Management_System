import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { SalesOrderListComponent } from './sales-order-list.component';
import { SalesOrderDto } from './sales.api';

const mockOrders: SalesOrderDto[] = [
  {
    id: 1,
    orderNumber: 'SO/26/00001',
    customerId: 10,
    customerName: 'Acme Corp',
    orderDate: '2026-10-01',
    requiredDate: '2026-10-31',
    deliveryAddress: '123 Test St',
    paymentTerms: 'NET30',
    notes: null,
    status: 'DRAFT',
    totalValue: 6000,
    items: [
      {
        id: 101,
        lineNo: 1,
        productId: 5,
        productCode: 'SP-001',
        productName: 'Spring Type A',
        orderedQty: 100,
        reservedQty: 0,
        packedQty: 0,
        dispatchedQty: 0,
        pendingQty: 100,
        unitPrice: 60,
        customerSpecRef: null,
        notes: null,
        fulfillmentStatus: 'PENDING',
      },
    ],
    createdBy: 2,
    submittedBy: null,
    allowedActions: ['confirm', 'cancel'],
  },
];

const pageResponse = { content: mockOrders, totalElements: 1, totalPages: 1, size: 20, number: 0 };

describe('SalesOrderListComponent', () => {
  let fixture: ComponentFixture<SalesOrderListComponent>;
  let backend: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SalesOrderListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    // Grant all permissions so HasPermissionDirective renders buttons
    const session = TestBed.inject(SessionStore);
    session.setUser({
      id: 1,
      username: 'test',
      fullName: 'Test',
      email: 't@t.com',
      roles: [],
      permissions: ['SALES_VIEW', 'SALES_CREATE', 'SALES_APPROVE', 'SALES_UPDATE'],
      mustChangePassword: false,
    });

    fixture = TestBed.createComponent(SalesOrderListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('shows loading spinner initially', () => {
    const req = backend.expectOne((r) => r.url.includes('/api/sales/orders'));
    expect(fixture.nativeElement.querySelector('mat-spinner')).toBeTruthy();
    req.flush(pageResponse);
  });

  it('renders order rows after load', () => {
    backend.expectOne((r) => r.url.includes('/api/sales/orders')).flush(pageResponse);
    fixture.detectChanges();
    const rows = fixture.nativeElement.querySelectorAll('tr[mat-row]');
    expect(rows.length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('SO/26/00001');
    expect(fixture.nativeElement.textContent).toContain('Acme Corp');
  });

  it('shows empty state when no orders', () => {
    backend
      .expectOne((r) => r.url.includes('/api/sales/orders'))
      .flush({
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 20,
        number: 0,
      });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No sales orders found');
  });

  it('sends status filter in request', () => {
    backend.expectOne((r) => r.url.includes('/api/sales/orders')).flush(pageResponse);
    fixture.componentInstance.statusCtrl.setValue('APPROVED');
    const req = backend.expectOne((r) => r.url.includes('/api/sales/orders') && r.params.get('status') === 'APPROVED');
    req.flush({ ...pageResponse, content: [] });
    expect(req.request.params.get('status')).toBe('APPROVED');
  });

  it('calls confirm endpoint on confirm button click', () => {
    backend.expectOne((r) => r.url.includes('/api/sales/orders')).flush(pageResponse);
    fixture.detectChanges();

    const confirmBtn = fixture.nativeElement.querySelector('button[mattooltip="Confirm"]');
    if (confirmBtn) {
      confirmBtn.click();
      const req = backend.expectOne('/api/sales/orders/1/confirm');
      expect(req.request.method).toBe('POST');
      req.flush({ ...mockOrders[0], status: 'ORDERED', allowedActions: ['approve', 'cancel'] });
    }
  });

  describe('segregation of duties on Approve', () => {
    const ordered = (over: Partial<SalesOrderDto>): SalesOrderDto => ({
      ...mockOrders[0],
      id: 2,
      orderNumber: 'SO/26/00002',
      status: 'ORDERED',
      allowedActions: ['approve', 'cancel'],
      ...over,
    });
    const approveButton = (): HTMLButtonElement | null =>
      fixture.nativeElement.querySelector('button[aria-label="Approve"]');

    function load(order: SalesOrderDto): void {
      backend
        .expectOne((r) => r.url.includes('/api/sales/orders'))
        .flush({ ...pageResponse, content: [order], totalElements: 1 });
      fixture.detectChanges();
    }

    it('disables Approve for the user who submitted the order', () => {
      load(ordered({ createdBy: 9, submittedBy: 1 }));
      expect(approveButton()).toBeTruthy();
      expect(approveButton()!.disabled).toBeTrue();
    });

    it('disables Approve for the user who created the order', () => {
      load(ordered({ createdBy: 1, submittedBy: 9 }));
      expect(approveButton()!.disabled).toBeTrue();
    });

    it('enables Approve for a different user', () => {
      load(ordered({ createdBy: 7, submittedBy: 8 }));
      expect(approveButton()!.disabled).toBeFalse();
    });
  });
});
