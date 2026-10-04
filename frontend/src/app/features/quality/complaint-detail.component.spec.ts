import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { ComplaintDetailComponent } from './complaint-detail.component';
import { Complaint } from './quality-plus.api';

function complaint(overrides: Partial<Complaint>): Complaint {
  return {
    id: 6,
    complaintNumber: 'CMP/26/00006',
    customerId: 2,
    customerName: 'Acme Auto',
    productId: 9,
    productCode: 'CS-100',
    productName: 'Compression spring',
    fgBatchId: 31,
    fgBatchNumber: 'FG-2026-0042',
    complaintDate: '2026-10-03',
    receivedBy: 1,
    category: 'DIMENSIONAL',
    description: 'Free length short by 1 mm',
    customerRef: 'REF-7',
    quantityComplained: 40,
    quantityReturned: null,
    disposition: null,
    resolutionNotes: null,
    status: 'IN_PROGRESS',
    nextStatuses: ['RESOLVED'],
    capaId: null,
    capaNumber: null,
    closedAt: null,
    createdAt: '2026-10-03T08:00:00Z',
    ...overrides,
  };
}

describe('ComplaintDetailComponent', () => {
  let fixture: ComponentFixture<ComplaintDetailComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[], loaded: Complaint): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'qm',
      fullName: 'Quality Manager',
      email: 'q@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(ComplaintDetailComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne('/api/complaints/6').flush(loaded);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ComplaintDetailComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '6' }) } } },
      ],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('shows the complaint with its batch and offers CAPA and trace actions by permission', () => {
    setup(['QUALITY_VIEW', 'CAPA_MANAGE'], complaint({}));
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('CMP/26/00006');
    expect(text).toContain('Acme Auto');
    expect(text).toContain('FG-2026-0042');
    expect(text).toContain('Free length short by 1 mm');
    expect(text).toContain('Raise CAPA');
    expect(text).toContain('Trace FG batch');
  });

  it('does not offer the CAPA or workflow controls to read-only users', () => {
    setup(['QUALITY_VIEW'], complaint({}));
    const text = fixture.nativeElement.textContent as string;
    expect(text.includes('Raise CAPA')).toBe(false);
    expect(text.includes('Work the complaint')).toBe(false);
  });

  it('links the CAPA once one exists and stops offering to raise another', () => {
    setup(['QUALITY_VIEW', 'CAPA_MANAGE'], complaint({ capaId: 4, capaNumber: 'CAPA/26/00004' }));
    expect(fixture.nativeElement.querySelector('a[href="/quality/capa/4"]')?.textContent).toContain('CAPA/26/00004');
    expect((fixture.nativeElement.textContent as string).includes('Raise CAPA')).toBe(false);
  });

  it('sends the disposition and notes when resolving', () => {
    setup(['QUALITY_VIEW', 'COMPLAINT_MANAGE'], complaint({}));
    fixture.componentInstance.resolve.setValue({
      disposition: 'REPLACEMENT',
      quantityReturned: 40,
      resolutionNotes: 'Replaced with batch FG-NEW',
    });
    fixture.componentInstance.move('RESOLVED');
    const req = backend.expectOne('/api/complaints/6/transition');
    expect(req.request.body).toEqual({
      target: 'RESOLVED',
      disposition: 'REPLACEMENT',
      quantityReturned: 40,
      resolutionNotes: 'Replaced with batch FG-NEW',
    });
    req.flush(complaint({ status: 'RESOLVED', nextStatuses: ['CLOSED', 'IN_PROGRESS'], disposition: 'REPLACEMENT' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('REPLACEMENT');
  });

  it('shows the batch trail when traced', () => {
    setup(['QUALITY_VIEW'], complaint({}));
    fixture.componentInstance.loadTrace();
    backend.expectOne('/api/complaints/6/trace').flush({
      queryType: 'FG_BATCH',
      batchId: 31,
      batchNumber: 'FG-2026-0042',
      trail: [{ stepType: 'MATERIAL_BATCH', reference: 'MB-77', description: 'Wire lot 77', timestamp: null }],
    });
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Traceability of batch FG-2026-0042');
    expect(text).toContain('MB-77');
  });
});
