import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { SpcChartDetail, SpcPoint } from './quality-plus.api';
import { SpcChartDetailComponent } from './spc-chart-detail.component';

function point(id: number, subgroup: number, measurement: number, extra: Partial<SpcPoint> = {}): SpcPoint {
  return {
    id,
    inspectionId: null,
    subgroupNo: subgroup,
    measurementNo: measurement,
    measuredValue: 10,
    measuredAt: '2026-10-04T08:00:00Z',
    subgroupMean: null,
    subgroupRange: null,
    sampleSize: null,
    defectCount: null,
    pointUcl: null,
    pointLcl: null,
    outOfControl: false,
    violationRule: null,
    ...extra,
  };
}

const chart: SpcChartDetail = {
  summary: {
    id: 3,
    productId: 9,
    productCode: 'CS-100',
    productName: 'Compression spring',
    parameterName: 'Wire diameter',
    chartType: 'XBAR_R',
    subgroupSize: 2,
    limitsEstablished: true,
    active: true,
    pointCount: 4,
    outOfControlCount: 1,
  },
  cl: 10,
  ucl: 10.3,
  lcl: 9.7,
  clR: 0.2,
  uclR: 0.65,
  lclR: 0,
  usl: 10.6,
  lsl: 9.4,
  limitsEstablishedAt: '2026-10-03T08:00:00Z',
  points: [
    point(1, 1, 1),
    point(2, 1, 2, { subgroupMean: 10, subgroupRange: 0.2 }),
    point(3, 2, 1, { measuredValue: 10.5 }),
    point(4, 2, 2, {
      measuredValue: 10.6,
      subgroupMean: 10.55,
      subgroupRange: 0.1,
      outOfControl: true,
      violationRule: 'NELSON_R1',
    }),
  ],
};

describe('SpcChartDetailComponent', () => {
  let fixture: ComponentFixture<SpcChartDetailComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'qm',
      fullName: 'Quality Manager',
      email: 'q@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(SpcChartDetailComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/spc/charts/3').flush(chart);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SpcChartDetailComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '3' }) } } },
      ],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('draws the X-bar and R charts from completed subgroups and marks the flagged mean', () => {
    setup(['QUALITY_VIEW']);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('CS-100');
    expect(el.textContent).toContain('X-bar chart');
    expect(el.textContent).toContain('R chart');
    expect(el.querySelectorAll('svg').length).toBe(2);
    // 2 completed subgroups on each chart; the NELSON rule marks one mean, the R chart none
    expect(el.querySelectorAll('circle.pt').length).toBe(4);
    expect(el.querySelectorAll('circle.pt.ooc').length).toBe(1);
    expect(el.textContent).toContain('1 out-of-control point');
  });

  it('lets only CAPA managers raise a CAPA from a flagged point', () => {
    setup(['QUALITY_VIEW']);
    expect((fixture.nativeElement.textContent as string).includes('Raise CAPA')).toBe(false);
  });

  it('raises a CAPA for the flagged point', () => {
    setup(['QUALITY_VIEW', 'CAPA_MANAGE']);
    expect((fixture.nativeElement.textContent as string).includes('Raise CAPA')).toBe(true);
    fixture.componentInstance.raiseCapa(chart.points[3]);
    const req = backend.expectOne('/api/capa/from-spc-point/4');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 12 });
  });

  it('parses the typed values and adds them to the chart', () => {
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT']);
    fixture.componentInstance.entry.controls.values.setValue('10.1, 10.2');
    fixture.componentInstance.addPoints();
    const req = backend.expectOne('/api/spc/charts/3/points');
    expect(req.request.body).toEqual({ values: [10.1, 10.2] });
    req.flush(chart);
  });

  it('refuses values that are not numbers without calling the server', () => {
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT']);
    fixture.componentInstance.entry.controls.values.setValue('10.1 oops');
    fixture.componentInstance.addPoints();
    backend.expectNone('/api/spc/charts/3/points');
  });

  it('asks for a recalculation only when the user may manage charts', () => {
    setup(['QUALITY_VIEW']);
    expect((fixture.nativeElement.textContent as string).includes('Recalculate limits')).toBe(false);
  });

  it('shows the capability indices returned for the period', () => {
    setup(['QUALITY_VIEW']);
    fixture.componentInstance.period.setValue({ from: '2026-10-01', to: '2026-10-04' });
    fixture.componentInstance.calculate();
    const req = backend.expectOne((r) => r.url === '/api/spc/charts/3/capability');
    expect(req.request.params.get('from')).toBe('2026-10-01');
    expect(req.request.params.get('to')).toBe('2026-10-04');
    req.flush({
      chartId: 3,
      from: '2026-10-01',
      to: '2026-10-04',
      cp: 1.414214,
      cpk: 1.178511,
      cpu: 1.178511,
      cpl: 1.414214,
      mean: 10,
      stdDev: 0.141421,
      count: 20,
    });
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('1.41');
    expect(text).toContain('1.18');
    expect(text).toContain('20');
  });
});
