import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ToastService } from '../../../core/notification/toast.service';
import { applyFieldErrors, errorText, problemOf } from '../../../shared/forms/server-errors';
import { codePattern } from '../../../shared/forms/validators';
import { RolesApi } from '../api/roles.api';
import { RoleResponse, RoleSummary } from '../models';

export interface RoleFormData {
  /** Absent when creating. */
  role?: RoleResponse | RoleSummary;
  /** Version of the role being edited. */
  version?: number;
}

/** Create a custom role (code is fixed afterwards) or edit a role's name and description. */
@Component({
  selector: 'app-role-form-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>{{ data.role ? 'Edit role ' + data.role.code : 'New role' }}</h2>
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <mat-dialog-content class="grid">
        @if (!data.role) {
          <mat-form-field appearance="outline">
            <mat-label>Code</mat-label>
            <input matInput formControlName="code" autocomplete="off" data-testid="code" />
            <mat-hint>Upper-case letters, digits and underscore</mat-hint>
            @if (form.controls.code.touched && form.controls.code.invalid) {
              <mat-error>{{ error(form.controls.code) }}</mat-error>
            }
          </mat-form-field>
        }
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" data-testid="name" />
          @if (form.controls.name.touched && form.controls.name.invalid) {
            <mat-error>{{ error(form.controls.name) }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Description</mat-label>
          <textarea matInput formControlName="description" rows="3"></textarea>
        </mat-form-field>
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
export class RoleFormDialogComponent {
  protected readonly data = inject<RoleFormData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<RoleFormDialogComponent, RoleResponse>>(MatDialogRef);
  private readonly api = inject(RolesApi);
  private readonly toast = inject(ToastService);

  protected readonly busy = signal(false);
  protected readonly message = signal('');
  protected readonly error = errorText;
  protected readonly form = inject(FormBuilder).nonNullable.group({
    code: ['', this.data.role ? [] : [Validators.required, codePattern(2, 40)]],
    name: [this.data.role?.name ?? '', [Validators.required, Validators.maxLength(100)]],
    description: [this.data.role?.description ?? '', [Validators.maxLength(500)]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.message.set('');
    const request$ = this.data.role
      ? this.api.update(this.data.role.id, v.name.trim(), v.description.trim(), this.data.version ?? 0)
      : this.api.create(v.code.trim(), v.name.trim(), v.description.trim());
    request$.subscribe({
      next: (saved) => {
        this.toast.success(this.data.role ? 'Role updated.' : `Role ${saved.code} created.`);
        this.ref.close(saved);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const problem = problemOf(error);
        const unmatched = applyFieldErrors(this.form, problem);
        this.message.set(unmatched.length ? unmatched.join(' ') : (problem?.detail ?? 'The role could not be saved.'));
      },
    });
  }
}
