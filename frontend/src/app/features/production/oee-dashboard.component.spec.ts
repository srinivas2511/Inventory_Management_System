import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { OeeDashboardComponent } from './oee-dashboard.component';
import { DowntimeReasonDto, OeeDto, OeeLossesDto, OeeTrendPointDto } from './oee.api';

const machine: OeeDto = {
  machineId: 3,
  machineCode: 'CL-01',
  machineName: 'Coiler one',
  from: '2026-09-01',
  to: '2026-09-30',
  availableMinutes: 540,
  plannedDowntimeMinutes: 0,
  unplannedDowntimeMinutes: 45,
  operatingMinutes: 495,
  idealMinutes: 420,
  goodQty: 400,
  badQty: 20,
  availability: 0.9167,
  performance: 0.8485,
  quality: 0.9524,
  oee: 0.7407,
};

const plant: OeeDto = { ...machine, machineId: null, machineCode: 'ALL', machineName: 'All machines' };

const trend: OeeTrendPointDto[] = [
  {
    day: '2026-09-28',
    availableMinutes: 540,
    operatingMinutes: 495,
    goodQty: 400,
    badQty: 20,
    availability: 0.9167,
    performance: 0.8485,
    quality: 0.9524,
    oee: 0.7407,
  },
  {
    day: '2026-09-29',
    availableMinutes: 540,
    operatingMinutes: 540,
    goodQty: 500,
    badQty: 0,
    availability: 1,
    performance: 0.9259,
    quality: 1,
    oee: 0.9259,
  },
];

const losses: OeeLossesDto = {
  from: '2026-09-01',
  to: '2026-09-30',
  totalLossMinutes: 140,
  losses: [
    { category: 'SPEED_LOSS', label: 'Reduced speed', minutes: 75, share: 0.5357, cumulativeShare: 0.5357 },
    { category: 'BREAKDOWN', label: 'Breakdown', minutes: 30, share: 0.2143, cumulativeShare: 0.75 },
    { category: 'QUALITY_LOSS', label: 'Rejects and scrap', minutes: 20, share: 0.1429, cumulativeShare: 0.8929 },
    { category: 'SETUP_CHANGEOVER', label: 'Setup and changeover', minutes: 15, share: 0.1071, cumulativeShare: 1 },
  ],
};

const reasons: DowntimeReasonDto[] = [
  { reason: 'Spindle bearing', category: 'BREAKDOWN', occurrences: 2, minutes: 50 },
  { reason: 'Die change', category: 'SETUP_CHANGEOVER', occurrences: 1, minutes: 15 },
];

