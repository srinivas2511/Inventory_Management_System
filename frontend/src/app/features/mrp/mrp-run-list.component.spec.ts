import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { MrpRunDto, Page } from './mrp.api';
import { MrpRunListComponent } from './mrp-run-list.component';

const run: MrpRunDto = {
  id: 7,
  runNumber: 'MRP/26/00007',
  initiatedAt: '2026-10-05T08:00:00Z',
  completedAt: '2026-10-05T08:00:03Z',
  planningHorizonDays: 90,
  includeSafetyStock: true,
  status: 'COMPLETED',
  initiatedBy: 1,
  totalProposals: 12,
  capacityOverloads: 2,
  notes: null,
  errorMessage: null,
};

const page: Page<MrpRunDto> = { content: [run], totalElements: 1, totalPages: 1, size: 20, number: 0 };

describe('MrpRunListComponent', () => {
  let fixture: ComponentFixture<MrpRunListComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'planner',
      fullName: 'Planner',
      email: 'p@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(MrpRunListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/mrp/runs').flush(page);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MrpRunListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists runs with proposal and overload counts', () => {
    setup(['MRP_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('MRP/26/00007');
    expect(text).toContain('12');
    expect(text).toContain('90 days + safety stock');
  });

  it('hides the Run MRP action without the MRP_RUN permission', () => {
    setup(['MRP_VIEW']);
    expect(fixture.nativeElement.textContent as string).not.toContain('Run MRP');
  });

  it('starts a run with the chosen horizon and reloads the list', () => {
    setup(['MRP_VIEW', 'MRP_RUN']);
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    buttons.find((b) => b.textContent?.includes('Run MRP'))?.click();
    fixture.detectChanges();

    fixture.componentInstance.form.patchValue({ horizonDays: 60, includeSafetyStock: false });
    fixture.componentInstance.run();

    const req = backend.expectOne((r) => r.url === '/api/mrp/runs' && r.method === 'POST');
    expect(req.request.body).toEqual({ horizonDays: 60, includeSafetyStock: false });
    req.flush({ ...run, id: 8, runNumber: 'MRP/26/00008' });
    backend.expectOne((r) => r.url === '/api/mrp/runs' && r.method === 'GET').flush(page);
  });
});
