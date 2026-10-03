import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { PayrollRunDto, PayrollSlipDto } from './hrm.api';
import { PayrollRunDetailComponent } from './payroll-run-detail.component';

const draft: PayrollRunDto = {
  id: 5,
  runNumber: 'PAY/26/00005',
  periodMonth: 9,
  periodYear: 2026,
  status: 'DRAFT',
  initiatedBy: 1,
  completedAt: null,
  approvedBy: null,
  totalEmployees: 1,
  totalGross: 16800,
  totalNet: 15034,
  journalHeaderId: null,
  journalNumber: null,
  paidAt: null,
  paidBy: null,
  paymentJournalHeaderId: null,
  version: 0,
  allowedActions: ['CALCULATE', 'APPROVE'],
};
const slip: PayrollSlipDto = {
  id: 11,
  payrollRunId: 5,
  runNumber: 'PAY/26/00005',
  periodMonth: 9,
  periodYear: 2026,
  employeeId: 3,
  employeeCode: 'EMP/26/00003',
  employeeName: 'Asha Rao',
  designation: null,
  department: null,
  costCentreId: null,
  basicPay: 12000,
  hra: 4800,
  da: 0,
  travelAllowance: 0,
  otherAllowances: 0,
  grossPay: 16800,
  pfEmployee: 1440,
  pfEmployer: 1440,
  esiEmployee: 126,
  esiEmployer: 546,
  professionalTax: 200,
  tds: 0,
  totalDeductions: 1766,
  netPay: 15034,
  workingDays: 26,
  paidDays: 26,
  lopDays: 0,
};

describe('PayrollRunDetailComponent', () => {
  let fixture: ComponentFixture<PayrollRunDetailComponent>;
  let backend: HttpTestingController;

  function setup(run: PayrollRunDto): void {
    fixture = TestBed.createComponent(PayrollRunDetailComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/hrm/payroll/runs/5').flush(run);
    backend.expectOne((r) => r.url === '/api/hrm/payroll/runs/5/slips').flush([slip]);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PayrollRunDetailComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '5' }) } } },
      ],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('shows slips and the draft actions', () => {
    setup(draft);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('PAY/26/00005');
    expect(text).toContain('Asha Rao');
    expect(text).toContain('15,034.00');
    expect(text).toContain('Recalculate');
    expect(text).toContain('Approve and post journal');
    expect(text).not.toContain('Mark paid');
  });

  it('offers only Mark paid once the run is approved', () => {
    setup({ ...draft, status: 'COMPLETED', allowedActions: ['MARK_PAID'], journalNumber: 'JNL/26/00042' });
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Mark paid');
    expect(text).toContain('JNL/26/00042');
    expect(text).not.toContain('Approve and post journal');
    expect(text).not.toContain('Recalculate');
  });

  it('approves the run and reloads it', () => {
    setup(draft);
    fixture.componentInstance.act('approve');
    const req = backend.expectOne((r) => r.url === '/api/hrm/payroll/runs/5/approve' && r.method === 'POST');
    req.flush({ ...draft, status: 'COMPLETED', allowedActions: ['MARK_PAID'] });
    backend.expectOne((r) => r.url === '/api/hrm/payroll/runs/5').flush(draft);
    backend.expectOne((r) => r.url === '/api/hrm/payroll/runs/5/slips').flush([slip]);
  });
});
