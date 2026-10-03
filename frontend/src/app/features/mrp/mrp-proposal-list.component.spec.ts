import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { MrpProposalDto, Page } from './mrp.api';
import { MrpProposalListComponent } from './mrp-proposal-list.component';

const base: MrpProposalDto = {
  id: 1,
  mrpRunId: 7,
  proposalType: 'PURCHASE',
  materialId: 10,
  materialCode: 'RM-SS-001',
  materialName: 'Spring steel wire',
  productId: null,
  productCode: null,
  productName: null,
  bomId: null,
  uom: 'KG',
  requiredQty: 55,
  proposedQty: 55,
  needDate: '2026-11-01',
  proposedOrderDate: '2026-10-25',
  leadTimeDays: 7,
  leadTimeAdjusted: false,
  sourceDemandType: 'BOM_EXPLOSION',
  sourceDemandId: null,
  lotSizeRule: 'EXACT',
  machineHoursRequired: null,
  capacityOverload: false,
  status: 'OPEN',
  convertedDocumentType: null,
  convertedDocumentId: null,
  discardReason: null,
};

const production: MrpProposalDto = {
  ...base,
  id: 2,
  proposalType: 'PRODUCTION',
  materialId: null,
  materialCode: null,
  materialName: null,
  productId: 3,
  productCode: 'CS-100',
  productName: 'Compression spring',
  bomId: 5,
  uom: 'PCS',
  requiredQty: 100,
  proposedQty: 120,
  sourceDemandType: 'SALES_ORDER',
  machineHoursRequired: 90,
  capacityOverload: true,
};

const page: Page<MrpProposalDto> = {
  content: [base, production],
  totalElements: 2,
  totalPages: 1,
  size: 50,
  number: 0,
};

describe('MrpProposalListComponent', () => {
  let fixture: ComponentFixture<MrpProposalListComponent>;
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
    fixture = TestBed.createComponent(MrpProposalListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/mrp/proposals').flush(page);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MrpProposalListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  function buttonLabels(): string[] {
    return (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]).map(
      (b) => b.textContent?.trim() ?? '',
    );
  }

  it('shows purchase and production proposals and flags capacity overloads', () => {
    setup(['MRP_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('RM-SS-001');
    expect(text).toContain('CS-100');
    expect(text).toContain('need 100');
    expect(fixture.nativeElement.querySelector('mat-icon.warn')).toBeTruthy();
  });

  it('offers only the actions the user is permitted', () => {
    setup(['MRP_VIEW', 'PURCHASE_PROPOSAL']);
    const labels = buttonLabels();
    expect(labels).toContain('Create PR');
    expect(labels).not.toContain('Create order');
    expect(labels).not.toContain('Discard');
  });

  it('creates a requisition from an open purchase proposal and reloads', () => {
    setup(['MRP_VIEW', 'PURCHASE_PROPOSAL']);
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])
      .find((b) => b.textContent?.includes('Create PR'))
      ?.click();

    backend.expectOne('/api/mrp/proposals/1/convert-to-pr').flush({ ...base, status: 'CONVERTED' });
    backend.expectOne((r) => r.url === '/api/mrp/proposals' && r.method === 'GET').flush(page);
  });

  it('passes the filters to the API', () => {
    setup(['MRP_VIEW']);
    fixture.componentInstance.filters.patchValue({ runId: 7, type: 'PURCHASE', status: 'OPEN', overload: 'true' });
    fixture.componentInstance.reload();
    const req = backend.expectOne((r) => r.url === '/api/mrp/proposals');
    expect(req.request.params.get('runId')).toBe('7');
    expect(req.request.params.get('type')).toBe('PURCHASE');
    expect(req.request.params.get('status')).toBe('OPEN');
    expect(req.request.params.get('capacityOverload')).toBe('true');
    req.flush(page);
  });
});
