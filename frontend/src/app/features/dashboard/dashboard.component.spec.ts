import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DashboardComponent } from './dashboard.component';

const mockKpis = {
  inventory: { itemsWithStock: 12, totalValueInr: 500000 },
  production: {
    ordersInProgress: 3,
    plannedQtyMonth: 1000,
    goodQtyMonth: 950,
    yieldPct: 95.0,
    monthlyTarget: 1900,
    targetAttainmentPct: 50.0,
  },
  quality: { pendingInspections: 2, passRateMonth: 97.5 },
  procurement: { openPOs: 5, overduePOs: 1 },
  sales: { openOrders: 8, dispatchedThisMonth: 4 },
  alerts: { critical: 0, high: 1, medium: 2, low: 0, total: 3 },
};

const mockTrends = {
  production: [
    { month: '2026-08-01', goodQty: 400, rejectedQty: 12 },
    { month: '2026-09-01', goodQty: 520, rejectedQty: 8 },
    { month: '2026-10-01', goodQty: 95, rejectedQty: 5 },
  ],
  inventoryValue: [
    { month: '2026-08-01', closingValue: 120000 },
    { month: '2026-09-01', closingValue: 310000 },
    { month: '2026-10-01', closingValue: 500000 },
  ],
};

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let backend: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(DashboardComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  function trendsRequest(): TestRequest {
    return backend.expectOne((r) => r.url === '/api/dashboard/trends');
  }

  function loadAll(kpis: object = mockKpis, trends: object = mockTrends): void {
    backend.expectOne('/api/dashboard/kpis').flush(kpis);
    trendsRequest().flush(trends);
    fixture.detectChanges();
  }

  it('shows loading spinner on init', () => {
    expect(fixture.nativeElement.querySelector('mat-spinner')).toBeTruthy();
    loadAll();
  });

  it('renders KPI cards after data loads', () => {
    loadAll();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Inventory');
    expect(text).toContain('Production');
    expect(text).toContain('Quality');
    expect(text).toContain('Procurement');
    expect(text).toContain('Alerts');
  });

  it('asks for six months of trends and renders the production and inventory value charts (INS-01, INS-02)', () => {
    backend.expectOne('/api/dashboard/kpis').flush(mockKpis);
    const req = trendsRequest();
    expect(req.request.params.get('months')).toBe('6');
    req.flush(mockTrends);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Production output by month');
    expect(text).toContain('Inventory value (INR)');
    expect(fixture.nativeElement.querySelectorAll('app-analytics-chart svg').length).toBe(2);
    expect(text).not.toMatch(/NaN|undefined|\[object/);
  });

  it('shows the KPI cards but no charts when the trends cannot be loaded', () => {
    backend.expectOne('/api/dashboard/kpis').flush(mockKpis);
    trendsRequest().flush('error', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Inventory');
    expect(text).not.toContain('Production output by month');
    expect(fixture.nativeElement.querySelectorAll('app-analytics-chart').length).toBe(0);
  });

  it('shows target attainment only when a monthly production target is set', () => {
    loadAll();
    expect(fixture.nativeElement.textContent as string).toContain('Target attainment');
    expect(fixture.nativeElement.textContent as string).toContain('50.0%');
  });

  it('hides target attainment when no monthly production target is configured', () => {
    loadAll({ ...mockKpis, production: { ...mockKpis.production, monthlyTarget: 0, targetAttainmentPct: 0 } });
    expect(fixture.nativeElement.textContent as string).not.toContain('Target attainment');
  });

  it('hides spinner on API error', () => {
    backend.expectOne('/api/dashboard/kpis').flush('error', { status: 500, statusText: 'Server Error' });
    trendsRequest().flush(mockTrends);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('mat-spinner')).toBeFalsy();
  });
});
