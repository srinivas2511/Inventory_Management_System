import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { SparePartDto } from './maintenance.api';
import { SparePartsComponent } from './spare-parts.component';

const bearing: SparePartDto = {
  id: 1,
  sparePartCode: 'SP-BRG-01',
  name: 'Bearing 6204',
  description: null,
  uom: 'PCS',
  materialId: 40,
  compatibleMachineTypes: 'COILER',
  minStock: 8,
  reorderLevel: 12,
  standardCost: 40,
  active: true,
  onHandQty: 7,
  belowMinStock: true,
  version: 3,
};

const belt: SparePartDto = {
  ...bearing,
  id: 2,
  sparePartCode: 'SP-BLT-02',
  name: 'Drive belt',
  onHandQty: 30,
  belowMinStock: false,
};

describe('SparePartsComponent', () => {
  let fixture: ComponentFixture<SparePartsComponent>;
  let backend: HttpTestingController;
  let component: SparePartsComponent;

  function flushList(parts: SparePartDto[] = [bearing, belt]): void {
    backend
      .expectOne((r) => r.url === '/api/spare-parts')
      .flush({ content: parts, totalElements: parts.length, totalPages: 1, size: 100, number: 0 });
    fixture.detectChanges();
  }

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'store',
      fullName: 'Store Manager',
      email: 's@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(SparePartsComponent);
    component = fixture.componentInstance;
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    flushList();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SparePartsComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists parts with stock and flags the ones below their minimum', () => {
    setup(['SPARE_PART_VIEW']);
    const el: HTMLElement = fixture.nativeElement;
    const rows = el.querySelectorAll('[data-test="parts"] tr.mat-mdc-row');

    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('SP-BRG-01');
    expect(rows[0].textContent).toContain('7 PCS');
    expect(rows[0].querySelector('[data-test="low-stock"]')).not.toBeNull();
    expect(rows[1].querySelector('[data-test="low-stock"]')).toBeNull();
  });

  it('asks for active parts only by default and searches by text', () => {
    setup(['SPARE_PART_VIEW']);
    component.searchCtrl.setValue('bear');

    component.load();

    const req = backend.expectOne((r) => r.url === '/api/spare-parts');
    expect(req.request.params.get('q')).toBe('bear');
    expect(req.request.params.get('active')).toBe('true');
    req.flush({ content: [bearing], totalElements: 1, totalPages: 1, size: 100, number: 0 });
    fixture.detectChanges();

    component.activeOnly.setValue(false);
    component.load();
    const all = backend.expectOne((r) => r.url === '/api/spare-parts');
    expect(all.request.params.has('active')).toBe(false);
    all.flush({ content: [], totalElements: 0, totalPages: 0, size: 100, number: 0 });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No spare parts');
  });

  it('hides the form and the row actions from people who can only view', () => {
    setup(['SPARE_PART_VIEW']);
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('[data-test="part-form"]')).toBeNull();
    expect(el.querySelector('[data-test="edit"]')).toBeNull();
    expect(el.querySelector('[data-test="deactivate"]')).toBeNull();
  });

  it('creates a part', () => {
    setup(['SPARE_PART_VIEW', 'SPARE_PART_MANAGE']);
    component.code.setValue(' SP-NEW ');
    component.name.setValue('Coolant pump');
    component.uom.setValue('PCS');
    component.minStock.setValue(2);
    component.reorderLevel.setValue(5);
    component.standardCost.setValue(120.5);
    component.machineTypes.setValue('COILER');
    expect(component.valid()).toBe(true);

    component.save();

    const post = backend.expectOne('/api/spare-parts');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({
      sparePartCode: 'SP-NEW',
      name: 'Coolant pump',
      description: null,
      uom: 'PCS',
      compatibleMachineTypes: 'COILER',
      minStock: 2,
      reorderLevel: 5,
      standardCost: 120.5,
      version: null,
    });
    post.flush({ ...bearing, id: 9, sparePartCode: 'SP-NEW' });
    flushList();
    expect(component.code.value).toBe('');
  });

  it('will not save when the minimum exceeds the reorder level or the name is missing', () => {
    setup(['SPARE_PART_VIEW', 'SPARE_PART_MANAGE']);
    component.code.setValue('SP-X');
    component.name.setValue('Part');
    component.minStock.setValue(9);
    component.reorderLevel.setValue(4);
    fixture.detectChanges();

    expect(component.levelsError()).toBe(true);
    expect(component.valid()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-test="levels-error"]')).not.toBeNull();
    component.save();
    backend.expectNone('/api/spare-parts');

    component.minStock.setValue(2);
    component.name.setValue('');
    expect(component.valid()).toBe(false);
  });

  it('edits a part under its own code and sends the version it read', () => {
    setup(['SPARE_PART_VIEW', 'SPARE_PART_MANAGE']);

    component.edit(bearing);
    expect(component.code.disabled).toBe(true);
    expect(component.valid()).toBe(true);
    component.name.setValue('Bearing 6205');
    component.minStock.setValue(10);
    component.save();

    const put = backend.expectOne('/api/spare-parts/1');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body.sparePartCode).toBe('SP-BRG-01');
    expect(put.request.body.name).toBe('Bearing 6205');
    expect(put.request.body.minStock).toBe(10);
    expect(put.request.body.version).toBe(3);
    put.flush({ ...bearing, name: 'Bearing 6205' });
    flushList();
    expect(component.editing()).toBeNull();
    expect(component.code.enabled).toBe(true);
  });

  it('deactivates a part', () => {
    setup(['SPARE_PART_VIEW', 'SPARE_PART_MANAGE']);

    component.deactivate(bearing);

    const del = backend.expectOne('/api/spare-parts/1');
    expect(del.request.method).toBe('DELETE');
    del.flush({ ...bearing, active: false });
    flushList([belt]);
  });

  it('keeps the form as it is when the server refuses the part', () => {
    setup(['SPARE_PART_VIEW', 'SPARE_PART_MANAGE']);
    component.code.setValue('SP-BRG-01');
    component.name.setValue('Duplicate');

    component.save();

    backend
      .expectOne('/api/spare-parts')
      .flush({ detail: 'Spare part code already exists: SP-BRG-01' }, { status: 409, statusText: 'Conflict' });
    expect(component.code.value).toBe('SP-BRG-01');
  });
});
