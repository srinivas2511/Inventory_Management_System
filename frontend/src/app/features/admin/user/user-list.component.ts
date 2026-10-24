import { Component, OnInit, inject, signal } from '@angular/core';

import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { IstDatePipe } from '../../../shared/pipes/ist-date.pipe';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { SessionStore } from '../../../core/auth/session.store';
import { problemMessage } from '../../../core/models/problem.model';
import { ToastService } from '../../../core/notification/toast.service';
import { ConfirmDialogComponent } from '../../../shared/components/confirm-dialog.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge.component';
import { AdminApi, UserCapacityDto, UserDto } from '../admin.api';
import { UserFormDialogComponent } from './user-form-dialog.component';
import { UserResetPasswordDialogComponent } from './user-reset-password-dialog.component';
import { UserPlantsDialogComponent } from './user-plants-dialog.component';
import { UserRolesDialogComponent } from './user-roles-dialog.component';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    IstDatePipe,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <div class="title-row">
        <h2 class="mat-h2">Users</h2>
        @if (capacity()) {
          <span
            class="capacity-badge"
            [class.capacity-badge--warn]="isNearLimit()"
            [class.capacity-badge--full]="isAtLimit()"
            [attr.data-testid]="'capacity-badge'"
          >
            {{ capacity()!.total }}{{ capacity()!.limit ? ' / ' + capacity()!.limit : '' }} users
          </span>
        }
      </div>
      @if (session.hasPermission('USER_CREATE')) {
        <button mat-flat-button color="primary" (click)="create()" [disabled]="isAtLimit()" data-testid="new-user">
          <mat-icon>person_add</mat-icon> New user
        </button>
      }
    </div>
    <div class="filter-bar">
      <mat-form-field appearance="outline" class="search-field">
        <mat-label>Search</mat-label>
        <input matInput [formControl]="searchCtrl" data-testid="user-search" /><mat-icon matSuffix>search</mat-icon>
      </mat-form-field>
      <mat-form-field appearance="outline" style="width:140px">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="activeCtrl">
          <mat-option [value]="null">All</mat-option>
          <mat-option [value]="true">Active</mat-option>
          <mat-option [value]="false">Inactive</mat-option>
        </mat-select>
      </mat-form-field>
    </div>
    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="username"
          ><th mat-header-cell *matHeaderCellDef>Username</th>
          <td mat-cell *matCellDef="let r">{{ r.username }}</td></ng-container
        >
        <ng-container matColumnDef="fullName"
          ><th mat-header-cell *matHeaderCellDef>Full name</th>
          <td mat-cell *matCellDef="let r">{{ r.fullName }}</td></ng-container
        >
        <ng-container matColumnDef="email"
          ><th mat-header-cell *matHeaderCellDef>Email</th>
          <td mat-cell *matCellDef="let r">{{ r.email }}</td></ng-container
        >
        <ng-container matColumnDef="roles"
          ><th mat-header-cell *matHeaderCellDef>Roles</th>
          <td mat-cell *matCellDef="let r">
            {{ r.roles?.length ? r.roles.join(', ') : 'No roles yet' }}
          </td></ng-container
        >
        <ng-container matColumnDef="status"
          ><th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r">
            <app-status-badge [status]="r.active ? 'ACTIVE' : 'INACTIVE'" />
            @if (r.mustChangePassword) {
              <div class="mat-caption pending">Password change pending</div>
            }
            @if (r.locked) {
              <div class="mat-caption pending" data-testid="locked-caption">
                Locked until {{ r.lockedUntil | istDate: 'shortTime' }}
              </div>
            }
          </td></ng-container
        >
        <ng-container matColumnDef="lastLogin"
          ><th mat-header-cell *matHeaderCellDef>Last sign-in</th>
          <td mat-cell *matCellDef="let r">
            {{ r.lastLoginAt ? (r.lastLoginAt | istDate: 'medium') : 'Never' }}
          </td></ng-container
        >
        <ng-container matColumnDef="actions"
          ><th mat-header-cell *matHeaderCellDef><span class="cdk-visually-hidden">Actions</span></th>
          <td mat-cell *matCellDef="let r" class="actions">
            @if (session.hasPermission('USER_UPDATE')) {
              <button
                mat-icon-button
                matTooltip="Edit details"
                [attr.aria-label]="'Edit ' + r.username"
                (click)="edit(r)"
                data-testid="edit-user"
              >
                <mat-icon>edit</mat-icon>
              </button>
            }
            @if (session.hasPermission('ROLE_MANAGE')) {
              <button
                mat-icon-button
                matTooltip="Assign roles"
                [attr.aria-label]="'Assign roles to ' + r.username"
                (click)="assignRoles(r)"
                data-testid="roles-user"
              >
                <mat-icon>admin_panel_settings</mat-icon>
              </button>
            }
            @if (session.hasPermission('PLANT_MANAGE')) {
              <button
                mat-icon-button
                matTooltip="Limit to plants"
                [attr.aria-label]="'Limit ' + r.username + ' to plants'"
                (click)="assignPlants(r)"
                data-testid="plants-user"
              >
                <mat-icon>factory</mat-icon>
              </button>
            }
            @if (r.active && session.hasPermission('USER_DELETE')) {
              <button
                mat-icon-button
                [matTooltip]="isSelf(r) ? 'You cannot deactivate your own account' : 'Deactivate'"
                [attr.aria-label]="'Deactivate ' + r.username"
                [disabled]="isSelf(r)"
                (click)="deactivate(r)"
                data-testid="deactivate-user"
              >
                <mat-icon>person_off</mat-icon>
              </button>
            }
            @if (!r.active && session.hasPermission('USER_UPDATE')) {
              <button
                mat-icon-button
                matTooltip="Reactivate"
                [attr.aria-label]="'Reactivate ' + r.username"
                (click)="reactivate(r)"
                data-testid="reactivate-user"
              >
                <mat-icon>person_check</mat-icon>
              </button>
            }
            @if (r.locked && session.hasPermission('USER_UPDATE')) {
              <button
                mat-icon-button
                matTooltip="Unlock account"
                [attr.aria-label]="'Unlock ' + r.username"
                (click)="unlock(r)"
                data-testid="unlock-user"
              >
                <mat-icon>lock_open</mat-icon>
              </button>
            }
            @if (session.hasPermission('USER_PASSWORD_RESET') && !isSelf(r)) {
              <button
                mat-icon-button
                matTooltip="Reset password"
                [attr.aria-label]="'Reset password for ' + r.username"
                (click)="resetPassword(r)"
                data-testid="reset-password-user"
              >
                <mat-icon>lock_reset</mat-icon>
              </button>
            }
          </td></ng-container
        >
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      @if (rows().length === 0) {
        <p class="mat-body-1 empty">No users match. Clear the search or change the status filter.</p>
      }
      <mat-paginator
        [length]="total()"
        [pageSize]="pageSize"
        [pageIndex]="pageIndex()"
        [pageSizeOptions]="[20, 50]"
        (page)="onPage($event)"
        showFirstLastButtons
      />
    }
  `,
  styles: [
    `
      .page-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 16px 16px 0;
      }
      .title-row {
        display: flex;
        align-items: center;
        gap: 12px;
      }
      .capacity-badge {
        font-size: 12px;
        font-weight: 500;
        padding: 2px 10px;
        border-radius: 12px;
        background: var(--mat-sys-surface-container, rgba(0, 0, 0, 0.06));
        color: var(--mat-sys-on-surface-variant, rgba(0, 0, 0, 0.6));
      }
      .capacity-badge--warn {
        background: #fff3e0;
        color: #e65100;
      }
      .capacity-badge--full {
        background: #ffebee;
        color: #c62828;
      }
      .filter-bar {
        display: flex;
        gap: 12px;
        padding: 8px 16px;
        flex-wrap: wrap;
      }
      .search-field {
        flex: 1;
        min-width: 200px;
      }
      .loading-center {
        display: flex;
        justify-content: center;
        padding: 48px;
      }
      .actions {
        white-space: nowrap;
        text-align: end;
      }
      .pending {
        color: var(--mat-sys-on-surface-variant, rgba(0, 0, 0, 0.6));
      }
      .empty {
        padding: 16px;
      }
      table {
        width: 100%;
      }
    `,
  ],
})
export class UserListComponent implements OnInit {
  protected readonly columns = ['username', 'fullName', 'email', 'roles', 'status', 'lastLogin', 'actions'];
  protected readonly rows = signal<UserDto[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = 20;
  protected readonly capacity = signal<UserCapacityDto | null>(null);
  protected readonly searchCtrl = new FormControl('');
  protected readonly activeCtrl = new FormControl<boolean | null>(true);
  protected readonly session = inject(SessionStore);
  private readonly api = inject(AdminApi);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);

  ngOnInit(): void {
    this.loadCapacity();
    this.load();
    this.searchCtrl.valueChanges.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => {
      this.pageIndex.set(0);
      this.load();
    });
    this.activeCtrl.valueChanges.subscribe(() => {
      this.pageIndex.set(0);
      this.load();
    });
  }

  private loadCapacity(): void {
    this.api.getUserCapacity().subscribe({
      next: (c) => this.capacity.set(c),
      error: () => {
        /* non-critical; hide badge on error */
      },
    });
  }

  protected isNearLimit(): boolean {
    const c = this.capacity();
    return !!c && c.limit > 0 && c.total >= c.limit * 0.8 && c.total < c.limit;
  }

  protected isAtLimit(): boolean {
    const c = this.capacity();
    return !!c && c.limit > 0 && c.total >= c.limit;
  }

  protected load(): void {
    this.loading.set(true);
    this.api.getUsers(this.searchCtrl.value, this.activeCtrl.value, this.pageIndex(), this.pageSize).subscribe({
      next: (p) => {
        this.rows.set(p.content);
        this.total.set(p.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected onPage(e: PageEvent): void {
    this.pageIndex.set(e.pageIndex);
    this.load();
  }

  protected isSelf(user: UserDto): boolean {
    return this.session.user()?.id === user.id;
  }

  protected create(): void {
    this.dialog
      .open(UserFormDialogComponent, { data: {} })
      .afterClosed()
      .subscribe((saved?: UserDto) => {
        if (saved) {
          this.toast.success(`${saved.username} created. They choose a new password at first sign-in.`);
          this.loadCapacity();
          this.load();
        }
      });
  }

  protected edit(user: UserDto): void {
    this.dialog
      .open(UserFormDialogComponent, { data: { user } })
      .afterClosed()
      .subscribe((saved?: UserDto) => {
        if (saved) {
          this.toast.success(`${saved.username} updated`);
          this.load();
        }
      });
  }

  protected assignRoles(user: UserDto): void {
    this.dialog
      .open(UserRolesDialogComponent, { data: { user }, width: '600px', maxHeight: '80vh' })
      .afterClosed()
      .subscribe((saved?: UserDto) => {
        if (saved) {
          this.toast.success(`Roles saved for ${saved.username}`);
          this.load();
        }
      });
  }

  protected assignPlants(user: UserDto): void {
    this.dialog
      .open(UserPlantsDialogComponent, { data: { user }, width: '480px' })
      .afterClosed()
      .subscribe((saved?: { plantIds: number[] }) => {
        if (saved) {
          this.toast.success(
            saved.plantIds.length
              ? `${user.username} limited to the chosen plants`
              : `${user.username} sees every plant`,
          );
        }
      });
  }

  protected deactivate(user: UserDto): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: `Deactivate ${user.username}?`,
          message:
            'They can no longer sign in, and any session they have open ends. You can reactivate the account later.',
          confirmLabel: 'Deactivate',
        },
      })
      .afterClosed()
      .subscribe((confirmed?: boolean) => {
        if (!confirmed) {
          return;
        }
        this.api.deactivateUser(user.id).subscribe({
          next: () => {
            this.toast.success(`${user.username} deactivated`);
            this.load();
          },
          error: (err) => this.toast.error(problemMessage(err, 'The user could not be deactivated.')),
        });
      });
  }

  protected reactivate(user: UserDto): void {
    this.api.reactivateUser(user.id).subscribe({
      next: () => {
        this.toast.success(`${user.username} reactivated`);
        this.load();
      },
      error: (err) => this.toast.error(problemMessage(err, 'The user could not be reactivated.')),
    });
  }

  protected resetPassword(user: UserDto): void {
    this.dialog
      .open(UserResetPasswordDialogComponent, { data: { user }, width: '520px' })
      .afterClosed()
      .subscribe((done?: boolean) => {
        if (done) {
          this.toast.success(`Password reset for ${user.username}`);
          this.load();
        }
      });
  }

  protected unlock(user: UserDto): void {
    this.api.unlockUser(user.id).subscribe({
      next: () => {
        this.toast.success(`${user.username} unlocked`);
        this.load();
      },
      error: (err) => this.toast.error(problemMessage(err, 'The account could not be unlocked.')),
    });
  }
}
