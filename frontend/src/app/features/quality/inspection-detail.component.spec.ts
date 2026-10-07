import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { InspectionDetailComponent, previewResult } from './inspection-detail.component';
import { InspectionFgBatchDto, InspectionItemDto, QualityInspectionDto, RejectionReasonDto } from './inspection.api';

function item(over: Partial<InspectionItemDto>): InspectionItemDto {
  return {
    id: 1,
    seq: 1,
    parameterName: 'Wire diameter',
    specification: '2.00 +/- 0.03',
    lowerLimit: 1.97,
    upperLimit: 2.03,
    actualValue: null,
    actualText: null,
    unit: 'mm',
    result: 'NOT_MEASURED',
    deviationNote: null,
    mandatory: true,
    deviationApprovedBy: null,
    deviationApprovedAt: null,
    ...over,
  };
}

function inspection(over: Partial<QualityInspectionDto>): QualityInspectionDto {
  return {
    id: 7,
    inspectionNumber: 'QI/26/00007',
    inspectionType: 'INCOMING',
    status: 'PENDING',
    goodsReceiptItemId: 3,
    grNumber: 'GR/26/00003',
    materialBatchId: 9,
    batchNumber: 'MB-0009',
    materialId: 2,
    materialCode: 'SPR-WIRE-200',
    fgBatchId: null,
    productionOrderId: null,
    productionOperationId: null,
    inspectedQty: 100,
    sampleSize: 8,
    acceptedQty: null,
    rejectedQty: null,
    holdQty: null,
    inspectorId: null,
    inspectedAt: null,
    decisionAt: null,
    remarks: null,
    items: [
      item({}),
      item({ id: 2, seq: 2, parameterName: 'Surface finish', lowerLimit: null, upperLimit: null, mandatory: false }),
    ],
    ...over,
  };
}

const reasons: RejectionReasonDto[] = [
  { id: 11, reasonCode: 'COIL', name: 'Coiling defect', appliesTo: 'FINAL', active: true },
  { id: 12, reasonCode: 'SURF', name: 'Surface defect', appliesTo: 'BOTH', active: true },
  { id: 13, reasonCode: 'RCV', name: 'Receiving damage', appliesTo: 'INCOMING', active: true },
];

const fgBatch: InspectionFgBatchDto = {
  id: 21,
  batchNumber: 'FG-0021',
  productCode: 'CS-001',
  productName: 'Compression spring',
  producedQty: 100,
  approvedQty: null,
  rejectedQty: null,
  remainingQty: null,
  qualityStatus: 'QUALITY_PENDING',
};

describe('previewResult', () => {
  const limits = { lowerLimit: 1.97, upperLimit: 2.03 };
  it('mirrors the server rule', () => {
    expect(previewResult(limits, null, null)).toBe('NOT_MEASURED');
    expect(previewResult(limits, 2.0, null)).toBe('PASS');
    expect(previewResult(limits, 1.97, null)).toBe('PASS');
    expect(previewResult(limits, 2.031, null)).toBe('FAIL');
    expect(previewResult(limits, 1.9, null)).toBe('FAIL');
    expect(previewResult({ lowerLimit: null, upperLimit: 5 }, 5, null)).toBe('PASS');
    expect(previewResult({ lowerLimit: null, upperLimit: null }, null, 'ok')).toBe('PASS');
    expect(previewResult(limits, null, '  ')).toBe('NOT_MEASURED');
  });
});

