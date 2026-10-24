import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { FinanceApi, FiscalPeriodDto } from '../finance/finance.api';
import { AllocationRunDto, ControllingApi, CostAllocationRuleDto } from './controlling.api';
import { OverheadAllocationComponent } from './overhead-allocation.component';

const RULE: CostAllocationRuleDto = {
  id: 1,
  costCentreId: 1,
  costCentreCode: 'CC-PROD-01',
  costCentreName: 'Production',
  allocationBase: 'LABOUR_HOURS',
  active: true,
};

const RUN: AllocationRunDto = {
  id: 1,
  periodId: 1,
  periodName: '2026/7',
  status: 'COMPLETED',
  runAt: '2026-10-24T07:00:00Z',
  runBy: 1,
  journalHeaderId: 42,
  totalAllocated: 5000,
  lines: [],
};

const PERIOD: FiscalPeriodDto = {
  id: 1,
  periodName: '2026/7',
  periodNumber: 7,
  fiscalYear: 2026,
  startDate: '2026-10-01',
  endDate: '2026-10-31',
  status: 'OPEN',
};

describe('OverheadAllocationComponent', () => {
  let fixture: ComponentFixture<OverheadAllocationComponent>;
  let api: jasmine.SpyObj<ControllingApi>;
  let financeApi: jasmine.SpyObj<FinanceApi>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  beforeEach(async () => {
    api = jasmine.createSpyObj('ControllingApi', [
      'getAllocationRules',
      'getAllocationRuns',
      'getCostCentres',
      'createAllocationRule',
      'setRuleActive',
      'getAllocationRun',
      'runAllocation',
    ]);
    financeApi = jasmine.createSpyObj('FinanceApi', ['getPeriods']);
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);

    api.getAllocationRules.and.returnValue(of([RULE]));
    api.getAllocationRuns.and.returnValue(of([RUN]));
    api.getCostCentres.and.returnValue(of([]));
    api.createAllocationRule.and.returnValue(of(RULE));
    api.setRuleActive.and.returnValue(of({ ...RULE, active: false }));
    api.getAllocationRun.and.returnValue(of(RUN));
    api.runAllocation.and.returnValue(of(RUN));
    financeApi.getPeriods.and.returnValue(of([PERIOD]));

    await TestBed.configureTestingModule({
      imports: [OverheadAllocationComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: ControllingApi, useValue: api },
        { provide: FinanceApi, useValue: financeApi },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OverheadAllocationComponent);
    fixture.detectChanges();
  });

  it('should load rules and runs on init (OA-1)', () => {
    expect(fixture.componentInstance).toBeTruthy();
    expect(api.getAllocationRules).toHaveBeenCalledOnceWith();
    expect(api.getAllocationRuns).toHaveBeenCalledOnceWith();
    expect(fixture.componentInstance.rules().length).toBe(1);
    expect(fixture.componentInstance.runs().length).toBe(1);
  });

  it('should load run lines on viewLines (OA-2)', () => {
    fixture.componentInstance.viewLines(RUN);
    expect(api.getAllocationRun).toHaveBeenCalledWith(1);
    expect(fixture.componentInstance.selectedRun()?.id).toBe(1);
  });

  it('should toggle selectedRun on repeated viewLines click (OA-3)', () => {
    fixture.componentInstance.viewLines(RUN);
    fixture.componentInstance.viewLines(RUN);
    expect(fixture.componentInstance.selectedRun()).toBeNull();
  });

  it('should call runAllocation with correct periodId (OA-4)', () => {
    fixture.componentInstance.runForm.setValue({ periodId: 1 });
    fixture.componentInstance.runAllocation();
    expect(api.runAllocation).toHaveBeenCalledWith(1);
  });

  it('should call setRuleActive to toggle rule (OA-5)', () => {
    fixture.componentInstance.toggleRule(RULE);
    expect(api.setRuleActive).toHaveBeenCalledWith(1, false);
  });
});
