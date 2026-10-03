import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { AttendanceDto, AttendanceSummaryDto } from './hrm.api';
import { AttendancePageComponent } from './attendance-page.component';

const rows: AttendanceDto[] = [
  {
    id: 1,
    employeeId: 3,
    employeeCode: 'EMP/26/00003',
    employeeName: 'Asha Rao',
    attendanceDate: '2026-10-01',
    shift: 'GENERAL',
    checkIn: '2026-10-01T03:50:00Z',
    checkOut: null,
    status: 'PRESENT',
    workHours: null,
    late: true,
    source: 'AUTO',
    remarks: null,
  },
  {
    id: null,
    employeeId: 4,
    employeeCode: 'EMP/26/00004',
    employeeName: 'Ravi Kumar',
    attendanceDate: '2026-10-01',
    shift: null,
    checkIn: null,
    checkOut: null,
    status: 'NOT_RECORDED',
    workHours: null,
    late: false,
    source: null,
    remarks: null,
  },
];
const summary: AttendanceSummaryDto[] = [
  {
    employeeId: 3,
    employeeCode: 'EMP/26/00003',
    employeeName: 'Asha Rao',
    present: 20,
    absent: 1,
    halfDay: 0,
    leave: 2,
    holiday: 1,
    lateCount: 3,
    workHours: 160,
  },
];

describe('AttendancePageComponent', () => {
  let fixture: ComponentFixture<AttendancePageComponent>;
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
    fixture = TestBed.createComponent(AttendancePageComponent);
    backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    backend.expectOne((r) => r.url === '/api/hrm/attendance').flush(rows);
    backend.expectOne((r) => r.url === '/api/hrm/attendance/summary').flush(summary);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AttendancePageComponent, NoopAnimationsModule],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => backend.verify());

  it('shows the daily view with late and not-recorded employees and the monthly summary', () => {
    setup(['HR_VIEW']);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Asha Rao');
    expect(text).toContain('late');
    expect(text).toContain('Not recorded');
    expect(text).toContain('160.00');
    expect(fixture.nativeElement.querySelector('.marks')).toBeNull();
  });

  it('marks attendance for users with ATTENDANCE_MANAGE', () => {
    setup(['HR_VIEW', 'ATTENDANCE_MANAGE']);
    expect(fixture.nativeElement.querySelector('.marks')).toBeTruthy();
    fixture.componentInstance.mark(rows[1], 'ABSENT');

    const req = backend.expectOne((r) => r.url === '/api/hrm/attendance' && r.method === 'POST');
    expect(req.request.body).toEqual({
      employeeId: 4,
      attendanceDate: '2026-10-01',
      shift: null,
      checkIn: null,
      checkOut: null,
      status: 'ABSENT',
      remarks: null,
    });
    req.flush(rows[0]);
    backend.expectOne((r) => r.url === '/api/hrm/attendance' && r.method === 'GET').flush(rows);
    backend.expectOne((r) => r.url === '/api/hrm/attendance/summary').flush(summary);
  });
});