describe('InspectionDetailComponent', () => {
  let fixture: ComponentFixture<InspectionDetailComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[], insp: QualityInspectionDto): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'qi',
      fullName: 'Quality Inspector',
      email: 'q@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(InspectionDetailComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/quality/inspections/7').flush(insp);
    if (insp.inspectionType === 'FINAL') {
      backend.expectOne('/api/rejection-reasons').flush(reasons);
      if (insp.fgBatchId !== null) {
        backend.expectOne(`/api/quality/fg-batches/${insp.fgBatchId}`).flush(fgBatch);
      }
    }
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InspectionDetailComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map([['id', '7']]) } } },
      ],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  function button(label: string): HTMLButtonElement | undefined {
    const all = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    return all.find((b) => (b.textContent ?? '').includes(label));
  }

  it('shows an incoming inspection with its parameters, sampling info and the quarantine effect', () => {
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT'], inspection({}));
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('QI/26/00007');
    expect(text).toContain('GR/26/00003');
    expect(text).toContain('SPR-WIRE-200');
    expect(text).toContain('Wire diameter');
    expect(text).toContain('1.97 to 2.03');
    expect(text).toContain('8');
    expect(text).toContain('stays in quarantine');
  });

  it('puts an incoming inspection on hold with a mandatory reason (QCI-04)', () => {
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT', 'QUALITY_HOLD'], inspection({}));
    expect(button('Hold')?.disabled).toBe(true);
    fixture.componentInstance.holdReasonCtrl.setValue('Surface rust suspected');
    fixture.detectChanges();
    button('Hold')?.click();
    const req = backend.expectOne('/api/quality/inspections/7/hold');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Surface rust suspected' });
    req.flush(inspection({ status: 'HOLD' }));
    fixture.detectChanges();
    expect(fixture.componentInstance.insp()?.status).toBe('HOLD');
  });

  it('does not offer Hold without QUALITY_HOLD', () => {
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT'], inspection({}));
    expect(button('Hold')).toBeUndefined();
  });

  it('re-inspects a held incoming inspection (QCI-05)', () => {
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT'], inspection({ status: 'HOLD' }));
    expect(fixture.nativeElement.textContent).toContain('on HOLD');
    button('Re-inspect')?.click();
    const req = backend.expectOne('/api/quality/inspections/7/reinspect');
    expect(req.request.method).toBe('POST');
    req.flush(inspection({ status: 'IN_PROGRESS' }));
    fixture.detectChanges();
    expect(fixture.componentInstance.insp()?.status).toBe('IN_PROGRESS');
  });

  it('highlights an out-of-tolerance value before saving and posts the entered results', () => {
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT'], inspection({}));
    const comp = fixture.componentInstance;
    comp.rows[0].value.setValue(2.1);
    comp.rows[1].text.setValue('smooth');
    comp.acceptedCtrl.setValue(95);
    comp.rejectedCtrl.setValue(5);
    comp.remarksCtrl.setValue('lot looked fine');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('tr.fail')).not.toBeNull();

    button('Save results')?.click();
    const req = backend.expectOne('/api/quality/inspections/7/results');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      acceptedQty: 95,
      rejectedQty: 5,
      remarks: 'lot looked fine',
      items: [
        { itemId: 1, actualValue: 2.1, actualText: null, deviationNote: null },
        { itemId: 2, actualValue: null, actualText: 'smooth', deviationNote: null },
      ],
    });
    req.flush(
      inspection({
        status: 'IN_PROGRESS',
        acceptedQty: 95,
        rejectedQty: 5,
        items: [
          item({ actualValue: 2.1, result: 'FAIL' }),
          item({ id: 2, seq: 2, actualText: 'smooth', result: 'PASS', mandatory: false }),
        ],
      }),
    );
    fixture.detectChanges();
    expect(fixture.componentInstance.insp()?.status).toBe('IN_PROGRESS');
  });

  it('blocks quantities above the inspected quantity', () => {
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT'], inspection({}));
    fixture.componentInstance.acceptedCtrl.setValue(90);
    fixture.componentInstance.rejectedCtrl.setValue(20);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('exceeds the inspected quantity');
    expect(button('Save results')?.disabled).toBe(true);
  });

  it('accepts the whole lot by default and sends the quantities, then shows the released batch', () => {
    // the server stores 0/0 for an inspection nobody has decided yet (not null)
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT'], inspection({ acceptedQty: 0, rejectedQty: 0, holdQty: 0 }));
    expect(fixture.componentInstance.acceptedCtrl.value).toBe(100);
    button('Accept')?.click();
    const req = backend.expectOne('/api/quality/inspections/7/accept');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ acceptedQty: 100, rejectedQty: 0 });
    req.flush(inspection({ status: 'APPROVED', acceptedQty: 100, rejectedQty: 0 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('released to available stock');
  });

  it('sends the entered accepted and rejected quantities when accepting', () => {
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT'], inspection({ acceptedQty: 0, rejectedQty: 0, holdQty: 0 }));
    fixture.componentInstance.acceptedCtrl.setValue(90);
    fixture.componentInstance.rejectedCtrl.setValue(10);
    fixture.detectChanges();
    button('Accept')?.click();
    const req = backend.expectOne('/api/quality/inspections/7/accept');
    expect(req.request.body).toEqual({ acceptedQty: 90, rejectedQty: 10 });
    req.flush(inspection({ status: 'APPROVED', acceptedQty: 90, rejectedQty: 10 }));
  });

  it('blocks Accept until accepted plus rejected equals the inspected quantity', () => {
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT'], inspection({ acceptedQty: 0, rejectedQty: 0, holdQty: 0 }));
    fixture.componentInstance.acceptedCtrl.setValue(60);
    fixture.componentInstance.rejectedCtrl.setValue(10);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('must equal the inspected quantity');
    expect(button('Accept')?.disabled).toBe(true);
    fixture.componentInstance.acceptedCtrl.setValue(90);
    fixture.detectChanges();
    expect(button('Accept')?.disabled).toBe(false);
  });

  it('needs a reason to reject', () => {
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT'], inspection({}));
    expect(button('Reject')?.disabled).toBe(true);
    fixture.componentInstance.rejectReasonCtrl.setValue('Wrong grade');
    fixture.detectChanges();
    button('Reject')?.click();
    const req = backend.expectOne('/api/quality/inspections/7/reject');
    expect(req.request.body).toEqual({ reason: 'Wrong grade' });
    req.flush(inspection({ status: 'REJECTED' }));
  });

  it('is read-only for users without QUALITY_INSPECT and for decided inspections', () => {
    setup(['QUALITY_VIEW'], inspection({}));
    expect(button('Accept')).toBeUndefined();
    expect(fixture.nativeElement.querySelector('input.cell')).toBeNull();
  });

  it('completes a final inspection with rejection reasons that add up and shows the FG batch status', () => {
    setup(
      ['QUALITY_VIEW', 'QUALITY_INSPECT'],
      inspection({
        inspectionType: 'FINAL',
        fgBatchId: 21,
        grNumber: null,
        materialCode: null,
        batchNumber: null,
        goodsReceiptItemId: null,
        materialBatchId: null,
        materialId: null,
      }),
    );
    const comp = fixture.componentInstance;
    comp.approvedCtrl.setValue(95);
    comp.finalRejectedCtrl.setValue(5);
    comp.holdCtrl.setValue(0);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('must add up to the rejected quantity');
    expect(button('Complete final inspection')?.disabled).toBe(true);

    comp.reasonLines[0].reason.setValue(11);
    comp.reasonLines[0].qty.setValue(5);
    comp.rows[0].value.setValue(2.0);
    fixture.detectChanges();
    expect(button('Complete final inspection')?.disabled).toBe(false);

    button('Complete final inspection')?.click();
    const req = backend.expectOne('/api/quality/inspections/7/final-decision');
    expect(req.request.body).toEqual({
      approvedQty: 95,
      rejectedQty: 5,
      holdQty: 0,
      itemResults: [
        { seq: 1, actualValue: 2.0, actualText: null, deviationNote: null },
        { seq: 2, actualValue: null, actualText: null, deviationNote: null },
      ],
      rejectionReasons: [{ rejectionReasonId: 11, quantity: 5, remarks: null }],
      remarks: '',
    });
    req.flush(
      inspection({
        inspectionType: 'FINAL',
        fgBatchId: 21,
        status: 'APPROVED',
        acceptedQty: 95,
        rejectedQty: 5,
        holdQty: 0,
      }),
    );
    backend
      .expectOne('/api/quality/fg-batches/21')
      .flush({ ...fgBatch, qualityStatus: 'PARTIALLY_APPROVED', approvedQty: 95, rejectedQty: 5, remainingQty: 95 });
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('FG-0021');
    expect(text).toContain('PARTIALLY APPROVED');
  });

  it('only offers deviation approval to QUALITY_APPROVE holders for a failed mandatory parameter', () => {
    const failed = inspection({
      status: 'IN_PROGRESS',
      items: [item({ actualValue: 2.2, result: 'FAIL' })],
    });
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT'], failed);
    expect(fixture.nativeElement.textContent).not.toContain('Deviation approval');
    fixture.destroy();
    backend.verify();

    setup(['QUALITY_VIEW', 'QUALITY_INSPECT', 'QUALITY_APPROVE'], failed);
    expect(fixture.nativeElement.textContent).toContain('Deviation approval');
    fixture.componentInstance.rows[0].override.setValue('Customer concession');
    fixture.detectChanges();
    button('Approve deviation')?.click();
    const req = backend.expectOne('/api/quality/inspections/7/items/1/deviation');
    expect(req.request.body).toEqual({ deviationNote: 'Customer concession' });
    req.flush(failed);
  });
});
