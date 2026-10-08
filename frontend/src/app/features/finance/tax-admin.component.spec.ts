/* eslint-disable @typescript-eslint/no-explicit-any */
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { TaxAdminComponent } from './tax-admin.component';

const slabs = [
  {
    slabCode: 'GST18',
    description: '18% goods',
    components: [
      { supplyType: 'INTRA', taxCode: 'CGST9', taxType: 'CGST', ratePct: 9 },
      { supplyType: 'INTRA', taxCode: 'SGST9', taxType: 'SGST', ratePct: 9 },
      { supplyType: 'INTER', taxCode: 'IGST18', taxType: 'IGST', ratePct: 18 },
    ],
  },
];

describe('TaxAdminComponent', () => {
  let fixture: ComponentFixture<TaxAdminComponent>;
  let backend: HttpTestingController;
  let instance: Record<string, any>;

  function setup(permissions: string[]): void {
    TestBed.configureTestingModule({
      imports: [TaxAdminComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'fin',
      fullName: 'Finance',
      email: 'f@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(TaxAdminComponent);
    backend = TestBed.inject(HttpTestingController);
    instance = fixture.componentInstance as unknown as Record<string, any>;
    fixture.detectChanges();
    backend.expectOne('/api/finance/tax/slabs').flush(slabs);
    backend.expectOne('/api/lookups/customers').flush([{ id: 4, code: 'C4', name: 'Spring Works' }]);
    if (permissions.includes('TAX_MANAGE')) {
      backend.expectOne('/api/lookups/products').flush([{ id: 12, code: 'P12', name: 'Coil spring' }]);
    }
    fixture.detectChanges();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => backend.verify());

  it('shows what each slab charges for each kind of supply', () => {
    setup(['GL_VIEW']);

    expect(text()).toContain('GST18');
    expect(text()).toContain('CGST 9% + SGST 9%');
    expect(text()).toContain('IGST 18%');
    expect(text()).toContain('sample data');
    expect(text()).not.toContain('Slab of a product');
    backend.expectNone('/api/lookups/products');
  });

  it('previews 18% on 20,000 for a customer and explains the supply type', () => {
    setup(['GL_VIEW']);
    instance['preview'].patchValue({ direction: 'SALES', partnerId: 4, slab: 'GST18', amount: 20000 });

    instance['runPreview']();

    const call = backend.expectOne((r) => r.url === '/api/finance/tax/determine');
    expect(call.request.params.get('direction')).toBe('SALES');
    expect(call.request.params.get('partnerId')).toBe('4');
    expect(call.request.params.get('amount')).toBe('20000');
    call.flush({
      slabCode: 'GST18',
      supplyType: 'INTRA',
      companyStateCode: '27',
      partnerStateCode: null,
      partnerStateAssumed: true,
      taxableAmount: 20000,
      taxTotal: 3600,
      lines: [
        { taxCode: 'CGST9', taxType: 'CGST', ratePct: 9, taxableAmount: 20000, taxAmount: 1800 },
        { taxCode: 'SGST9', taxType: 'SGST', ratePct: 9, taxableAmount: 20000, taxAmount: 1800 },
      ],
    });
    fixture.detectChanges();

    const result = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="tax-result"]');
    expect(result?.textContent).toContain('Within the company state: CGST + SGST');
    expect(result?.textContent).toContain('assumed to be the company state');
    expect(result?.textContent).toContain('Tax 3,600.00');
  });

  it('lets a TAX_MANAGE user set and clear the slab of a product', () => {
    setup(['GL_VIEW', 'TAX_MANAGE']);
    expect(text()).toContain('Slab of a product');
    instance['assign'].patchValue({ productId: 12 });

    instance['loadAssignment']();
    backend.expectOne('/api/finance/tax/product-slabs/12').flush({ productId: 12, slabCode: 'GST18' });
    expect(instance['assign'].controls.slabCode.value).toBe('GST18');

    instance['assign'].patchValue({ slabCode: null });
    instance['saveAssignment']();
    const put = backend.expectOne('/api/finance/tax/product-slabs/12');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ slabCode: null });
    put.flush({ productId: 12, slabCode: null });
  });
});
