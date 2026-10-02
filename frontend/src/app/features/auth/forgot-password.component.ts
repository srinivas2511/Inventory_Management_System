import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="page-wrapper">
      <mat-card class="card">
        <mat-card-header>
          <mat-card-title>Reset Password</mat-card-title>
          <mat-card-subtitle>Enter your email to receive a reset link</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          @if (!sent()) {
            <form [formGroup]="form" (ngSubmit)="onSubmit()" novalidate>
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Email</mat-label>
                <input matInput type="email" formControlName="email" autocomplete="email" data-testid="email" />
                @if (form.controls.email.hasError('required') && form.controls.email.touched) {
                  <mat-error>Email is required</mat-error>
                }
                @if (form.controls.email.hasError('email') && form.controls.email.touched) {
                  <mat-error>Enter a valid email</mat-error>
                }
              </mat-form-field>
              @if (errorMessage()) {
                <p class="error mat-body-2">{{ errorMessage() }}</p>
              }
              <button mat-flat-button color="primary" type="submit" class="full-width" [disabled]="loading()">
                @if (loading()) { <mat-spinner diameter="20" /> } @else { Send reset link }
              </button>
            </form>
          } @else {
            <p class="mat-body-1" data-testid="sent-message">
              If an account with that email exists, a reset link has been sent. Please check your inbox.
            </p>
          }
        </mat-card-content>
        <mat-card-actions>
          <a mat-button routerLink="/login">Back to sign in</a>
        </mat-card-actions>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-wrapper { display: flex; justify-content: center; align-items: center; min-height: 100vh;
      background: var(--mat-app-background-color, #f5f5f5); padding: 16px; }
    .card { width: 100%; max-width: 400px; }
    .full-width { width: 100%; margin-bottom: 8px; }
    .error { color: var(--mat-warn-color, #f44336); margin: 4px 0 8px; }
  `],
})
export class ForgotPasswordComponent {
  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });
  protected readonly loading = signal(false);
  protected readonly sent = signal(false);
  protected readonly errorMessage = signal('');

  private readonly authService = inject(AuthService);

  onSubmit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading.set(true);
    this.authService.forgotPassword(this.form.getRawValue().email).subscribe({
      next: () => { this.loading.set(false); this.sent.set(true); },
      error: () => { this.loading.set(false); this.sent.set(true); }, // always show success to prevent enumeration
    });
  }
}
