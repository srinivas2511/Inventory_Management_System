import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { AnalyticsExplorerComponent } from './analytics-explorer.component';
import { QueryDescriptor, QueryResult } from './analytics.api';

const catalogue: QueryDescriptor[] = [
  {
    key: 'PRODUCTION_MONTHLY',
    title: 'Production by month',
    description: 'Production orders, planned and good quantity and yield per month.',
    category: 'Production',
    chart: 'BAR',
    xColumn: 'month',
    yColumns: ['planned_qty', 'good_qty'],
    monthly: true,
    columns: [
      { name: 'month', label: 'Month', type: 'DATE' },
      { name: 'planned_qty', label: 'Planned qty', type: 'NUMBER' },
      { name: 'good_qty', label: 'Good qty', type: 'NUMBER' },
      { name: 'yield_pct', label: 'Yield %', type: 'PERCENT' },
    ],
  },
];

const result: QueryResult = {
  key: 'PRODUCTION_MONTHLY',
  title: 'Production by month',
  from: '2025-11-01',
  to: '2026-10-05',
  columns: catalogue[0].columns,
  rows: [
    { month: '2026-08-01', planned_qty: 1000, good_qty: 950, yield_pct: 95 },
    { month: '2026-09-01', planned_qty: 1200, good_qty: 1020, yield_pct: 85 },
  ],
  rowCount: 2,
  truncated: false,
  generatedAt: '2026-10-05T08:00:00Z',
};

describe('AnalyticsExplorerComponent', () => {
  let fixture: ComponentFixture<AnalyticsExplorerComponent>;
  let backend: HttpTestingController;

  const q = (id: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
  const all = (id: string): HTMLElement[] =>
    Array.from(fixture.nativeElement.querySelectorAll(`[data-testid="${id}"]`));

  function setup(permissions: string[] = ['ANALYTICS_VIEW']): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'u',
      fullName: 'U',
      email: 'u@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(AnalyticsExplorerComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/analytics/catalogue').flush(catalogue);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnalyticsExplorerComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  function run(): void {
    (q('run') as HTMLButtonElement).click();
    backend
      .expectOne((r) => r.url === '/api/analytics/query' && r.params.get('key') === 'PRODUCTION_MONTHLY')
      .flush(result);
    fixture.detectChanges();
  }

  it('selects the first query, takes its default chart and shows its description', () => {
    setup();
    const c = fixture.componentInstance;
    expect(c.queryCtrl.value).toBe('PRODUCTION_MONTHLY');
    expect(c.chartType()).toBe('BAR');
    expect(c.xColumn()).toBe('month');
    expect(c.yColumns()).toEqual(['planned_qty', 'good_qty']);
    expect(fixture.nativeElement.textContent).toContain('planned and good quantity');
  });

  it('runs the query with the chosen period and shows the table and the chart', () => {
    setup();
    fixture.componentInstance.from.setValue('2025-11-01');
    fixture.componentInstance.to.setValue('2026-10-05');
    (q('run') as HTMLButtonElement).click();
    const req = backend.expectOne((r) => r.url === '/api/analytics/query');
    expect(req.request.params.get('key')).toBe('PRODUCTION_MONTHLY');
    expect(req.request.params.get('from')).toBe('2025-11-01');
    expect(req.request.params.get('to')).toBe('2026-10-05');
    expect(req.request.params.has('limit')).toBeFalse();
    req.flush(result);
    fixture.detectChanges();

    expect(all('row')).toHaveSize(2);
    expect(all('row')[0].textContent).toContain('2026-08-01');
    expect(all('row')[0].textContent).toContain('1,000');
    expect(all('row')[0].textContent).toContain('95.0 %');
    expect(all('bar')).toHaveSize(4); // two rows, two value columns
    expect(fixture.nativeElement.textContent).toContain('2 rows from 2025-11-01 to 2026-10-05');
  });

  it('lets the user hide a column and switch to a line chart', () => {
    setup();
    run();
    const c = fixture.componentInstance;
    c.toggleColumn('yield_pct', false);
    fixture.detectChanges();
    expect(c.visibleNames()).toEqual(['month', 'planned_qty', 'good_qty']);
    expect(all('row')[0].textContent).not.toContain('95.0 %');

    c.chartType.set('LINE');
    fixture.detectChanges();
    expect(all('line')).toHaveSize(2);
    expect(all('bar')).toHaveSize(0);

    c.chartType.set('TABLE');
    fixture.detectChanges();
    expect(all('line')).toHaveSize(0);
  });

  it('offers only numeric columns as chart values', () => {
    setup();
    run();
    expect(fixture.componentInstance.numeric().map((x) => x.name)).toEqual(['planned_qty', 'good_qty', 'yield_pct']);
  });

  it('downloads what is on screen as CSV', () => {
    setup();
    run();
    const created: Blob[] = [];
    spyOn(URL, 'createObjectURL').and.callFake((b: Blob | MediaSource) => {
      created.push(b as Blob);
      return 'blob:x';
    });
    spyOn(URL, 'revokeObjectURL');
    fixture.componentInstance.toggleColumn('yield_pct', false);
    (q('download') as HTMLButtonElement).click();
    expect(created).toHaveSize(1);
    expect(created[0].type).toContain('text/csv');
    expect(created[0].size).toBeGreaterThan(0);
  });

  it('hides the refresh button from someone who may not manage analytics', () => {
    setup(['ANALYTICS_VIEW']);
    expect(q('refresh')).toBeNull();
  });

  it('refreshes the analytics data for a manager', () => {
    setup(['ANALYTICS_VIEW', 'ANALYTICS_MANAGE']);
    (q('refresh') as HTMLButtonElement).click();
    backend.expectOne('/api/analytics/refresh').flush({ refreshed: ['mv_production_monthly'], failed: {} });
  });

  it('tells the user when a query is not allowed', () => {
    setup();
    (q('run') as HTMLButtonElement).click();
    backend
      .expectOne((r) => r.url === '/api/analytics/query')
      .flush({ title: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(fixture.componentInstance.loading()).toBeFalse();
    expect(fixture.componentInstance.result()).toBeNull();
  });
});
