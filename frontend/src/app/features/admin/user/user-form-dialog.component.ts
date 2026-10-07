import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { SessionStore } from '../../../core/auth/session.store';
import { passwordMeetsPolicy } from '../../../core/auth/password-policy';
import { problemMessage } from '../../../core/models/problem.model';
import { PasswordHintsComponent } from '../../../shared/components/password-hints.component';
import { AdminApi, CreateUserRequest, RoleDto, UpdateUserRequest, UserDto } from '../admin.api';

export interface UserFormData {
  /** Present when editing; absent when creating. */
  user?: UserDto;
}

@Component({
  selector: 'app-user-form-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    PasswordHintsComponent,
  ],
  template: `
    <h2 mat-dialog-title>{{ editing ? 'Edit ' + data.user!.username : 'New user' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="user-form" (ngSubmit)="save()" novalidate class="form-grid">
        @if (!editing) {
          <mat-form-field appearance="outline">
            <mat-label>Username</mat-label>
            <input matInput formControlName="username" autocomplete="off" data-testid="user-username" />
            @if (form.controls.username.invalid && form.controls.username.touched) {
              <mat-error>3 to 50 characters</mat-error>
            }
          </mat-form-field>
        }
        <mat-form-field appearance="outline">
          <mat-label>Employee code</mat-label>
          <input matInput formControlName="employeeCode" data-testid="user-employee-code" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="full-span">
          <mat-label>Full name</mat-label>
          <input matInput formControlName="fullName" data-testid="user-full-name" />
          @if (form.controls.fullName.invalid && form.controls.fullName.touched) {
            <mat-error>Full name is required</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Email</mat-label>
          <input matInput type="email" formControlName="email" data-testid="user-email" />
          @if (form.controls.email.invalid && form.controls.email.touched) {
            <mat-error>Enter a valid email address</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Phone</mat-label>
          <input matInput formControlName="phone" data-testid="user-phone" />
        </mat-form-field>

        @if (!editing) {
          <mat-form-field appearance="outline" class="full-span">
            <mat-label>Initial password</mat-label>
            <input
              matInput
              [type]="showPassword() ? 'text' : 'password'"
              formControlName="password"
              autocomplete="new-password"
              data-testid="user-password"
            />
            <button
              mat-icon-button
              matSuffix
              type="button"
              [attr.aria-label]="showPassword() ? 'Hide password' : 'Show password'"
              (click)="showPassword.set(!showPassword())"
            >
              <mat-icon>{{ showPassword() ? 'visibility_off' : 'visibility' }}</mat-icon>
            </button>
            <mat-hint>They must choose a new one the first time they sign in.</mat-hint>
          </mat-form-field>
          <div class="full-span">
            <app-password-hints [password]="form.controls.password.value" [username]="form.controls.username.value" />
          </div>
          @if (canAssignRoles) {
            <mat-form-field appearance="outline" class="full-span">
              <mat-label>Roles</mat-label>
              <mat-select formControlName="roleCodes" multiple data-testid="user-roles">
                @for (r of roles(); track r.code) {
                  <mat-option [value]="r.code">{{ r.name }} ({{ r.code }})</mat-option>
                }
              </mat-select>
              <mat-hint>You can also assign roles later from the user list.</mat-hint>
            </mat-form-field>
          }
        }

        @if (errorMessage()) {
          <p class="form-error mat-body-2 full-span" role="alert" data-testid="user-form-error">{{ errorMessage() }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button
        mat-flat-button
        color="primary"
        form="user-form"
        type="submit"
        [disabled]="saving()"
        data-testid="user-save"
      >
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          {{ editing ? 'Save changes' : 'Create user' }}
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .form-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 0 12px;
        padding: 8px 0;
        min-width: min(560px, 80vw);
      }
      .form-grid mat-form-field {
        width: 100%;
      }
      .full-span {
        grid-column: 1/-1;
      }
      .form-error {
        color: var(--mat-warn-color, #f44336);
      }
      @media (max-width: 600px) {
        .form-grid {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class UserFormDialogComponent implements OnInit {
  protected readonly data = inject<UserFormData>(MAT_DIALOG_DATA);
  protected readonly editing = !!this.data.user;
  protected readonly saving = signal(false);
  protected readonly showPassword = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly roles = signal<RoleDto[]>([]);
  /** Handing out roles needs ROLE_MANAGE on top of USER_CREATE; the server refuses it otherwise. */
  protected readonly canAssignRoles = inject(SessionStore).hasPermission('ROLE_MANAGE');

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AdminApi);
  private readonly dialogRef = inject(MatDialogRef<UserFormDialogComponent, UserDto>);

  private readonly passwordPolicy: ValidatorFn = (control) =>
    passwordMeetsPolicy(String(control.value ?? ''), this.form?.controls.username.value ?? null)
      ? null
      : { policy: true };

  protected readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
    employeeCode: ['', Validators.maxLength(20)],
    fullName: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(160)]],
    phone: ['', Validators.maxLength(20)],
    password: ['', [Validators.required, this.passwordPolicy]],
    roleCodes: this.fb.nonNullable.control<string[]>([]),
  });

  ngOnInit(): void {
    const user = this.data.user;
    if (user) {
      this.form.patchValue({
        employeeCode: user.employeeCode ?? '',
        fullName: user.fullName,
        email: user.email,
        phone: user.phone ?? '',
      });
      this.form.controls.username.disable();
      this.form.controls.password.disable();
      this.form.controls.roleCodes.disable();
      return;
    }
    // The username is part of the password rules, so a change to it re-judges the password.
    this.form.controls.username.valueChanges.subscribe(() => this.form.controls.password.updateValueAndValidity());
    if (this.canAssignRoles) {
      this.api.getRoles().subscribe((r) => this.roles.set(r));
    }
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const optional = (v: string): string | null => (v.trim() === '' ? null : v.trim());
    this.saving.set(true);
    this.errorMessage.set('');

    const request$ = this.editing
      ? this.api.updateUser(this.data.user!.id, {
          employeeCode: optional(raw.employeeCode),
          fullName: raw.fullName.trim(),
          email: raw.email.trim(),
          phone: optional(raw.phone),
          version: this.data.user!.version,
        } satisfies UpdateUserRequest)
      : this.api.createUser({
          username: raw.username.trim(),
          employeeCode: optional(raw.employeeCode),
          fullName: raw.fullName.trim(),
          email: raw.email.trim(),
          phone: optional(raw.phone),
          password: raw.password,
          // Left out when nothing was chosen, so a holder of USER_CREATE alone is never refused over an empty list.
          ...(this.canAssignRoles && raw.roleCodes.length > 0 ? { roleCodes: raw.roleCodes } : {}),
        } satisfies CreateUserRequest);

    request$.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.dialogRef.close(saved);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The user could not be saved.'));
      },
    });
  }
}
