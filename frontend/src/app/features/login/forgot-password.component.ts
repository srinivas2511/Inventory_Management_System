import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { AuthApi } from '../../core/auth/auth.api';
import { errorText } from '../../shared/forms/server-errors';
import { AuthLayoutComponent } from './auth-layout.component';

/** Asks for a reset e-mail. The answer is the same whether or not the address is known (no account enumeration). */
@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatInputModule, AuthLayoutComponent],
  template: `
    <app-auth-layout heading="Reset your password">
      @if (sent()) {
        <p data-testid="sent">
          If that e-mail address belongs to an active account, a reset link is on its way. The link works once and
          expires after 30 minutes.
        </p>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <p class="hint">Enter the e-mail address of your account.</p>
          <mat-form-field appearance="outline" class="full">
            <mat-label>E-mail</mat-label>
            <input matInput type="email" formControlName="email" autocomplete="email" data-testid="email" />
            @if (form.controls.email.touched && form.controls.email.invalid) {
              <mat-error>{{ error(form.controls.email) }}</mat-error>
            }
          </mat-form-field>
          @if (message()) {
            <p class="message" role="alert" data-testid="forgot-error">{{ message() }}</p>
          }
          <button mat-flat-button color="primary" type="submit" class="full" [disabled]="busy()" data-testid="send">
            Send reset link
          </button>
        </form>
      }
      <p class="links"><a routerLink="/login">Back to sign in</a></p>
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
export class ForgotPasswordComponent {
  private readonly auth = inject(AuthApi);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(160)]],
  });
  protected readonly busy = signal(false);
  protected readonly sent = signal(false);
  protected readonly message = signal('');
  protected readonly error = errorText;

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.message.set('');
    this.auth.forgotPassword(this.form.controls.email.value.trim()).subscribe({
      next: () => {
        this.busy.set(false);
        this.sent.set(true);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.message.set(
          error instanceof HttpErrorResponse && error.status === 429
            ? 'Too many requests. Please wait a few minutes and try again.'
            : 'The request could not be sent. Please try again.',
        );
      },
    });
  }
}
