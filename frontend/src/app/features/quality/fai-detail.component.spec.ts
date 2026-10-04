import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { FaiDetailComponent } from './fai-detail.component';
import { Fai, FaiReport } from './quality-plus.api';

function fai(overrides: Partial<Fai>): Fai {
  return {
    id: 8,
    faiNumber: 'FAI/26/00008',
    productId: 9,
    productCode: 'CS-100',
    productName: 'Compression spring',
    triggerType: 'ECO_IMPLEMENTED',
    triggerReference: 'ECO/26/00005',
    inspectionPlanId: null,
    planCode: null,
    productionOrderId: null,
    productionOrderNumber: null,
    fgBatchId: null,
    fgBatchNumber: null,
    inspectionId: null,
    inspectionNumber: null,
    status: 'REQUIRED',
    nextStatuses: ['IN_PROGRESS'],
    resultSummary: null,
    completedBy: null,
    completedAt: null,
    createdAt: '2026-10-03T08:00:00Z',
    ...overrides,
  };
}

function report(f: Fai, actual: number | null, result: string): FaiReport {
  return {
    fai: f,
    generatedAt: '2026-10-04T09:00:00Z',
    items: [
      {
        itemId: 51,
        seq: 1,
        parameterName: 'Free length',
        specification: null,
        lowerLimit: 49.5,
        upperLimit: 50.5,
        unit: 'mm',
        actualValue: actual,
        actualText: null,
        result,
        mandatory: true,
      },
    ],
  };
}

describe('FaiDetailComponent', () => {
  let fixture: ComponentFixture<FaiDetailComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[], loaded: Fai, rep?: FaiReport): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'qm',
      fullName: 'Quality Manager',
      email: 'q@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(FaiDetailComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/fai/8').flush(loaded);
    if (loaded.inspectionId) {
      backend.expectOne('/api/fai/8/report').flush(rep as FaiReport);
    }
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FaiDetailComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '8' }) } } },
      ],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('starts a required FAI against a production order', () => {
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT'], fai({}));
    expect(fixture.nativeElement.textContent).toContain('Start the first article inspection');
    fixture.componentInstance.orderCtrl.setValue(44);
    fixture.componentInstance.start();
    const req = backend.expectOne('/api/fai/8/start');
    expect(req.request.body).toEqual({ productionOrderId: 44 });
    const started = fai({ status: 'IN_PROGRESS', nextStatuses: ['PASSED', 'FAILED'], inspectionId: 70 });
    req.flush(started);
    backend.expectOne('/api/fai/8').flush(started);
    backend.expectOne('/api/fai/8/report').flush(report(started, null, 'NOT_MEASURED'));
  });

  it('hides the start controls from read-only users', () => {
    setup(['QUALITY_VIEW'], fai({}));
    expect((fixture.nativeElement.textContent as string).includes('Start FAI')).toBe(false);
  });

  it('builds the value form from the report items and posts the entered values', () => {
    const started = fai({ status: 'IN_PROGRESS', nextStatuses: ['PASSED', 'FAILED'], inspectionId: 70 });
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT', 'QUALITY_APPROVE'], started, report(started, null, 'NOT_MEASURED'));
    expect(fixture.nativeElement.textContent).toContain('Enter measured values');
    expect(fixture.nativeElement.textContent).toContain('Free length');
    fixture.componentInstance.results.controls['v51'].setValue(50.1);
    fixture.componentInstance.saveResults();
    const req = backend.expectOne('/api/fai/8/results');
    expect(req.request.body).toEqual({ values: [{ itemId: 51, actualValue: 50.1 }] });
    req.flush(report(started, 50.1, 'PASS'));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('PASS');
  });

  it('does not call the server when no value was entered', () => {
    const started = fai({ status: 'IN_PROGRESS', inspectionId: 70 });
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT'], started, report(started, null, 'NOT_MEASURED'));
    fixture.componentInstance.saveResults();
    backend.expectNone('/api/fai/8/results');
  });

  it('completes an in-progress FAI for approvers only', () => {
    const started = fai({ status: 'IN_PROGRESS', inspectionId: 70 });
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT'], started, report(started, 50.1, 'PASS'));
    expect((fixture.nativeElement.textContent as string).includes('Complete FAI')).toBe(false);
  });

  it('shows the printable report of a finished FAI and highlights failures', () => {
    const failed = fai({
      status: 'FAILED',
      nextStatuses: [],
      inspectionId: 70,
      resultSummary: '0 of 1 parameters passed',
      completedAt: '2026-10-04T09:00:00Z',
    });
    setup(['QUALITY_VIEW'], failed, report(failed, 50.9, 'FAIL'));
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('First article report');
    expect(text).toContain('0 of 1 parameters passed');
    expect(text).toContain('Print report');
    expect(fixture.nativeElement.querySelector('tr.fail')).not.toBeNull();
  });
});
