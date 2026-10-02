import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { CostEstimateDto } from './controlling.api';
import { ProductCostingListComponent } from './product-costing-list.component';

function estimate(id: number, status: CostEstimateDto['status']): CostEstimateDto {
  return {
    id,
    estimateNumber: `CE/26/0000${id}`,
    productId: 10,
    productCode: 'SPR-001',
    productName: 'Compression Spring',
    bomId: 5,
    bomNumber: 'BOM/26/00005',
    costCentreId: 1,
    status,
    estimatedAt: '2026-10-02T10:00:00Z',
    validFrom: status === 'RELEASED' ? '2026-10-02' : null,
    validTo: null,
    materialCost: 670,
    labourCost: 375,
    machineCost: 400,
    overheadCost: 77.5,
    totalStandardCost: 1522.5,
    costLotSize: 100,
    unitStandardCost: 15.225,
    releasedAt: null,
    lines: [],
    allowedActions: status === 'DRAFT' ? ['release'] : [],
  };
}

const page = {
  content: [estimate(1, 'DRAFT'), estimate(2, 'RELEASED')],
  totalElements: 2,
  totalPages: 1,
  size: 20,
  number: 0,
};

describe('ProductCostingListComponent', () => {
  let fixture: ComponentFixture<ProductCostingListComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'controller',
      fullName: 'Controller',
      email: 'c@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(ProductCostingListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/controlling/cost-estimates').flush(page);
    fixture.detectChanges();
  }

  function releaseButtons(): HTMLButtonElement[] {
    return Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button')).filter(
      (b) => b.textContent?.trim() === 'Release',
    );
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProductCostingListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists estimates with their standard cost', () => {
    setup(['CONTROLLING_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('CE/26/00001');
    expect(text).toContain('SPR-001');
    expect(text).toContain('1,522.50');
  });

  it('offers Release only on DRAFT estimates for users who may release', () => {
    setup(['CONTROLLING_VIEW', 'COST_ESTIMATE_RELEASE']);
    expect(releaseButtons().length).toBe(1);
  });

  it('hides Release without the COST_ESTIMATE_RELEASE permission', () => {
    setup(['CONTROLLING_VIEW', 'CONTROLLING_MANAGE']);
    expect(releaseButtons().length).toBe(0);
  });

  it('releases the estimate and reloads the list', () => {
    setup(['CONTROLLING_VIEW', 'COST_ESTIMATE_RELEASE']);
    releaseButtons()[0].click();
    backend
      .expectOne((r) => r.method === 'POST' && r.url === '/api/controlling/cost-estimates/1/release')
      .flush(estimate(1, 'RELEASED'));
    backend.expectOne((r) => r.method === 'GET' && r.url === '/api/controlling/cost-estimates').flush(page);
  });
});
