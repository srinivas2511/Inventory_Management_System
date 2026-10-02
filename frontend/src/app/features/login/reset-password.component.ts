import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { AuthApi } from '../../core/auth/auth.api';
import { applyFieldErrors, errorText, problemOf } from '../../shared/forms/server-errors';
import { matchingFields } from '../../shared/forms/validators';
import { AuthLayoutComponent } from './auth-layout.component';

/** Sets a new password from the single-use link in the reset e-mail ({@code /reset-password/:token}). */
@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule, AuthLayoutComponent],
  template: `
    <app-auth-layout heading="Choose a new password">
      @if (done()) {
        <p data-testid="done">Your password has been changed. You can sign in with it now.</p>
        <p class="links"><a routerLink="/login" data-testid="to-login">Go to sign in</a></p>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <p class="hint">
            At least 12 characters with upper and lower case letters, a digit and a symbol. It must differ from your
            recent passwords.
          </p>
          <mat-form-field appearance="outline" class="full">
            <mat-label>New password</mat-label>
            <input
              matInput
              type="password"
              formControlName="newPassword"
              autocomplete="new-password"
              data-testid="new-password"
            />
            @if (form.controls.newPassword.touched && form.controls.newPassword.invalid) {
              <mat-error>{{ error(form.controls.newPassword) }}</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline" class="full">
            <mat-label>Repeat the new password</mat-label>
            <input
              matInput
              type="password"
              formControlName="confirm"
              autocomplete="new-password"
              data-testid="confirm-password"
            />
            @if (form.controls.confirm.touched && form.controls.confirm.invalid) {
              <mat-error>{{ error(form.controls.confirm) }}</mat-error>
            }
          </mat-form-field>
          @if (linkInvalid()) {
            <p class="message" role="alert" data-testid="link-invalid">
              This reset link is invalid or has expired.
              <a routerLink="/forgot-password">Request a new one.</a>
            </p>
          }
          @if (message()) {
            <p class="message" role="alert" data-testid="reset-error">{{ message() }}</p>
          }
          <button
            mat-flat-button
            color="primary"
            type="submit"
            class="full"
            [disabled]="busy()"
            data-testid="reset-submit"
          >
            Change password
          </button>
        </form>
      }
    </app-auth-layout>
  `,
  styles: `
    .full {
      width: 100%;
    }
    .hint {
      margin-top: 0;
    }
    .message {
      color: #c62828;
      margin: 0 0 12px;
    }
    .links {
      text-align: center;
      margin-bottom: 0;
    }
  `,
})
export class ResetPasswordComponent {
  /** The token from the URL ({@code withComponentInputBinding}). */
  readonly token = input.required<string>();

  private readonly auth = inject(AuthApi);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly form = this.fb.group(
    { newPassword: ['', [Validators.required, Validators.maxLength(128)]], confirm: ['', [Validators.required]] },
    { validators: matchingFields('newPassword', 'confirm') },
  );
  protected readonly busy = signal(false);
  protected readonly done = signal(false);
  protected readonly linkInvalid = signal(false);
  protected readonly message = signal('');
  protected readonly error = errorText;

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.message.set('');
    this.linkInvalid.set(false);
    this.auth.resetPassword(this.token(), this.form.controls.newPassword.value).subscribe({
      next: () => {
        this.busy.set(false);
        this.done.set(true);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const problem = problemOf(error);
        if (problem?.fieldErrors?.some((f) => f.field === 'token')) {
          this.linkInvalid.set(true);
          return;
        }
        const unmatched = applyFieldErrors(this.form, problem);
        this.message.set(
          problem?.status === 400
            ? unmatched.join(' ')
            : error instanceof HttpErrorResponse && error.status === 429
              ? 'Too many requests. Please wait a few minutes and try again.'
              : 'The password could not be changed. Please try again.',
        );
      },
    });
  }
}
