import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { HasPermissionDirective } from '../../../core/permission/has-permission.directive';
import { ToastService } from '../../../core/notification/toast.service';
import { DialogService } from '../../../shared/dialogs/dialog.service';
import { problemOf } from '../../../shared/forms/server-errors';
import { RolesApi } from '../api/roles.api';
import { UsersApi } from '../api/users.api';
import { ResetPasswordDialogComponent } from '../dialogs/reset-password-dialog.component';
import { UserFormDialogComponent } from '../dialogs/user-form-dialog.component';
import { UserRolesDialogComponent } from '../dialogs/user-roles-dialog.component';
import { RoleSummary, UserSummary } from '../models';

/** User administration: search, filter, create, edit, change roles, reset password, (de)activate. */
@Component({
  selector: 'app-users-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatMenuModule,
    MatPaginatorModule,
    MatSelectModule,
    MatSortModule,
    MatTableModule,
    HasPermissionDirective,
  ],
  template: `
    <header class="head">
      <h1>Users</h1>
      <button
        *appHasPermission="'USER_CREATE'"
        mat-flat-button
        color="primary"
        (click)="create()"
        data-testid="new-user"
      >
        <mat-icon>add</mat-icon> New user
      </button>
    </header>

    <div class="filters">
      <mat-form-field appearance="outline">
        <mat-label>Search</mat-label>
        <input matInput [formControl]="search" placeholder="Name, username, e-mail" data-testid="search" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Role</mat-label>
        <mat-select [formControl]="role" data-testid="role-filter">
          <mat-option [value]="''">All roles</mat-option>
          @for (r of roles(); track r.code) {
            <mat-option [value]="r.code">{{ r.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="status" data-testid="status-filter">
          <mat-option value="all">All</mat-option>
          <mat-option value="active">Active</mat-option>
          <mat-option value="inactive">Inactive</mat-option>
        </mat-select>
      </mat-form-field>
    </div>

    @if (error()) {
      <p class="error" role="alert" data-testid="list-error">{{ error() }}</p>
    }

    <table
      mat-table
      [dataSource]="rows()"
      matSort
      matSortActive="username"
      matSortDirection="asc"
      (matSortChange)="sortChanged($event)"
      class="table"
    >
      <ng-container matColumnDef="username">
        <th mat-header-cell *matHeaderCellDef mat-sort-header>Username</th>
        <td mat-cell *matCellDef="let u" data-testid="username-cell">{{ u.username }}</td>
      </ng-container>
      <ng-container matColumnDef="fullName">
        <th mat-header-cell *matHeaderCellDef mat-sort-header>Name</th>
        <td mat-cell *matCellDef="let u">{{ u.fullName }}</td>
      </ng-container>
      <ng-container matColumnDef="email">
        <th mat-header-cell *matHeaderCellDef>E-mail</th>
        <td mat-cell *matCellDef="let u">{{ u.email }}</td>
      </ng-container>
      <ng-container matColumnDef="roles">
        <th mat-header-cell *matHeaderCellDef>Roles</th>
        <td mat-cell *matCellDef="let u">{{ u.roles.join(', ') }}</td>
      </ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let u">
          <span [class]="u.active ? 'badge ok' : 'badge off'">{{ u.active ? 'Active' : 'Inactive' }}</span>
          @if (u.lockedUntil) {
            <span class="badge warn">Locked</span>
          }
        </td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let u">
          <button
            mat-icon-button
            [matMenuTriggerFor]="menu"
            [attr.aria-label]="'Actions for ' + u.username"
            data-testid="row-menu"
          >
            <mat-icon>more_vert</mat-icon>
          </button>
          <mat-menu #menu="matMenu">
            <button *appHasPermission="'USER_UPDATE'" mat-menu-item (click)="edit(u)" data-testid="action-edit">
              Edit
            </button>
            <button *appHasPermission="'USER_UPDATE'" mat-menu-item (click)="changeRoles(u)" data-testid="action-roles">
              Change roles
            </button>
            <button
              *appHasPermission="'USER_UPDATE'"
              mat-menu-item
              (click)="resetPassword(u)"
              data-testid="action-reset"
            >
              Reset password
            </button>
            @if (u.active) {
              <button
                *appHasPermission="'USER_DELETE'"
                mat-menu-item
                (click)="deactivate(u)"
                data-testid="action-deactivate"
              >
                Deactivate
              </button>
            } @else {
              <button
                *appHasPermission="'USER_UPDATE'"
                mat-menu-item
                (click)="activate(u)"
                data-testid="action-activate"
              >
                Activate
              </button>
            }
          </mat-menu>
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell empty" [attr.colspan]="columns.length" data-testid="empty">No users match the filters.</td>
      </tr>
    </table>
    <mat-paginator
      [length]="total()"
      [pageSize]="size"
      [pageIndex]="page"
      [pageSizeOptions]="[10, 25, 50]"
      (page)="paged($event)"
    />
  `,
  styles: `
    .head {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .filters {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
    }
    .table {
      width: 100%;
    }
    .empty {
      padding: 24px;
      text-align: center;
    }
    .error {
      color: #c62828;
    }
    .badge {
      border-radius: 10px;
      font-size: 12px;
      margin-right: 4px;
      padding: 2px 8px;
    }
    .ok {
      background: #e8f5e9;
      color: #1b5e20;
    }
    .off {
      background: #eceff1;
      color: #455a64;
    }
    .warn {
      background: #fff3e0;
      color: #e65100;
    }
  `,
})
export class UsersPageComponent implements OnInit {
  private readonly users = inject(UsersApi);
  private readonly rolesApi = inject(RolesApi);
  private readonly dialog = inject(MatDialog);
  private readonly dialogs = inject(DialogService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly columns = ['username', 'fullName', 'email', 'roles', 'status', 'actions'];
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly role = new FormControl('', { nonNullable: true });
  protected readonly status = new FormControl<'all' | 'active' | 'inactive'>('all', { nonNullable: true });

  protected readonly rows = signal<UserSummary[]>([]);
  protected readonly roles = signal<RoleSummary[]>([]);
  protected readonly total = signal(0);
  protected readonly error = signal('');
  protected page = 0;
  protected size = 25;
  private sort = 'username,asc';

  ngOnInit(): void {
    this.rolesApi.list().subscribe({ next: (roles) => this.roles.set(roles), error: () => undefined });
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reset());
    this.role.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.reset());
    this.status.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.reset());
    this.load();
  }

  protected sortChanged(sort: Sort): void {
    this.sort = sort.direction ? `${sort.active},${sort.direction}` : 'username,asc';
    this.reset();
  }

  protected paged(event: PageEvent): void {
    this.page = event.pageIndex;
    this.size = event.pageSize;
    this.load();
  }

  protected create(): void {
    this.dialog
      .open(UserFormDialogComponent, { data: { mode: 'create', roles: this.roles() }, width: '480px' })
      .afterClosed()
      .subscribe((saved) => saved && this.load());
  }

  protected edit(user: UserSummary): void {
    this.users.get(user.id).subscribe({
      next: (full) =>
        this.dialog
          .open(UserFormDialogComponent, { data: { mode: 'edit', roles: this.roles(), user: full }, width: '480px' })
          .afterClosed()
          .subscribe((saved) => saved && this.load()),
      error: (e: unknown) => this.toast.error(problemOf(e)?.detail ?? 'The user could not be loaded.'),
    });
  }

  protected changeRoles(user: UserSummary): void {
    this.dialog
      .open(UserRolesDialogComponent, { data: { user, roles: this.roles() }, width: '520px' })
      .afterClosed()
      .subscribe((saved) => saved && this.load());
  }

  protected resetPassword(user: UserSummary): void {
    this.dialog.open(ResetPasswordDialogComponent, { data: user, width: '460px' });
  }

  protected async deactivate(user: UserSummary): Promise<void> {
    const reason = await this.dialogs.askReason({
      title: `Deactivate ${user.username}?`,
      message: 'They can no longer sign in and their sessions end. History is kept.',
      confirmLabel: 'Deactivate',
      danger: true,
      required: false,
    });
    if (reason === null) {
      return;
    }
    this.users.deactivate(user.id, reason).subscribe({
      next: () => {
        this.toast.success(`${user.username} deactivated.`);
        this.load();
      },
      error: (e: unknown) => this.toast.error(problemOf(e)?.detail ?? 'The user could not be deactivated.'),
    });
  }

  protected activate(user: UserSummary): void {
    this.users.activate(user.id).subscribe({
      next: () => {
        this.toast.success(`${user.username} activated.`);
        this.load();
      },
      error: (e: unknown) => this.toast.error(problemOf(e)?.detail ?? 'The user could not be activated.'),
    });
  }

  private reset(): void {
    this.page = 0;
    this.load();
  }

  private load(): void {
    const status = this.status.value;
    this.users
      .list({
        q: this.search.value,
        role: this.role.value || undefined,
        active: status === 'all' ? null : status === 'active',
        page: this.page,
        size: this.size,
        sort: this.sort,
      })
      .subscribe({
        next: (result) => {
          this.error.set('');
          this.rows.set(result.content);
          this.total.set(result.totalElements);
        },
        error: (e: unknown) => this.error.set(problemOf(e)?.detail ?? 'The users could not be loaded.'),
      });
  }
}
