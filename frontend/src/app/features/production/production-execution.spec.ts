/* eslint-disable @typescript-eslint/no-explicit-any */
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Type } from '@angular/core';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { SessionStore } from '../../core/auth/session.store';
import { AssignOperatorDialogComponent } from './assign-operator-dialog.component';
import { CloseOrderDialogComponent } from './close-order-dialog.component';
import { IssueMaterialDialogComponent } from './issue-material-dialog.component';
import { ProductionOrderListComponent } from './production-order-list.component';
import {
  ProductionOperationDto,
  ProductionOrderDto,
  ProductionOrderMaterialDto,
  isRunning,
  leftoverQty,
  stillToIssueQty,
} from './production-order.model';
import { ReturnMaterialDialogComponent } from './return-material-dialog.component';

function material(over: Partial<ProductionOrderMaterialDto> = {}): ProductionOrderMaterialDto {
  return {
    id: 1,
    materialId: 9,
    materialCode: 'WIRE-2',
    materialName: 'Music wire 2 mm',
    plannedQty: 4.635,
    issuedQty: 0,
    consumedQty: 0,
    returnedQty: 0,
    scrapQty: 0,
    remainingQty: 0, // the real value on a fresh line: issued - consumed - returned - scrapped
    uom: 'KG',
    availableQty: null,
    shortage: null,
    ...over,
  };
}

function operation(over: Partial<ProductionOperationDto> = {}): ProductionOperationDto {
  return {
    id: 5,
    sequenceNo: 10,
    operationId: 2,
    operationName: 'Coiling',
    machineId: 3,
    machineName: 'Coiler 1',
    assignedOperatorId: null,
    assignedOperatorName: null,
    inspectionPoint: false,
    status: 'PENDING',
    inputQty: 100,
    goodQty: 0,
    rejectedQty: 0,
    scrapQty: 0,
    finalOperation: false,
    ...over,
  };
}

function order(over: Partial<ProductionOrderDto> = {}): ProductionOrderDto {
  return {
    id: 11,
    orderNumber: 'PO/26/00011',
    productId: 4,
    productCode: 'SPR-01',
    plannedQty: 100,
    goodQty: 0,
    rejectedQty: 0,
    scrapQty: 0,
    status: 'RELEASED',
    priority: 2,
    startDate: '2026-10-07',
    expectedCompletionDate: '2026-10-10',
    materials: [material()],
    operations: [operation()],
    allowedActions: ['cancel'],
    ...over,
  };
}

