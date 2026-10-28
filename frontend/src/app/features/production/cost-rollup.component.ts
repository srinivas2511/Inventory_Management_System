import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';

export interface CostRollupLogDto {
  id: number;
  materialId: number;
  rolledCost: number;
  bomId: number | null;
  depth: number;
  rolledAt: string | null;
}

@Component({
  selector: 'app-cost-rollup',
  standalone: true,
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatSnackBarModule,
    IstDatePipe,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.item-cost-rollup">Cost Roll-up</h2>
      <button mat-flat-button color="primary" (click)="runRollup()" [disabled]="running()">
        <mat-icon aria-hidden="true">refresh</mat-icon> Run Roll-up
      </button>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <table mat-table [dataSource]="logs()" class="mat-elevation-z2 full-width">
        <ng-container matColumnDef="id">
          <th mat-header-cell *matHeaderCellDef>#</th>
          <td mat-cell *matCellDef="let row">{{ row.id }}</td>
        </ng-container>
        <ng-container matColumnDef="materialId">
          <th mat-header-cell *matHeaderCellDef>Material ID</th>
          <td mat-cell *matCellDef="let row">{{ row.materialId }}</td>
        </ng-container>
        <ng-container matColumnDef="rolledCost">
          <th mat-header-cell *matHeaderCellDef>Rolled Cost (₹)</th>
          <td mat-cell *matCellDef="let row">{{ row.rolledCost | number: '1.4-4' }}</td>
        </ng-container>
        <ng-container matColumnDef="depth">
          <th mat-header-cell *matHeaderCellDef>Depth</th>
          <td mat-cell *matCellDef="let row">{{ row.depth }}</td>
        </ng-container>
        <ng-container matColumnDef="rolledAt">
          <th mat-header-cell *matHeaderCellDef>Rolled At</th>
          <td mat-cell *matCellDef="let row">{{ row.rolledAt | istDate: 'short' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
})
export class CostRollupComponent implements OnInit {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);

  readonly columns = ['id', 'materialId', 'rolledCost', 'depth', 'rolledAt'];
  readonly logs = signal<CostRollupLogDto[]>([]);
  readonly loading = signal(true);
  readonly running = signal(false);

  ngOnInit(): void {
    this.loadLogs();
  }

  runRollup(): void {
    this.running.set(true);
    this.http.post<CostRollupLogDto[]>('/api/production/cost-rollup/run', {}).subscribe({
      next: (data) => {
        this.logs.set(data);
        this.running.set(false);
        this.snack.open('Roll-up completed', 'Close', { duration: 3000 });
      },
      error: () => {
        this.snack.open('Roll-up failed', 'Close', { duration: 3000 });
        this.running.set(false);
      },
    });
  }

  private loadLogs(): void {
    this.http.get<CostRollupLogDto[]>('/api/production/cost-rollup').subscribe({
      next: (data) => {
        this.logs.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.snack.open('Failed to load cost roll-up log', 'Close', { duration: 3000 });
        this.loading.set(false);
      },
    });
  }
}
