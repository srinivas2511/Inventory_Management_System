import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { LifecycleListComponent } from './lifecycle-list.component';
import { Page, ProductLifecycleDto, ProductLifecycleRowDto } from './plm.api';

const row: ProductLifecycleRowDto = {
  productId: 40,
  productCode: 'PRD-40',
  productName: 'Compression spring 4mm',
  lifecycleStatus: 'ACTIVE',
  nextStatus: 'PHASE_OUT',
};
const page: Page<ProductLifecycleRowDto> = { content: [row], totalElements: 1, totalPages: 1, size: 50, number: 0 };

const detail: ProductLifecycleDto = {
  productId: 40,
  productCode: 'PRD-40',
  productName: 'Compression spring 4mm',
  lifecycleStatus: 'ACTIVE',
  nextStatus: 'PHASE_OUT',
  history: [
    {
      id: 1,
      productId: 40,
      fromStatus: 'APPROVED',
      toStatus: 'ACTIVE',
      ecoId: null,
      changedBy: 2,
      changedAt: '2026-09-01T10:00:00Z',
      reason: 'Released to production',
    },
  ],
};

describe('LifecycleListComponent', () => {
  let fixture: ComponentFixture<LifecycleListComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'eng',
      fullName: 'Engineer',
      email: 'e@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(LifecycleListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/product-lifecycle').flush(page);
    fixture.detectChanges();
  }

  function selectRow(): void {
    fixture.componentInstance.select(row);
    backend.expectOne('/api/product-lifecycle/40').flush(detail);
    fixture.detectChanges();
  }

  function button(text: string): HTMLButtonElement | undefined {
    return Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find((b) =>
      (b.textContent ?? '').includes(text),
    );
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LifecycleListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists products with their lifecycle state and next step', () => {
    setup(['PLM_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('PRD-40');
    expect(text).toContain('Compression spring 4mm');
    expect(text).toContain('PHASE_OUT');
  });

  it('shows history to a viewer but no transition control', () => {
    setup(['PLM_VIEW']);
    selectRow();
    expect(fixture.nativeElement.textContent).toContain('Released to production');
    expect(button('Move to')).toBeUndefined();
  });

  it('requires a reason and posts the next step for a lifecycle manager', () => {
    setup(['PLM_VIEW', 'LIFECYCLE_MANAGE']);
    selectRow();
    expect(button('Move to PHASE_OUT')!.disabled).toBe(true);

    fixture.componentInstance.reasonCtrl.setValue('Replaced by new design');
    fixture.detectChanges();
    button('Move to PHASE_OUT')!.click();

    const req = backend.expectOne('/api/product-lifecycle/40/transition');
    expect(req.request.body).toEqual({ target: 'PHASE_OUT', reason: 'Replaced by new design' });
    req.flush({ ...detail, lifecycleStatus: 'PHASE_OUT', nextStatus: 'OBSOLETE' });
    backend.expectOne((r) => r.url === '/api/product-lifecycle').flush(page);
    fixture.detectChanges();
    expect(button('Move to OBSOLETE')).toBeDefined();
  });
});
