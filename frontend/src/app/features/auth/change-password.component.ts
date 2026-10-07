import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/auth/auth.service';
import { passwordMeetsPolicy } from '../../core/auth/password-policy';
import { safeReturnUrl } from '../../core/auth/return-url';
import { SessionStore } from '../../core/auth/session.store';
import { isProblemDetail, problemMessage } from '../../core/models/problem.model';
import { ToastService } from '../../core/notification/toast.service';
import { PasswordHintsComponent } from '../../shared/components/password-hints.component';

/** The new password must differ from the current one, and the confirmation must match it. */
function notSameAsCurrent(group: AbstractControl): ValidationErrors | null {
  const current = group.get('currentPassword')?.value;
  const next = group.get('newPassword')?.value;
  return current && next && current === next ? { sameAsCurrent: true } : null;
}

function confirmMatches(group: AbstractControl): ValidationErrors | null {
  const next = group.get('newPassword')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return confirm && next !== confirm ? { mismatch: true } : null;
}

@Component({
  selector: 'app-change-password',
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
    PasswordHintsComponent,
  ],
  template: `
    <div class="page">
      <mat-card class="card">
        <mat-card-header>
          <mat-card-title>{{ forced ? 'Choose a new password' : 'Change password' }}</mat-card-title>
          @if (forced) {
            <mat-card-subtitle data-testid="forced-note">
              You signed in with a temporary password. Choose your own to continue.
            </mat-card-subtitle>
          }
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="save()" novalidate>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Current password</mat-label>
              <input
                matInput
                [type]="show() ? 'text' : 'password'"
                formControlName="currentPassword"
                autocomplete="current-password"
                data-testid="current-password"
              />
              @if (form.controls.currentPassword.hasError('incorrect')) {
                <mat-error>That is not your current password</mat-error>
              } @else if (form.controls.currentPassword.hasError('required') && form.controls.currentPassword.touched) {
                <mat-error>Enter your current password</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>New password</mat-label>
              <input
                matInput
                [type]="show() ? 'text' : 'password'"
                formControlName="newPassword"
                autocomplete="new-password"
                data-testid="new-password"
              />
              <button
                mat-icon-button
                matSuffix
                type="button"
                [attr.aria-label]="show() ? 'Hide passwords' : 'Show passwords'"
                (click)="show.set(!show())"
              >
                <mat-icon>{{ show() ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
            </mat-form-field>
            <app-password-hints [password]="form.controls.newPassword.value" [username]="username" />
            @if (form.hasError('sameAsCurrent')) {
              <p class="field-error mat-body-2" data-testid="same-as-current">
                The new password must be different from the current one.
              </p>
            }

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Confirm new password</mat-label>
              <input
                matInput
                [type]="show() ? 'text' : 'password'"
                formControlName="confirmPassword"
                autocomplete="new-password"
                data-testid="confirm-password"
              />
              @if (form.hasError('mismatch') && form.controls.confirmPassword.touched) {
                <mat-error>The two passwords do not match</mat-error>
              }
            </mat-form-field>

            @if (errorMessage()) {
              <p class="field-error mat-body-2" role="alert" data-testid="change-error">{{ errorMessage() }}</p>
            }

            <button
              mat-flat-button
              color="primary"
              type="submit"
              class="full-width"
              [disabled]="saving()"
              data-testid="change-submit"
            >
              @if (saving()) {
                <mat-spinner diameter="20" />
              } @else {
                Change password
              }
            </button>
          </form>
        </mat-card-content>
        <mat-card-actions>
          @if (forced) {
            <button mat-button type="button" (click)="signOut()" data-testid="forced-signout">Sign out instead</button>
          } @else {
            <a mat-button routerLink="/dashboard" data-testid="change-cancel">Cancel</a>
          }
        </mat-card-actions>
      </mat-card>
    </div>
  `,
  styles: [
    `
      .page {
        display: flex;
        justify-content: center;
        align-items: flex-start;
        min-height: 100vh;
        padding: 48px 16px;
        background: var(--mat-app-background-color, #f5f5f5);
      }
      .card {
        width: 100%;
        max-width: 460px;
      }
      .full-width {
        width: 100%;
        margin-bottom: 8px;
      }
      .field-error {
        color: var(--mat-warn-color, #f44336);
        margin: 4px 0 8px;
      }
    `,
  ],
})
export class ChangePasswordComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);
  private readonly session = inject(SessionStore);

  /** Decided once on arrival: after a successful change the session no longer says the change is forced. */
  protected readonly forced = this.session.mustChangePassword();
  protected readonly username = this.session.user()?.username ?? null;
  protected readonly show = signal(false);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');

  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: [
        '',
        [
          Validators.required,
          (c: AbstractControl) => (passwordMeetsPolicy(c.value ?? '', this.username) ? null : { policy: true }),
        ],
      ],
      confirmPassword: ['', Validators.required],
    },
    { validators: [notSameAsCurrent, confirmMatches] },
  );

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set('');
    this.auth.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success('Password changed. Your other sessions have been signed out.');
        this.router.navigateByUrl(safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl')) ?? '/dashboard');
      },
      error: (err) => {
        this.saving.set(false);
        const code = isProblemDetail(err?.error) ? err.error.code : null;
        if (code === 'CURRENT_PASSWORD_INCORRECT') {
          this.form.controls.currentPassword.setErrors({ incorrect: true });
          this.form.controls.currentPassword.markAsTouched();
        } else {
          this.errorMessage.set(problemMessage(err, 'The password could not be changed.'));
        }
      },
    });
  }

  protected signOut(): void {
    this.auth.logout();
  }
}