describe('OeeDashboardComponent', () => {
  let fixture: ComponentFixture<OeeDashboardComponent>;
  let backend: HttpTestingController;

  function flushAll(): void {
    backend.expectOne((r) => r.url === '/api/oee/summary').flush(plant);
    backend.expectOne((r) => r.url === '/api/oee/machines').flush([machine]);
    backend.expectOne((r) => r.url === '/api/oee/trend').flush(trend);
    backend.expectOne((r) => r.url === '/api/oee/losses').flush(losses);
    backend.expectOne((r) => r.url === '/api/oee/downtime-reasons').flush(reasons);
    fixture.detectChanges();
  }

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'pm',
      fullName: 'Production Manager',
      email: 'pm@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(OeeDashboardComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    flushAll();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OeeDashboardComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('shows the plant OEE and its three factors as percentages', () => {
    setup(['OEE_VIEW']);
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('[data-test="oee"]')?.textContent).toContain('74.1%');
    expect(el.querySelector('[data-test="availability"]')?.textContent).toContain('91.7%');
    expect(el.querySelector('[data-test="performance"]')?.textContent).toContain('84.9%');
    expect(el.querySelector('[data-test="quality"]')?.textContent).toContain('95.2%');
  });

  it('lists each machine with its downtime and output', () => {
    setup(['OEE_VIEW']);
    const text = (fixture.nativeElement as HTMLElement).querySelector('[data-test="machines"]')?.textContent ?? '';

    expect(text).toContain('CL-01 Coiler one');
    expect(text).toContain('74.1%');
    expect(text).toContain('45 unplanned');
    expect(text).toContain('400 / 20');
  });

  it('draws the trend with one point per day and the Pareto with one bar per loss', () => {
    setup(['OEE_VIEW']);
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('[data-test="trend-oee"]')?.getAttribute('d')).toMatch(/^M0,.* L640,/);
    expect(el.querySelectorAll('[data-test="pareto"] rect').length).toBe(4);
    const rows = el.querySelectorAll('[data-test="loss-table"] tr');
    expect(rows.length).toBe(5); // header + 4 losses
    expect(rows[1].textContent).toContain('Reduced speed');
    expect(rows[1].textContent).toContain('53.6%');
    expect(rows[4].textContent).toContain('100.0%');
  });

  it('ranks the downtime reasons', () => {
    setup(['OEE_VIEW']);
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-test="reasons"] tr.mat-mdc-row');

    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Spindle bearing');
    expect(rows[0].textContent).toContain('Breakdown');
    expect(rows[0].textContent).toContain('50');
  });

  it('reloads every figure for the chosen machine and range', () => {
    setup(['OEE_VIEW']);
    const component = fixture.componentInstance;
    component.fromCtrl.setValue('2026-09-01');
    component.toCtrl.setValue('2026-09-07');
    component.machineCtrl.setValue(3);

    component.load();

    const trendReq = backend.expectOne((r) => r.url === '/api/oee/trend');
    expect(trendReq.request.params.get('machineId')).toBe('3');
    expect(trendReq.request.params.get('from')).toBe('2026-09-01');
    expect(trendReq.request.params.get('to')).toBe('2026-09-07');
    trendReq.flush([]);
    backend.expectOne((r) => r.url === '/api/oee/summary').flush(plant);
    backend.expectOne((r) => r.url === '/api/oee/machines').flush([machine]);
    backend.expectOne((r) => r.url === '/api/oee/losses').flush({ ...losses, losses: [], totalLossMinutes: 0 });
    const reasonsReq = backend.expectOne((r) => r.url === '/api/oee/downtime-reasons');
    expect(reasonsReq.request.params.get('limit')).toBe('5');
    reasonsReq.flush([]);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No days in the range');
    expect(el.textContent).toContain('No losses recorded in the range');
    expect(el.textContent).toContain('No downtime in the range');
  });

  it('hides the downtime form from people who cannot record downtime', () => {
    setup(['OEE_VIEW']);

    expect((fixture.nativeElement as HTMLElement).querySelector('[data-test="downtime-form"]')).toBeNull();
  });

  it('records downtime with its start and end and refreshes the figures', () => {
    setup(['OEE_VIEW', 'DOWNTIME_RECORD']);
    const component = fixture.componentInstance;
    expect(component.canRecord()).toBe(false);
    component.dtMachine.setValue(3);
    component.dtCategory.setValue('MATERIAL_SHORTAGE');
    component.dtReason.setValue('Wire reel late');
    component.dtStart.setValue('2026-09-29T10:00');
    component.dtEnd.setValue('2026-09-29T10:40');
    expect(component.canRecord()).toBe(true);

    component.record();

    const post = backend.expectOne('/api/production/downtime');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({
      machineId: 3,
      reason: 'Wire reel late',
      category: 'MATERIAL_SHORTAGE',
      startedAt: new Date('2026-09-29T10:00').toISOString(),
      endedAt: new Date('2026-09-29T10:40').toISOString(),
    });
    post.flush({});
    flushAll();
    expect(component.dtReason.value).toBe('');
  });

  it('leaves the end out when the machine is still down', () => {
    setup(['OEE_VIEW', 'DOWNTIME_RECORD']);
    const component = fixture.componentInstance;
    component.dtMachine.setValue(3);
    component.dtReason.setValue('Motor trip');
    component.dtStart.setValue('2026-09-29T10:00');

    component.record();

    const post = backend.expectOne('/api/production/downtime');
    expect(post.request.body.endedAt).toBeNull();
    post.flush({});
    flushAll();
  });

  it('shows the server reason when downtime is refused', () => {
    setup(['OEE_VIEW', 'DOWNTIME_RECORD']);
    const component = fixture.componentInstance;
    component.dtMachine.setValue(3);
    component.dtReason.setValue('x');
    component.dtStart.setValue('2026-09-29T10:00');

    component.record();

    backend
      .expectOne('/api/production/downtime')
      .flush({ detail: 'Unknown downtime category' }, { status: 422, statusText: 'Unprocessable' });
    expect(component.dtReason.value).toBe('x'); // nothing was cleared
  });
});
