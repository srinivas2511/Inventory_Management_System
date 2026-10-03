import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { DemandForecastDto, Page } from './mrp.api';
import { DemandForecastListComponent } from './demand-forecast-list.component';

const forecast: DemandForecastDto = {
  id: 4,
  productId: 3,
  productCode: 'CS-100',
  productName: 'Compression spring',
  targetDate: '2026-11-15',
  quantity: 400,
  notes: 'Festival season',
};

const page: Page<DemandForecastDto> = { content: [forecast], totalElements: 1, totalPages: 1, size: 50, number: 0 };

describe('DemandForecastListComponent', () => {
  let fixture: ComponentFixture<DemandForecastListComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'planner',
      fullName: 'Planner',
      email: 'p@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(DemandForecastListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/mrp/forecasts').flush(page);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DemandForecastListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists forecasts without edit controls for read-only users', () => {
    setup(['MRP_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('CS-100');
    expect(text).toContain('Festival season');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    expect(text).not.toContain('Delete');
  });

  it('adds a forecast for planners and reloads', () => {
    setup(['MRP_VIEW', 'DEMAND_PLAN']);
    expect(fixture.nativeElement.querySelector('form')).toBeTruthy();
    fixture.componentInstance.form.patchValue({ productId: 3, targetDate: '2026-12-01', quantity: 250, notes: '' });
    fixture.componentInstance.add();

    const req = backend.expectOne((r) => r.url === '/api/mrp/forecasts' && r.method === 'POST');
    expect(req.request.body).toEqual({ productId: 3, targetDate: '2026-12-01', quantity: 250, notes: null });
    req.flush(forecast);
    backend.expectOne((r) => r.url === '/api/mrp/forecasts' && r.method === 'GET').flush(page);
  });
});
