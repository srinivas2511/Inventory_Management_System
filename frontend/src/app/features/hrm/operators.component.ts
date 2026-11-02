import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';

export interface OperatorDto {
  id: number;
  employeeCode: string;
  fullName: string;
  operatorCode?: string;
  designation?: string;
  department?: string;
  active: boolean;
}

@Component({
  selector: 'app-operators',
  standalone: true,
  imports: [MatTableModule, MatProgressSpinnerModule, MatIconModule],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Operators</h2>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="operatorCode">
          <th mat-header-cell *matHeaderCellDef>Operator ID</th>
          <td mat-cell *matCellDef="let r">{{ r.operatorCode ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="employeeCode">
          <th mat-header-cell *matHeaderCellDef>Employee Code</th>
          <td mat-cell *matCellDef="let r">{{ r.employeeCode }}</td>
        </ng-container>
        <ng-container matColumnDef="fullName">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let r">{{ r.fullName }}</td>
        </ng-container>
        <ng-container matColumnDef="designation">
          <th mat-header-cell *matHeaderCellDef>Designation</th>
          <td mat-cell *matCellDef="let r">{{ r.designation ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="department">
          <th mat-header-cell *matHeaderCellDef>Department</th>
          <td mat-cell *matCellDef="let r">{{ r.department ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Active</th>
          <td mat-cell *matCellDef="let r">
            <mat-icon [style.color]="r.active ? 'green' : 'grey'" aria-hidden="true">
              {{ r.active ? 'check_circle' : 'cancel' }}
            </mat-icon>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      @if (rows().length === 0) {
        <p class="empty">No operators defined. Mark employees as operators in the HR module.</p>
      }
    }
  `,
  styles: [`
    .page-header { display: flex; align-items: center; padding: 16px 16px 0; }
    .loading-center { display: flex; justify-content: center; padding: 48px; }
    table { width: 100%; }
    .empty { padding: 24px 16px; color: #666; }
  `],
})
export class OperatorsComponent implements OnInit {
  protected readonly columns = ['operatorCode', 'employeeCode', 'fullName', 'designation', 'department', 'status'];
  protected readonly rows = signal<OperatorDto[]>([]);
  protected readonly loading = signal(false);

  private readonly http = inject(HttpClient);

  ngOnInit(): void {
    this.loading.set(true);
    this.http.get<OperatorDto[]>('/api/hrm/operators').subscribe({
      next: (data) => { this.rows.set(data); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}
