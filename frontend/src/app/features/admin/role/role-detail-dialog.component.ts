import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { PermissionDto, RoleDto } from '../admin.api';

export interface RoleDetailData {
  role: RoleDto;
}

interface ModuleGroup {
  module: string;
  permissions: PermissionDto[];
}

/** What a role can do: its permissions, grouped by the module they belong to. */
@Component({
  selector: 'app-role-detail-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.role.name }}</h2>
    <mat-dialog-content>
      <p class="mat-body-2 meta">
        {{ data.role.code }}{{ data.role.systemRole ? ' · built in' : '' }} · {{ data.role.permissions.length }}
        permissions
      </p>
      @if (data.role.description) {
        <p class="mat-body-1">{{ data.role.description }}</p>
      }
      @for (group of groups; track group.module) {
        <h3 class="mat-subtitle-2 module">{{ group.module }}</h3>
        <ul class="perms" [attr.data-testid]="'module-' + group.module">
          @for (p of group.permissions; track p.code) {
            <li>
              <code>{{ p.code }}</code>
              @if (p.description) {
                <span class="desc"> {{ p.description }}</span>
              }
            </li>
          }
        </ul>
      } @empty {
        <p class="mat-body-1">This role holds no permissions.</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .meta {
        color: var(--mat-sys-on-surface-variant, rgba(0, 0, 0, 0.6));
      }
      .module {
        margin: 16px 0 4px;
      }
      .perms {
        margin: 0;
        padding-left: 20px;
        columns: 2;
        column-gap: 24px;
      }
      .desc {
        color: var(--mat-sys-on-surface-variant, rgba(0, 0, 0, 0.6));
      }
      @media (max-width: 600px) {
        .perms {
          columns: 1;
        }
      }
    `,
  ],
})
export class RoleDetailDialogComponent {
  protected readonly data = inject<RoleDetailData>(MAT_DIALOG_DATA);
  protected readonly groups: ModuleGroup[] = this.group(this.data.role.permissions);

  private group(permissions: PermissionDto[]): ModuleGroup[] {
    const byModule = new Map<string, PermissionDto[]>();
    for (const p of permissions) {
      byModule.set(p.module, [...(byModule.get(p.module) ?? []), p]);
    }
    return [...byModule.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([module, perms]) => ({ module, permissions: [...perms].sort((a, b) => a.code.localeCompare(b.code)) }));
  }
}
