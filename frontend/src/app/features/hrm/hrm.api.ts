import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export type EmploymentType = 'PERMANENT' | 'CONTRACT' | 'TRAINEE';
export type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'HALF_DAY' | 'HOLIDAY' | 'LEAVE';
export type SkillType = 'MACHINE' | 'OPERATION' | 'CERTIFICATION';
export type TrainingType = 'INTERNAL' | 'EXTERNAL' | 'CERTIFICATION' | 'ON_THE_JOB';
export type TrainingStatus = 'PLANNED' | 'COMPLETED' | 'NO_SHOW' | 'CANCELLED';

export const EMPLOYMENT_TYPES: EmploymentType[] = ['PERMANENT', 'CONTRACT', 'TRAINEE'];
export const SKILL_TYPES: SkillType[] = ['MACHINE', 'OPERATION', 'CERTIFICATION'];
export const TRAINING_TYPES: TrainingType[] = ['INTERNAL', 'EXTERNAL', 'CERTIFICATION', 'ON_THE_JOB'];
export const TRAINING_STATUSES: TrainingStatus[] = ['PLANNED', 'COMPLETED', 'NO_SHOW', 'CANCELLED'];
export const MONTH_NAMES = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
];

export interface EmployeeDto {
  id: number;
  employeeCode: string;
  userId: number | null;
  username: string | null;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  designation: string | null;
  department: string | null;
  plantId: number | null;
  costCentreId: number | null;
  dateOfJoining: string;
  dateOfLeaving: string | null;
  employmentType: EmploymentType;
  payGrade: string | null;
  defaultShift: string | null;
  active: boolean;
  compensationVisible: boolean;
  basicPay: number | null;
  fixedTravelAllowance: number | null;
  fixedOtherAllowances: number | null;
  bankAccountNo: string | null;
  bankIfsc: string | null;
  pfNumber: string | null;
  esiNumber: string | null;
  version: number;
}

export interface EmployeeSummaryDto {
  id: number;
  employeeCode: string;
  fullName: string;
  designation: string | null;
  department: string | null;
}

export interface SaveEmployeeRequest {
  employeeCode: string | null;
  userId: number | null;
  firstName: string;
  lastName: string;
  email: string | null;
  phone: string | null;
  designation: string | null;
  department: string | null;
  plantId: number | null;
  costCentreId: number | null;
  dateOfJoining: string;
  employmentType: EmploymentType;
  payGrade: string | null;
  basicPay: number | null;
  fixedTravelAllowance: number | null;
  fixedOtherAllowances: number | null;
  bankAccountNo: string | null;
  bankIfsc: string | null;
  pfNumber: string | null;
  esiNumber: string | null;
  defaultShift: string | null;
  payChangeReason: string | null;
  version: number | null;
}

export interface UnlinkedUserDto {
  id: number;
  username: string;
  fullName: string;
  roles: string;
}

export interface SkillDto {
  id: number;
  employeeId: number;
  skillType: SkillType;
  machineId: number | null;
  machineName: string | null;
  operationId: number | null;
  operationName: string | null;
  skillName: string | null;
  certificationNo: string | null;
  proficiencyLevel: number | null;
  certifiedOn: string | null;
  expiresOn: string | null;
  expired: boolean;
  active: boolean;
}

export interface SaveSkillRequest {
  skillType: SkillType;
  machineId: number | null;
  operationId: number | null;
  skillName: string | null;
  certificationNo: string | null;
  proficiencyLevel: number | null;
  certifiedOn: string | null;
  expiresOn: string | null;
  active: boolean;
}

export interface TrainingDto {
  id: number;
  employeeId: number;
  employeeCode: string | null;
  employeeName: string | null;
  programmeName: string;
  trainingType: TrainingType;
  provider: string | null;
  startDate: string;
  endDate: string | null;
  hours: number | null;
  status: TrainingStatus;
  score: number | null;
  certificateNo: string | null;
  certifiedOn: string | null;
  renewalDueOn: string | null;
  notes: string | null;
  version: number;
}

export interface SaveTrainingRequest {
  programmeName: string;
  trainingType: TrainingType;
  provider: string | null;
  startDate: string;
  endDate: string | null;
  hours: number | null;
  status: TrainingStatus;
  score: number | null;
  certificateNo: string | null;
  certifiedOn: string | null;
  renewalDueOn: string | null;
  notes: string | null;
  version: number | null;
}

export interface ShiftDto {
  id: number;
  shiftCode: string;
  name: string;
  startTime: string;
  endTime: string;
  graceMinutes: number;
  active: boolean;
}

