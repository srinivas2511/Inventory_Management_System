import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { AttendanceDto, AttendanceStatus, AttendanceSummaryDto, HrmApi, MONTH_NAMES } from './hrm.api';

function today(): string {
  return new Date().toLocaleDateString('en-CA');
}

@Component({
  selector: 'app-attendance-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Attendance</h2>
    </div>

    <h3 class="mat-h3">Daily</h3>
    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Date</mat-label>
        <input matInput type="date" [formControl]="dateCtrl" (change)="loadDaily()" />
      </mat-form-field>
      <button mat-stroked-button (click)="loadDaily()"><mat-icon>refresh</mat-icon> Refresh</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="daily()" class="mat-elevation-z1">
        <ng-container matColumnDef="employee">
          <th mat-header-cell *matHeaderCellDef>Employee</th>
          <td mat-cell *matCellDef="let r">{{ r.employeeCode }} {{ r.employeeName }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r">
            <app-status-badge [status]="r.status" />
            @if (r.late) {
              <span class="late">late</span>
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="shift">
          <th mat-header-cell *matHeaderCellDef>Shift</th>
          <td mat-cell *matCellDef="let r">{{ r.shift }}</td>
        </ng-container>
        <ng-container matColumnDef="in">
          <th mat-header-cell *matHeaderCellDef>In</th>
          <td mat-cell *matCellDef="let r">{{ r.checkIn | date: 'HH:mm' }}</td>
        </ng-container>
        <ng-container matColumnDef="out">
          <th mat-header-cell *matHeaderCellDef>Out</th>
          <td mat-cell *matCellDef="let r">{{ r.checkOut | date: 'HH:mm' }}</td>
        </ng-container>
        <ng-container matColumnDef="hours">
          <th mat-header-cell *matHeaderCellDef>Hours</th>
          <td mat-cell *matCellDef="let r">{{ r.workHours | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="source">
          <th mat-header-cell *matHeaderCellDef>Source</th>
          <td mat-cell *matCellDef="let r">{{ r.source }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Mark</th>
          <td mat-cell *matCellDef="let r">
            <span *appHasPermission="'ATTENDANCE_MANAGE'" class="marks">
              @for (s of marks; track s) {
                <button mat-button type="button" [disabled]="r.status === s" (click)="mark(r, s)">{{ s }}</button>
              }
            </span>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="dailyColumns"></tr>
        <tr
          mat-row
          *matRowDef="let row; columns: dailyColumns"
          [class.absent]="row.status === 'ABSENT'"
          [class.is-late]="row.late"
        ></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="dailyColumns.length" style="text-align: center; padding: 2rem">
            No employees employed on this date
          </td>
        </tr>
      </table>
    }

    <h3 class="mat-h3 spaced">Monthly summary</h3>
    <form class="filter-bar" [formGroup]="monthForm" (ngSubmit)="loadSummary()">
      <mat-form-field appearance="outline">
        <mat-label>Month</mat-label>
        <mat-select formControlName="month">
          @for (m of monthNames; track m; let i = $index) {
            <mat-option [value]="i + 1">{{ m }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Year</mat-label>
        <input matInput type="number" formControlName="year" />
      </mat-form-field>
      <button mat-stroked-button type="submit"><mat-icon>calendar_month</mat-icon> Show</button>
    </form>
    <table mat-table [dataSource]="summary()" class="mat-elevation-z1">
      <ng-container matColumnDef="employee">
        <th mat-header-cell *matHeaderCellDef>Employee</th>
        <td mat-cell *matCellDef="let r">{{ r.employeeCode }} {{ r.employeeName }}</td>
      </ng-container>
      <ng-container matColumnDef="present">
        <th mat-header-cell *matHeaderCellDef>Present</th>
        <td mat-cell *matCellDef="let r">{{ r.present }}</td>
      </ng-container>
      <ng-container matColumnDef="absent">
        <th mat-header-cell *matHeaderCellDef>Absent</th>
        <td mat-cell *matCellDef="let r" [class.flag]="r.absent > 0">{{ r.absent }}</td>
      </ng-container>
      <ng-container matColumnDef="half">
        <th mat-header-cell *matHeaderCellDef>Half days</th>
        <td mat-cell *matCellDef="let r">{{ r.halfDay }}</td>
      </ng-container>
      <ng-container matColumnDef="leave">
        <th mat-header-cell *matHeaderCellDef>Leave</th>
        <td mat-cell *matCellDef="let r">{{ r.leave }}</td>
      </ng-container>
      <ng-container matColumnDef="holiday">
        <th mat-header-cell *matHeaderCellDef>Holidays</th>
        <td mat-cell *matCellDef="let r">{{ r.holiday }}</td>
      </ng-container>
      <ng-container matColumnDef="late">
        <th mat-header-cell *matHeaderCellDef>Late</th>
        <td mat-cell *matCellDef="let r" [class.flag]="r.lateCount > 0">{{ r.lateCount }}</td>
      </ng-container>
      <ng-container matColumnDef="hours">
        <th mat-header-cell *matHeaderCellDef>Hours</th>
        <td mat-cell *matCellDef="let r">{{ r.workHours | number: '1.2-2' }}</td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="summaryColumns"></tr>
      <tr mat-row *matRowDef="let row; columns: summaryColumns"></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell" [attr.colspan]="summaryColumns.length" style="text-align: center; padding: 2rem">
          No data for this month
        </td>
      </tr>
    </table>
  `,
  styles: `
    .filter-bar {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .spaced {
      margin-top: 32px;
    }
    .late,
    .flag {
      color: #c62828;
      font-weight: 600;
    }
    .late {
      margin-left: 8px;
    }
    .absent {
      background: rgba(198, 40, 40, 0.06);
    }
    .marks button {
      min-width: 0;
      padding: 0 8px;
    }
  `,
})
export class AttendancePageComponent implements OnInit {
  private api = inject(HrmApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  monthNames = MONTH_NAMES;
  marks: AttendanceStatus[] = ['PRESENT', 'ABSENT', 'HALF_DAY', 'HOLIDAY'];
  dailyColumns = ['employee', 'status', 'shift', 'in', 'out', 'hours', 'source', 'actions'];
  summaryColumns = ['employee', 'present', 'absent', 'half', 'leave', 'holiday', 'late', 'hours'];

  daily = signal<AttendanceDto[]>([]);
  summary = signal<AttendanceSummaryDto[]>([]);
  loading = signal(false);

  dateCtrl = this.fb.nonNullable.control(today());
  monthForm = this.fb.nonNullable.group({
    month: [new Date().getMonth() + 1],
    year: [new Date().getFullYear()],
  });

  ngOnInit(): void {
    this.loadDaily();
    this.loadSummary();
  }

  loadDaily(): void {
    if (!this.dateCtrl.value) return;
    this.loading.set(true);
    this.api.getAttendance(this.dateCtrl.value).subscribe({
      next: (rows) => {
        this.daily.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load attendance', 'Dismiss', { duration: 3000 });
      },
    });
  }

  loadSummary(): void {
    const v = this.monthForm.getRawValue();
    this.api.getAttendanceSummary(v.year, v.month).subscribe({
      next: (rows) => this.summary.set(rows),
      error: () => this.snack.open('Failed to load the monthly summary', 'Dismiss', { duration: 3000 }),
    });
  }

  mark(row: AttendanceDto, status: AttendanceStatus): void {
    const timed = status === 'PRESENT' || status === 'HALF_DAY';
    this.api
      .recordAttendance({
        employeeId: row.employeeId,
        attendanceDate: row.attendanceDate,
        shift: row.shift,
        checkIn: timed ? row.checkIn : null,
        checkOut: timed ? row.checkOut : null,
        status,
        remarks: row.remarks,
      })
      .subscribe({
        next: () => {
          this.loadDaily();
          this.loadSummary();
        },
        error: (err) => this.snack.open(err?.error?.detail ?? 'Could not record', 'Dismiss', { duration: 5000 }),
      });
  }
}
