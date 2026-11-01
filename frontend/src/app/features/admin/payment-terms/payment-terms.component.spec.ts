import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { PaymentTermsComponent } from './payment-terms.component';

describe('PaymentTermsComponent', () => {
  let backend: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaymentTermsComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('should create', () => {
    const fixture = TestBed.createComponent(PaymentTermsComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
    backend.expectOne('/api/admin/payment-terms').flush([]);
  });

  it('sends termCode and netDays (not code/dueDays) on save — DEF-007 regression', () => {
    const fixture = TestBed.createComponent(PaymentTermsComponent);
    fixture.detectChanges();
    backend.expectOne('/api/admin/payment-terms').flush([]);

    const comp = fixture.componentInstance;
    comp.startCreate();
    comp.form.setValue({ code: 'QA-60', name: 'Net 60', dueDays: 60, discountPct: null, discountDays: null });
    comp.save();

    const req = backend.expectOne((r) => r.method === 'POST' && r.url === '/api/admin/payment-terms');
    expect(req.request.body['termCode']).toBe('QA-60');
    expect(req.request.body['netDays']).toBe(60);
    expect(req.request.body['code']).toBeUndefined();
    expect(req.request.body['dueDays']).toBeUndefined();
    req.flush({ id: 99, termCode: 'QA-60', code: 'QA-60', name: 'Net 60', netDays: 60, dueDays: 60, discountPct: null, discountDays: null, active: true });
  });
});
