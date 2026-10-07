/* eslint-disable @typescript-eslint/no-explicit-any */
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { OpeningBalanceDialogComponent } from './opening-balance-dialog.component';

function setup() {
  TestBed.configureTestingModule({
    imports: [OpeningBalanceDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: { close: () => undefined } },
    ],
  });
  const fixture = TestBed.createComponent(OpeningBalanceDialogComponent);
  const backend = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  backend.expectOne('/api/lookups/materials').flush([{ id: 9, code: 'WIRE-2', name: 'Music wire 2 mm', uom: 'KG' }]);
  backend.expectOne('/api/lookups/warehouses').flush([{ id: 1, code: 'MAIN', name: 'Main store' }]);
  return { fixture, backend, instance: fixture.componentInstance as unknown as Record<string, any> };
}

describe('OpeningBalanceDialogComponent (UAT STK-04, D-28)', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('will not submit until the material, place and a quantity above zero are given', () => {
    const { instance, backend } = setup();
    instance['save']();
    backend.expectNone('/api/inventory/opening-balances');
    expect(instance['form'].controls.materialId.touched).toBeTrue();
    instance['form'].patchValue({ materialId: 9, locationId: 4, quantity: 0 });
    expect(instance['form'].controls.quantity.invalid).toBeTrue();
  });

  it('posts the OpeningBalanceLine the API expects and shows what was recorded', () => {
    const { instance, backend, fixture } = setup();
    instance['form'].controls.warehouseId.setValue(1);
    backend
      .expectOne((r) => r.url === '/api/lookups/locations' && r.params.get('warehouseId') === '1')
      .flush([{ id: 4, warehouseId: 1, code: 'RM-01', name: 'Raw material rack', locationType: 'STORAGE' }]);
    instance['form'].patchValue({
      materialId: 9,
      locationId: 4,
      quantity: 100,
      unitCost: 85,
      remarks: 'Opening stock',
    });
    instance['save']();

    const req = backend.expectOne('/api/inventory/opening-balances');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual([
      {
        itemType: 'MATERIAL',
        materialId: 9,
        batchId: null,
        warehouseId: 1,
        locationId: 4,
        stockStatus: 'AVAILABLE',
        quantity: 100,
        uom: 'KG',
        unitCost: 85,
        remarks: 'Opening stock',
      },
    ]);
    req.flush([{ transactionId: 70, transactionNumber: 'TXN/26/00070', balanceAfter: 100, unitCost: 85 }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="opening-result"]').textContent).toContain('TXN/26/00070');
  });

  it('names the failing field when the server refuses the line', () => {
    const { instance, backend } = setup();
    instance['form'].controls.warehouseId.setValue(1);
    backend.expectOne((r) => r.url === '/api/lookups/locations').flush([]);
    instance['form'].patchValue({ materialId: 9, locationId: 4, quantity: 5 });
    instance['save']();
    backend.expectOne('/api/inventory/opening-balances').flush(
      {
        code: 'VALIDATION_FAILED',
        status: 400,
        detail: 'Validation failed',
        fieldErrors: [{ field: 'unitCost', message: 'must be positive' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    expect(instance['errorMessage']()).toBe('Validation failed (unitCost: must be positive)');
    expect(instance['form'].controls.unitCost.hasError('server')).toBeTrue();
  });
});
