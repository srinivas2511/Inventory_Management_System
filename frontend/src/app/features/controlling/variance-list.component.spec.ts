import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { VarianceDto } from './controlling.api';
import { VarianceListComponent } from './variance-list.component';

const variance: VarianceDto = {
  id: 1,
  productionOrderId: 7,
  orderNumber: 'PO/26/00007',
  productId: 10,
  productCode: 'SPR-001',
  productName: 'Compression Spring',
  estimateId: 3,
  goodQty: 100,
  standardCost: 1522.5,
  actualMaterialCost: 765,
  actualLabourCost: 400,
  actualMachineCost: 400,
  actualOverheadCost: 80,
  actualCost: 1645,
  priceVariance: 25,
  quantityVariance: 70,
  efficiencyVariance: 25,
  otherVariance: 2.5,
  totalVariance: 122.5,
  variancePct: 8.05,
  status: 'POSTED',
  journalHeaderId: 55,
  calculatedAt: '2026-10-02T10:00:00Z',
};

describe('VarianceListComponent', () => {
  let fixture: ComponentFixture<VarianceListComponent>;
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
    fixture = TestBed.createComponent(VarianceListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VarianceListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('shows the variance split and flags an unfavourable total', () => {
    setup(['CONTROLLING_VIEW']);
    backend
      .expectOne((r) => r.url === '/api/controlling/variances')
      .flush({ content: [variance], totalElements: 1, totalPages: 1, size: 20, number: 0 });
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('PO/26/00007');
    expect(text).toContain('122.50');
    expect(text).toContain('8.1%');
    expect(fixture.nativeElement.querySelector('.unfavourable')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.favourable')).toBeFalsy();
  });

  it('shows the empty state', () => {
    setup(['CONTROLLING_VIEW']);
    backend
      .expectOne((r) => r.url === '/api/controlling/variances')
      .flush({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No variances calculated yet');
  });

  it('shows the calculate control only with CONTROLLING_MANAGE', () => {
    setup(['CONTROLLING_VIEW']);
    backend
      .expectOne((r) => r.url === '/api/controlling/variances')
      .flush({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Calculate and post');
  });
});
