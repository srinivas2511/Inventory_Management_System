import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { AdminApi, RoleDto } from '../admin.api';
import { RoleDetailDialogComponent } from './role-detail-dialog.component';

@Component({
  selector: 'app-role-list',
  standalone: true,
  imports: [MatTableModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <div style="padding:16px 16px 0"><h2 class="mat-h2">Roles</h2></div>
    @if (loading()) {
      <div style="display:flex;justify-content:center;padding:48px"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1" style="width:100%">
        <ng-container matColumnDef="code"
          ><th mat-header-cell *matHeaderCellDef>Code</th>
          <td mat-cell *matCellDef="let r">{{ r.code }}</td></ng-container
        >
        <ng-container matColumnDef="name"
          ><th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let r">{{ r.name }}</td></ng-container
        >
        <ng-container matColumnDef="desc"
          ><th mat-header-cell *matHeaderCellDef>Description</th>
          <td mat-cell *matCellDef="let r">{{ r.description }}</td></ng-container
        >
        <ng-container matColumnDef="perms"
          ><th mat-header-cell *matHeaderCellDef>Permissions</th>
          <td mat-cell *matCellDef="let r">
            <button mat-button (click)="show(r)" [attr.data-testid]="'role-' + r.code">
              {{ r.permissions?.length ?? 0 }} permissions
            </button>
          </td></ng-container
        >
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
})
export class RoleListComponent implements OnInit {
  protected readonly columns = ['code', 'name', 'desc', 'perms'];
  protected readonly rows = signal<RoleDto[]>([]);
  protected readonly loading = signal(false);
  private readonly api = inject(AdminApi);
  private readonly dialog = inject(MatDialog);

  ngOnInit(): void {
    this.loading.set(true);
    this.api.getRoles().subscribe({
      next: (list) => {
        this.rows.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected show(role: RoleDto): void {
    this.dialog.open(RoleDetailDialogComponent, { data: { role }, width: '720px' });
  }
}
