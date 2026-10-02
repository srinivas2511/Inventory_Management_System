import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ToastService } from '../../../core/notification/toast.service';
import { applyFieldErrors, errorText, problemOf } from '../../../shared/forms/server-errors';
import { UsersApi } from '../api/users.api';
import { UserSummary } from '../models';

/** Sets a temporary password: unlocks the account, ends its sessions and forces a change at next sign-in. */
@Component({
  selector: 'app-reset-password-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>Reset password of {{ data.username }}</h2>
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <mat-dialog-content>
        <p class="note">All their sessions end and they must choose a new password at the next sign-in.</p>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Temporary password</mat-label>
          <input
            matInput
            type="password"
            formControlName="temporaryPassword"
            autocomplete="new-password"
            data-testid="temporary-password"
          />
          @if (form.controls.temporaryPassword.touched && form.controls.temporaryPassword.invalid) {
            <mat-error>{{ error(form.controls.temporaryPassword) }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Reason (optional)</mat-label>
          <input matInput formControlName="reason" maxlength="500" />
        </mat-form-field>
        @if (message()) {
          <p class="message" role="alert" data-testid="form-error">{{ message() }}</p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancel</button>
        <button mat-flat-button color="primary" type="submit" [disabled]="busy()" data-testid="save">
          Reset password
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .full {
      width: 100%;
    }
    .note {
      margin-top: 0;
    }
    .message {
      color: #c62828;
      margin: 0;
    }
  `,
})
export class ResetPasswordDialogComponent {
  protected readonly data = inject<UserSummary>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<ResetPasswordDialogComponent, boolean>>(MatDialogRef);
  private readonly api = inject(UsersApi);
  private readonly toast = inject(ToastService);

  protected readonly busy = signal(false);
  protected readonly message = signal('');
  protected readonly error = errorText;
  protected readonly form = inject(FormBuilder).nonNullable.group({
    temporaryPassword: ['', [Validators.required, Validators.maxLength(128)]],
    reason: [''],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { temporaryPassword, reason } = this.form.getRawValue();
    this.busy.set(true);
    this.message.set('');
    this.api.resetPassword(this.data.id, temporaryPassword, reason.trim()).subscribe({
      next: () => {
        this.toast.success(`Password of ${this.data.username} reset.`);
        this.ref.close(true);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const problem = problemOf(error);
        const unmatched = applyFieldErrors(this.form, problem);
        this.message.set(
          unmatched.length ? unmatched.join(' ') : (problem?.detail ?? 'The password could not be reset.'),
        );
      },
    });
  }
}
