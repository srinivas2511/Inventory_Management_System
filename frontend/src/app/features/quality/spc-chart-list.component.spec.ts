import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { SpcChartSummary } from './quality-plus.api';
import { SpcChartListComponent } from './spc-chart-list.component';

const chart: SpcChartSummary = {
  id: 3,
  productId: 9,
  productCode: 'CS-100',
  productName: 'Compression spring',
  parameterName: 'Wire diameter',
  chartType: 'XBAR_R',
  subgroupSize: 5,
  limitsEstablished: true,
  active: true,
  pointCount: 40,
  outOfControlCount: 2,
};

describe('SpcChartListComponent', () => {
  let fixture: ComponentFixture<SpcChartListComponent>;
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
    fixture = TestBed.createComponent(SpcChartListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/spc/charts').flush([chart]);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SpcChartListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists the charts with their out-of-control count and limit status', () => {
    setup(['QUALITY_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('CS-100');
    expect(text).toContain('Wire diameter');
    expect(text).toContain('XBAR_R');
    expect(text).toContain('Established');
    expect(fixture.nativeElement.querySelector('td.ooc')?.textContent).toContain('2');
  });

  it('hides the new-chart button from users who cannot manage charts', () => {
    setup(['QUALITY_VIEW']);
    expect((fixture.nativeElement.textContent as string).includes('New chart')).toBe(false);
  });

  it('offers the new-chart button to SPC managers and loads products when it is opened', () => {
    setup(['QUALITY_VIEW', 'SPC_MANAGE']);
    expect((fixture.nativeElement.textContent as string).includes('New chart')).toBe(true);
    fixture.componentInstance.toggleCreate();
    const req = backend.expectOne((r) => r.url.endsWith('/api/products'));
    expect(req.request.params.get('size')).toBe('200');
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 200, number: 0 });
  });

  it('sends the search text', () => {
    setup(['QUALITY_VIEW']);
    fixture.componentInstance.searchCtrl.setValue('wire');
    fixture.componentInstance.load();
    const req = backend.expectOne((r) => r.url === '/api/spc/charts');
    expect(req.request.params.get('q')).toBe('wire');
    req.flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No control charts found');
  });
});
