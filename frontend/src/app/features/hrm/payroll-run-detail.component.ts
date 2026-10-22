import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { SessionStore } from '../../core/auth/session.store';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HrmApi, MONTH_NAMES, PayrollRunDto, PayrollSlipDto } from './hrm.api';

@Component({
  selector: 'app-payroll-run-detail',
  standalone: true,
  imports: [
    IstDatePipe,
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTableModule,
    MatTooltipModule,
    StatusBadgeComponent,
  ],
  template: `
    @if (run(); as r) {
      <div class="page-header">
        <a mat-icon-button routerLink="/hrm/payroll" aria-label="Back"><mat-icon>arrow_back</mat-icon></a>
        <h2 class="mat-h2">{{ r.runNumber }} - {{ monthNames[r.periodMonth - 1] }} {{ r.periodYear }}</h2>
        <app-status-badge [status]="r.status" />
      </div>

      <dl class="facts">
        <dt>Employees</dt>
        <dd>{{ r.totalEmployees ?? '-' }}</dd>
        <dt>Total gross</dt>
        <dd>{{ r.totalGross | number: '1.2-2' }}</dd>
        <dt>Total net</dt>
        <dd>{{ r.totalNet | number: '1.2-2' }}</dd>
        @if (r.journalNumber) {
          <dt>Payroll journal</dt>
          <dd>{{ r.journalNumber }} (approved {{ r.completedAt | istDate: 'dd MMM yyyy HH:mm' }})</dd>
        }
        @if (r.paidAt) {
          <dt>Paid</dt>
          <dd>{{ r.paidAt | istDate: 'dd MMM yyyy HH:mm' }}</dd>
        }
      </dl>

      <div class="actions">
        @if (r.allowedActions.includes('CALCULATE')) {
          <button mat-flat-button color="primary" [disabled]="busy()" (click)="act('calculate')">
            <mat-icon>calculate</mat-icon> {{ r.totalEmployees ? 'Recalculate' : 'Calculate slips' }}
          </button>
        }
        @if (r.allowedActions.includes('APPROVE') && r.totalEmployees) {
          <span
            [matTooltip]="
              ownRun(r) ? 'You initiated this run, so another user must approve it and post the journal' : ''
            "
          >
            <button mat-flat-button color="primary" [disabled]="busy() || ownRun(r)" (click)="act('approve')">
              <mat-icon>verified</mat-icon> Approve and post journal
            </button>
          </span>
        }
        @if (r.allowedActions.includes('MARK_PAID')) {
          <button mat-flat-button color="primary" [disabled]="busy()" (click)="act('pay')">
            <mat-icon>payments</mat-icon> Mark paid
          </button>
        }
      </div>

      <table mat-table [dataSource]="slips()" class="mat-elevation-z1">
        <ng-container matColumnDef="employee">
          <th mat-header-cell *matHeaderCellDef>Employee</th>
          <td mat-cell *matCellDef="let s">{{ s.employeeCode }} {{ s.employeeName }}</td>
        </ng-container>
        <ng-container matColumnDef="days">
          <th mat-header-cell *matHeaderCellDef>Paid days</th>
          <td mat-cell *matCellDef="let s">
            {{ s.paidDays | number: '1.0-1' }} / {{ s.workingDays | number: '1.0-1' }}
          </td>
        </ng-container>
        <ng-container matColumnDef="gross">
          <th mat-header-cell *matHeaderCellDef>Gross</th>
          <td mat-cell *matCellDef="let s">{{ s.grossPay | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="deductions">
          <th mat-header-cell *matHeaderCellDef>Deductions</th>
          <td mat-cell *matCellDef="let s">{{ s.totalDeductions | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="net">
          <th mat-header-cell *matHeaderCellDef>Net pay</th>
          <td mat-cell *matCellDef="let s">{{ s.netPay | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="slip">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let s">
            <a mat-button [routerLink]="['/hrm/payroll/slips', s.id]"><mat-icon>receipt_long</mat-icon> Pay slip</a>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align: center; padding: 2rem">
            No slips yet - calculate the run to generate them
          </td>
        </tr>
      </table>
    } @else {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    }
  `,
  styles: `
    .page-header {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .facts {
      display: grid;
      grid-template-columns: max-content 1fr;
      gap: 4px 16px;
      margin: 8px 0 16px;
    }
    dt {
      opacity: 0.7;
    }
    dd {
      margin: 0;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-bottom: 16px;
    }
  `,
})
export class PayrollRunDetailComponent implements OnInit {
  private api = inject(HrmApi);
  private session = inject(SessionStore);
  private route = inject(ActivatedRoute);
  private snack = inject(MatSnackBar);

  monthNames = MONTH_NAMES;
  columns = ['employee', 'days', 'gross', 'deductions', 'net', 'slip'];
  run = signal<PayrollRunDto | null>(null);
  slips = signal<PayrollSlipDto[]>([]);
  busy = signal(false);

  private get id(): number {
    return Number(this.route.snapshot.paramMap.get('id'));
  }

  ngOnInit(): void {
    this.reload();
  }

  private reload(): void {
    this.api.getPayrollRun(this.id).subscribe({
      next: (r) => this.run.set(r),
      error: () => this.snack.open('Failed to load the payroll run', 'Dismiss', { duration: 3000 }),
    });
    this.api.getPayrollSlips(this.id).subscribe({ next: (s) => this.slips.set(s) });
  }

  /** The person who initiated the run cannot approve it (the server enforces this too). */
  ownRun(run: PayrollRunDto): boolean {
    const userId = this.session.user()?.id;
    return userId != null && run.initiatedBy === userId;
  }

  act(action: 'calculate' | 'approve' | 'pay'): void {
    this.busy.set(true);
    this.api.runPayrollAction(this.id, action).subscribe({
      next: () => {
        this.busy.set(false);
        this.reload();
      },
      error: (err) => {
        this.busy.set(false);
        this.snack.open(err?.error?.detail ?? 'Action failed', 'Dismiss', { duration: 6000 });
      },
    });
  }
}
