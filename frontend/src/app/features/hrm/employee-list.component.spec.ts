import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { EmployeeListComponent } from './employee-list.component';
import { EmployeeDto, Page } from './hrm.api';

const employee: EmployeeDto = {
  id: 3,
  employeeCode: 'EMP/26/00003',
  userId: null,
  username: null,
  firstName: 'Asha',
  lastName: 'Rao',
  fullName: 'Asha Rao',
  email: null,
  phone: null,
  designation: 'CNC Operator',
  department: 'Production',
  plantId: null,
  costCentreId: null,
  dateOfJoining: '2024-04-01',
  dateOfLeaving: null,
  employmentType: 'PERMANENT',
  payGrade: null,
  defaultShift: 'GENERAL',
  active: true,
  compensationVisible: false,
  basicPay: null,
  fixedTravelAllowance: null,
  fixedOtherAllowances: null,
  bankAccountNo: null,
  bankIfsc: null,
  pfNumber: null,
  esiNumber: null,
  version: 0,
};

const page: Page<EmployeeDto> = { content: [employee], totalElements: 1, totalPages: 1, size: 50, number: 0 };

describe('EmployeeListComponent', () => {
  let fixture: ComponentFixture<EmployeeListComponent>;
  let backend: HttpTestingController;

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'hr',
      fullName: 'HR',
      email: 'hr@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(EmployeeListComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/hrm/employees').flush(page);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmployeeListComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('lists employees without a create form for read-only users', () => {
    setup(['HR_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('EMP/26/00003');
    expect(text).toContain('Asha Rao');
    expect(text).toContain('CNC Operator');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('drops the active filter when All is chosen', () => {
    setup(['HR_VIEW']);
    fixture.componentInstance.activeCtrl.setValue(null);
    fixture.componentInstance.load();
    const req = backend.expectOne((r) => r.url === '/api/hrm/employees');
    expect(req.request.params.has('active')).toBe(false);
    req.flush(page);
  });

  it('creates an employee for users with EMPLOYEE_MANAGE', () => {
    setup(['HR_VIEW', 'EMPLOYEE_MANAGE']);
    expect(fixture.nativeElement.querySelector('form')).toBeTruthy();
    fixture.componentInstance.form.patchValue({
      firstName: 'Ravi',
      lastName: 'Kumar',
      designation: 'Fitter',
      department: 'Maintenance',
      dateOfJoining: '2026-09-01',
      employmentType: 'CONTRACT',
      basicPay: 18000,
    });
    fixture.componentInstance.create();

    const req = backend.expectOne((r) => r.url === '/api/hrm/employees' && r.method === 'POST');
    expect(req.request.body.firstName).toBe('Ravi');
    expect(req.request.body.employmentType).toBe('CONTRACT');
    expect(req.request.body.basicPay).toBe(18000);
    expect(req.request.body.employeeCode).toBeNull();
    req.flush(employee);
  });

  it('does not submit an incomplete employee', () => {
    setup(['HR_VIEW', 'EMPLOYEE_MANAGE']);
    fixture.componentInstance.create();
    backend.expectNone((r) => r.method === 'POST');
  });
});
