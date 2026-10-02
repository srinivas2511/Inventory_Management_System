import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { errorText } from '../forms/server-errors';

export interface ReasonDialogData {
  title: string;
  message?: string;
  label?: string;
  confirmLabel?: string;
  danger?: boolean;
  /** When true the action cannot proceed without a reason (adjustments, cancellations, overrides). */
  required?: boolean;
}

/** Collects a free-text reason with an action. Closes with the text (possibly empty if optional) or undefined if cancelled. */
@Component({
  selector: 'app-reason-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <form (ngSubmit)="submit()">
      <mat-dialog-content>
        @if (data.message) {
          <p class="message">{{ data.message }}</p>
        }
        <mat-form-field appearance="outline" class="full">
          <mat-label>{{ data.label ?? 'Reason' }}</mat-label>
          <textarea matInput rows="3" maxlength="500" [formControl]="reason" data-testid="reason-input"></textarea>
          @if (reason.invalid && reason.touched) {
            <mat-error>{{ error(reason) }}</mat-error>
          }
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" (click)="cancel()" data-testid="reason-cancel">Cancel</button>
        <button mat-flat-button type="submit" [color]="data.danger ? 'warn' : 'primary'" data-testid="reason-ok">
          {{ data.confirmLabel ?? 'Confirm' }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .full {
      width: 100%;
    }
    .message {
      margin-top: 0;
    }
  `,
})
export class ReasonDialogComponent {
  protected readonly data = inject<ReasonDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<ReasonDialogComponent, string | undefined>>(MatDialogRef);
  protected readonly reason = new FormControl('', {
    nonNullable: true,
    validators: this.data.required ? [Validators.required, Validators.pattern(/\S/)] : [],
  });
  protected readonly error = errorText;

  protected submit(): void {
    this.reason.markAsTouched();
    if (this.reason.valid) {
      this.ref.close(this.reason.value.trim());
    }
  }

  protected cancel(): void {
    this.ref.close(undefined);
  }
}
