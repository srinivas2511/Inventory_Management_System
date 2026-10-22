import { Component, OnInit, inject, signal } from '@angular/core';

import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { HrmApi, TrainingDto } from './hrm.api';

@Component({
  selector: 'app-training-renewals',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    IstDatePipe,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSnackBarModule,
    MatTableModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Training and certification renewals</h2>
    </div>
    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Due within (days)</mat-label>
        <input matInput type="number" min="0" [formControl]="daysCtrl" (keyup.enter)="load()" />
      </mat-form-field>
      <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon> Refresh</button>
    </div>
    <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
      <ng-container matColumnDef="employee">
        <th mat-header-cell *matHeaderCellDef>Employee</th>
        <td mat-cell *matCellDef="let r">
          <a [routerLink]="['/hrm/employees', r.employeeId]">{{ r.employeeCode }} {{ r.employeeName }}</a>
        </td>
      </ng-container>
      <ng-container matColumnDef="programme">
        <th mat-header-cell *matHeaderCellDef>Programme</th>
        <td mat-cell *matCellDef="let r">{{ r.programmeName }}</td>
      </ng-container>
      <ng-container matColumnDef="certificate">
        <th mat-header-cell *matHeaderCellDef>Certificate</th>
        <td mat-cell *matCellDef="let r">{{ r.certificateNo }}</td>
      </ng-container>
      <ng-container matColumnDef="due">
        <th mat-header-cell *matHeaderCellDef>Renewal due</th>
        <td mat-cell *matCellDef="let r" [class.overdue]="isOverdue(r)">
          {{ r.renewalDueOn | istDate: 'dd MMM yyyy' }}
          @if (isOverdue(r)) {
            (overdue)
          }
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell" [attr.colspan]="columns.length" style="text-align: center; padding: 2rem">
          No renewals due
        </td>
      </tr>
    </table>
  `,
  styles: `
    .filter-bar {
      display: flex;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .overdue {
      color: #c62828;
      font-weight: 600;
    }
  `,
})
export class TrainingRenewalsComponent implements OnInit {
  private api = inject(HrmApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  columns = ['employee', 'programme', 'certificate', 'due'];
  rows = signal<TrainingDto[]>([]);
  daysCtrl = this.fb.nonNullable.control(30);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.getRenewalsDue(this.daysCtrl.value).subscribe({
      next: (r) => this.rows.set(r),
      error: () => this.snack.open('Failed to load renewals', 'Dismiss', { duration: 3000 }),
    });
  }

  isOverdue(t: TrainingDto): boolean {
    return !!t.renewalDueOn && t.renewalDueOn < new Date().toLocaleDateString('en-CA');
  }
}
