import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="page-wrapper">
      <mat-card class="card">
        <mat-card-header>
          <mat-card-title>Set New Password</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (!done()) {
            <form [formGroup]="form" (ngSubmit)="onSubmit()" novalidate>
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>New password</mat-label>
                <input matInput [type]="showPwd() ? 'text' : 'password'" formControlName="password"
                  autocomplete="new-password" data-testid="new-password" />
                <button mat-icon-button matSuffix type="button" (click)="showPwd.set(!showPwd())">
                  <mat-icon>{{ showPwd() ? 'visibility_off' : 'visibility' }}</mat-icon>
                </button>
                @if (form.controls.password.hasError('required') && form.controls.password.touched) {
                  <mat-error>Password is required</mat-error>
                }
                @if (form.controls.password.hasError('minlength') && form.controls.password.touched) {
                  <mat-error>Minimum 10 characters</mat-error>
                }
              </mat-form-field>
              @if (errorMessage()) {
                <p class="error mat-body-2">{{ errorMessage() }}</p>
              }
              <button mat-flat-button color="primary" type="submit" class="full-width" [disabled]="loading()">
                @if (loading()) { <mat-spinner diameter="20" /> } @else { Set password }
              </button>
            </form>
          } @else {
            <p class="mat-body-1" data-testid="done-message">
              Password updated. <a routerLink="/login">Sign in</a>
            </p>
          }
        </mat-card-content>
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
export class ResetPasswordComponent {
  protected readonly form = inject(FormBuilder).nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(10)]],
  });
  protected readonly loading = signal(false);
  protected readonly done = signal(false);
  protected readonly showPwd = signal(false);
  protected readonly errorMessage = signal('');

  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  onSubmit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const token = this.route.snapshot.paramMap.get('token') ?? '';
    this.loading.set(true);
    this.authService.resetPassword(token, this.form.getRawValue().password).subscribe({
      next: () => { this.loading.set(false); this.done.set(true); },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(err?.error?.detail ?? 'Invalid or expired link.');
      },
    });
  }
}
