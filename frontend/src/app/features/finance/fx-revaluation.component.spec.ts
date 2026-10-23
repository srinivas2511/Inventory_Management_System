import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { FinanceApi, FxRevaluationRunResult } from './finance.api';
import { FxRevaluationComponent } from './fx-revaluation.component';

describe('FxRevaluationComponent', () => {
  let component: FxRevaluationComponent;
  let fixture: ComponentFixture<FxRevaluationComponent>;
  let api: jasmine.SpyObj<FinanceApi>;

  const mockPeriods = [
    {
      id: 1,
      periodName: 'FX-JAN-2012',
      startDate: '2012-01-01',
      endDate: '2012-01-31',
      status: 'CLOSED',
      fiscalYear: 2012,
      periodNumber: 1,
    },
    {
      id: 2,
      periodName: 'FX-FEB-2012',
      startDate: '2012-02-01',
      endDate: '2012-02-29',
      status: 'OPEN',
      fiscalYear: 2012,
      periodNumber: 2,
    },
  ];
  const mockResult: FxRevaluationRunResult = {
    apCount: 1,
    arCount: 0,
    apGainInr: 0,
    apLossInr: 2000,
    arGainInr: 0,
    arLossInr: 0,
  };

  beforeEach(async () => {
    api = jasmine.createSpyObj('FinanceApi', ['getPeriods', 'runFxRevaluation', 'listFxRevaluations']);
    api.getPeriods.and.returnValue(of(mockPeriods));
    api.runFxRevaluation.and.returnValue(of(mockResult));
    api.listFxRevaluations.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [FxRevaluationComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: FinanceApi, useValue: api },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(FxRevaluationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads fiscal periods on init', () => {
    expect(api.getPeriods).toHaveBeenCalled();
    expect(component.periods().length).toBe(2);
  });

  it('shows result summary and loads rows after running', () => {
    component.selectedPeriodId.set(2);
    component.runRevaluation();
    expect(api.runFxRevaluation).toHaveBeenCalledWith(2);
    expect(component.lastResult()).toEqual(mockResult);
    expect(component.lastResult()!.apLossInr).toBe(2000);
  });

  it('sets error message when run fails', () => {
    component.selectedPeriodId.set(2);
    api.runFxRevaluation.and.returnValue(
      throwError(() => ({ status: 400, error: { code: 'FX_NO_RATE', status: 400, detail: 'No exchange rate' } })),
    );
    component.runRevaluation();
    expect(component.errorMessage()).toContain('No exchange rate');
  });
});
