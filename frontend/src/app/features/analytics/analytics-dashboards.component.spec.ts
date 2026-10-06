import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { AnalyticsDashboardsComponent, PANELS } from './analytics-dashboards.component';
import { QueryDescriptor, QueryResult } from './analytics.api';

function descriptor(key: string): QueryDescriptor {
  return {
    key,
    title: key,
    description: '',
    category: 'x',
    chart: 'BAR',
    xColumn: 'month',
    yColumns: [],
    monthly: true,
    columns: [],
  };
}

function result(key: string): QueryResult {
  return {
    key,
    title: key,
    from: '2025-11-01',
    to: '2026-10-05',
    columns: [
      { name: 'month', label: 'Month', type: 'DATE' },
      { name: 'planned_qty', label: 'Planned', type: 'NUMBER' },
      { name: 'good_qty', label: 'Good', type: 'NUMBER' },
    ],
    rows: [
      { month: '2026-08-01', planned_qty: 100, good_qty: 90 },
      { month: '2026-09-01', planned_qty: 120, good_qty: 110 },
    ],
    rowCount: 2,
    truncated: false,
    generatedAt: '2026-10-05T08:00:00Z',
  };
}

describe('AnalyticsDashboardsComponent', () => {
  let fixture: ComponentFixture<AnalyticsDashboardsComponent>;
  let backend: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnalyticsDashboardsComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(AnalyticsDashboardsComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  const panels = (): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll('[data-testid="panel"]'));

  it('shows only the panels whose query the user may run', () => {
    backend
      .expectOne('/api/analytics/catalogue')
      .flush([descriptor('PRODUCTION_MONTHLY'), descriptor('INVENTORY_TURNS')]);
    fixture.detectChanges();
    expect(panels()).toHaveSize(2);
    expect(panels()[0].textContent).toContain('Production: planned against good output');
    expect(fixture.nativeElement.textContent).not.toContain('Financial summary');

    backend.expectOne((r) => r.params.get('key') === 'PRODUCTION_MONTHLY').flush(result('PRODUCTION_MONTHLY'));
    backend.expectOne((r) => r.params.get('key') === 'INVENTORY_TURNS').flush(result('INVENTORY_TURNS'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('[data-testid="bar"]')).toHaveSize(4);
    expect(fixture.nativeElement.querySelectorAll('[data-testid="line"]')).toHaveSize(1);
  });

  it('asks each query for a twelve month window ending today', () => {
    backend.expectOne('/api/analytics/catalogue').flush([descriptor('PRODUCTION_MONTHLY')]);
    const req = backend.expectOne((r) => r.params.get('key') === 'PRODUCTION_MONTHLY');
    const from = req.request.params.get('from')!;
    const to = req.request.params.get('to')!;
    expect(from).toMatch(/^\d{4}-\d{2}-01$/);
    expect(to).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(new Date(to).getTime() - new Date(from).getTime()).toBeGreaterThan(330 * 86_400_000);
    expect(req.request.params.get('limit')).toBe('500');
    req.flush(result('PRODUCTION_MONTHLY'));
  });

  it('shows a failed panel without breaking the others', () => {
    backend
      .expectOne('/api/analytics/catalogue')
      .flush([descriptor('PRODUCTION_MONTHLY'), descriptor('INVENTORY_TURNS')]);
    backend
      .expectOne((r) => r.params.get('key') === 'PRODUCTION_MONTHLY')
      .flush({}, { status: 500, statusText: 'Server Error' });
    backend.expectOne((r) => r.params.get('key') === 'INVENTORY_TURNS').flush(result('INVENTORY_TURNS'));
    fixture.detectChanges();
    expect(panels()[0].textContent).toContain('Could not load this panel');
    expect(panels()[1].querySelectorAll('[data-testid="line"]')).toHaveSize(1);
  });

  it('says so when the user has no analytics at all', () => {
    backend.expectOne('/api/analytics/catalogue').flush([]);
    fixture.detectChanges();
    expect(panels()).toHaveSize(0);
    expect(fixture.nativeElement.textContent).toContain('You have no analytics available');
  });

  it('defines a panel for every analytical dashboard of ANA-002', () => {
    backend.expectOne('/api/analytics/catalogue').flush([]);
    const keys = PANELS.map((p) => p.key);
    expect(keys).toEqual(
      jasmine.arrayContaining([
        'OEE_DAILY',
        'INVENTORY_TURNS',
        'SUPPLIER_SCORECARD',
        'CUSTOMER_PROFITABILITY',
        'COST_VARIANCE',
      ]),
    );
  });
});
