import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { CostCentreDto } from './controlling.api';
import { CostCentreListComponent } from './cost-centre-list.component';

const costCentres: CostCentreDto[] = [
  {
    id: 1,
    costCentreCode: 'CC-PROD-01',
    name: 'Production - Spring Shop',
    parentId: null,
    responsibleUserId: null,
    plantId: null,
    labourRatePerHour: 250,
    machineRatePerHour: 400,
    overheadPct: 10,
    active: true,
  },
];

describe('CostCentreListComponent', () => {
  let fixture: ComponentFixture<CostCentreListComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'controller',
      fullName: 'Controller',
      email: 'c@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(CostCentreListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/controlling/cost-centres').flush(costCentres);
    backend
      .expectOne((r) => r.url === '/api/controlling/profit-centres')
      .flush([{ id: 1, profitCentreCode: 'PC-SPRINGS', name: 'Springs Business', active: true }]);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CostCentreListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists cost and profit centres with rates', () => {
    setup(['CONTROLLING_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('CC-PROD-01');
    expect(text).toContain('250.00');
    expect(text).toContain('PC-SPRINGS');
  });

  it('hides edit actions for view-only users', () => {
    setup(['CONTROLLING_VIEW']);
    expect(fixture.nativeElement.textContent).not.toContain('New cost centre');
    expect(fixture.nativeElement.textContent).not.toContain('Deactivate');
  });

  it('shows edit actions and the create form for managers', () => {
    setup(['CONTROLLING_VIEW', 'CONTROLLING_MANAGE']);
    expect(fixture.nativeElement.textContent).toContain('Deactivate');
    const newButton = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('New cost centre'),
    );
    newButton?.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('form')).toBeTruthy();
  });
});
