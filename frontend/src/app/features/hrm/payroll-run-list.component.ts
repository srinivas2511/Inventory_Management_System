import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HrmApi, MONTH_NAMES, PayrollRunDto } from './hrm.api';

@Component({
  selector: 'app-payroll-run-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Payroll</h2>
    </div>

    <form class="edit-form" [formGroup]="form" (ngSubmit)="initiate()">
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
      <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">
        <mat-icon>add</mat-icon> Start payroll run
      </button>
    </form>

    <table mat-table [dataSource]="runs()" class="mat-elevation-z1">
      <ng-container matColumnDef="run">
        <th mat-header-cell *matHeaderCellDef>Run</th>
        <td mat-cell *matCellDef="let r">{{ r.runNumber }}</td>
      </ng-container>
      <ng-container matColumnDef="period">
        <th mat-header-cell *matHeaderCellDef>Period</th>
        <td mat-cell *matCellDef="let r">{{ monthNames[r.periodMonth - 1] }} {{ r.periodYear }}</td>
      </ng-container>
      <ng-container matColumnDef="employees">
        <th mat-header-cell *matHeaderCellDef>Employees</th>
        <td mat-cell *matCellDef="let r">{{ r.totalEmployees }}</td>
      </ng-container>
      <ng-container matColumnDef="gross">
        <th mat-header-cell *matHeaderCellDef>Gross</th>
        <td mat-cell *matCellDef="let r">{{ r.totalGross | number: '1.2-2' }}</td>
      </ng-container>
      <ng-container matColumnDef="net">
        <th mat-header-cell *matHeaderCellDef>Net</th>
        <td mat-cell *matCellDef="let r">{{ r.totalNet | number: '1.2-2' }}</td>
      </ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns" class="clickable" (click)="open(row)"></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell" [attr.colspan]="columns.length" style="text-align: center; padding: 2rem">
          No payroll runs yet
        </td>
      </tr>
    </table>
  `,
  styles: `
    .edit-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .clickable {
      cursor: pointer;
    }
  `,
})
export class PayrollRunListComponent implements OnInit {
  private api = inject(HrmApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);
  private router = inject(Router);

  monthNames = MONTH_NAMES;
  columns = ['run', 'period', 'employees', 'gross', 'net', 'status'];
  runs = signal<PayrollRunDto[]>([]);
  form = this.fb.nonNullable.group({
    month: [new Date().getMonth() + 1, Validators.required],
    year: [new Date().getFullYear(), Validators.required],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.getPayrollRuns().subscribe({
      next: (r) => this.runs.set(r),
      error: () => this.snack.open('Failed to load payroll runs', 'Dismiss', { duration: 3000 }),
    });
  }

  initiate(): void {
    const v = this.form.getRawValue();
    this.api.initiatePayroll(v.month, v.year).subscribe({
      next: (run) => this.router.navigate(['/hrm/payroll', run.id]),
      error: (err) => this.snack.open(err?.error?.detail ?? 'Could not start the run', 'Dismiss', { duration: 5000 }),
    });
  }

  open(run: PayrollRunDto): void {
    this.router.navigate(['/hrm/payroll', run.id]);
  }
}
