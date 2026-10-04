import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { CapaListComponent } from './capa-list.component';
import { CapaSummary, Page } from './quality-plus.api';

const capa: CapaSummary = {
  id: 4,
  capaNumber: 'CAPA/26/00004',
  title: 'Coil rejects',
  sourceType: 'REJECTION_ANALYSIS',
  sourceId: 17,
  status: 'ROOT_CAUSE_ANALYSIS',
  targetCompletionDate: '2026-11-15',
  assignedTo: null,
  openedAt: '2026-10-02T08:00:00Z',
  closedAt: null,
};

const page: Page<CapaSummary> = { content: [capa], totalElements: 1, totalPages: 1, size: 50, number: 0 };

describe('CapaListComponent', () => {
  let fixture: ComponentFixture<CapaListComponent>;
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
    fixture = TestBed.createComponent(CapaListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/capa').flush(page);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CapaListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists CAPAs and hides the new-CAPA link from read-only users', () => {
    setup(['QUALITY_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('CAPA/26/00004');
    expect(text).toContain('Coil rejects');
    expect(text).toContain('REJECTION_ANALYSIS');
    expect(fixture.nativeElement.querySelector('a[href="/quality/capa/new"]')).toBeNull();
  });

  it('offers a new-CAPA link to CAPA managers', () => {
    setup(['QUALITY_VIEW', 'CAPA_MANAGE']);
    expect(fixture.nativeElement.querySelector('a[href="/quality/capa/new"]')).not.toBeNull();
  });

  it('filters by status, source and search text', () => {
    setup(['QUALITY_VIEW']);
    fixture.componentInstance.statusCtrl.setValue('VERIFICATION');
    fixture.componentInstance.sourceCtrl.setValue('SPC_VIOLATION');
    fixture.componentInstance.searchCtrl.setValue('coil');
    fixture.componentInstance.load();
    const req = backend.expectOne((r) => r.url === '/api/capa');
    expect(req.request.params.get('status')).toBe('VERIFICATION');
    expect(req.request.params.get('source')).toBe('SPC_VIOLATION');
    expect(req.request.params.get('q')).toBe('coil');
    req.flush({ ...page, content: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No corrective or preventive actions found');
  });
});
