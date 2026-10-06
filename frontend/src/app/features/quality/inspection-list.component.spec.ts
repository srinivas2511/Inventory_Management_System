import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { InspectionListComponent } from './inspection-list.component';
import { QualityInspectionDto } from './inspection.api';

const row: QualityInspectionDto = {
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
  sampleSize: null,
  acceptedQty: null,
  rejectedQty: null,
  holdQty: null,
  inspectorId: null,
  inspectedAt: null,
  decisionAt: null,
  remarks: null,
  items: [],
};

describe('InspectionListComponent', () => {
  let fixture: ComponentFixture<InspectionListComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'qi',
      fullName: 'QI',
      email: 'q@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(InspectionListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend
      .expectOne((r) => r.url === '/api/quality/inspections')
      .flush({ content: [row], totalElements: 1, totalPages: 1, size: 50, number: 0 });
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InspectionListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('links each inspection to its detail screen and shows the reference', () => {
    setup(['QUALITY_VIEW']);
    const link = fixture.nativeElement.querySelector('a[href="/quality/inspections/7"]');
    expect(link).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('GR/26/00003');
    expect(fixture.nativeElement.textContent).toContain('SPR-WIRE-200');
  });

  it('hides the create controls from read-only users', () => {
    setup(['QUALITY_VIEW']);
    expect(fixture.nativeElement.textContent).not.toContain('New in-process inspection');
  });

  it('filters by type', () => {
    setup(['QUALITY_VIEW']);
    fixture.componentInstance.typeCtrl.setValue('FINAL');
    const req = backend.expectOne((r) => r.url === '/api/quality/inspections');
    expect(req.request.params.get('type')).toBe('FINAL');
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 50, number: 0 });
  });

  it('creates an in-process inspection and opens it', () => {
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT']);
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    fixture.componentInstance.operationCtrl.setValue(55);
    fixture.componentInstance.inProcessQtyCtrl.setValue(40);
    fixture.componentInstance.startInProcess();
    const req = backend.expectOne('/api/quality/inspections/in-process');
    expect(req.request.body).toEqual({ productionOperationId: 55, inspectedQty: 40 });
    req.flush({ ...row, id: 8, inspectionType: 'IN_PROCESS', inspectionNumber: 'QI/26/00008' });
    expect(navigate).toHaveBeenCalledWith(['/quality/inspections', 8]);
  });
});
