import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { LeaveApplicationDto, LeaveBalanceDto, LeaveTypeDto, Page } from './hrm.api';
import { LeavePageComponent } from './leave-page.component';

const types: LeaveTypeDto[] = [
  {
    id: 1,
    leaveCode: 'CL',
    name: 'Casual leave',
    maxDaysPerYear: 12,
    carryForward: false,
    maxCarryForwardDays: 0,
    paid: true,
    active: true,
  },
];
const balances: LeaveBalanceDto[] = [
  {
    leaveTypeId: 1,
    leaveCode: 'CL',
    leaveName: 'Casual leave',
    year: 2026,
    paid: true,
    entitled: 12,
    carriedForward: 0,
    used: 2,
    available: 10,
  },
];
const pending: LeaveApplicationDto = {
  id: 9,
  applicationNumber: 'LV/26/00009',
  employeeId: 4,
  employeeCode: 'EMP/26/00004',
  employeeName: 'Ravi Kumar',
  leaveTypeId: 1,
  leaveCode: 'CL',
  fromDate: '2026-10-05',
  toDate: '2026-10-06',
  halfDay: false,
  days: 2,
  reason: null,
  status: 'PENDING',
  decidedBy: null,
  decidedAt: null,
  decisionReason: null,
  version: 0,
  allowedActions: ['APPROVE', 'REJECT', 'CANCEL'],
};
const page = (content: LeaveApplicationDto[]): Page<LeaveApplicationDto> => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  size: 100,
  number: 0,
});

describe('LeavePageComponent', () => {
  let fixture: ComponentFixture<LeavePageComponent>;
  let backend: HttpTestingController;

  function flushReload(): void {
    backend.expectOne((r) => r.url === '/api/hrm/leave/balances').flush(balances);
    backend.match((r) => r.url === '/api/hrm/leave/applications').forEach((r) => r.flush(page([])));
  }

  function setup(permissions: string[]): void {
    TestBed.inject(SessionStore).setUser({
      id: 1,
      username: 'u',
      fullName: 'U',
      email: 'u@example.com',
      roles: [],
      permissions,
      mustChangePassword: false,
    });
    fixture = TestBed.createComponent(LeavePageComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/hrm/leave/types').flush(types);
    backend.expectOne((r) => r.url === '/api/hrm/leave/balances').flush(balances);
    const lists = backend.match((r) => r.url === '/api/hrm/leave/applications');
    expect(lists.length).toBe(2);
    lists[0].flush(page([pending]));
    lists[1].flush(page([{ ...pending, status: 'APPROVED' }]));
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LeavePageComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('shows balances and applications', () => {
    setup(['LEAVE_APPLY']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('CL');
    expect(text).toContain('LV/26/00009');
    expect(text).toContain('Ravi Kumar');
  });

  it('offers approve, reject and cancel when the server allows them', () => {
    setup(['LEAVE_APPLY', 'LEAVE_APPROVE']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Approve');
    expect(text).toContain('Reject');
    expect(text).toContain('Cancel');
  });

  it('approves an application and reloads', () => {
    setup(['LEAVE_APPLY', 'LEAVE_APPROVE']);
    fixture.componentInstance.decisionCtrl.setValue('go ahead');
    fixture.componentInstance.decide(pending, 'approve');

    const req = backend.expectOne((r) => r.url === '/api/hrm/leave/applications/9/approve' && r.method === 'POST');
    expect(req.request.body).toEqual({ reason: 'go ahead' });
    req.flush({ ...pending, status: 'APPROVED' });
    flushReload();
  });

  it('applies for leave and collapses a half day to one date', () => {
    setup(['LEAVE_APPLY']);
    fixture.componentInstance.form.patchValue({
      leaveTypeId: 1,
      fromDate: '2026-10-12',
      toDate: '2026-10-14',
      halfDay: true,
      reason: 'appointment',
    });
    fixture.componentInstance.apply();

    const req = backend.expectOne((r) => r.url === '/api/hrm/leave/applications' && r.method === 'POST');
    expect(req.request.body).toEqual({
      employeeId: null,
      leaveTypeId: 1,
      fromDate: '2026-10-12',
      toDate: '2026-10-12',
      halfDay: true,
      reason: 'appointment',
    });
    req.flush(pending);
    flushReload();
  });
});
