import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { TransferOrderDto } from './wms.api';
import { WmsTasksComponent } from './wms-tasks.component';

const putAway: TransferOrderDto = {
  id: 5,
  toNumber: 'TO/26/00005',
  warehouseId: 1,
  movementType: 'PUTAWAY',
  sourceDocumentType: 'GOODS_RECEIPT',
  sourceDocumentId: 9,
  crossDockTargetType: null,
  crossDockTargetId: null,
  waveId: null,
  status: 'CREATED',
  assignedTo: null,
  completedAt: null,
  notes: null,
  createdAt: '2026-10-03T08:00:00Z',
  version: 0,
  lines: [],
  allowedActions: [],
};

describe('WmsTasksComponent', () => {
  let fixture: ComponentFixture<WmsTasksComponent>;
  let backend: HttpTestingController;

  function setup(movementType: string, title: string, permissions: string[]): void {
    TestBed.overrideProvider(ActivatedRoute, { useValue: { snapshot: { data: { movementType, title } } } });
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'op',
      fullName: 'Operator',
      email: 'op@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(WmsTasksComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WmsTasksComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('asks for the movement type of its route and lists the orders', () => {
    setup('PUTAWAY', 'Put-away Tasks', ['WMS_VIEW', 'PUTAWAY_MANAGE']);
    const req = backend.expectOne((r) => r.url === '/api/wms/transfer-orders');
    expect(req.request.params.get('movementType')).toBe('PUTAWAY');
    req.flush({ content: [putAway], totalElements: 1, totalPages: 1, size: 50, number: 0 });
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-test="title"]')?.textContent).toContain('Put-away Tasks');
    expect(el.querySelector('[data-test="orders"]')?.textContent).toContain('TO/26/00005');
    expect(el.querySelector('[data-test="orders"]')?.textContent).toContain('GOODS_RECEIPT #9');
    expect(el.querySelector('[data-test="receipt"]')).not.toBeNull();
  });

  it('offers planning only to users who can manage that movement', () => {
    setup('PICKING', 'Picking List', ['WMS_VIEW']);
    backend
      .expectOne((r) => r.url === '/api/wms/transfer-orders')
      .flush({ content: [], totalElements: 0, totalPages: 1, size: 50, number: 0 });
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-test="sales-order"]')).toBeNull();
    expect(el.textContent).toContain('No orders found');
  });

  it('plans put-away for a goods receipt and opens the new order', () => {
    setup('PUTAWAY', 'Put-away Tasks', ['WMS_VIEW', 'PUTAWAY_MANAGE']);
    backend
      .expectOne((r) => r.url === '/api/wms/transfer-orders')
      .flush({ content: [], totalElements: 0, totalPages: 1, size: 50, number: 0 });
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);

    fixture.componentInstance.receiptCtrl.setValue(9);
    fixture.componentInstance.planPutAway();

    const req = backend.expectOne('/api/wms/transfer-orders/putaway/from-receipt/9');
    expect(req.request.method).toBe('POST');
    req.flush(putAway);
    expect(navigate).toHaveBeenCalledWith(['/wms/orders', 5]);
  });

  it('plans picking for a sales order and reloads the list', () => {
    setup('PICKING', 'Picking List', ['WMS_VIEW', 'PICKING_MANAGE']);
    backend
      .expectOne((r) => r.url === '/api/wms/transfer-orders')
      .flush({ content: [], totalElements: 0, totalPages: 1, size: 50, number: 0 });

    fixture.componentInstance.orderCtrl.setValue(12);
    fixture.componentInstance.planPicking();

    const req = backend.expectOne('/api/wms/picking/sales-orders/12');
    expect(req.request.method).toBe('POST');
    req.flush([{ ...putAway, movementType: 'PICKING' }]);
    backend
      .expectOne((r) => r.url === '/api/wms/transfer-orders')
      .flush({ content: [], totalElements: 0, totalPages: 1, size: 50, number: 0 });
  });
});
