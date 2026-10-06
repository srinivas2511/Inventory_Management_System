/* eslint-disable @typescript-eslint/no-explicit-any */
import { provideHttpClient } from '@angular/common/http';
import { Type } from '@angular/core';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { GoodsReceiptFormComponent } from './goods-receipt-form.component';
import { PurchaseOrderFormComponent } from './purchase-order-form.component';
import { PurchaseRequisitionFormComponent } from './purchase-requisition-form.component';

const problem = {
  status: 422,
  code: 'VALIDATION_FAILED',
  detail: 'Validation failed',
  fieldErrors: [{ field: 'items[0].rate', message: 'must be greater than 0' }],
};

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

describe('PurchaseOrderFormComponent', () => {
  it('loads suppliers and materials, refuses an empty form and posts nothing', () => {
    const { fixture, backend, instance } = setup(PurchaseOrderFormComponent);
    fixture.detectChanges();
    backend.expectOne('/api/lookups/suppliers').flush([{ id: 4, code: 'S4', name: 'Wire Co' }]);
    backend.expectOne('/api/lookups/materials').flush([{ id: 9, code: 'M9', name: 'Music wire', uom: 'KG' }]);

    instance['save']();

    backend.expectNone('/api/procurement/purchase-orders');
    expect(instance['form'].touched).toBeTrue();
    backend.verify();
  });

  it('posts the CreatePoRequest shape and closes the dialog on success', () => {
    const { fixture, backend, close, instance } = setup(PurchaseOrderFormComponent);
    fixture.detectChanges();
    backend.expectOne('/api/lookups/suppliers').flush([{ id: 4, code: 'S4', name: 'Wire Co' }]);
    backend.expectOne('/api/lookups/materials').flush([{ id: 9, code: 'M9', name: 'Music wire', uom: 'KG' }]);

    instance['form'].patchValue({
      supplierId: 4,
      poDate: '2026-10-07',
      expectedDeliveryDate: '2026-10-20',
      paymentTerms: 'NET30',
      items: [{ materialId: 9, orderedQty: 25, rate: 85.5, taxPct: 18 }],
    });
    instance['save']();

    const req = backend.expectOne('/api/procurement/purchase-orders');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      supplierId: 4,
      poDate: '2026-10-07',
      expectedDeliveryDate: '2026-10-20',
      paymentTerms: 'NET30',
      remarks: null,
      requisitionId: null,
      items: [{ materialId: 9, orderedQty: 25, rate: 85.5, taxPct: 18 }],
    });
    req.flush({ id: 1 });
    expect(close).toHaveBeenCalledWith(true);
  });

  it('shows the server problem detail and field errors and stays open', () => {
    const { fixture, backend, close, instance } = setup(PurchaseOrderFormComponent);
    fixture.detectChanges();
    backend.expectOne('/api/lookups/suppliers').flush([]);
    backend.expectOne('/api/lookups/materials').flush([]);
    instance['form'].patchValue({
      supplierId: 4,
      items: [{ materialId: 9, orderedQty: 1, rate: 1, taxPct: null }],
    });

    instance['save']();
    backend.expectOne('/api/procurement/purchase-orders').flush(problem, { status: 422, statusText: 'Unprocessable' });
    fixture.detectChanges();

    expect(close).not.toHaveBeenCalled();
    expect(instance['saving']()).toBeFalse();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('items[0].rate: must be greater than 0');
  });

  it('adds a line, keeps one line minimum and totals the order', () => {
    const { fixture, backend, instance } = setup(PurchaseOrderFormComponent);
    fixture.detectChanges();
    backend.expectOne('/api/lookups/suppliers').flush([]);
    backend.expectOne('/api/lookups/materials').flush([]);

    instance['removeLine'](0);
    expect(instance['items'].length).toBe(1);
    instance['addLine']();
    instance['items'].at(0).patchValue({ orderedQty: 10, rate: 2.5 });
    instance['items'].at(1).patchValue({ orderedQty: 4, rate: 5 });

    expect(instance['subtotal']()).toBe('45.00');
  });
});

