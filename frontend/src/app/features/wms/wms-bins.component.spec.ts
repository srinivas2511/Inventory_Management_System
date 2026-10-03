import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { SessionStore } from '../../core/auth/session.store';
import { WarehouseApi } from '../warehouse/warehouse.api';
import { BinDto, Page, PutAwayRuleDto, ZoneDto } from './wms.api';
import { WmsBinsComponent } from './wms-bins.component';

const zone: ZoneDto = {
  id: 4,
  warehouseId: 1,
  zoneCode: 'BULK1',
  name: 'Bulk store',
  zoneType: 'BULK',
  active: true,
  binCount: 1,
  version: 0,
};

const bin: BinDto = {
  id: 9,
  zoneId: 4,
  zoneCode: 'BULK1',
  zoneType: 'BULK',
  warehouseId: 1,
  locationId: 70,
  binCode: 'A-01-02',
  aisle: 'A',
  rack: '01',
  level: '02',
  binPosition: null,
  binType: 'STANDARD',
  maxWeightKg: 500,
  maxVolumeM3: null,
  currentWeightKg: 120.5,
  allowedMaterialTypes: 'SPRING_STEEL',
  active: true,
  occupied: true,
  version: 3,
};

const rule: PutAwayRuleDto = { id: 2, warehouseId: 1, materialType: null, strategy: 'FIXED_BIN' };

describe('WmsBinsComponent', () => {
  let fixture: ComponentFixture<WmsBinsComponent>;
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
    fixture = TestBed.createComponent(WmsBinsComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/wms/zones').flush([zone]);
    const page: Page<BinDto> = { content: [bin], totalElements: 1, totalPages: 1, size: 200, number: 0 };
    backend.expectOne((r) => r.url === '/api/wms/bins').flush(page);
    backend.expectOne((r) => r.url === '/api/wms/putaway-rules').flush([rule]);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WmsBinsComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: WarehouseApi,
          useValue: {
            list: () => of([{ id: 1, warehouseCode: 'WH1', name: 'Main', active: true, version: 0 }]),
          },
        },
      ],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists zones, bins with weight against capacity, and the put-away rules', () => {
    setup(['WMS_VIEW']);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-test="zones"]')?.textContent).toContain('Bulk store');
    const bins = (el.querySelector('[data-test="bins"]')?.textContent ?? '').replace(/\s+/g, ' ');
    expect(bins).toContain('A-01-02');
    expect(bins).toContain('120.5 / 500');
    expect(bins).toContain('Occupied');
    expect(el.querySelector('[data-test="rules"]')?.textContent).toContain('Any (default)');
    expect(el.querySelector('[data-test="rules"]')?.textContent).toContain('FIXED_BIN');
  });

  it('hides the maintenance forms and buttons from a viewer', () => {
    setup(['WMS_VIEW']);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('form')).toBeNull();
    expect(el.textContent).not.toContain('Deactivate');
  });

  it('editing a bin sends its version and keeps the code and zone fixed', () => {
    setup(['WMS_VIEW', 'BIN_MANAGE']);
    const component = fixture.componentInstance;
    component.edit(bin);
    component.binForm.controls.maxWeightKg.setValue(650);

    component.saveBin();

    const req = backend.expectOne('/api/wms/bins/9');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.version).toBe(3);
    expect(req.request.body.zoneId).toBe(4);
    expect(req.request.body.binCode).toBe('A-01-02');
    expect(req.request.body.maxWeightKg).toBe(650);
    expect(req.request.body.allowedMaterialTypes).toBe('SPRING_STEEL');
    req.flush({ ...bin, maxWeightKg: 650, version: 4 });
    backend.expectOne((r) => r.url === '/api/wms/zones').flush([zone]);
    backend
      .expectOne((r) => r.url === '/api/wms/bins')
      .flush({ content: [], totalElements: 0, totalPages: 1, size: 200, number: 0 });
    backend.expectOne((r) => r.url === '/api/wms/putaway-rules').flush([rule]);
  });

  it('saves a put-away rule for the chosen warehouse and strategy', () => {
    setup(['WMS_VIEW', 'BIN_MANAGE']);
    const component = fixture.componentInstance;
    component.ruleForm.setValue({ materialType: 'SPRING_STEEL', strategy: 'BULK_FIRST' });

    component.saveRule();

    const req = backend.expectOne('/api/wms/putaway-rules');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ warehouseId: 1, materialType: 'SPRING_STEEL', strategy: 'BULK_FIRST' });
    req.flush({ id: 3, warehouseId: 1, materialType: 'SPRING_STEEL', strategy: 'BULK_FIRST' });
    backend.expectOne((r) => r.url === '/api/wms/zones').flush([zone]);
    backend
      .expectOne((r) => r.url === '/api/wms/bins')
      .flush({ content: [], totalElements: 0, totalPages: 1, size: 200, number: 0 });
    backend.expectOne((r) => r.url === '/api/wms/putaway-rules').flush([rule]);
  });
});