function dialogSetup<T>(component: Type<T>, data: unknown) {
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

describe('production order model helpers', () => {
  it('works out the leftover as issued minus consumed, returned and written off', () => {
    expect(leftoverQty(material({ issuedQty: 5, consumedQty: 3.5, returnedQty: 0.5, scrapQty: 0.25 }))).toBe(0.75);
    expect(leftoverQty(material({ issuedQty: 0 }))).toBe(0);
  });

  it('still to issue is planned minus issued, never the leftover in WIP (D-06)', () => {
    expect(stillToIssueQty(material())).toBe(4.635);
    expect(stillToIssueQty(material({ plannedQty: 5, issuedQty: 2, consumedQty: 0, remainingQty: 2 }))).toBe(3);
    expect(stillToIssueQty(material({ plannedQty: 5, issuedQty: 6, remainingQty: 6 }))).toBe(0);
  });

  it('treats only released and in-progress orders as running', () => {
    expect(isRunning({ status: 'RELEASED' })).toBeTrue();
    expect(isRunning({ status: 'IN_PROGRESS' })).toBeTrue();
    expect(isRunning({ status: 'COMPLETED' })).toBeFalse();
    expect(isRunning({ status: 'DRAFT' })).toBeFalse();
  });
});

describe('IssueMaterialDialogComponent', () => {
  const data = { orderId: 11, orderNumber: 'PO/26/00011', materials: [material()] };
  const stock = [
    {
      batchId: 70,
      batchNumber: 'RM-70',
      expiryDate: '2027-01-01',
      receivedDate: '2026-09-01',
      locationId: 1,
      locationCode: 'RM-01',
      onHand: 10,
      reserved: 2,
      available: 8,
    },
    {
      batchId: 70,
      batchNumber: 'RM-70',
      expiryDate: '2027-01-01',
      receivedDate: '2026-09-01',
      locationId: 2,
      locationCode: 'RM-02',
      onHand: 4,
      reserved: 0,
      available: 4,
    },
  ];

  function open() {
    const ctx = dialogSetup(IssueMaterialDialogComponent, data);
    ctx.fixture.detectChanges();
    ctx.backend.expectOne('/api/lookups/warehouses').flush([{ id: 1, code: 'WH1', name: 'Main store' }]);
    return ctx;
  }

  function chooseWarehouse(ctx: ReturnType<typeof open>): void {
    ctx.instance['form'].controls['warehouseId'].setValue(1);
    ctx.instance['warehouseChosen'](1);
    const req = ctx.backend.expectOne(
      (r) =>
        r.url === '/api/lookups/material-stock' &&
        r.params.get('materialId') === '9' &&
        r.params.get('warehouseId') === '1',
    );
    req.flush(stock);
  }

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('pre-fills the quantity still to issue and sums availability across locations of a batch', () => {
    const ctx = open();
    expect(ctx.instance['lines'].at(0).value.quantity).toBe(4.635);
    chooseWarehouse(ctx);
    expect(ctx.instance['availableFor'](0)).toBe(12);
    const options = ctx.instance['batchOptions'](0);
    expect(options.length).toBe(1);
    expect(options[0].batchId).toBe(70);
    expect(options[0].label).toContain('12 KG available');
    expect(options[0].label).toContain('expires 2027-01-01');
  });

  it('posts the CreateIssueRequest shape with the chosen batch and shows what was issued', () => {
    const ctx = open();
    chooseWarehouse(ctx);
    ctx.instance['lines'].at(0).patchValue({ batchId: 70 });
    ctx.instance['form'].patchValue({ remarks: 'Shift 1' });
    ctx.instance['save']();

    const req = ctx.backend.expectOne('/api/production/issues');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      productionOrderId: 11,
      warehouseId: 1,
      lines: [{ materialId: 9, quantity: 4.635, explicitBatchId: 70 }],
      remarks: 'Shift 1',
    });
    req.flush({
      id: 3,
      issueNumber: 'MI/26/00003',
      productionOrderId: 11,
      lines: [
        { materialId: 9, materialBatchId: 70, batchNumber: 'RM-70', fromLocationId: 1, quantity: 4.635, unitCost: 85 },
      ],
    });
    ctx.fixture.detectChanges();
    expect(ctx.fixture.nativeElement.textContent).toContain('MI/26/00003');
    ctx.instance['done']();
    expect(ctx.close).toHaveBeenCalledWith(true);
  });

  it('leaves the batch automatic by default and skips lines set to 0; submit needs a quantity somewhere', () => {
    const ctx = open();
    chooseWarehouse(ctx);
    ctx.instance['lines'].at(0).patchValue({ quantity: 0 });
    expect(ctx.instance['canSubmit']()).toBeFalse();
    ctx.instance['lines'].at(0).patchValue({ quantity: 2 });
    expect(ctx.instance['canSubmit']()).toBeTrue();
    ctx.instance['save']();
    const req = ctx.backend.expectOne('/api/production/issues');
    expect(req.request.body.lines).toEqual([{ materialId: 9, quantity: 2, explicitBatchId: null }]);
    req.flush({ id: 1, issueNumber: 'MI/1', productionOrderId: 11, lines: [] });
  });

  it('warns when the quantity is above what is still to issue but lets the server decide', () => {
    const ctx = open();
    ctx.instance['lines'].at(0).patchValue({ quantity: 6 });
    expect(ctx.instance['overIssue'](0)).toBeTrue();
    ctx.instance['lines'].at(0).patchValue({ quantity: 4.635 });
    expect(ctx.instance['overIssue'](0)).toBeFalse();
  });

  it('shows the server message when the issue is refused', () => {
    const ctx = open();
    chooseWarehouse(ctx);
    ctx.instance['save']();
    ctx.backend
      .expectOne('/api/production/issues')
      .flush(
        { status: 422, code: 'INSUFFICIENT_STOCK', detail: 'Batch RM-70: available 0.000, requested 4.635' },
        { status: 422, statusText: 'Unprocessable' },
      );
    expect(ctx.instance['errorMessage']()).toContain('available 0.000');
    expect(ctx.instance['saving']()).toBeFalse();
  });
});

