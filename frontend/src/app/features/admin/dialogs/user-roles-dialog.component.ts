import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ToastService } from '../../../core/notification/toast.service';
import { problemOf } from '../../../shared/forms/server-errors';
import { UsersApi } from '../api/users.api';
import { RoleSummary, UserResponse, UserSummary } from '../models';

export interface UserRolesData {
  user: UserSummary;
  roles: RoleSummary[];
}

/** Replaces a user's roles. Their open sessions pick up the change at the next token refresh. */
@Component({
  selector: 'app-user-roles-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  template: `
    <h2 mat-dialog-title>Roles of {{ data.user.username }}</h2>
    <mat-dialog-content>
      <div class="roles" role="group" aria-label="Roles">
        @for (role of data.roles; track role.code) {
          <mat-checkbox
            [checked]="selected().has(role.code)"
            (change)="toggle(role.code, $event.checked)"
            [attr.data-testid]="'role-' + role.code"
          >
            {{ role.name }}
          </mat-checkbox>
        }
      </div>
      <mat-form-field appearance="outline" class="full">
        <mat-label>Reason (optional)</mat-label>
        <input matInput [formControl]="reason" maxlength="500" data-testid="reason" />
      </mat-form-field>
      @if (message()) {
        <p class="message" role="alert" data-testid="form-error">{{ message() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancel</button>
      <button
        mat-flat-button
        color="primary"
        type="button"
        (click)="save()"
        [disabled]="busy() || selected().size === 0"
        data-testid="save"
      >
        Save roles
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .roles {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      margin-bottom: 8px;
    }
    .full {
      width: 100%;
    }
    .message {
      color: #c62828;
      margin: 0;
    }
  `,
})
export class UserRolesDialogComponent {
  protected readonly data = inject<UserRolesData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<UserRolesDialogComponent, UserResponse>>(MatDialogRef);
  private readonly api = inject(UsersApi);
  private readonly toast = inject(ToastService);

  protected readonly selected = signal(new Set(this.data.user.roles));
  protected readonly reason = inject(FormBuilder).nonNullable.control('');
  protected readonly busy = signal(false);
  protected readonly message = signal('');

  protected toggle(code: string, checked: boolean): void {
    const next = new Set(this.selected());
    if (checked) {
      next.add(code);
    } else {
      next.delete(code);
    }
    this.selected.set(next);
  }

  protected save(): void {
    this.busy.set(true);
    this.message.set('');
    this.api.setRoles(this.data.user.id, [...this.selected()], this.reason.value.trim()).subscribe({
      next: (saved) => {
        this.toast.success('Roles updated.');
        this.ref.close(saved);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const problem = problemOf(error);
        this.message.set(problem?.fieldErrors?.[0]?.message ?? problem?.detail ?? 'The roles could not be changed.');
      },
    });
  }
}
