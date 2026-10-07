import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { PickListLineDto, WaveDto } from './wms.api';
import { WmsWavesComponent } from './wms-waves.component';

const wave: WaveDto = {
  id: 3,
  waveNumber: 'WV/26/00003',
  warehouseId: 1,
  status: 'CREATED',
  releasedAt: null,
  notes: 'morning',
  salesOrderIds: [10, 11],
  transferOrderIds: [20, 21],
  version: 0,
};

const pickList: PickListLineDto[] = [
  {
    zoneId: 1,
    zoneCode: 'PA',
    binId: 5,
    binCode: 'PA-01',
    locationId: 50,
    itemType: 'PRODUCT',
    itemId: 1,
    itemCode: 'SPR-1',
    itemName: 'Spring one',
    batchId: 8,
    batchNumber: 'FG-8',
    totalQty: 25,
    orders: [
      { salesOrderId: 10, orderNumber: 'SO/26/00010', transferOrderId: 20, quantity: 10 },
      { salesOrderId: 11, orderNumber: 'SO/26/00011', transferOrderId: 21, quantity: 15 },
    ],
  },
  {
    zoneId: 2,
    zoneCode: 'PB',
    binId: 6,
    binCode: 'PB-01',
    locationId: 51,
    itemType: 'PRODUCT',
    itemId: 2,
    itemCode: 'SPR-2',
    itemName: 'Spring two',
    batchId: 9,
    batchNumber: 'FG-9',
    totalQty: 7,
    orders: [{ salesOrderId: 12, orderNumber: 'SO/26/00012', transferOrderId: 22, quantity: 7 }],
  },
];

describe('WmsWavesComponent', () => {
  let fixture: ComponentFixture<WmsWavesComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'wm',
      fullName: 'Warehouse Manager',
      email: 'wm@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(WmsWavesComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend
      .expectOne((r) => r.url === '/api/wms/waves')
      .flush({ content: [wave], totalElements: 1, totalPages: 1, size: 50, number: 0 });
    if (permissions.includes('WAVE_MANAGE')) {
      // D-33: the planner picks from a lookup that needs only WAVE_MANAGE, never the sales list
      backend
        .expectOne('/api/lookups/sales-orders/approved')
        .flush([{ id: 10, orderNumber: 'SO/26/00010', customerName: 'Demo Customer' }]);
    } else {
      // a user who cannot create waves makes no request for orders at all (store roles may not read sales)
      backend.expectNone('/api/lookups/sales-orders/approved');
      backend.expectNone((r) => r.url === '/api/sales/orders');
    }
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WmsWavesComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists waves with their order counts', () => {
    setup(['WMS_VIEW']);
    const el: HTMLElement = fixture.nativeElement;
    const text = el.querySelector('[data-test="waves"]')?.textContent ?? '';
    expect(text).toContain('WV/26/00003');
    expect(text).toContain('morning');
    expect(el.querySelector('[data-test="create-wave"]')).toBeNull();
    expect(el.querySelector('[data-test="release"]')).toBeNull();
  });

  it('shows the consolidated pick list in zone order and can filter to one zone', () => {
    setup(['WMS_VIEW', 'WAVE_MANAGE']);
    const component = fixture.componentInstance;
    component.showPickList(wave);
    backend.expectOne('/api/wms/waves/3/pick-list').flush(pickList);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const rows = el.querySelectorAll('[data-test="pick-list"] tr.mat-mdc-row');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('PA-01');
    expect(rows[0].textContent).toContain('SO/26/00010: 10');
    expect(rows[0].textContent).toContain('SO/26/00011: 15');
    expect(rows[0].textContent).toContain('25');

    component.zoneCtrl.setValue(2);
    component.showPickList(wave);
    backend.expectOne('/api/wms/waves/3/pick-list').flush(pickList);
    fixture.detectChanges();
    const filtered = el.querySelectorAll('[data-test="pick-list"] tr.mat-mdc-row');
    expect(filtered.length).toBe(1);
    expect(filtered[0].textContent).toContain('PB-01');
  });

  it('creates a wave from the chosen sales orders', () => {
    setup(['WMS_VIEW', 'WAVE_MANAGE']);
    const component = fixture.componentInstance;
    component.ordersCtrl.setValue([10, 11]);
    component.notesCtrl.setValue('afternoon');

    component.create();

    const req = backend.expectOne('/api/wms/waves');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ salesOrderIds: [10, 11], warehouseId: null, notes: 'afternoon' });
    req.flush(wave);
    backend
      .expectOne((r) => r.url === '/api/wms/waves' && r.method === 'GET')
      .flush({ content: [wave], totalElements: 1, totalPages: 1, size: 50, number: 0 });
    backend.expectOne('/api/wms/waves/3/pick-list').flush(pickList);
  });

  it('releases a created wave', () => {
    setup(['WMS_VIEW', 'WAVE_MANAGE']);
    fixture.componentInstance.release(wave);
    const req = backend.expectOne('/api/wms/waves/3/release');
    expect(req.request.method).toBe('POST');
    req.flush({ ...wave, status: 'RELEASED' });
    backend
      .expectOne((r) => r.url === '/api/wms/waves' && r.method === 'GET')
      .flush({ content: [], totalElements: 0, totalPages: 1, size: 50, number: 0 });
  });
});
