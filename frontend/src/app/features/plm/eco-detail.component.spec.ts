import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { EcoDetailComponent } from './eco-detail.component';
import { EcoDto } from './plm.api';

function eco(overrides: Partial<EcoDto>): EcoDto {
  return {
    id: 5,
    ecoNumber: 'ECO/26/00005',
    title: 'Switch to music wire grade B',
    description: null,
    reason: 'Supplier discontinued grade A',
    changeCategory: 'BOM_CHANGE',
    urgency: 'HIGH',
    effectivityDate: '2026-11-01',
    impactAssessment: null,
    status: 'DRAFT',
    initiatedBy: 2,
    approvedBy: null,
    approvedAt: null,
    implementedAt: null,
    statusReason: null,
    createdAt: '2026-10-01T08:00:00Z',
    items: [
      {
        id: 11,
        itemType: 'BOM',
        productId: 40,
        productCode: 'PRD-40',
        productName: 'Compression spring 4mm',
        oldBomId: null,
        oldBomLabel: null,
        newBomId: null,
        newBomLabel: null,
        changeDescription: 'Use grade B wire',
      },
    ],
    allowedActions: [],
    ...overrides,
  };
}

describe('EcoDetailComponent', () => {
  let fixture: ComponentFixture<EcoDetailComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[], dto: EcoDto, editable = false): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'eng',
      fullName: 'Engineer',
      email: 'e@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(EcoDetailComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/ecos/5').flush(dto);
    if (editable) {
      backend
        .expectOne((r) => r.url === '/api/product-lifecycle')
        .flush({ content: [], totalElements: 0, totalPages: 0, size: 200, number: 0 });
    }
    fixture.detectChanges();
  }

  function button(text: string): HTMLButtonElement | undefined {
    return Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find((b) =>
      (b.textContent ?? '').includes(text),
    );
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EcoDetailComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '5' }) } } },
      ],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('shows an approver the approve and return actions but not implement', () => {
    setup(['PLM_VIEW', 'ECO_APPROVE'], eco({ status: 'REVIEW', allowedActions: ['approve', 'return', 'cancel'] }));
    expect(button('Approve')).toBeDefined();
    expect(button('Return to draft')).toBeDefined();
    expect(button('Implement')).toBeUndefined();
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('keeps approve hidden from an engineer without the approval permission', () => {
    setup(['PLM_VIEW', 'ECO_CREATE'], eco({ status: 'REVIEW', allowedActions: ['approve', 'return', 'cancel'] }));
    expect(button('Approve')).toBeUndefined();
    expect(button('Return to draft')).toBeUndefined();
  });

  it('implements an approved ECO and shows the new draft BOM revision', () => {
    setup(['PLM_VIEW', 'ECO_CREATE'], eco({ status: 'APPROVED', allowedActions: ['implement', 'cancel'] }));
    button('Implement')!.click();
    const req = backend.expectOne('/api/ecos/5/implement');
    expect(req.request.method).toBe('POST');
    const done = eco({ status: 'IMPLEMENTED', implementedAt: '2026-10-02T09:00:00Z', allowedActions: [] });
    done.items[0].oldBomId = 1;
    done.items[0].oldBomLabel = 'BOM/26/00001 rev A';
    done.items[0].newBomId = 2;
    done.items[0].newBomLabel = 'BOM/26/00001 rev B';
    req.flush(done);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('BOM/26/00001 rev A');
    expect(text).toContain('BOM/26/00001 rev B');
    expect(button('Implement')).toBeUndefined();
  });

  it('asks for a reason before returning an ECO to draft', () => {
    setup(['PLM_VIEW', 'ECO_APPROVE'], eco({ status: 'REVIEW', allowedActions: ['approve', 'return', 'cancel'] }));
    button('Return to draft')!.click();
    fixture.detectChanges();
    expect(button('Confirm')!.disabled).toBe(true);

    fixture.componentInstance.reasonCtrl.setValue('Impact assessment missing');
    fixture.detectChanges();
    button('Confirm')!.click();
    const req = backend.expectOne('/api/ecos/5/return');
    expect(req.request.body).toEqual({ reason: 'Impact assessment missing' });
    req.flush(eco({ status: 'DRAFT', statusReason: 'Impact assessment missing', allowedActions: ['edit', 'submit'] }));
  });

  it('lets an engineer edit a draft and saves the affected items', () => {
    setup(['PLM_VIEW', 'ECO_CREATE'], eco({ allowedActions: ['edit', 'submit', 'cancel'] }), true);
    expect(fixture.nativeElement.querySelector('form')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('PRD-40');

    fixture.componentInstance.form.controls.title.setValue('Switch to music wire grade C');
    fixture.componentInstance.save();
    const req = backend.expectOne('/api/ecos/5');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.title).toBe('Switch to music wire grade C');
    expect(req.request.body.items).toEqual([{ itemType: 'BOM', productId: 40, changeDescription: 'Use grade B wire' }]);
    req.flush(eco({ title: 'Switch to music wire grade C', allowedActions: ['edit', 'submit', 'cancel'] }));
  });

  it('blocks submitting while there are unsaved changes', () => {
    setup(['PLM_VIEW', 'ECO_CREATE'], eco({ allowedActions: ['edit', 'submit', 'cancel'] }), true);
    expect(button('Submit for review')!.disabled).toBe(false);
    fixture.componentInstance.removeItem(0);
    fixture.detectChanges();
    expect(button('Submit for review')!.disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Save your changes before submitting.');
  });
});
