import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { ComplaintListComponent } from './complaint-list.component';
import { ComplaintSummary, Page } from './quality-plus.api';

const complaint: ComplaintSummary = {
  id: 6,
  complaintNumber: 'CMP/26/00006',
  customerName: 'Acme Auto',
  productCode: 'CS-100',
  fgBatchNumber: 'FG-2026-0042',
  complaintDate: '2026-10-03',
  category: 'DIMENSIONAL',
  status: 'IN_PROGRESS',
  capaNumber: 'CAPA/26/00004',
};

const page: Page<ComplaintSummary> = { content: [complaint], totalElements: 1, totalPages: 1, size: 50, number: 0 };

describe('ComplaintListComponent', () => {
  let fixture: ComponentFixture<ComplaintListComponent>;
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
    fixture = TestBed.createComponent(ComplaintListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/complaints').flush(page);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ComplaintListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists complaints with their batch and CAPA links', () => {
    setup(['QUALITY_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('CMP/26/00006');
    expect(text).toContain('Acme Auto');
    expect(text).toContain('FG-2026-0042');
    expect(text).toContain('CAPA/26/00004');
    expect(fixture.nativeElement.querySelector('a[href="/quality/complaints/new"]')).toBeNull();
  });

  it('offers registration to complaint managers', () => {
    setup(['QUALITY_VIEW', 'COMPLAINT_MANAGE']);
    expect(fixture.nativeElement.querySelector('a[href="/quality/complaints/new"]')).not.toBeNull();
  });

  it('filters by status and search text', () => {
    setup(['QUALITY_VIEW']);
    fixture.componentInstance.statusCtrl.setValue('OPEN');
    fixture.componentInstance.searchCtrl.setValue('acme');
    fixture.componentInstance.load();
    const req = backend.expectOne((r) => r.url === '/api/complaints');
    expect(req.request.params.get('status')).toBe('OPEN');
    expect(req.request.params.get('q')).toBe('acme');
    req.flush({ ...page, content: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No customer complaints found');
  });
});