export interface SaveShiftRequest {
  shiftCode: string;
  name: string;
  startTime: string;
  endTime: string;
  graceMinutes: number | null;
  active: boolean;
}

export interface AttendanceDto {
  id: number | null;
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  attendanceDate: string;
  shift: string | null;
  checkIn: string | null;
  checkOut: string | null;
  status: AttendanceStatus | 'NOT_RECORDED';
  workHours: number | null;
  late: boolean;
  source: string | null;
  remarks: string | null;
}

export interface RecordAttendanceRequest {
  employeeId: number;
  attendanceDate: string;
  shift: string | null;
  checkIn: string | null;
  checkOut: string | null;
  status: AttendanceStatus;
  remarks: string | null;
}

export interface AttendanceSummaryDto {
  employeeId: number;
  employeeCode: string;
  employeeName: string;
  present: number;
  absent: number;
  halfDay: number;
  leave: number;
  holiday: number;
  lateCount: number;
  workHours: number;
}

export interface LeaveTypeDto {
  id: number;
  leaveCode: string;
  name: string;
  maxDaysPerYear: number | null;
  carryForward: boolean;
  maxCarryForwardDays: number;
  paid: boolean;
  active: boolean;
}

export interface LeaveBalanceDto {
  leaveTypeId: number;
  leaveCode: string;
  leaveName: string;
  year: number;
  paid: boolean;
  entitled: number;
  carriedForward: number;
  used: number;
  available: number;
}

export interface LeaveApplicationDto {
  id: number;
  applicationNumber: string;
  employeeId: number;
  employeeCode: string | null;
  employeeName: string | null;
  leaveTypeId: number;
  leaveCode: string | null;
  fromDate: string;
  toDate: string;
  halfDay: boolean;
  days: number;
  reason: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
  decidedBy: number | null;
  decidedAt: string | null;
  decisionReason: string | null;
  version: number;
  allowedActions: string[];
}

export interface ApplyLeaveRequest {
  employeeId: number | null;
  leaveTypeId: number;
  fromDate: string;
  toDate: string;
  halfDay: boolean;
  reason: string | null;
}

export interface PayrollRunDto {
  id: number;
  runNumber: string;
  periodMonth: number;
  periodYear: number;
  status: 'DRAFT' | 'COMPLETED' | 'PAID';
  initiatedBy: number;
  completedAt: string | null;
  approvedBy: number | null;
  totalEmployees: number | null;
  totalGross: number | null;
  totalNet: number | null;
  journalHeaderId: number | null;
  journalNumber: string | null;
  paidAt: string | null;
  paidBy: number | null;
  paymentJournalHeaderId: number | null;
  version: number;
  allowedActions: string[];
}

export interface PayrollSlipDto {
  id: number;
  payrollRunId: number;
  runNumber: string;
  periodMonth: number;
  periodYear: number;
  employeeId: number;
  employeeCode: string | null;
  employeeName: string | null;
  designation: string | null;
  department: string | null;
  costCentreId: number | null;
  basicPay: number;
  hra: number;
  da: number;
  travelAllowance: number;
  otherAllowances: number;
  grossPay: number;
  pfEmployee: number;
  pfEmployer: number;
  esiEmployee: number;
  esiEmployer: number;
  professionalTax: number;
  tds: number;
  totalDeductions: number;
  netPay: number;
  workingDays: number | null;
  paidDays: number | null;
  lopDays: number | null;
}

@Injectable({ providedIn: 'root' })
export class HrmApi {
  private http = inject(HttpClient);
  private base = '/api/hrm';

  // employees
  getEmployees(q: string | null, department: string | null, active: boolean | null, page = 0, size = 50) {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (department) params = params.set('department', department);
    if (active !== null) params = params.set('active', active);
    return this.http.get<Page<EmployeeDto>>(`${this.base}/employees`, { params });
  }
  getEmployeeSummaries(): Observable<EmployeeSummaryDto[]> {
    return this.http.get<EmployeeSummaryDto[]>(`${this.base}/employees/summaries`);
  }
  getEmployee(id: number) {
    return this.http.get<EmployeeDto>(`${this.base}/employees/${id}`);
  }
  createEmployee(req: SaveEmployeeRequest) {
    return this.http.post<EmployeeDto>(`${this.base}/employees`, req);
  }
  updateEmployee(id: number, req: SaveEmployeeRequest) {
    return this.http.put<EmployeeDto>(`${this.base}/employees/${id}`, req);
  }
  deactivateEmployee(id: number, reason: string, dateOfLeaving: string | null) {
    return this.http.post<EmployeeDto>(`${this.base}/employees/${id}/deactivate`, { reason, dateOfLeaving });
  }
  getUnlinkedUsers() {
    return this.http.get<UnlinkedUserDto[]>(`${this.base}/employees/unlinked-users`);
  }

