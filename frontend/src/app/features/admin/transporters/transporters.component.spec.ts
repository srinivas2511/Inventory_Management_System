import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { TransportersComponent } from './transporters.component';

describe('TransportersComponent', () => {
  let backend: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TransportersComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('should create', () => {
    const fixture = TestBed.createComponent(TransportersComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
    backend.expectOne('/api/admin/transporters').flush([]);
  });

  it('sends transporterCode and gstTransporterId (not code/gstin) on save — DEF-008 regression', () => {
    const fixture = TestBed.createComponent(TransportersComponent);
    fixture.detectChanges();
    backend.expectOne('/api/admin/transporters').flush([]);

    const comp = fixture.componentInstance;
    comp.startCreate();
    comp.form.setValue({ code: 'QA-TRP-001', name: 'QA Logistics Pvt Ltd', gstin: '29AAQCQ5344R1ZW', phone: '9876543210', email: null });
    comp.save();

    const req = backend.expectOne((r) => r.method === 'POST' && r.url === '/api/admin/transporters');
    expect(req.request.body['transporterCode']).toBe('QA-TRP-001');
    expect(req.request.body['gstTransporterId']).toBe('29AAQCQ5344R1ZW');
    expect(req.request.body['contact']).toBe('9876543210');
    expect(req.request.body['code']).toBeUndefined();
    expect(req.request.body['gstin']).toBeUndefined();
    req.flush({ id: 1, code: 'QA-TRP-001', transporterCode: 'QA-TRP-001', name: 'QA Logistics Pvt Ltd', gstin: '29AAQCQ5344R1ZW', gstTransporterId: '29AAQCQ5344R1ZW', contact: '9876543210', phone: null, email: null, active: true });
  });
});
