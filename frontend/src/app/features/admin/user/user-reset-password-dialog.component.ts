import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { passwordMeetsPolicy } from '../../../core/auth/password-policy';
import { problemMessage } from '../../../core/models/problem.model';
import { PasswordHintsComponent } from '../../../shared/components/password-hints.component';
import { AdminApi, AdminResetPasswordResponse, UserDto } from '../admin.api';

export interface UserResetPasswordData {
  user: UserDto;
}

/**
 * Two steps in one dialog: ask for a reason (and optionally a password the administrator chooses), then, when the
 * server generated the password, show it once in a copyable field. The dialog cannot be dismissed by accident while
 * the one-time password is on screen.
 */
@Component({
  selector: 'app-user-reset-password-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    PasswordHintsComponent,
  ],
  template: `
    @if (result(); as done) {
      <h2 mat-dialog-title>Password reset for {{ data.user.fullName }}</h2>
      <mat-dialog-content>
        @if (done.generated) {
          <p class="mat-body-1">Give {{ data.user.username }} this temporary password.</p>
          <div class="secret">
            <mat-form-field appearance="outline" class="grow">
              <mat-label>Temporary password</mat-label>
              <input
                matInput
                readonly
                [value]="done.temporaryPassword"
                (focus)="selectAll($event)"
                data-testid="temporary-password"
              />
            </mat-form-field>
            <button mat-stroked-button type="button" (click)="copy(done.temporaryPassword)" data-testid="copy-password">
              <mat-icon>content_copy</mat-icon>
              {{ copied() ? 'Copied' : 'Copy' }}
            </button>
          </div>
          <p class="mat-body-2 warning" role="note" data-testid="shown-once">
            This password is shown once and cannot be looked up later. Close this window only after you have passed it
            on.
          </p>
        } @else {
          <p class="mat-body-1">The password you chose is set.</p>
        }
        <p class="mat-body-2 note">
          {{ data.user.username }} is signed out everywhere and must choose a new password the next time they sign in.
        </p>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-flat-button color="primary" (click)="finish()" data-testid="reset-done">Done</button>
      </mat-dialog-actions>
    } @else {
      <h2 mat-dialog-title>Reset password for {{ data.user.fullName }}</h2>
      <form [formGroup]="form" (ngSubmit)="submit()">
        <mat-dialog-content>
          <p class="mat-body-1">
            {{ data.user.username }} is signed out everywhere and must choose a new password the next time they sign in.
          </p>
          <mat-form-field appearance="outline" class="full">
            <mat-label>Reason</mat-label>
            <input matInput formControlName="reason" maxlength="300" required data-testid="reset-reason" />
            <mat-hint>Kept in the audit trail. The password is never recorded.</mat-hint>
          </mat-form-field>
          <mat-checkbox formControlName="chooseOwn" data-testid="reset-own"
            >I will choose the password myself</mat-checkbox
          >
          @if (form.controls.chooseOwn.value) {
            <mat-form-field appearance="outline" class="full">
              <mat-label>Temporary password</mat-label>
              <input
                matInput
                type="password"
                formControlName="temporaryPassword"
                autocomplete="new-password"
                data-testid="reset-password-input"
              />
            </mat-form-field>
            <app-password-hints [password]="form.controls.temporaryPassword.value" [username]="data.user.username" />
          } @else {
            <p class="mat-body-2 note">A strong one-time password is generated and shown to you once.</p>
          }
          @if (errorMessage()) {
            <p class="form-error mat-body-2" role="alert" data-testid="reset-error">{{ errorMessage() }}</p>
          }
        </mat-dialog-content>
        <mat-dialog-actions align="end">
          <button mat-button type="button" mat-dialog-close>Cancel</button>
          <button mat-flat-button color="primary" type="submit" [disabled]="!canSubmit()" data-testid="reset-submit">
            Reset password
          </button>
        </mat-dialog-actions>
      </form>
    }
  `,
  styles: [
    `
      .full {
        width: 100%;
      }
      .secret {
        display: flex;
        align-items: flex-start;
        gap: 12px;
      }
      .grow {
        flex: 1;
      }
      .note {
        color: var(--mat-sys-on-surface-variant, rgba(0, 0, 0, 0.6));
      }
      .warning {
        color: var(--mat-warn-color, #b00020);
      }
      .form-error {
        color: var(--mat-warn-color, #f44336);
      }
    `,
  ],
})
export class UserResetPasswordDialogComponent {
  protected readonly data = inject<UserResetPasswordData>(MAT_DIALOG_DATA);
  protected readonly result = signal<AdminResetPasswordResponse | null>(null);
  protected readonly saving = signal(false);
  protected readonly copied = signal(false);
  protected readonly errorMessage = signal('');

  private readonly api = inject(AdminApi);
  private readonly dialogRef = inject(MatDialogRef<UserResetPasswordDialogComponent, boolean>);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    reason: ['', [Validators.required, Validators.maxLength(300)]],
    chooseOwn: [false],
    temporaryPassword: [''],
  });

  protected canSubmit(): boolean {
    const v = this.form.getRawValue();
    const reasonOk = v.reason.trim().length > 0;
    const passwordOk = !v.chooseOwn || passwordMeetsPolicy(v.temporaryPassword, this.data.user.username);
    return reasonOk && passwordOk && !this.saving();
  }

  protected submit(): void {
    if (!this.canSubmit()) {
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set('');
    this.api
      .resetPassword(this.data.user.id, {
        reason: v.reason.trim(),
        ...(v.chooseOwn ? { temporaryPassword: v.temporaryPassword } : {}),
      })
      .subscribe({
        next: (res) => {
          this.saving.set(false);
          this.result.set(res);
          this.dialogRef.disableClose = true;
        },
        error: (err) => {
          this.saving.set(false);
          this.errorMessage.set(problemMessage(err, 'The password could not be reset.'));
        },
      });
  }

  protected async copy(password: string | null): Promise<void> {
    if (!password) {
      return;
    }
    try {
      await navigator.clipboard.writeText(password);
      this.copied.set(true);
    } catch {
      this.copied.set(false);
    }
  }

  protected selectAll(event: Event): void {
    (event.target as HTMLInputElement).select();
  }

  protected finish(): void {
    this.dialogRef.close(true);
  }
}
