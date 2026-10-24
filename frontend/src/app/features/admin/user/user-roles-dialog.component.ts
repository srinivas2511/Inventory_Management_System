import { Component, OnInit, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { problemMessage } from '../../../core/models/problem.model';
import { AdminApi, PermissionDto, RoleDto, UserDto } from '../admin.api';

export interface UserRolesData {
  user: UserDto;
}

interface PermGroup {
  module: string;
  perms: PermissionDto[];
}

interface RoleView extends RoleDto {
  permGroups: PermGroup[];
}

interface RoleGroup {
  label: string;
  roles: RoleView[];
}

const GROUP_DEFS: { label: string; codes: string[] }[] = [
  { label: 'Platform', codes: ['ADMIN', 'MANAGEMENT'] },
  { label: 'Operations', codes: ['PRODUCTION_MANAGER', 'SUPERVISOR', 'OPERATOR'] },
  { label: 'Quality', codes: ['QUALITY_MANAGER'] },
  {
    label: 'Supply Chain',
    codes: ['STORE_MANAGER', 'STORE_OPERATOR', 'PURCHASE_MANAGER', 'PLANNER', 'WAREHOUSE_MANAGER'],
  },
  { label: 'Sales & Dispatch', codes: ['SALES', 'DISPATCH', 'CRM_AGENT'] },
  { label: 'Finance', codes: ['FINANCE_MANAGER', 'ACCOUNTANT', 'CONTROLLER'] },
  { label: 'Engineering', codes: ['ENGINEER', 'PLM_ENGINEER'] },
  { label: 'HR', codes: ['HR_MANAGER'] },
  { label: 'Maintenance', codes: ['MAINTENANCE'] },
];

@Component({
  selector: 'app-user-roles-dialog',
  standalone: true,
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  template: `
    <h2 mat-dialog-title>Roles — {{ data.user.fullName }}</h2>

    <mat-dialog-content>
      @if (loading()) {
        <div class="loading"><mat-spinner diameter="32" /></div>
      } @else {
        @for (g of groups(); track g.label) {
          <div class="role-group">
            <div class="group-label">{{ g.label }}</div>

            @for (r of g.roles; track r.code) {
              <div class="role-row" [class.role-row--on]="isSelected(r.code)">
                <div class="role-main">
                  <mat-checkbox
                    [checked]="isSelected(r.code)"
                    (change)="toggle(r.code)"
                    [attr.data-testid]="'role-' + r.code"
                    [attr.aria-label]="r.name"
                  >
                    <span class="role-name">{{ r.name }}</span>
                  </mat-checkbox>
                  @if (r.description) {
                    <span class="role-desc">{{ r.description }}</span>
                  }
                </div>

                @if (r.permissions.length) {
                  <button
                    mat-button
                    class="perm-toggle"
                    [attr.aria-label]="'Show permissions for ' + r.name"
                    [attr.aria-expanded]="isExpanded(r.code)"
                    (click)="toggleExpand(r.code)"
                    [attr.data-testid]="'expand-' + r.code"
                  >
                    {{ r.permissions.length }} permissions
                    <mat-icon iconPositionEnd>{{ isExpanded(r.code) ? 'expand_less' : 'expand_more' }}</mat-icon>
                  </button>
                }
              </div>

              @if (isExpanded(r.code)) {
                <div class="perm-panel" [attr.data-testid]="'perms-' + r.code">
                  @for (pg of r.permGroups; track pg.module) {
                    <div class="perm-module">{{ pg.module }}</div>
                    <div class="perm-chips">
                      @for (p of pg.perms; track p.id) {
                        <span class="perm-chip" [title]="p.code">
                          {{ p.description ?? p.code }}
                        </span>
                      }
                    </div>
                  }
                </div>
              }
            }
          </div>
        }

        <p class="note">
          Changes take effect on the user's next request. If
          {{ data.user.username }} is signed in now, their session continues with the old permissions until they
          refresh.
        </p>
      }

      @if (errorMessage()) {
        <p class="form-error" role="alert" data-testid="roles-error">{{ errorMessage() }}</p>
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
        {{ saving() ? 'Saving…' : 'Save roles' }}
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

      /* ── Group ─────────────────────────────────────── */
      .role-group {
        margin-bottom: 8px;
      }

      .group-label {
        font-size: 11px;
        font-weight: 600;
        letter-spacing: 0.06em;
        text-transform: uppercase;
        color: var(--mat-sys-on-surface-variant, rgba(0, 0, 0, 0.6));
        padding: 12px 0 4px;
        border-top: 1px solid var(--mat-sys-outline-variant, rgba(0, 0, 0, 0.12));
      }

      .role-group:first-of-type .group-label {
        border-top: none;
        padding-top: 4px;
      }

      /* ── Role row ──────────────────────────────────── */
      .role-row {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 8px;
        padding: 4px 8px 4px 4px;
        border-radius: 6px;
        transition: background 0.1s;
      }

      .role-row:hover {
        background: var(--mat-sys-surface-container, rgba(0, 0, 0, 0.04));
      }

      .role-row--on {
        background: var(--mat-sys-primary-container, #e8f4fd);
      }

      .role-main {
        display: flex;
        flex-direction: column;
        flex: 1;
        min-width: 0;
      }

      .role-name {
        font-weight: 500;
      }

      .role-desc {
        font-size: 12px;
        color: var(--mat-sys-on-surface-variant, rgba(0, 0, 0, 0.6));
        margin-left: 36px;
        line-height: 1.4;
      }

      .perm-toggle {
        flex-shrink: 0;
        font-size: 12px;
        color: var(--mat-sys-on-surface-variant, rgba(0, 0, 0, 0.6));
        min-width: 0;
        padding: 0 4px;
        height: 32px;
        line-height: 32px;
        margin-top: 2px;
      }

      /* ── Permission panel ──────────────────────────── */
      .perm-panel {
        margin: 0 0 8px 36px;
        padding: 10px 12px;
        background: var(--mat-sys-surface-container-low, #f5f5f5);
        border-radius: 6px;
        border-left: 3px solid var(--mat-sys-primary, #1976d2);
      }

      .perm-module {
        font-size: 10px;
        font-weight: 700;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        color: var(--mat-sys-on-surface-variant, rgba(0, 0, 0, 0.6));
        margin: 8px 0 4px;
      }

      .perm-module:first-child {
        margin-top: 0;
      }

      .perm-chips {
        display: flex;
        flex-wrap: wrap;
        gap: 4px;
      }

      .perm-chip {
        font-size: 11px;
        padding: 2px 8px;
        border-radius: 12px;
        background: var(--mat-sys-surface-container, rgba(0, 0, 0, 0.06));
        color: var(--mat-sys-on-surface, rgba(0, 0, 0, 0.87));
        white-space: nowrap;
        cursor: default;
      }

      /* ── Footer ────────────────────────────────────── */
      .note {
        font-size: 12px;
        color: var(--mat-sys-on-surface-variant, rgba(0, 0, 0, 0.6));
        margin: 12px 0 0;
      }

      .form-error {
        color: var(--mat-sys-error, #f44336);
        font-size: 13px;
        margin-top: 8px;
      }
    `,
  ],
})
export class UserRolesDialogComponent implements OnInit {
  protected readonly data = inject<UserRolesData>(MAT_DIALOG_DATA);
  protected readonly groups = signal<RoleGroup[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly selected = signal(new Set<string>(this.data.user.roles));
  protected readonly expanded = signal(new Set<string>());

  private readonly api = inject(AdminApi);
  private readonly dialogRef = inject(MatDialogRef<UserRolesDialogComponent, UserDto>);

  ngOnInit(): void {
    this.api.getRoles().subscribe({
      next: (roles) => {
        this.groups.set(this.buildGroups(roles));
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(problemMessage(err, 'The roles could not be loaded.'));
      },
    });
  }

  private buildGroups(roles: RoleDto[]): RoleGroup[] {
    const byCode = new Map(roles.map((r) => [r.code, r]));
    const placed = new Set<string>();
    const groups: RoleGroup[] = [];

    for (const def of GROUP_DEFS) {
      const matched = def.codes
        .map((c) => byCode.get(c))
        .filter((r): r is RoleDto => !!r)
        .map((r) => this.toView(r));
      if (matched.length) {
        matched.forEach((r) => placed.add(r.code));
        groups.push({ label: def.label, roles: matched });
      }
    }

    const others = roles.filter((r) => !placed.has(r.code)).map((r) => this.toView(r));
    if (others.length) groups.push({ label: 'Other', roles: others });

    return groups;
  }

  private toView(r: RoleDto): RoleView {
    const moduleMap = new Map<string, PermissionDto[]>();
    for (const p of r.permissions) {
      const list = moduleMap.get(p.module) ?? [];
      list.push(p);
      moduleMap.set(p.module, list);
    }
    const permGroups: PermGroup[] = Array.from(moduleMap.entries()).map(([module, perms]) => ({
      module,
      perms,
    }));
    return { ...r, permGroups };
  }

  protected isSelected(code: string): boolean {
    return this.selected().has(code);
  }

  protected isExpanded(code: string): boolean {
    return this.expanded().has(code);
  }

  protected toggle(code: string): void {
    const next = new Set(this.selected());
    if (next.has(code)) next.delete(code);
    else next.add(code);
    this.selected.set(next);
  }

  protected toggleExpand(code: string): void {
    const next = new Set(this.expanded());
    if (next.has(code)) next.delete(code);
    else next.add(code);
    this.expanded.set(next);
  }

  protected save(): void {
    this.saving.set(true);
    this.errorMessage.set('');
    this.api.assignRoles(this.data.user.id, { roleCodes: [...this.selected()] }).subscribe({
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
