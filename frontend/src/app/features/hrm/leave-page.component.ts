import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { HrmApi, LeaveApplicationDto, LeaveBalanceDto, LeaveTypeDto, MONTH_NAMES } from './hrm.api';

interface CalendarCell {
  day: number | null;
  names: string[];
}

@Component({
  selector: 'app-leave-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Leave</h2>
    </div>

    <div class="balances">
      @for (b of balances(); track b.leaveTypeId) {
        <div class="balance mat-elevation-z1">
          <div class="code">{{ b.leaveCode }}</div>
          <div class="available">{{ b.available | number: '1.0-1' }}</div>
          <div class="detail">
            of {{ b.entitled + b.carriedForward | number: '1.0-1' }} days, {{ b.used | number: '1.0-1' }} used
          </div>
        </div>
      }
    </div>

    <form *appHasPermission="'LEAVE_APPLY'" class="edit-form" [formGroup]="form" (ngSubmit)="apply()">
      <mat-form-field appearance="outline">
        <mat-label>Leave type</mat-label>
        <mat-select formControlName="leaveTypeId">
          @for (t of types(); track t.id) {
            <mat-option [value]="t.id">{{ t.leaveCode }} - {{ t.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>From</mat-label>
        <input matInput type="date" formControlName="fromDate" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>To</mat-label>
        <input matInput type="date" formControlName="toDate" />
      </mat-form-field>
      <mat-checkbox formControlName="halfDay">Half day</mat-checkbox>
      <mat-form-field appearance="outline" class="wide">
        <mat-label>Reason</mat-label>
        <input matInput maxlength="300" formControlName="reason" />
      </mat-form-field>
      <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">
        <mat-icon>event_available</mat-icon> Apply
      </button>
    </form>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl" (selectionChange)="loadApplications()">
          <mat-option [value]="null">All</mat-option>
          @for (s of statuses; track s) {
            <mat-option [value]="s">{{ s }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field *appHasPermission="'LEAVE_APPROVE'" appearance="outline">
        <mat-label>Reason for decision</mat-label>
        <input matInput maxlength="300" [formControl]="decisionCtrl" />
      </mat-form-field>
    </div>

    <table mat-table [dataSource]="applications()" class="mat-elevation-z1">
      <ng-container matColumnDef="number">
        <th mat-header-cell *matHeaderCellDef>Application</th>
        <td mat-cell *matCellDef="let r">{{ r.applicationNumber }}</td>
      </ng-container>
      <ng-container matColumnDef="employee">
        <th mat-header-cell *matHeaderCellDef>Employee</th>
        <td mat-cell *matCellDef="let r">{{ r.employeeCode }} {{ r.employeeName }}</td>
      </ng-container>
      <ng-container matColumnDef="type">
        <th mat-header-cell *matHeaderCellDef>Type</th>
        <td mat-cell *matCellDef="let r">{{ r.leaveCode }}</td>
      </ng-container>
      <ng-container matColumnDef="dates">
        <th mat-header-cell *matHeaderCellDef>Dates</th>
        <td mat-cell *matCellDef="let r">
          {{ r.fromDate | date: 'dd MMM' }} - {{ r.toDate | date: 'dd MMM yyyy' }}
          @if (r.halfDay) {
            (half day)
          }
        </td>
      </ng-container>
      <ng-container matColumnDef="days">
        <th mat-header-cell *matHeaderCellDef>Days</th>
        <td mat-cell *matCellDef="let r">{{ r.days | number: '1.0-1' }}</td>
      </ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let r">
          @if (r.allowedActions.includes('APPROVE')) {
            <button mat-button color="primary" (click)="decide(r, 'approve')">Approve</button>
          }
          @if (r.allowedActions.includes('REJECT')) {
            <button mat-button color="warn" [disabled]="!decisionCtrl.value.trim()" (click)="decide(r, 'reject')">
              Reject
            </button>
          }
          @if (r.allowedActions.includes('CANCEL')) {
            <button mat-button (click)="decide(r, 'cancel')">Cancel</button>
          }
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell" [attr.colspan]="columns.length" style="text-align: center; padding: 2rem">
          No leave applications
        </td>
      </tr>
    </table>

    <h3 class="mat-h3 spaced">Calendar - {{ monthLabel() }}</h3>
    <div class="calendar-nav">
      <button mat-icon-button aria-label="Previous month" (click)="shiftMonth(-1)">
        <mat-icon>chevron_left</mat-icon>
      </button>
      <button mat-icon-button aria-label="Next month" (click)="shiftMonth(1)">
        <mat-icon>chevron_right</mat-icon>
      </button>
    </div>
    <div class="calendar">
      @for (d of weekdays; track d) {
        <div class="head">{{ d }}</div>
      }
      @for (cell of calendar(); track $index) {
        <div class="cell" [class.empty]="cell.day === null">
          @if (cell.day !== null) {
            <div class="day">{{ cell.day }}</div>
            @for (n of cell.names; track n) {
              <div class="chip">{{ n }}</div>
            }
          }
        </div>
      }
    </div>
  `,
  styles: `
    .balances {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-bottom: 16px;
    }
    .balance {
      padding: 12px 20px;
      border-radius: 8px;
      text-align: center;
      min-width: 140px;
    }
    .code {
      font-weight: 600;
    }
    .available {
      font-size: 2rem;
    }
    .detail {
      opacity: 0.7;
      font-size: 0.8rem;
    }
    .filter-bar,
    .edit-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .wide {
      min-width: 260px;
    }
    .spaced {
      margin-top: 32px;
    }
    .calendar {
      display: grid;
      grid-template-columns: repeat(7, minmax(0, 1fr));
      gap: 4px;
    }
    .head {
      text-align: center;
      font-weight: 600;
      opacity: 0.7;
    }
    .cell {
      min-height: 72px;
      padding: 4px;
      border: 1px solid rgba(128, 128, 128, 0.3);
      border-radius: 4px;
    }
    .cell.empty {
      border-color: transparent;
    }
    .day {
      font-size: 0.8rem;
      opacity: 0.7;
    }
    .chip {
      font-size: 0.75rem;
      background: rgba(0, 150, 136, 0.18);
      border-radius: 8px;
      padding: 0 6px;
      margin-top: 2px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  `,
})
export class LeavePageComponent implements OnInit {
  private api = inject(HrmApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  statuses = ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'];
  weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  columns = ['number', 'employee', 'type', 'dates', 'days', 'status', 'actions'];

  types = signal<LeaveTypeDto[]>([]);
  balances = signal<LeaveBalanceDto[]>([]);
  applications = signal<LeaveApplicationDto[]>([]);
  calendarApplications = signal<LeaveApplicationDto[]>([]);
  month = signal(new Date().getMonth());
  year = signal(new Date().getFullYear());

  monthLabel = computed(() => `${MONTH_NAMES[this.month()]} ${this.year()}`);
  calendar = computed<CalendarCell[]>(() => {
    const y = this.year();
    const m = this.month();
    const daysInMonth = new Date(y, m + 1, 0).getDate();
    const leadingBlanks = (new Date(y, m, 1).getDay() + 6) % 7;
    const cells: CalendarCell[] = Array.from({ length: leadingBlanks }, () => ({ day: null, names: [] }));
    for (let day = 1; day <= daysInMonth; day++) {
      const iso = `${y}-${String(m + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
      const names = this.calendarApplications()
        .filter((a) => a.fromDate <= iso && a.toDate >= iso)
        .map((a) => `${a.employeeCode ?? ''} ${a.leaveCode ?? ''}`.trim());
      cells.push({ day, names });
    }
    return cells;
  });

  statusCtrl = this.fb.control<string | null>(null);
  decisionCtrl = this.fb.nonNullable.control('');
  form = this.fb.nonNullable.group({
    leaveTypeId: this.fb.control<number | null>(null, Validators.required),
    fromDate: ['', Validators.required],
    toDate: ['', Validators.required],
    halfDay: [false],
    reason: [''],
  });

  ngOnInit(): void {
    this.api.getLeaveTypes().subscribe({ next: (t) => this.types.set(t) });
    this.loadBalances();
    this.loadApplications();
    this.loadCalendar();
  }

  loadBalances(): void {
    this.api.getLeaveBalances(null, null).subscribe({
      next: (b) => this.balances.set(b),
      error: () => this.balances.set([]),
    });
  }

  loadApplications(): void {
    this.api.getLeaveApplications(this.statusCtrl.value, null, null).subscribe({
      next: (page) => this.applications.set(page.content),
      error: () => this.snack.open('Failed to load leave applications', 'Dismiss', { duration: 3000 }),
    });
  }

  loadCalendar(): void {
    const y = this.year();
    const m = this.month();
    const from = `${y}-${String(m + 1).padStart(2, '0')}-01`;
    const to = `${y}-${String(m + 1).padStart(2, '0')}-${String(new Date(y, m + 1, 0).getDate()).padStart(2, '0')}`;
    this.api.getLeaveApplications('APPROVED', from, to).subscribe({
      next: (page) => this.calendarApplications.set(page.content),
      error: () => this.calendarApplications.set([]),
    });
  }

  shiftMonth(delta: number): void {
    const d = new Date(this.year(), this.month() + delta, 1);
    this.year.set(d.getFullYear());
    this.month.set(d.getMonth());
    this.loadCalendar();
  }

  apply(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.api
      .applyLeave({
        employeeId: null,
        leaveTypeId: v.leaveTypeId as number,
        fromDate: v.fromDate,
        toDate: v.halfDay ? v.fromDate : v.toDate,
        halfDay: v.halfDay,
        reason: v.reason || null,
      })
      .subscribe({
        next: () => {
          this.form.reset({ leaveTypeId: null, fromDate: '', toDate: '', halfDay: false, reason: '' });
          this.refresh();
        },
        error: (err) => this.snack.open(err?.error?.detail ?? 'Could not apply', 'Dismiss', { duration: 5000 }),
      });
  }

  decide(app: LeaveApplicationDto, action: 'approve' | 'reject' | 'cancel'): void {
    this.api.decideLeave(app.id, action, this.decisionCtrl.value.trim() || null).subscribe({
      next: () => {
        this.decisionCtrl.setValue('');
        this.refresh();
      },
      error: (err) => this.snack.open(err?.error?.detail ?? 'Action failed', 'Dismiss', { duration: 5000 }),
    });
  }

  private refresh(): void {
    this.loadBalances();
    this.loadApplications();
    this.loadCalendar();
  }
}
