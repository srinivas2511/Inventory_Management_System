/* eslint-disable @typescript-eslint/no-explicit-any */
import { provideHttpClient } from '@angular/common/http';
import { Type } from '@angular/core';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { BomFormComponent } from '../engineering/bom-form.component';
import { ProductionOrderFormComponent } from './production-order-form.component';

function setup<T>(component: Type<T>) {
  const close = jasmine.createSpy('close');
  TestBed.configureTestingModule({
    imports: [component, NoopAnimationsModule],
    providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MatDialogRef, useValue: { close } }],
  });
  const fixture = TestBed.createComponent(component);
  const backend = TestBed.inject(HttpTestingController);
  return { fixture, backend, close, instance: fixture.componentInstance as unknown as Record<string, any> };
}

const products = [{ id: 3, code: 'SPR-01', name: 'Compression spring', uom: 'PCS' }];
const materials = [{ id: 9, code: 'WIRE-2', name: 'Music wire 2 mm', uom: 'KG' }];

describe('BomFormComponent', () => {
  it('takes the unit from the chosen material and posts the CreateBomRequest shape', () => {
    const { fixture, backend, close, instance } = setup(BomFormComponent);
    fixture.detectChanges();
    backend.expectOne('/api/lookups/products').flush(products);
    backend.expectOne('/api/lookups/materials').flush(materials);

    instance['form'].patchValue({
      productId: 3,
      baseQuantity: 1,
      changeReason: 'First release',
      items: [{ materialId: 9, requiredQty: 0.045, scrapPct: 3, instruction: 'Cut to length' }],
    });
    instance['materialChosen'](0, 9);
    expect(instance['lines'].at(0).value.uom).toBe('KG');
    instance['save']();

    const req = backend.expectOne('/api/boms');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      productId: 3,
      baseQuantity: 1,
      changeReason: 'First release',
      items: [
        {
          lineNo: 10,
          itemKind: 'MATERIAL',
          materialId: 9,
          operationId: null,
          requiredQty: 0.045,
          uom: 'KG',
          scrapPct: 3,
          instruction: 'Cut to length',
        },
      ],
    });
    req.flush({ id: 1 });
    expect(close).toHaveBeenCalledWith(true);
  });

  it('numbers extra lines in tens and refuses a scrap percentage above 100', () => {
    const { fixture, backend, instance } = setup(BomFormComponent);
    fixture.detectChanges();
    backend.expectOne('/api/lookups/products').flush(products);
    backend.expectOne('/api/lookups/materials').flush(materials);

    instance['addLine']();
    instance['form'].patchValue({
      productId: 3,
      items: [
        { materialId: 9, requiredQty: 1, uom: 'KG', scrapPct: 0 },
        { materialId: 9, requiredQty: 2, uom: 'KG', scrapPct: 101 },
      ],
    });
    instance['save']();
    backend.expectNone('/api/boms');

    instance['lines'].at(1).patchValue({ scrapPct: 5 });
    instance['save']();
    const req = backend.expectOne('/api/boms');
    expect(req.request.body.items.map((i: { lineNo: number }) => i.lineNo)).toEqual([10, 20]);
    req.flush({ id: 2 });
  });

  it('shows the server error and keeps the dialog open', () => {
    const { fixture, backend, close, instance } = setup(BomFormComponent);
    fixture.detectChanges();
    backend.expectOne('/api/lookups/products').flush(products);
    backend.expectOne('/api/lookups/materials').flush(materials);
    instance['form'].patchValue({
      productId: 3,
      items: [{ materialId: 9, requiredQty: 1, uom: 'KG', scrapPct: 0 }],
    });

    instance['save']();
    backend
      .expectOne('/api/boms')
      .flush(
        { status: 409, code: 'CONFLICT', detail: 'A draft BOM already exists' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    expect(close).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('A draft BOM already exists');
  });
});

describe('ProductionOrderFormComponent', () => {
  it('looks up the active BOM of the chosen product and posts the CreateProductionOrderRequest shape', () => {
    const { fixture, backend, close, instance } = setup(ProductionOrderFormComponent);
    fixture.detectChanges();
    backend.expectOne('/api/lookups/products').flush(products);

    instance['form'].patchValue({ productId: 3, plannedQty: 500, priority: 2, expectedCompletionDate: '2026-10-30' });
    instance['productChosen'](3);
    backend
      .expectOne('/api/lookups/products/3/active-bom')
      .flush({ id: 21, bomNumber: 'BOM-21', revision: 'B', baseQuantity: 1 });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('BOM-21 revision B');

    instance['form'].patchValue({ startDate: '2026-10-08' });
    instance['save']();

    const req = backend.expectOne('/api/production-orders');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      productId: 3,
      bomId: 21,
      plannedQty: 500,
      startDate: '2026-10-08',
      expectedCompletionDate: '2026-10-30',
      productionLine: null,
      primaryMachineId: null,
      supervisorId: null,
      salesOrderId: null,
      priority: 2,
      remarks: null,
    });
    req.flush({ id: 8 });
    expect(close).toHaveBeenCalledWith(true);
  });

  it('says so and blocks creation when the product has no active BOM', () => {
    const { fixture, backend, instance } = setup(ProductionOrderFormComponent);
    fixture.detectChanges();
    backend.expectOne('/api/lookups/products').flush(products);

    instance['form'].patchValue({ productId: 3, plannedQty: 10 });
    instance['productChosen'](3);
    backend.expectOne('/api/lookups/products/3/active-bom').flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('no active BOM');
    instance['save']();
    backend.expectNone('/api/production-orders');
    backend.verify();
  });
});
