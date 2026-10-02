import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { SessionStore } from '../../core/auth/session.store';
import { applyFieldErrors, errorText, problemOf } from '../../shared/forms/server-errors';
import { matchingFields } from '../../shared/forms/validators';
import { AuthLayoutComponent } from './auth-layout.component';

type Step = 'credentials' | 'newPassword';

/**
 * Sign-in. An account flagged "must change password" gets no session from the server, so after the right
 * credentials this page asks for a new password and then signs the user in with it (DESIGN.md section 5.1).
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    AuthLayoutComponent,
  ],
  template: `
    <app-auth-layout [heading]="step() === 'credentials' ? 'Sign in' : 'Choose a new password'">
      @if (step() === 'credentials') {
        <form [formGroup]="credentials" (ngSubmit)="signIn()" novalidate>
          <mat-form-field appearance="outline" class="full">
            <mat-label>Username</mat-label>
            <input matInput formControlName="username" autocomplete="username" data-testid="username" />
            @if (credentials.controls.username.touched && credentials.controls.username.invalid) {
              <mat-error>{{ error(credentials.controls.username) }}</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline" class="full">
            <mat-label>Password</mat-label>
            <input
              matInput
              type="password"
              formControlName="password"
              autocomplete="current-password"
              data-testid="password"
            />
            @if (credentials.controls.password.touched && credentials.controls.password.invalid) {
              <mat-error>{{ error(credentials.controls.password) }}</mat-error>
            }
          </mat-form-field>
          @if (message()) {
            <p class="message" role="alert" data-testid="login-error">{{ message() }}</p>
          }
          <button mat-flat-button color="primary" type="submit" class="full" [disabled]="busy()" data-testid="sign-in">
            Sign in
          </button>
        </form>
        <p class="links"><a routerLink="/forgot-password" data-testid="forgot-link">Forgot your password?</a></p>
      } @else {
        <p class="hint" data-testid="change-hint">
          Your password must be changed before you continue. Use at least 12 characters with upper and lower case
          letters, a digit and a symbol, and do not include your username.
        </p>
        <form [formGroup]="change" (ngSubmit)="changePassword()" novalidate>
          <mat-form-field appearance="outline" class="full">
            <mat-label>New password</mat-label>
            <input
              matInput
              type="password"
              formControlName="newPassword"
              autocomplete="new-password"
              data-testid="new-password"
            />
            @if (change.controls.newPassword.touched && change.controls.newPassword.invalid) {
              <mat-error>{{ error(change.controls.newPassword) }}</mat-error>
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
            @if (change.controls.confirm.touched && change.controls.confirm.invalid) {
              <mat-error>{{ error(change.controls.confirm) }}</mat-error>
            }
          </mat-form-field>
          @if (message()) {
            <p class="message" role="alert" data-testid="change-error">{{ message() }}</p>
          }
          <button
            mat-flat-button
            color="primary"
            type="submit"
            class="full"
            [disabled]="busy()"
            data-testid="change-submit"
          >
            Save and sign in
          </button>
        </form>
      }
    </app-auth-layout>
  `,
  styles: `
    .full {
      width: 100%;
    }
    .message {
      color: #c62828;
      margin: 0 0 12px;
    }
    .hint {
      margin-top: 0;
    }
    .links {
      text-align: center;
      margin-bottom: 0;
    }
  `,
})
export class LoginComponent {
  /** Where to go after sign-in (set by the guards as a query parameter). */
  readonly returnUrl = input<string | undefined>();

  private readonly session = inject(SessionStore);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly step = signal<Step>('credentials');
  protected readonly busy = signal(false);
  protected readonly message = signal('');
  protected readonly error = errorText;

  protected readonly credentials = this.fb.group({
    username: ['', [Validators.required, Validators.maxLength(50)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });
  protected readonly change = this.fb.group(
    {
      newPassword: ['', [Validators.required, Validators.maxLength(128)]],
      confirm: ['', [Validators.required]],
    },
    { validators: matchingFields('newPassword', 'confirm') },
  );

  protected signIn(): void {
    if (this.credentials.invalid) {
      this.credentials.markAllAsTouched();
      return;
    }
    const { username, password } = this.credentials.getRawValue();
    this.start();
    this.session.login(username.trim(), password).subscribe({
      next: (outcome) => {
        this.busy.set(false);
        if (outcome === 'authenticated') {
          this.finish();
        } else {
          this.step.set('newPassword');
        }
      },
      error: (error: unknown) => this.fail(error, 'Invalid username or password.'),
    });
  }

  protected changePassword(): void {
    if (this.change.invalid) {
      this.change.markAllAsTouched();
      return;
    }
    const { username, password } = this.credentials.getRawValue();
    this.start();
    this.session.changePassword(username.trim(), password, this.change.controls.newPassword.value).subscribe({
      next: () => {
        this.busy.set(false);
        this.finish();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const problem = problemOf(error);
        const unmatched = applyFieldErrors(this.change, problem);
        this.message.set(
          problem?.status === 400 ? unmatched.join(' ') : this.describe(error, 'The password could not be changed.'),
        );
      },
    });
  }

  private start(): void {
    this.busy.set(true);
    this.message.set('');
  }

  private finish(): void {
    void this.router.navigateByUrl(this.safeReturnUrl());
  }

  /** Only same-application paths are followed, so a crafted link cannot redirect elsewhere. */
  private safeReturnUrl(): string {
    const url = this.returnUrl();
    return url && url.startsWith('/') && !url.startsWith('//') ? url : '/dashboard';
  }

  private fail(error: unknown, fallback: string): void {
    this.busy.set(false);
    this.message.set(this.describe(error, fallback));
  }

  private describe(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        return fallback;
      }
      if (error.status === 423 || error.status === 429) {
        return problemOf(error)?.detail ?? 'Too many attempts. Try again later.';
      }
      if (error.status === 0) {
        return 'Cannot reach the server. Check your connection and try again.';
      }
    }
    return 'Something went wrong. Please try again.';
  }
}
