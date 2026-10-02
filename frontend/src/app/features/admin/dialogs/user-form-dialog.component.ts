import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ToastService } from '../../../core/notification/toast.service';
import { applyFieldErrors, errorText, problemOf } from '../../../shared/forms/server-errors';
import { UsersApi } from '../api/users.api';
import { RoleSummary, UserResponse } from '../models';

export interface UserFormData {
  mode: 'create' | 'edit';
  roles: RoleSummary[];
  user?: UserResponse;
}

/** Create a user (with a temporary password they must change at first sign-in) or edit a user's profile. */
@Component({
  selector: 'app-user-form-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <h2 mat-dialog-title>{{ data.mode === 'create' ? 'New user' : 'Edit ' + data.user?.username }}</h2>
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <mat-dialog-content class="grid">
        @if (data.mode === 'create') {
          <mat-form-field appearance="outline">
            <mat-label>Username</mat-label>
            <input matInput formControlName="username" autocomplete="off" data-testid="username" />
            <mat-hint>3-50 letters, digits, dot, underscore or hyphen</mat-hint>
            @if (form.controls.username.touched && form.controls.username.invalid) {
              <mat-error>{{ error(form.controls.username) }}</mat-error>
            }
          </mat-form-field>
        }
        <mat-form-field appearance="outline">
          <mat-label>Full name</mat-label>
          <input matInput formControlName="fullName" data-testid="full-name" />
          @if (form.controls.fullName.touched && form.controls.fullName.invalid) {
            <mat-error>{{ error(form.controls.fullName) }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>E-mail</mat-label>
          <input matInput type="email" formControlName="email" data-testid="email" />
          @if (form.controls.email.touched && form.controls.email.invalid) {
            <mat-error>{{ error(form.controls.email) }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Phone</mat-label>
          <input matInput formControlName="phone" />
          @if (form.controls.phone.touched && form.controls.phone.invalid) {
            <mat-error>{{ error(form.controls.phone) }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Employee code</mat-label>
          <input matInput formControlName="employeeCode" />
          @if (form.controls.employeeCode.touched && form.controls.employeeCode.invalid) {
            <mat-error>{{ error(form.controls.employeeCode) }}</mat-error>
          }
        </mat-form-field>
        @if (data.mode === 'create') {
          <mat-form-field appearance="outline">
            <mat-label>Roles</mat-label>
            <mat-select formControlName="roles" multiple data-testid="roles">
              @for (role of data.roles; track role.code) {
                <mat-option [value]="role.code">{{ role.name }}</mat-option>
              }
            </mat-select>
            @if (form.controls.roles.touched && form.controls.roles.invalid) {
              <mat-error>{{ error(form.controls.roles) }}</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Temporary password</mat-label>
            <input
              matInput
              type="password"
              formControlName="temporaryPassword"
              autocomplete="new-password"
              data-testid="temporary-password"
            />
            <mat-hint>The user must change it at first sign-in</mat-hint>
            @if (form.controls.temporaryPassword.touched && form.controls.temporaryPassword.invalid) {
              <mat-error>{{ error(form.controls.temporaryPassword) }}</mat-error>
            }
          </mat-form-field>
        }
        @if (message()) {
          <p class="message" role="alert" data-testid="form-error">{{ message() }}</p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancel</button>
        <button mat-flat-button color="primary" type="submit" [disabled]="busy()" data-testid="save">Save</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .grid {
      display: grid;
      gap: 4px;
      min-width: min(420px, 80vw);
    }
    .message {
      color: #c62828;
      margin: 0;
    }
  `,
})
export class UserFormDialogComponent {
  protected readonly data = inject<UserFormData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<UserFormDialogComponent, UserResponse>>(MatDialogRef);
  private readonly api = inject(UsersApi);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly busy = signal(false);
  protected readonly message = signal('');
  protected readonly error = errorText;
  protected readonly form = this.fb.group({
    username: [
      this.data.user?.username ?? '',
      this.data.mode === 'create' ? [Validators.required, Validators.pattern(/^[A-Za-z0-9._-]{3,50}$/)] : [],
    ],
    fullName: [this.data.user?.fullName ?? '', [Validators.required, Validators.maxLength(120)]],
    email: [this.data.user?.email ?? '', [Validators.required, Validators.email, Validators.maxLength(160)]],
    phone: [this.data.user?.phone ?? '', [Validators.maxLength(20)]],
    employeeCode: [this.data.user?.employeeCode ?? '', [Validators.maxLength(20)]],
    roles: [[] as string[], this.data.mode === 'create' ? [Validators.required] : []],
    temporaryPassword: ['', this.data.mode === 'create' ? [Validators.required, Validators.maxLength(128)] : []],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.message.set('');
    const request$ =
      this.data.mode === 'create'
        ? this.api.create({
            username: v.username.trim(),
            fullName: v.fullName.trim(),
            email: v.email.trim(),
            phone: v.phone.trim() || undefined,
            employeeCode: v.employeeCode.trim() || undefined,
            roles: v.roles,
            temporaryPassword: v.temporaryPassword,
          })
        : this.api.update(this.data.user!.id, {
            fullName: v.fullName.trim(),
            email: v.email.trim(),
            phone: v.phone.trim() || undefined,
            employeeCode: v.employeeCode.trim() || undefined,
            version: this.data.user!.version,
          });
    request$.subscribe({
      next: (saved) => {
        this.toast.success(this.data.mode === 'create' ? `User ${saved.username} created.` : 'User updated.');
        this.ref.close(saved);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const problem = problemOf(error);
        const unmatched = applyFieldErrors(this.form, problem);
        this.message.set(unmatched.length ? unmatched.join(' ') : (problem?.detail ?? 'The user could not be saved.'));
      },
    });
  }
}
