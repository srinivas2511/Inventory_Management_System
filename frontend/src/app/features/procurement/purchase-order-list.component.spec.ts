import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { PurchaseOrderDto, PurchaseOrderListComponent } from './purchase-order-list.component';

function po(id: number, submittedBy: number | null): PurchaseOrderDto {
  return {
    id,
    poNumber: `PO/26/0000${id}`,
    supplierId: 1,
    supplierName: 'Test Supplier',
    poDate: '2026-10-08',
    expectedDeliveryDate: '2026-10-20',
    status: 'SUBMITTED',
    grandTotal: 1000,
    currency: 'INR',
    submittedBy,
    items: [],
    allowedActions: ['approve', 'cancel'],
  };
}

describe('PurchaseOrderListComponent approval (D-37)', () => {
  let fixture: ComponentFixture<PurchaseOrderListComponent>;
  let backend: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PurchaseOrderListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    TestBed.inject(SessionStore).setUser({
      id: 7,
      username: 'approver',
      fullName: 'Approver',
      email: 'a@example.com',
      roles: [],
      permissions: ['PURCHASE_VIEW', 'PURCHASE_APPROVE'],
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(PurchaseOrderListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend
      .expectOne((r) => r.url === '/api/procurement/purchase-orders')
      .flush({ content: [po(1, 7), po(2, 9), po(3, null)], totalElements: 3, totalPages: 1, size: 20, number: 0 });
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('disables Approve for the user who submitted the order and leaves it enabled for others', () => {
    const buttons: HTMLButtonElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('button[aria-label="Approve"]'),
    );
    expect(buttons.length).toBe(3);
    expect(buttons[0].disabled).toBe(true);
    expect(buttons[1].disabled).toBe(false);
    expect(buttons[2].disabled).toBe(false);
  });

  it('identifies an own submission only when both ids are known and equal', () => {
    const c = fixture.componentInstance;
    expect(c.ownSubmission(po(1, 7))).toBe(true);
    expect(c.ownSubmission(po(2, 9))).toBe(false);
    expect(c.ownSubmission(po(3, null))).toBe(false);
  });
});
