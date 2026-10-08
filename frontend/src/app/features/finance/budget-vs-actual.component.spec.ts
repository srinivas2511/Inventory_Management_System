/* eslint-disable @typescript-eslint/no-explicit-any */
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SessionStore } from '../../core/auth/session.store';
import { BudgetVsActualComponent } from './budget-vs-actual.component';
import { BudgetVsActualDto } from './finance.api';

const periods = [
  {
    id: 1,
    periodName: 'APR-2026',
    fiscalYear: 2026,
    periodNumber: 1,
    startDate: '2026-04-01',
    endDate: '2026-04-30',
    status: 'OPEN',
  },
  {
    id: 2,
    periodName: 'MAY-2026',
    fiscalYear: 2026,
    periodNumber: 2,
    startDate: '2026-05-01',
    endDate: '2026-05-31',
    status: 'OPEN',
  },
];

const report: BudgetVsActualDto = {
  fiscalYear: 2026,
  periodId: null,
  periodName: null,
  totalBudgeted: 24000,
  totalActual: 16000,
  rows: [
    {
      accountId: 1,
      accountCode: '5201',
      accountName: 'Salary Expense',
      accountType: 'EXPENSE',
      normalBalance: 'DEBIT',
      budgeted: 10000,
      actual: 12500,
      variance: 2500,
      variancePct: 25,
      favourable: false,
    },
    {
      accountId: 2,
      accountCode: '4001',
      accountName: 'Sales Revenue',
      accountType: 'REVENUE',
      normalBalance: 'CREDIT',
      budgeted: 10000,
      actual: 8000,
      variance: -2000,
      variancePct: -20,
      favourable: false,
    },
    {
      accountId: 3,
      accountCode: '1201',
      accountName: 'Main Bank Account',
      accountType: 'ASSET',
      normalBalance: 'DEBIT',
      budgeted: 0,
      actual: -4500,
      variance: -4500,
      variancePct: null,
      favourable: true,
    },
  ],
};

describe('BudgetVsActualComponent', () => {
  let fixture: ComponentFixture<BudgetVsActualComponent>;
  let backend: HttpTestingController;
  let instance: Record<string, any>;

  function setup(permissions: string[]): void {
    TestBed.configureTestingModule({
      imports: [BudgetVsActualComponent, NoopAnimationsModule],
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
    fixture = TestBed.createComponent(BudgetVsActualComponent);
    backend = TestBed.inject(HttpTestingController);
    instance = fixture.componentInstance as unknown as Record<string, any>;
    fixture.detectChanges();
    backend.expectOne('/api/gl/accounts').flush([]);
    backend.expectOne('/api/periods').flush(periods);
    backend
      .expectOne((r) => r.url === '/api/config/budgets/vs-actual' && r.params.get('fiscalYear') === '2026')
      .flush(report);
    fixture.detectChanges();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => backend.verify());

  it('loads the open year and marks an expense over budget and revenue under it', () => {
    setup(['GL_VIEW']);

    expect(text()).toContain('5201 · Salary Expense');
    expect(text()).toContain('Over budget');
    expect(text()).toContain('Under budget');
    expect(text()).toContain('25.00%');
    expect(text()).toContain('no budget');
    expect(text()).toContain('Budget 24,000.00 · Actual 16,000.00');
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="budget-file"]')).toBeNull();
  });

  it('asks for one period when it is chosen', () => {
    setup(['GL_VIEW']);

    instance['periodChanged'](2);

    const call = backend.expectOne((r) => r.url === '/api/config/budgets/vs-actual');
    expect(call.request.params.get('periodId')).toBe('2');
    call.flush({ ...report, periodId: 2, periodName: 'MAY-2026' });
  });

  it('imports a CSV for the year shown and reloads the report with the counts', () => {
    setup(['GL_VIEW', 'BUDGET_MANAGE']);
    const file = new File(['account_code,period,amount\n5201,1,11000\n'], 'budget.csv', { type: 'text/csv' });
    instance['file'].set(file);

    instance['importFile']();

    const post = backend.expectOne((r) => r.url === '/api/config/budgets/import');
    expect(post.request.params.get('fiscalYear')).toBe('2026');
    expect((post.request.body as FormData).get('file')).toBe(file);
    post.flush({ imported: 1, created: 0, updated: 1 });
    backend.expectOne((r) => r.url === '/api/config/budgets/vs-actual').flush(report);
    fixture.detectChanges();
    expect(text()).toContain('Imported 1 lines: 0 new, 1 replaced.');
  });

  it('shows why a file was refused', () => {
    setup(['GL_VIEW', 'BUDGET_MANAGE']);
    instance['file'].set(new File(['x'], 'bad.csv'));

    instance['importFile']();
    backend
      .expectOne((r) => r.url === '/api/config/budgets/import')
      .flush(
        { status: 400, code: 'VALIDATION_FAILED', detail: 'Nothing was imported. line 3: unknown account 9999' },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(text()).toContain('Nothing was imported. line 3: unknown account 9999');
  });
});