describe('ReturnMaterialDialogComponent', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  const data = {
    orderId: 11,
    orderNumber: 'PO/26/00011',
    material: material({ issuedQty: 5, consumedQty: 3, remainingQty: 0 }),
  };

  it('offers the leftover and posts the ReturnMaterialRequest shape', () => {
    const { fixture, backend, close, instance } = dialogSetup(ReturnMaterialDialogComponent, data);
    fixture.detectChanges();
    expect(instance['form'].value.quantity).toBe(2);
    instance['form'].patchValue({ quantity: 1.5, remarks: 'Clean offcut' });
    instance['save']();
    const req = backend.expectOne('/api/production-orders/11/material-returns');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ materialId: 9, quantity: 1.5, remarks: 'Clean offcut' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(close).toHaveBeenCalledWith(true);
  });

  it('refuses more than the leftover and shows the server message when the return is rejected', () => {
    const { fixture, backend, instance } = dialogSetup(ReturnMaterialDialogComponent, data);
    fixture.detectChanges();
    instance['form'].patchValue({ quantity: 2.5 });
    instance['save']();
    backend.expectNone('/api/production-orders/11/material-returns');
    expect(instance['form'].controls['quantity'].hasError('max')).toBeTrue();

    instance['form'].patchValue({ quantity: 2 });
    instance['save']();
    backend
      .expectOne('/api/production-orders/11/material-returns')
      .flush({ status: 422, code: 'OVER_CONSUMPTION', detail: 'Cannot return 2' }, { status: 422, statusText: 'x' });
    expect(instance['errorMessage']()).toContain('Cannot return 2');
  });
});

describe('CloseOrderDialogComponent', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('closes straight away when every issued kilogram is accounted for', () => {
    const data = {
      orderId: 11,
      orderNumber: 'PO/26/00011',
      materials: [material({ issuedQty: 5, consumedQty: 5, remainingQty: 0 })],
    };
    const { fixture, backend, close, instance } = dialogSetup(CloseOrderDialogComponent, data);
    fixture.detectChanges();
    instance['save']();
    const req = backend.expectOne('/api/production-orders/11/close');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ writeOffReason: null });
    req.flush(order({ status: 'CLOSED' }));
    expect(close).toHaveBeenCalled();
  });

  it('needs a reason before it writes leftover material off, then sends it trimmed', () => {
    const data = {
      orderId: 11,
      orderNumber: 'PO/26/00011',
      materials: [material({ issuedQty: 5, consumedQty: 3, remainingQty: 0 })],
    };
    const { fixture, backend, instance } = dialogSetup(CloseOrderDialogComponent, data);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('WIRE-2');
    instance['save']();
    backend.expectNone('/api/production-orders/11/close');

    instance['form'].patchValue({ reason: '  Cut-offs too short to reuse  ' });
    instance['save']();
    const req = backend.expectOne('/api/production-orders/11/close');
    expect(req.request.body).toEqual({ writeOffReason: 'Cut-offs too short to reuse' });
    req.flush(order({ status: 'CLOSED' }));
  });

  it('shows the server message when the order cannot be closed', () => {
    const data = { orderId: 11, orderNumber: 'PO/26/00011', materials: [] };
    const { fixture, backend, instance } = dialogSetup(CloseOrderDialogComponent, data);
    fixture.detectChanges();
    instance['save']();
    backend
      .expectOne('/api/production-orders/11/close')
      .flush(
        { status: 422, code: 'ILLEGAL_STATE_TRANSITION', detail: 'Order is IN_PROGRESS' },
        { status: 422, statusText: 'x' },
      );
    expect(instance['errorMessage']()).toContain('IN_PROGRESS');
  });
});

