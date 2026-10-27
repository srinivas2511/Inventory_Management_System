import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import {
  FinanceApi,
  FiscalPeriodDto,
  ProfitLossStatement,
  BalanceSheetStatement,
  CashFlowStatement,
} from './finance.api';
import { FinancialStatementsComponent } from './financial-statements.component';

const PERIOD: FiscalPeriodDto = {
  id: 1,
  periodName: 'Apr 2026',
  fiscalYear: 2026,
  periodNumber: 1,
  startDate: '2026-04-01',
  endDate: '2026-04-30',
  status: 'OPEN',
};

const PL: ProfitLossStatement = {
  periodId: 1,
  periodName: 'Apr 2026',
  fiscalYear: 2026,
  revenue: [
    { glAccountId: 10, accountCode: '4001', accountName: 'Sales Revenue', accountType: 'REVENUE', balance: 100000 },
  ],
  cogs: [{ glAccountId: 11, accountCode: '5001', accountName: 'COGS', accountType: 'COGS', balance: 60000 }],
  expenses: [{ glAccountId: 12, accountCode: '5201', accountName: 'Salary', accountType: 'EXPENSE', balance: 20000 }],
  totalRevenue: 100000,
  totalCogs: 60000,
  grossProfit: 40000,
  totalExpenses: 20000,
  netIncome: 20000,
};

const BS: BalanceSheetStatement = {
  periodId: 1,
  periodName: 'Apr 2026',
  assets: [{ glAccountId: 1, accountCode: '1401', accountName: 'RM Stock', accountType: 'ASSET', balance: 50000 }],
  liabilities: [
    { glAccountId: 5, accountCode: '2402', accountName: 'AP Control', accountType: 'LIABILITY', balance: 30000 },
  ],
  equity: [{ glAccountId: 7, accountCode: '3901', accountName: 'Opening Equity', accountType: 'EQUITY', balance: 0 }],
  totalAssets: 50000,
  totalLiabilities: 30000,
  retainedEarnings: 20000,
  totalEquity: 20000,
  totalLiabilitiesAndEquity: 50000,
};

const CF: CashFlowStatement = {
  periodId: 1,
  periodName: 'Apr 2026',
  lines: [{ activity: 'OPERATING', journalType: 'AR_RECEIPT', sourceModule: 'SALES', netCash: 80000 }],
  netOperating: 80000,
  netInvesting: 0,
  netFinancing: 0,
  netCashChange: 80000,
};

describe('FinancialStatementsComponent', () => {
  let fixture: ComponentFixture<FinancialStatementsComponent>;
  let api: jasmine.SpyObj<FinanceApi>;

  beforeEach(async () => {
    api = jasmine.createSpyObj('FinanceApi', ['getPeriods', 'getProfitLoss', 'getBalanceSheet', 'getCashFlow']);
    api.getPeriods.and.returnValue(of([PERIOD]));
    api.getProfitLoss.and.returnValue(of(PL));
    api.getBalanceSheet.and.returnValue(of(BS));
    api.getCashFlow.and.returnValue(of(CF));

    await TestBed.configureTestingModule({
      imports: [FinancialStatementsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: FinanceApi, useValue: api },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(FinancialStatementsComponent);
    fixture.detectChanges();
  });

  it('should create, load periods, and auto-generate statements on init (FS-1)', () => {
    expect(fixture.componentInstance).toBeTruthy();
    expect(api.getPeriods).toHaveBeenCalledOnceWith();
    expect(fixture.componentInstance.periods().length).toBe(1);
    expect(fixture.componentInstance.selectedPeriodId()).toBe(1);
    // auto-load fires immediately after period selection
    expect(api.getProfitLoss).toHaveBeenCalledOnceWith(1);
    expect(api.getBalanceSheet).toHaveBeenCalledOnceWith(1);
    expect(api.getCashFlow).toHaveBeenCalledOnceWith(1);
    expect(fixture.componentInstance.pl()).toBeTruthy();
  });

  it('should load all three statements on generate (FS-2)', () => {
    // auto-load already ran on init; verify data is present and re-generate works
    expect(fixture.componentInstance.pl()?.netIncome).toBe(20000);
    expect(fixture.componentInstance.bs()?.totalAssets).toBe(50000);
    expect(fixture.componentInstance.cf()?.netCashChange).toBe(80000);

    api.getProfitLoss.calls.reset();
    api.getBalanceSheet.calls.reset();
    api.getCashFlow.calls.reset();
    fixture.componentInstance.load();
    fixture.detectChanges();

    expect(api.getProfitLoss).toHaveBeenCalledOnceWith(1);
    expect(api.getBalanceSheet).toHaveBeenCalledOnceWith(1);
    expect(api.getCashFlow).toHaveBeenCalledOnceWith(1);
  });

  it('should reset statements when period changes (FS-3)', () => {
    fixture.componentInstance.load();
    fixture.detectChanges();
    fixture.componentInstance.selectPeriod(2);

    expect(fixture.componentInstance.pl()).toBeNull();
    expect(fixture.componentInstance.bs()).toBeNull();
    expect(fixture.componentInstance.cf()).toBeNull();
    expect(fixture.componentInstance.selectedPeriodId()).toBe(2);
  });

  it('should not make API calls when load() is called with no period selected (FS-4)', () => {
    // auto-load ran during init; reset counts to isolate this call
    api.getProfitLoss.calls.reset();
    api.getBalanceSheet.calls.reset();
    api.getCashFlow.calls.reset();
    fixture.componentInstance.selectedPeriodId.set(null);
    fixture.componentInstance.load();
    expect(api.getProfitLoss).not.toHaveBeenCalled();
    expect(api.getBalanceSheet).not.toHaveBeenCalled();
    expect(api.getCashFlow).not.toHaveBeenCalled();
  });

  it('should show error message when API fails (FS-5)', () => {
    api.getProfitLoss.and.returnValue(throwError(() => ({ status: 500, error: { detail: 'Server error' } })));
    api.getBalanceSheet.and.returnValue(of(BS));
    api.getCashFlow.and.returnValue(of(CF));
    fixture.componentInstance.load();
    fixture.detectChanges();
    expect(fixture.componentInstance.errorMessage()).toBeTruthy();
  });
});
