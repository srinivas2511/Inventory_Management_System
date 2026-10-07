import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface ReasonDialogData {
  title: string;
  message: string;
  label: string;
  confirmLabel: string;
  maxLength?: number;
}

/** Asks for a reason before an action that is kept in the audit trail. Closes with the trimmed reason, or nothing. */
@Component({
  selector: 'app-reason-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p class="mat-body-1">{{ data.message }}</p>
      <form [formGroup]="form" id="reason-form" (ngSubmit)="confirm()" novalidate>
        <mat-form-field appearance="outline" class="reason">
          <mat-label>{{ data.label }}</mat-label>
          <textarea matInput rows="3" formControlName="reason" [attr.maxlength]="maxLength"></textarea>
          @if (form.controls.reason.hasError('required') && form.controls.reason.touched) {
            <mat-error>A reason is required</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" form="reason-form" type="submit">{{ data.confirmLabel }}</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .reason {
        width: 100%;
        min-width: 360px;
      }
    `,
  ],
})
export class ReasonDialogComponent {
  protected readonly data = inject<ReasonDialogData>(MAT_DIALOG_DATA);
  protected readonly maxLength = this.data.maxLength ?? 200;

  private readonly dialogRef = inject(MatDialogRef<ReasonDialogComponent, string>);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({
    reason: ['', [Validators.required, Validators.pattern(/\S/)]],
  });

  protected confirm(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.dialogRef.close(this.form.controls.reason.value.trim());
  }
}