describe('AssignOperatorDialogComponent', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  const people = [
    { id: 7, username: 'asha', fullName: 'Asha Rao' },
    { id: 8, username: 'ben', fullName: 'Ben Dsouza' },
  ];

  it('assigns one operation and posts the AssignOperatorRequest shape', () => {
    const { fixture, backend, close, instance } = dialogSetup(AssignOperatorDialogComponent, {
      orderId: 11,
      orderNumber: 'PO/26/00011',
      operation: operation(),
    });
    fixture.detectChanges();
    backend.expectOne('/api/production/assignable-operators').flush(people);
    instance['form'].patchValue({ operatorId: 7, reason: ' Night shift ' });
    instance['save']();
    const req = backend.expectOne('/api/production/operations/5/assignment');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ operatorId: 7, reason: 'Night shift' });
    req.flush(operation({ assignedOperatorId: 7, assignedOperatorName: 'Asha Rao' }));
    expect(close).toHaveBeenCalledWith(true);
  });

  it('assigns every open operation of the order, and unassigning sends a null operator', () => {
    const { fixture, backend, instance } = dialogSetup(AssignOperatorDialogComponent, {
      orderId: 11,
      orderNumber: 'PO/26/00011',
      operation: null,
    });
    fixture.detectChanges();
    backend.expectOne('/api/production/assignable-operators').flush(people);
    instance['save']();
    const req = backend.expectOne('/api/production-orders/11/operator-assignment');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ operatorId: null, reason: null });
    req.flush([operation()]);
  });

  it('searches people on the server after a pause and keeps the current operator selectable', fakeAsync(() => {
    const { fixture, backend, instance } = dialogSetup(AssignOperatorDialogComponent, {
      orderId: 11,
      orderNumber: 'PO/26/00011',
      operation: operation({ assignedOperatorId: 99, assignedOperatorName: 'Gone Away' }),
    });
    fixture.detectChanges();
    backend.expectOne('/api/production/assignable-operators').flush(people);
    expect(instance['options']().map((o: any) => o.id)).toEqual([99, 7, 8]);

    instance['search'].setValue('as');
    tick(250);
    const req = backend.expectOne(
      (r) => r.url === '/api/production/assignable-operators' && r.params.get('q') === 'as',
    );
    req.flush([people[0]]);
    expect(instance['options']().map((o: any) => o.id)).toEqual([99, 7]);
  }));

  it('shows the server message when the assignment is refused', () => {
    const { fixture, backend, instance } = dialogSetup(AssignOperatorDialogComponent, {
      orderId: 11,
      orderNumber: 'PO/26/00011',
      operation: operation(),
    });
    fixture.detectChanges();
    backend.expectOne('/api/production/assignable-operators').flush(people);
    instance['form'].patchValue({ operatorId: 7 });
    instance['save']();
    backend
      .expectOne('/api/production/operations/5/assignment')
      .flush(
        { status: 422, code: 'ILLEGAL_STATE_TRANSITION', detail: 'Operation 10 is COMPLETED' },
        { status: 422, statusText: 'x' },
      );
    expect(instance['errorMessage']()).toContain('COMPLETED');
  });
});

