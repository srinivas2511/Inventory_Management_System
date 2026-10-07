import { Component, OnInit, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule, MatSelectionListChange } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { problemMessage } from '../../../core/models/problem.model';
import { AdminApi, RoleDto, UserDto } from '../admin.api';

export interface UserRolesData {
  user: UserDto;
}

@Component({
  selector: 'app-user-roles-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatListModule, MatProgressSpinnerModule],
  template: `
    <h2 mat-dialog-title>Roles for {{ data.user.fullName }}</h2>
    <mat-dialog-content>
      @if (loading()) {
        <div class="loading"><mat-spinner diameter="32" /></div>
      } @else {
        <mat-selection-list (selectionChange)="onSelection($event)" aria-label="Roles" data-testid="roles-list">
          @for (r of roles(); track r.code) {
            <mat-list-option [value]="r.code" [selected]="selected.includes(r.code)">
              <span matListItemTitle>{{ r.name }}</span>
              <span matListItemLine>{{ r.code }} · {{ r.permissions.length }} permissions</span>
            </mat-list-option>
          }
        </mat-selection-list>
        <p class="mat-body-2 note">
          Permissions change straight away: if {{ data.user.username }} is signed in, their next request asks for a new
          session.
        </p>
      }
      @if (errorMessage()) {
        <p class="form-error mat-body-2" role="alert" data-testid="roles-error">{{ errorMessage() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button
        mat-flat-button
        color="primary"
        (click)="save()"
        [disabled]="saving() || loading()"
        data-testid="roles-save"
      >
        Save roles
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .loading {
        display: flex;
        justify-content: center;
        padding: 24px;
      }
      .note {
        color: var(--mat-sys-on-surface-variant, rgba(0, 0, 0, 0.6));
      }
      .form-error {
        color: var(--mat-warn-color, #f44336);
      }
    `,
  ],
})
export class UserRolesDialogComponent implements OnInit {
  protected readonly data = inject<UserRolesData>(MAT_DIALOG_DATA);
  protected readonly roles = signal<RoleDto[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');
  protected selected: string[] = [...this.data.user.roles];

  private readonly api = inject(AdminApi);
  private readonly dialogRef = inject(MatDialogRef<UserRolesDialogComponent, UserDto>);

  ngOnInit(): void {
    this.api.getRoles().subscribe({
      next: (r) => {
        this.roles.set(r);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(problemMessage(err, 'The roles could not be loaded.'));
      },
    });
  }

  protected onSelection(change: MatSelectionListChange): void {
    this.selected = change.source.selectedOptions.selected.map((o) => o.value as string);
  }

  protected save(): void {
    this.saving.set(true);
    this.errorMessage.set('');
    this.api.assignRoles(this.data.user.id, { roleCodes: this.selected }).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.dialogRef.close(saved);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The roles could not be saved.'));
      },
    });
  }
}
