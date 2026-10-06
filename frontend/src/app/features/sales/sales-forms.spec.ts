/* eslint-disable @typescript-eslint/no-explicit-any */
import { provideHttpClient } from '@angular/common/http';
import { Type } from '@angular/core';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { DispatchFormComponent } from './dispatch-form.component';
import { ReservationFormComponent } from './reservation-form.component';
import { SalesOrderFormComponent } from './sales-order-form.component';
import { SalesOrderDto } from './sales.api';

function setup<T>(component: Type<T>, data?: unknown) {
  const close = jasmine.createSpy('close');
  TestBed.configureTestingModule({
    imports: [component, NoopAnimationsModule],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: { close } },
      { provide: MAT_DIALOG_DATA, useValue: data },
    ],
  });
  const fixture = TestBed.createComponent(component);
  const backend = TestBed.inject(HttpTestingController);
  return { fixture, backend, close, instance: fixture.componentInstance as unknown as Record<string, any> };
}

const stock = [
  {
    fgBatchId: 55,
    batchNumber: 'FG/26/0007',
    productId: 3,
    warehouseId: 1,
    locationId: 12,
    locationCode: 'FG-01',
    onHand: 100,
    reserved: 20,
    available: 80,
  },
];

describe('SalesOrderFormComponent', () => {
  it('posts the CreateSalesOrderRequest shape and totals the order value', () => {
    const { fixture, backend, close, instance } = setup(SalesOrderFormComponent);
    fixture.detectChanges();
    backend.expectOne('/api/lookups/customers').flush([{ id: 6, code: 'C6', name: 'Auto Parts Ltd' }]);
    backend.expectOne('/api/lookups/products').flush([{ id: 3, code: 'SPR-01', name: 'Spring', uom: 'PCS' }]);

    instance['form'].patchValue({
      customerId: 6,
      orderDate: '2026-10-07',
      requiredDate: '2026-10-21',
      deliveryAddress: 'Plot 9, Pune',
      paymentTerms: 'NET30',
      items: [{ productId: 3, orderedQty: 80, unitPrice: 55, customerSpecRef: 'DWG-5' }],
    });
    expect(instance['total']()).toBe('4400.00');
    instance['save']();

    const req = backend.expectOne('/api/sales/orders');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      customerId: 6,
      orderDate: '2026-10-07',
      requiredDate: '2026-10-21',
      deliveryAddress: 'Plot 9, Pune',
      paymentTerms: 'NET30',
      notes: null,
      items: [{ productId: 3, orderedQty: 80, unitPrice: 55, customerSpecRef: 'DWG-5', notes: null }],
    });
    req.flush({ id: 90 });
    expect(close).toHaveBeenCalledWith(true);
  });

  it('refuses an order without a customer or with a zero quantity', () => {
    const { fixture, backend, instance } = setup(SalesOrderFormComponent);
    fixture.detectChanges();
    backend.expectOne('/api/lookups/customers').flush([]);
    backend.expectOne('/api/lookups/products').flush([]);

    instance['form'].patchValue({ items: [{ productId: 3, orderedQty: 0 }] });
    instance['save']();

    backend.expectNone('/api/sales/orders');
    expect(instance['form'].controls.customerId.hasError('required')).toBeTrue();
    backend.verify();
  });

  it('shows the server problem and stays open', () => {
    const { fixture, backend, close, instance } = setup(SalesOrderFormComponent);
    fixture.detectChanges();
    backend.expectOne('/api/lookups/customers').flush([]);
    backend.expectOne('/api/lookups/products').flush([]);
    instance['form'].patchValue({ customerId: 6, items: [{ productId: 3, orderedQty: 1 }] });

    instance['save']();
    backend
      .expectOne('/api/sales/orders')
      .flush(
        { status: 422, code: 'VALIDATION_FAILED', detail: 'Customer is inactive' },
        { status: 422, statusText: 'x' },
      );
    fixture.detectChanges();

    expect(close).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Customer is inactive');
  });
});

