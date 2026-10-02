import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { ToastService } from '../../../core/notification/toast.service';
import { DialogService } from '../../../shared/dialogs/dialog.service';
import { problemOf } from '../../../shared/forms/server-errors';
import { RolesApi } from '../api/roles.api';
import { RoleFormDialogComponent } from '../dialogs/role-form-dialog.component';
import { PermissionResponse, RoleResponse, RoleSummary } from '../models';

/** Role administration: list roles, edit a role's permissions grouped by module, create/delete custom roles. */
@Component({
  selector: 'app-roles-page',
  standalone: true,
  imports: [MatButtonModule, MatCheckboxModule, MatIconModule, MatListModule],
  template: `
    <header class="head">
      <h1>Roles and permissions</h1>
      <button mat-flat-button color="primary" (click)="create()" data-testid="new-role">
        <mat-icon>add</mat-icon> New role
      </button>
    </header>
    @if (error()) {
      <p class="error" role="alert" data-testid="list-error">{{ error() }}</p>
    }
    <div class="layout">
      <nav class="list" aria-label="Roles">
        @for (r of roles(); track r.id) {
          <button
            type="button"
            class="role"
            [class.active]="r.id === selectedId()"
            (click)="select(r)"
            [attr.data-testid]="'role-' + r.code"
          >
            <strong>{{ r.name }}</strong>
            <span>{{ r.code }}{{ r.systemRole ? ' · system' : '' }} · {{ r.userCount }} users</span>
          </button>
        }
      </nav>
      <section class="detail">
        @if (role(); as current) {
          <div class="detail-head">
            <div>
              <h2>{{ current.name }}</h2>
              <p>{{ current.description }}</p>
            </div>
            <div>
              <button mat-button (click)="editDetails(current)" data-testid="edit-role">Edit details</button>
              @if (!current.systemRole) {
                <button mat-button color="warn" (click)="remove(current)" data-testid="delete-role">Delete</button>
              }
            </div>
          </div>
          @for (group of groups(); track group.module) {
            <fieldset class="module">
              <legend>{{ group.module }}</legend>
              @for (p of group.items; track p.code) {
                <mat-checkbox
                  [checked]="draft().has(p.code)"
                  (change)="toggle(p.code, $event.checked)"
                  [attr.data-testid]="'perm-' + p.code"
                >
                  {{ p.code }}
                  @if (p.description) {
                    <small> — {{ p.description }}</small>
                  }
                </mat-checkbox>
              }
            </fieldset>
          }
          <footer class="actions">
            <button
              mat-flat-button
              color="primary"
              (click)="save()"
              [disabled]="!dirty() || busy()"
              data-testid="save-permissions"
            >
              Save permissions
            </button>
            <button mat-button (click)="discard()" [disabled]="!dirty()" data-testid="discard">Discard</button>
          </footer>
        } @else {
          <p data-testid="pick-role">Select a role to view its permissions.</p>
        }
      </section>
    </div>
  `,
  styles: `
    .head,
    .detail-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .layout {
      display: grid;
      gap: 16px;
      grid-template-columns: minmax(220px, 300px) 1fr;
    }
    .list {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .role {
      background: none;
      border: 1px solid #cfd8dc;
      border-radius: 6px;
      cursor: pointer;
      display: flex;
      flex-direction: column;
      padding: 8px 12px;
      text-align: left;
    }
    .role span {
      font-size: 12px;
      opacity: 0.7;
    }
    .role.active {
      border-color: #1565c0;
      background: #e3f2fd;
    }
    .module {
      border: 1px solid #cfd8dc;
      border-radius: 6px;
      display: grid;
      gap: 0 16px;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      margin: 0 0 12px;
    }
    .actions {
      display: flex;
      gap: 8px;
    }
    .error {
      color: #c62828;
    }
    @media (max-width: 800px) {
      .layout {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class RolesPageComponent implements OnInit {
  private readonly api = inject(RolesApi);
  private readonly dialog = inject(MatDialog);
  private readonly dialogs = inject(DialogService);
  private readonly toast = inject(ToastService);

  protected readonly roles = signal<RoleSummary[]>([]);
  protected readonly catalogue = signal<PermissionResponse[]>([]);
  protected readonly role = signal<RoleResponse | null>(null);
  protected readonly selectedId = computed(() => this.role()?.id ?? null);
  protected readonly draft = signal(new Set<string>());
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  protected readonly groups = computed(() => {
    const byModule = new Map<string, PermissionResponse[]>();
    for (const p of this.catalogue()) {
      byModule.set(p.module, [...(byModule.get(p.module) ?? []), p]);
    }
    return [...byModule.entries()].map(([module, items]) => ({ module, items }));
  });

  protected readonly dirty = computed(() => {
    const current = this.role();
    const draft = this.draft();
    return !!current && (current.permissions.length !== draft.size || current.permissions.some((p) => !draft.has(p)));
  });

  ngOnInit(): void {
    this.api.permissions().subscribe({
      next: (permissions) => this.catalogue.set(permissions),
      error: (e: unknown) => this.error.set(problemOf(e)?.detail ?? 'The permissions could not be loaded.'),
    });
    this.reload();
  }

  protected select(summary: RoleSummary): void {
    this.api.get(summary.id).subscribe({
      next: (full) => this.show(full),
      error: (e: unknown) => this.toast.error(problemOf(e)?.detail ?? 'The role could not be loaded.'),
    });
  }

  protected toggle(code: string, checked: boolean): void {
    const next = new Set(this.draft());
    if (checked) {
      next.add(code);
    } else {
      next.delete(code);
    }
    this.draft.set(next);
  }

  protected discard(): void {
    this.draft.set(new Set(this.role()?.permissions ?? []));
  }

  protected async save(): Promise<void> {
    const current = this.role();
    if (!current) {
      return;
    }
    const reason = await this.dialogs.askReason({
      title: `Save permissions of ${current.name}?`,
      message: 'Users holding this role pick the change up within a minute.',
      confirmLabel: 'Save',
      required: false,
    });
    if (reason === null) {
      return;
    }
    this.busy.set(true);
    this.api.setPermissions(current.id, [...this.draft()], reason).subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.toast.success('Permissions saved.');
        this.show(saved);
        this.reload();
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.toast.error(problemOf(e)?.detail ?? 'The permissions could not be saved.');
      },
    });
  }

  protected create(): void {
    this.dialog
      .open(RoleFormDialogComponent, { data: {}, width: '480px' })
      .afterClosed()
      .subscribe((saved: RoleResponse | undefined) => {
        if (saved) {
          this.show(saved);
          this.reload();
        }
      });
  }

  protected editDetails(current: RoleResponse): void {
    this.dialog
      .open(RoleFormDialogComponent, { data: { role: current, version: current.version }, width: '480px' })
      .afterClosed()
      .subscribe((saved: RoleResponse | undefined) => {
        if (saved) {
          this.show(saved);
          this.reload();
        }
      });
  }

  protected async remove(current: RoleResponse): Promise<void> {
    const ok = await this.dialogs.confirm({
      title: `Delete role ${current.name}?`,
      message: 'A role that still has users cannot be deleted.',
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!ok) {
      return;
    }
    this.api.delete(current.id).subscribe({
      next: () => {
        this.toast.success('Role deleted.');
        this.role.set(null);
        this.draft.set(new Set());
        this.reload();
      },
      error: (e: unknown) => this.toast.error(problemOf(e)?.detail ?? 'The role could not be deleted.'),
    });
  }

  private show(full: RoleResponse): void {
    this.role.set(full);
    this.draft.set(new Set(full.permissions));
  }

  private reload(): void {
    this.api.list().subscribe({
      next: (roles) => this.roles.set(roles),
      error: (e: unknown) => this.error.set(problemOf(e)?.detail ?? 'The roles could not be loaded.'),
    });
  }
}