describe('ProductionOrderListComponent execution actions', () => {
  let backend: HttpTestingController;

  function show(permissions: string[], orders: ProductionOrderDto[]) {
    TestBed.configureTestingModule({
      imports: [ProductionOrderListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'u',
      fullName: 'User',
      email: 'u@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    const fixture = TestBed.createComponent(ProductionOrderListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    expectOrderList().flush({ content: orders, totalElements: orders.length });
    fixture.detectChanges();
    return fixture;
  }

  /** The list asks for one page of the newest orders first; the paginator offers the older ones. */
  function expectOrderList() {
    const req = backend.expectOne((r) => r.method === 'GET' && r.url === '/api/production-orders');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('sort')).toBe('id,desc');
    return req;
  }

  function buttonLabels(fixture: { nativeElement: HTMLElement }): string[] {
    return (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]).map((b) =>
      (b.textContent ?? '').trim(),
    );
  }

  afterEach(() => backend.verify());

  it('offers issue and assign only on a running order and only to holders of the permission', () => {
    const fixture = show(
      ['PRODUCTION_VIEW', 'INVENTORY_ISSUE', 'PRODUCTION_ASSIGN'],
      [order({ operations: [operation(), operation({ id: 6, sequenceNo: 20, status: 'COMPLETED' })] })],
    );
    const labels = buttonLabels(fixture);
    expect(labels.some((l) => l.includes('Issue material'))).toBeTrue();
    expect(labels.some((l) => l.includes('Assign operator to all'))).toBeTrue();
    // one assign control per unfinished operation
    expect(fixture.nativeElement.querySelectorAll('button[aria-label="Assign operator"]').length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Unassigned');
  });

  it('hides issue and assign without the permissions, and on orders that are not running', () => {
    const noPermission = show(['PRODUCTION_VIEW'], [order()]);
    expect(buttonLabels(noPermission).some((l) => l.includes('Issue material'))).toBeFalse();
    expect(noPermission.nativeElement.querySelectorAll('button[aria-label="Assign operator"]').length).toBe(0);
    TestBed.resetTestingModule();

    const completed = show(
      ['PRODUCTION_VIEW', 'INVENTORY_ISSUE', 'PRODUCTION_ASSIGN'],
      [order({ status: 'COMPLETED' })],
    );
    expect(buttonLabels(completed).some((l) => l.includes('Issue material'))).toBeFalse();
    expect(completed.nativeElement.querySelectorAll('button[aria-label="Assign operator"]').length).toBe(0);
  });

  it('shows who is assigned', () => {
    const fixture = show(
      ['PRODUCTION_VIEW'],
      [order({ operations: [operation({ assignedOperatorId: 7, assignedOperatorName: 'Asha Rao' })] })],
    );
    expect(fixture.nativeElement.textContent).toContain('Asha Rao');
  });

  it('lets a completed order return leftover material, explains the reconciliation and closes through a dialog', () => {
    const completed = order({
      status: 'COMPLETED',
      allowedActions: ['close'],
      materials: [material({ issuedQty: 5, consumedQty: 3, remainingQty: 0 })],
    });
    const fixture = show(['PRODUCTION_VIEW', 'PRODUCTION_CLOSE'], [completed]);
    const labels = buttonLabels(fixture);
    expect(labels).toContain('Return');
    expect(fixture.nativeElement.textContent).toContain('write it off');

    const dialog = TestBed.inject(MatDialog);
    const open = spyOn(dialog, 'open').and.returnValue({ afterClosed: () => of(true) } as MatDialogRef<unknown>);
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])
      .find((b) => (b.textContent ?? '').includes('Close'))
      ?.click();
    expect(open).toHaveBeenCalledTimes(1);
    expect(open.calls.mostRecent().args[0]).toBe(CloseOrderDialogComponent);
    // the dialog does the posting; the list only reloads
    backend.expectNone('/api/production-orders/11/close');
    expectOrderList().flush({ content: [completed], totalElements: 1 });
  });

  it('does not offer a return when nothing is left over', () => {
    const fixture = show(
      ['PRODUCTION_VIEW', 'PRODUCTION_CLOSE'],
      [
        order({
          status: 'COMPLETED',
          allowedActions: ['close'],
          materials: [material({ issuedQty: 5, consumedQty: 5, remainingQty: 0 })],
        }),
      ],
    );
    expect(buttonLabels(fixture)).not.toContain('Return');
  });

  it('opens the issue dialog with the order materials and reloads after it closes', () => {
    const running = order();
    const fixture = show(['PRODUCTION_VIEW', 'INVENTORY_ISSUE'], [running]);
    const dialog = TestBed.inject(MatDialog);
    const open = spyOn(dialog, 'open').and.returnValue({ afterClosed: () => of(true) } as MatDialogRef<unknown>);
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])
      .find((b) => (b.textContent ?? '').includes('Issue material'))
      ?.click();
    const config = open.calls.mostRecent().args[1] as { data: any };
    expect(open.calls.mostRecent().args[0]).toBe(IssueMaterialDialogComponent);
    expect(config.data.orderId).toBe(11);
    expect(config.data.materials.length).toBe(1);
    expectOrderList().flush({ content: [running], totalElements: 1 });
  });
});
