import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { EcoListComponent } from './eco-list.component';
import { EcoSummaryDto, Page } from './plm.api';

const eco: EcoSummaryDto = {
  id: 5,
  ecoNumber: 'ECO/26/00005',
  title: 'Switch to music wire grade B',
  changeCategory: 'BOM_CHANGE',
  urgency: 'HIGH',
  status: 'REVIEW',
  effectivityDate: '2026-11-01',
  initiatedBy: 2,
  itemCount: 3,
  createdAt: '2026-10-01T08:00:00Z',
};

const page: Page<EcoSummaryDto> = { content: [eco], totalElements: 1, totalPages: 1, size: 50, number: 0 };

describe('EcoListComponent', () => {
  let fixture: ComponentFixture<EcoListComponent>;
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
    fixture = TestBed.createComponent(EcoListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/ecos').flush(page);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EcoListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists ECOs and hides the new-ECO link from read-only users', () => {
    setup(['PLM_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('ECO/26/00005');
    expect(text).toContain('Switch to music wire grade B');
    expect(text).toContain('BOM_CHANGE');
    expect(fixture.nativeElement.querySelector('a[href="/plm/ecos/new"]')).toBeNull();
  });

  it('offers a new-ECO link to users who can create ECOs', () => {
    setup(['PLM_VIEW', 'ECO_CREATE']);
    expect(fixture.nativeElement.querySelector('a[href="/plm/ecos/new"]')).not.toBeNull();
  });

  it('sends the search text when searching', () => {
    setup(['PLM_VIEW']);
    fixture.componentInstance.searchCtrl.setValue('wire');
    fixture.componentInstance.load();
    const req = backend.expectOne((r) => r.url === '/api/ecos');
    expect(req.request.params.get('q')).toBe('wire');
    req.flush({ ...page, content: [] });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No engineering change orders found');
  });
});
