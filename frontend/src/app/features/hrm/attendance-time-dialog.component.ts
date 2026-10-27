import { Component, Inject, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AttendanceStatus } from './hrm.api';

export interface AttendanceTimeDialogData {
  employeeName: string;
  attendanceDate: string; // YYYY-MM-DD
  status: AttendanceStatus;
}

export interface AttendanceTimeDialogResult {
  checkIn: string | null;
  checkOut: string | null;
  remarks: string | null;
}

function toIst(date: string, time: string): string {
  return `${date}T${time}:00+05:30`;
}

@Component({
  selector: 'app-attendance-time-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>Record Attendance — {{ data.status }}</h2>
    <mat-dialog-content>
      <p style="margin:0 0 12px">
        <strong>{{ data.employeeName }}</strong> &mdash; {{ data.attendanceDate }}
      </p>
      <form [formGroup]="form" style="display:flex;flex-direction:column;gap:12px;min-width:300px">
        <mat-form-field appearance="outline">
          <mat-label>Check-in time *</mat-label>
          <input matInput type="time" formControlName="checkIn" />
          @if (form.get('checkIn')?.hasError('required') && form.get('checkIn')?.touched) {
            <mat-error>Check-in time is required</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Check-out time (optional)</mat-label>
          <input matInput type="time" formControlName="checkOut" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Remarks (optional)</mat-label>
          <input matInput formControlName="remarks" maxlength="200" />
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="form.invalid" (click)="save()">Save</button>
    </mat-dialog-actions>
  `,
})
export class AttendanceTimeDialogComponent {
  private fb = inject(FormBuilder);

  form = this.fb.nonNullable.group({
    checkIn: ['', Validators.required],
    checkOut: [''],
    remarks: [''],
  });

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: AttendanceTimeDialogData,
    private ref: MatDialogRef<AttendanceTimeDialogComponent, AttendanceTimeDialogResult>,
  ) {}

  save(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.ref.close({
      checkIn: v.checkIn ? toIst(this.data.attendanceDate, v.checkIn) : null,
      checkOut: v.checkOut ? toIst(this.data.attendanceDate, v.checkOut) : null,
      remarks: v.remarks || null,
    });
  }
}