describe('ReservationFormComponent', () => {
  const order = {
    id: 90,
    orderNumber: 'SO/26/00090',
    items: [
      {
        id: 301,
        productId: 3,
        productCode: 'SPR-01',
        productName: 'Spring',
        orderedQty: 80,
        reservedQty: 20,
        dispatchedQty: 0,
      },
    ],
  } as unknown as SalesOrderDto;

  it('offers the released stock for the first unreserved item and posts the ReserveRequest shape', () => {
    const { fixture, backend, close, instance } = setup(ReservationFormComponent, order);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/lookups/fg-stock' && r.params.get('productId') === '3').flush(stock);
    expect(instance['form'].controls.qty.value).toBe(60);

    instance['form'].patchValue({ stockKey: '55:1:12', qty: 60 });
    instance['save']();

    const req = backend.expectOne('/api/sales/reservations');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ salesOrderItemId: 301, fgBatchId: 55, warehouseId: 1, locationId: 12, qty: 60 });
    req.flush({});
    expect(close).toHaveBeenCalledWith(true);
  });

  it('shows the server error when the stock cannot be reserved', () => {
    const { fixture, backend, close, instance } = setup(ReservationFormComponent, order);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/lookups/fg-stock').flush(stock);
    instance['form'].patchValue({ stockKey: '55:1:12', qty: 60 });

    instance['save']();
    backend
      .expectOne('/api/sales/reservations')
      .flush(
        { status: 422, code: 'DISPATCH_EXCEEDS_STOCK', detail: 'Requested 60.000; available 40.000' },
        { status: 422, statusText: 'x' },
      );
    fixture.detectChanges();

    expect(close).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('available 40.000');
  });
});

describe('DispatchFormComponent', () => {
  const orders = [
    {
      id: 90,
      orderNumber: 'SO/26/00090',
      customerId: 6,
      customerName: 'Auto Parts Ltd',
      deliveryAddress: 'Plot 9, Pune',
      status: 'APPROVED',
      items: [
        {
          salesOrderItemId: 301,
          lineNo: 1,
          productId: 3,
          productCode: 'SPR-01',
          productName: 'Spring',
          orderedQty: 80,
          dispatchedQty: 0,
          remainingQty: 80,
          unitPrice: 55,
        },
      ],
    },
  ];

  function ready() {
    const ctx = setup(DispatchFormComponent);
    ctx.fixture.detectChanges();
    ctx.backend.expectOne('/api/lookups/sales-orders/dispatchable').flush(orders);
    ctx.backend.expectOne('/api/lookups/fg-stock').flush(stock);
    ctx.instance['form'].patchValue({ salesOrderId: 90 });
    ctx.instance['orderChosen'](90);
    return ctx;
  }

  it('fills the delivery address and the first stock option, then posts the CreateDispatchRequest shape', () => {
    const { instance, backend, close } = ready();
    expect(instance['form'].controls.deliveryAddress.value).toBe('Plot 9, Pune');
    expect(instance['lines'].at(0).value.stockKey).toBe('55:1:12');

    instance['form'].patchValue({ dispatchDate: '2026-10-09', transporter: 'Blue Dart', lrNumber: 'LR-5' });
    instance['lines'].at(0).patchValue({ quantity: 80 });
    instance['save']();

    const req = backend.expectOne('/api/sales/dispatches');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      salesOrderId: 90,
      dispatchDate: '2026-10-09',
      transporter: 'Blue Dart',
      vehicleNo: null,
      lrNumber: 'LR-5',
      deliveryAddress: 'Plot 9, Pune',
      invoiceReference: null,
      notes: null,
      lines: [{ salesOrderItemId: 301, fgBatchId: 55, quantity: 80, warehouseId: 1, locationId: 12, unitPrice: 55 }],
    });
    req.flush({ id: 4 });
    expect(close).toHaveBeenCalledWith(true);
  });

  it('refuses a quantity above the open quantity and a dispatch with no quantity', () => {
    const { instance, backend } = ready();

    instance['lines'].at(0).patchValue({ quantity: 81 });
    instance['save']();
    expect(instance['errorMessage']()).toContain('within the open quantity');

    instance['lines'].at(0).patchValue({ quantity: null });
    instance['save']();
    expect(instance['errorMessage']()).toContain('at least one line');
    backend.expectNone('/api/sales/dispatches');
    backend.verify();
  });

  it('shows the server error and stays open', () => {
    const { instance, backend, close, fixture } = ready();
    instance['lines'].at(0).patchValue({ quantity: 80 });

    instance['save']();
    backend
      .expectOne('/api/sales/dispatches')
      .flush(
        { status: 422, code: 'DISPATCH_EXCEEDS_STOCK', detail: 'Requested 80.000; available 0.000' },
        { status: 422, statusText: 'x' },
      );
    fixture.detectChanges();

    expect(close).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Requested 80.000; available 0.000');
  });
});
