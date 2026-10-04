import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { FaiListComponent } from './fai-list.component';
import { FaiSummary, Page } from './quality-plus.api';

const fai: FaiSummary = {
  id: 8,
  faiNumber: 'FAI/26/00008',
  productCode: 'CS-100',
  productName: 'Compression spring',
  triggerType: 'ECO_IMPLEMENTED',
  triggerReference: 'ECO/26/00005',
  status: 'REQUIRED',
  createdAt: '2026-10-03T08:00:00Z',
  completedAt: null,
};

const page: Page<FaiSummary> = { content: [fai], totalElements: 1, totalPages: 1, size: 50, number: 0 };

describe('FaiListComponent', () => {
  let fixture: ComponentFixture<FaiListComponent>;
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
    fixture = TestBed.createComponent(FaiListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/fai').flush(page);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FaiListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists the FAIs with what triggered them', () => {
    setup(['QUALITY_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('FAI/26/00008');
    expect(text).toContain('CS-100');
    expect(text).toContain('ECO_IMPLEMENTED');
    expect(text).toContain('ECO/26/00005');
    expect(text.includes('Require FAI')).toBe(false);
  });

  it('lets inspectors require an FAI by hand', () => {
    setup(['QUALITY_VIEW', 'QUALITY_INSPECT']);
    expect((fixture.nativeElement.textContent as string).includes('Require FAI')).toBe(true);
    fixture.componentInstance.productCtrl.setValue(9);
    fixture.componentInstance.create();
    const req = backend.expectOne('/api/fai');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ productId: 9 });
    req.flush({ id: 9 });
  });

  it('filters by status', () => {
    setup(['QUALITY_VIEW']);
    fixture.componentInstance.statusCtrl.setValue('PASSED');
    fixture.componentInstance.load();
    const req = backend.expectOne((r) => r.url === '/api/fai');
    expect(req.request.params.get('status')).toBe('PASSED');
    req.flush({ ...page, content: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No first article inspections found');
  });
});
