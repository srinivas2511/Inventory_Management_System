import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { AppConfigService } from '../../../core/config/app-config.service';

interface RoleDto { id: number; code: string; name: string; description?: string; permissions: string[]; }

@Component({
  selector: 'app-role-list',
  standalone: true,
  imports: [MatTableModule, MatProgressSpinnerModule],
  template: `
    <div style="padding:16px 16px 0"><h2 class="mat-h2">Roles</h2></div>
    @if (loading()) { <div style="display:flex;justify-content:center;padding:48px"><mat-spinner diameter="40"/></div> } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1" style="width:100%">
        <ng-container matColumnDef="code"><th mat-header-cell *matHeaderCellDef>Code</th><td mat-cell *matCellDef="let r">{{ r.code }}</td></ng-container>
        <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Name</th><td mat-cell *matCellDef="let r">{{ r.name }}</td></ng-container>
        <ng-container matColumnDef="desc"><th mat-header-cell *matHeaderCellDef>Description</th><td mat-cell *matCellDef="let r">{{ r.description }}</td></ng-container>
        <ng-container matColumnDef="perms"><th mat-header-cell *matHeaderCellDef>Permissions</th><td mat-cell *matCellDef="let r">{{ r.permissions?.length }}</td></ng-container>
        <tr mat-header-row *matHeaderRowDef="['code','name','desc','perms']"></tr>
        <tr mat-row *matRowDef="let row; columns: ['code','name','desc','perms'];"></tr>
      </table>
    }
  `,
})
export class RoleListComponent implements OnInit {
  protected readonly rows = signal<RoleDto[]>([]);
  protected readonly loading = signal(false);
  private readonly http = inject(HttpClient);
  private readonly base = inject(AppConfigService).apiBaseUrl;

  ngOnInit(): void {
    this.loading.set(true);
    this.http.get<RoleDto[]>(`${this.base}/api/admin/roles`).subscribe({
      next: (list) => { this.rows.set(list); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}