  // skills
  getSkills(employeeId: number) {
    return this.http.get<SkillDto[]>(`${this.base}/skills/employee/${employeeId}`);
  }
  addSkill(employeeId: number, req: SaveSkillRequest) {
    return this.http.post<SkillDto>(`${this.base}/skills/employee/${employeeId}`, req);
  }
  removeSkill(employeeId: number, skillId: number) {
    return this.http.delete<void>(`${this.base}/skills/employee/${employeeId}/${skillId}`);
  }
  getAuthorised(machineId: number | null, operationId: number | null) {
    let params = new HttpParams();
    if (machineId !== null) params = params.set('machineId', machineId);
    if (operationId !== null) params = params.set('operationId', operationId);
    return this.http.get<EmployeeSummaryDto[]>(`${this.base}/skills/authorised`, { params });
  }

  // training
  getTraining(employeeId: number) {
    return this.http.get<TrainingDto[]>(`${this.base}/training/employee/${employeeId}`);
  }
  addTraining(employeeId: number, req: SaveTrainingRequest) {
    return this.http.post<TrainingDto>(`${this.base}/training/employee/${employeeId}`, req);
  }
  deleteTraining(employeeId: number, id: number) {
    return this.http.delete<void>(`${this.base}/training/employee/${employeeId}/${id}`);
  }
  getRenewalsDue(days: number) {
    return this.http.get<TrainingDto[]>(`${this.base}/training/renewals-due`, {
      params: new HttpParams().set('days', days),
    });
  }

  // shifts
  getShifts() {
    return this.http.get<ShiftDto[]>(`${this.base}/shifts`);
  }
  createShift(req: SaveShiftRequest) {
    return this.http.post<ShiftDto>(`${this.base}/shifts`, req);
  }
  updateShift(id: number, req: SaveShiftRequest) {
    return this.http.put<ShiftDto>(`${this.base}/shifts/${id}`, req);
  }

  // attendance
  getAttendance(date: string) {
    return this.http.get<AttendanceDto[]>(`${this.base}/attendance`, { params: new HttpParams().set('date', date) });
  }
  getAttendanceSummary(year: number, month: number) {
    return this.http.get<AttendanceSummaryDto[]>(`${this.base}/attendance/summary`, {
      params: new HttpParams().set('year', year).set('month', month),
    });
  }
  recordAttendance(req: RecordAttendanceRequest) {
    return this.http.post<AttendanceDto>(`${this.base}/attendance`, req);
  }

  // leave
  getLeaveTypes() {
    return this.http.get<LeaveTypeDto[]>(`${this.base}/leave/types`);
  }
  getLeaveBalances(employeeId: number | null, year: number | null) {
    let params = new HttpParams();
    if (employeeId !== null) params = params.set('employeeId', employeeId);
    if (year !== null) params = params.set('year', year);
    return this.http.get<LeaveBalanceDto[]>(`${this.base}/leave/balances`, { params });
  }
  getLeaveApplications(status: string | null, from: string | null, to: string | null, page = 0, size = 100) {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<Page<LeaveApplicationDto>>(`${this.base}/leave/applications`, { params });
  }
  applyLeave(req: ApplyLeaveRequest) {
    return this.http.post<LeaveApplicationDto>(`${this.base}/leave/applications`, req);
  }
  decideLeave(id: number, action: 'approve' | 'reject' | 'cancel', reason: string | null) {
    return this.http.post<LeaveApplicationDto>(`${this.base}/leave/applications/${id}/${action}`, { reason });
  }

  // payroll
  getPayrollRuns() {
    return this.http.get<PayrollRunDto[]>(`${this.base}/payroll/runs`);
  }
  getPayrollRun(id: number) {
    return this.http.get<PayrollRunDto>(`${this.base}/payroll/runs/${id}`);
  }
  getPayrollSlips(runId: number) {
    return this.http.get<PayrollSlipDto[]>(`${this.base}/payroll/runs/${runId}/slips`);
  }
  getPayrollSlip(id: number) {
    return this.http.get<PayrollSlipDto>(`${this.base}/payroll/slips/${id}`);
  }
  initiatePayroll(month: number, year: number) {
    return this.http.post<PayrollRunDto>(`${this.base}/payroll/runs`, { month, year });
  }
  runPayrollAction(id: number, action: 'calculate' | 'approve' | 'pay', paymentDate: string | null = null) {
    const body = action === 'pay' ? { paymentDate } : null;
    return this.http.post<PayrollRunDto>(`${this.base}/payroll/runs/${id}/${action}`, body);
  }
}
