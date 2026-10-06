import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { GoodsReceiptDto, GoodsReceiptListComponent } from './goods-receipt-list.component';

const gr: GoodsReceiptDto = {
  id: 3,
  grNumber: 'GR/26/00003',
  purchaseOrderId: 12,
  poNumber: 'PO/26/00012',
  receiptDate: '2026-10-02',
  status: 'POSTED',
  receivedBy: 5,
};

describe('GoodsReceiptListComponent', () => {
  let fixture: ComponentFixture<GoodsReceiptListComponent>;
  let backend: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GoodsReceiptListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'store',
      fullName: 'Store',
      email: 's@example.com',
      roles: [],
      permissions: ['PURCHASE_VIEW'],
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(GoodsReceiptListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    const req = backend.expectOne((r) => r.url === '/api/procurement/goods-receipts');
    expect(req.request.params.has('po')).toBe(false);
    req.flush({ content: [gr], totalElements: 1, totalPages: 1, size: 20, number: 0 });
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('lists receipts with their PO number', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('GR/26/00003');
    expect(text).toContain('PO/26/00012');
  });

  it('filters by PO number through the list endpoint', () => {
    fixture.componentInstance.poCtrl.setValue(' PO/26/00012 ');
    fixture.componentInstance.load();
    const req = backend.expectOne((r) => r.url === '/api/procurement/goods-receipts');
    expect(req.request.params.get('po')).toBe('PO/26/00012');
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 });
  });
});
