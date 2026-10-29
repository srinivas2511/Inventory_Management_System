import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { BiDashboardComponent } from './bi-dashboard.component';

describe('BiDashboardComponent', () => {
  let fixture: ComponentFixture<BiDashboardComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BiDashboardComponent, HttpClientTestingModule, NoopAnimationsModule],
    }).compileComponents();
    fixture = TestBed.createComponent(BiDashboardComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should create', () => {
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
    http.expectOne('/api/bi').flush([]);
  });

  it('should load areas on init', () => {
    fixture.detectChanges();
    const req = http.expectOne('/api/bi');
    req.flush(['sales', 'purchase']);
    fixture.detectChanges();
    expect(fixture.componentInstance.areas()).toEqual(['sales', 'purchase']);
    http.expectOne('/api/bi/sales').flush({ area: 'sales', totalCount: 5, totalAmount: 100, summary: {} });
  });

  it('should load area data on tab click', () => {
    fixture.detectChanges();
    http.expectOne('/api/bi').flush(['sales']);
    http.expectOne('/api/bi/sales').flush({ area: 'sales', totalCount: 10, totalAmount: 200, summary: {} });
    fixture.detectChanges();
    expect(fixture.componentInstance.areaData()['sales'].totalCount).toBe(10);
  });
});
