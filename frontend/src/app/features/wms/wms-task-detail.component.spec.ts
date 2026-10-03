import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { BinDto, ConfirmResultDto, Page, TransferOrderDto } from './wms.api';
import { WmsTaskDetailComponent } from './wms-task-detail.component';

const order: TransferOrderDto = {
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
  allowedActions: ['ASSIGN', 'START', 'SET_TARGET', 'CONFIRM', 'CANCEL'],
  lines: [
    {
      id: 11,
      lineNo: 1,
      itemType: 'MATERIAL',
      itemId: 3,
      itemCode: 'MAT-3',
      itemName: 'Music wire',
      uom: 'KG',
      batchId: 7,
      batchNumber: 'MB-7',
      stockStatus: null,
      fromBinId: 100,
      fromBinCode: 'RCV-01',
      toBinId: 200,
      toBinCode: 'A-01',
      fromLocationId: 50,
      toLocationId: 60,
      plannedQty: 40,
      confirmedQty: 0,
      status: 'OPEN',
      warning: 'Bin A-01 would hold 120 kg, over its 100 kg capacity',
      confirmedAt: null,
    },
  ],
};

const bins: Page<BinDto> = { content: [], totalElements: 0, totalPages: 1, size: 200, number: 0 };

describe('WmsTaskDetailComponent', () => {
  let fixture: ComponentFixture<WmsTaskDetailComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'op',
      fullName: 'Operator',
      email: 'op@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(WmsTaskDetailComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/wms/transfer-orders/5').flush(order);
    backend.expectOne((r) => r.url === '/api/wms/bins').flush(bins);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WmsTaskDetailComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '5' }) } } },
      ],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('shows the order, its lines and the capacity warning of a line', () => {
    setup(['WMS_VIEW', 'PUTAWAY_MANAGE']);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-test="title"]')?.textContent).toContain('TO/26/00005');
    expect(el.querySelector('[data-test="lines"]')?.textContent).toContain('Music wire');
    expect(el.querySelector('[data-test="lines"]')?.textContent).toContain('over its 100 kg capacity');
  });

  it('confirms the remaining quantity with the scanned codes', () => {
    setup(['WMS_VIEW', 'PUTAWAY_MANAGE']);
    const el: HTMLElement = fixture.nativeElement;
    const bin = el.querySelector<HTMLInputElement>('[data-test="scan-bin"]')!;
    const batch = el.querySelector<HTMLInputElement>('[data-test="scan-batch"]')!;
    bin.value = ' A-01 ';
    bin.dispatchEvent(new Event('input'));
    batch.value = 'MB-7';
    batch.dispatchEvent(new Event('input'));

    el.querySelector<HTMLButtonElement>('[data-test="confirm"]')!.click();

    const req = backend.expectOne('/api/wms/transfer-orders/5/confirm');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.lines).toEqual([
      { lineId: 11, quantity: 40, scannedFromBin: null, scannedToBin: 'A-01', scannedBatch: 'MB-7', toBinId: 200 },
    ]);
    const done: ConfirmResultDto = {
      order: {
        ...order,
        status: 'COMPLETED',
        allowedActions: [],
        lines: [{ ...order.lines[0], confirmedQty: 40, status: 'CONFIRMED' }],
      },
      warnings: ['Bin A-01 would hold 120 kg, over its 100 kg capacity'],
    };
    req.flush(done);
    fixture.detectChanges();

    expect(el.querySelector('[data-test="warnings"]')?.textContent).toContain('over its 100 kg capacity');
    expect(el.querySelector('[data-test="confirm"]')).toBeNull();
  });

  it('does not offer confirmation to someone who lacks the permission for this movement', () => {
    setup(['WMS_VIEW', 'PICKING_MANAGE']);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-test="confirm"]')).toBeNull();
    expect(el.querySelector('[data-test="start"]')).toBeNull();
    expect(el.querySelector('[data-test="lines"]')?.textContent).toContain('MAT-3');
  });

  it('refuses to send a confirmation with no quantity', () => {
    setup(['WMS_VIEW', 'PUTAWAY_MANAGE']);
    const el: HTMLElement = fixture.nativeElement;
    const qty = el.querySelector<HTMLInputElement>('[data-test="qty"]')!;
    qty.value = '0';
    qty.dispatchEvent(new Event('input'));
    el.querySelector<HTMLButtonElement>('[data-test="confirm"]')!.click();
    backend.expectNone('/api/wms/transfer-orders/5/confirm');
  });
});
