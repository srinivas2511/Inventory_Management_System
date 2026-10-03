import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
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

  it('shows loading spinner on init', () => {
    expect(fixture.nativeElement.querySelector('mat-spinner')).toBeTruthy();
    backend.expectOne('/api/dashboard/kpis').flush(mockKpis);
  });

  it('renders KPI cards after data loads', () => {
    backend.expectOne('/api/dashboard/kpis').flush(mockKpis);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Inventory');
    expect(text).toContain('Production');
    expect(text).toContain('Quality');
    expect(text).toContain('Procurement');
    expect(text).toContain('Alerts');
  });

  it('shows target attainment only when a monthly production target is set', () => {
    backend.expectOne('/api/dashboard/kpis').flush(mockKpis);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent as string).toContain('Target attainment');
    expect(fixture.nativeElement.textContent as string).toContain('50.0%');
  });

  it('hides target attainment when no monthly production target is configured', () => {
    backend
      .expectOne('/api/dashboard/kpis')
      .flush({ ...mockKpis, production: { ...mockKpis.production, monthlyTarget: 0, targetAttainmentPct: 0 } });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent as string).not.toContain('Target attainment');
  });

  it('hides spinner on API error', () => {
    backend.expectOne('/api/dashboard/kpis').flush('error', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('mat-spinner')).toBeFalsy();
  });
});
