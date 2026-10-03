import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { HrmApi, ShiftDto } from './hrm.api';

@Component({
  selector: 'app-shift-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSnackBarModule,
    MatTableModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Shifts</h2>
    </div>

    <form *appHasPermission="'ATTENDANCE_MANAGE'" class="edit-form" [formGroup]="form" (ngSubmit)="create()">
      <mat-form-field appearance="outline">
        <mat-label>Code</mat-label>
        <input matInput maxlength="10" formControlName="shiftCode" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Name</mat-label>
        <input matInput maxlength="60" formControlName="name" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Start</mat-label>
        <input matInput type="time" formControlName="startTime" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>End</mat-label>
        <input matInput type="time" formControlName="endTime" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Grace (minutes)</mat-label>
        <input matInput type="number" min="0" formControlName="graceMinutes" />
      </mat-form-field>
      <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">
        <mat-icon>add</mat-icon> Add shift
      </button>
    </form>

    <table mat-table [dataSource]="shifts()" class="mat-elevation-z1">
      <ng-container matColumnDef="code">
        <th mat-header-cell *matHeaderCellDef>Code</th>
        <td mat-cell *matCellDef="let s">{{ s.shiftCode }}</td>
      </ng-container>
      <ng-container matColumnDef="name">
        <th mat-header-cell *matHeaderCellDef>Name</th>
        <td mat-cell *matCellDef="let s">{{ s.name }}</td>
      </ng-container>
      <ng-container matColumnDef="hours">
        <th mat-header-cell *matHeaderCellDef>Hours</th>
        <td mat-cell *matCellDef="let s">{{ s.startTime.slice(0, 5) }} - {{ s.endTime.slice(0, 5) }}</td>
      </ng-container>
      <ng-container matColumnDef="grace">
        <th mat-header-cell *matHeaderCellDef>Grace</th>
        <td mat-cell *matCellDef="let s">{{ s.graceMinutes }} min</td>
      </ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let s"><app-status-badge [status]="s.active ? 'ACTIVE' : 'INACTIVE'" /></td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let s">
          <button *appHasPermission="'ATTENDANCE_MANAGE'" mat-button (click)="toggle(s)">
            {{ s.active ? 'Deactivate' : 'Activate' }}
          </button>
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
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
  `,
})
export class ShiftListComponent implements OnInit {
  private api = inject(HrmApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  columns = ['code', 'name', 'hours', 'grace', 'status', 'actions'];
  shifts = signal<ShiftDto[]>([]);
  form = this.fb.nonNullable.group({
    shiftCode: ['', [Validators.required, Validators.maxLength(10)]],
    name: ['', Validators.required],
    startTime: ['', Validators.required],
    endTime: ['', Validators.required],
    graceMinutes: [10],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.getShifts().subscribe({
      next: (s) => this.shifts.set(s),
      error: () => this.snack.open('Failed to load shifts', 'Dismiss', { duration: 3000 }),
    });
  }

  create(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.api.createShift({ ...v, active: true }).subscribe({
      next: () => {
        this.form.reset({ shiftCode: '', name: '', startTime: '', endTime: '', graceMinutes: 10 });
        this.load();
      },
      error: (err) => this.snack.open(err?.error?.detail ?? 'Could not add shift', 'Dismiss', { duration: 5000 }),
    });
  }

  toggle(s: ShiftDto): void {
    this.api
      .updateShift(s.id, {
        shiftCode: s.shiftCode,
        name: s.name,
        startTime: s.startTime,
        endTime: s.endTime,
        graceMinutes: s.graceMinutes,
        active: !s.active,
      })
      .subscribe({
        next: () => this.load(),
        error: (err) => this.snack.open(err?.error?.detail ?? 'Could not update', 'Dismiss', { duration: 5000 }),
      });
  }
}