describe('PurchaseRequisitionFormComponent', () => {
  it('fills the unit from the chosen material and posts the CreateRequisitionRequest shape', () => {
    const { fixture, backend, close, instance } = setup(PurchaseRequisitionFormComponent);
    fixture.detectChanges();
    backend.expectOne('/api/lookups/materials').flush([{ id: 9, code: 'M9', name: 'Music wire', uom: 'KG' }]);

    instance['form'].patchValue({ materialId: 9, requestedQty: 120 });
    instance['materialChosen'](9);
    expect(instance['form'].controls.uom.value).toBe('KG');
    instance['save']();

    const req = backend.expectOne('/api/procurement/requisitions');
    expect(req.request.body).toEqual({ materialId: 9, requestedQty: 120, uom: 'KG', neededBy: null, reason: null });
    req.flush({ id: 2 });
    expect(close).toHaveBeenCalledWith(true);
  });

  it('does not post a requisition without a material or quantity', () => {
    const { fixture, backend, instance } = setup(PurchaseRequisitionFormComponent);
    fixture.detectChanges();
    backend.expectOne('/api/lookups/materials').flush([]);

    instance['save']();

    backend.expectNone('/api/procurement/requisitions');
    backend.verify();
  });
});

describe('GoodsReceiptFormComponent', () => {
  const receivable = [
    {
      id: 12,
      poNumber: 'PO/26/00012',
      supplierId: 4,
      supplierName: 'Wire Co',
      status: 'SENT_TO_SUPPLIER',
      items: [
        {
          poItemId: 31,
          lineNo: 1,
          materialId: 9,
          materialCode: 'M9',
          materialName: 'Music wire',
          uom: 'KG',
          orderedQty: 100,
          receivedQty: 40,
          remainingQty: 60,
        },
      ],
    },
  ];
  const locations = [
    { id: 7, warehouseId: 1, code: 'RM-01', name: 'Raw material', locationType: 'RAW_MATERIAL' },
    { id: 8, warehouseId: 1, code: 'QRN-01', name: 'Quarantine', locationType: 'QUARANTINE' },
  ];

  function ready() {
    const ctx = setup(GoodsReceiptFormComponent);
    ctx.fixture.detectChanges();
    ctx.backend.expectOne('/api/lookups/purchase-orders/receivable').flush(receivable);
    ctx.backend.expectOne('/api/lookups/warehouses').flush([{ id: 1, code: 'MAIN-WH', name: 'Main' }]);
    ctx.instance['form'].patchValue({ purchaseOrderId: 12, warehouseId: 1 });
    ctx.instance['poChosen'](12);
    ctx.instance['warehouseChosen'](1);
    ctx.backend
      .expectOne((r) => r.url === '/api/lookups/locations' && r.params.get('warehouseId') === '1')
      .flush(locations);
    return ctx;
  }

  it('starts each line at the open quantity and prefers the quarantine location', () => {
    const { instance, backend } = ready();

    expect(instance['lines'].length).toBe(1);
    expect(instance['lines'].at(0).value.receivedQty).toBe(60);
    expect(instance['lines'].at(0).value.locationId).toBe(8);
    backend.verify();
  });

  it('posts the CreateGrRequest shape for the lines that have a quantity', () => {
    const { instance, backend, close } = ready();
    instance['form'].patchValue({ receiptDate: '2026-10-07', supplierChallanNo: 'CH-9', vehicleNo: 'MH12AB1234' });
    instance['lines'].at(0).patchValue({ receivedQty: 55, heatNumber: 'H-77', inspectionRequired: true });

    instance['save']();

    const req = backend.expectOne('/api/procurement/goods-receipts');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      purchaseOrderId: 12,
      receiptDate: '2026-10-07',
      warehouseId: 1,
      supplierChallanNo: 'CH-9',
      vehicleNo: 'MH12AB1234',
      items: [
        {
          poItemId: 31,
          receivedQty: 55,
          locationId: 8,
          heatNumber: 'H-77',
          certificateNumber: null,
          manufacturingDate: null,
          inspectionRequired: true,
        },
      ],
    });
    req.flush({ id: 5 });
    expect(close).toHaveBeenCalledWith(true);
  });

  it('refuses a quantity above the open quantity and a receipt with no quantity at all', () => {
    const { instance, backend, fixture } = ready();

    instance['lines'].at(0).patchValue({ receivedQty: 61 });
    instance['save']();
    expect(instance['errorMessage']()).toContain('within the open quantity');

    instance['lines'].at(0).patchValue({ receivedQty: null });
    instance['save']();
    fixture.detectChanges();
    expect(instance['errorMessage']()).toContain('at least one line');
    backend.expectNone('/api/procurement/goods-receipts');
    backend.verify();
  });
});
