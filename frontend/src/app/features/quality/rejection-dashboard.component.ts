import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';

export interface ParetoEntryDto {
  reasonId: number;
  reasonCode: string;
  reasonName: string;
  totalQty: number;
  count: number;
  pct: number;
}

@Component({
  selector: 'app-rejection-dashboard',
  standalone: true,
  imports: [MatTableModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatCardModule, DecimalPipe],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Rejection Analysis</h2>
      <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon> Refresh</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <mat-card class="section-card">
        <mat-card-header>
          <mat-card-title>Pareto — Top Rejection Reasons</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="pareto()" class="mat-elevation-z0">
            <ng-container matColumnDef="rank">
              <th mat-header-cell *matHeaderCellDef>#</th>
              <td mat-cell *matCellDef="let r; let i = index">{{ i + 1 }}</td>
            </ng-container>
            <ng-container matColumnDef="reasonCode">
              <th mat-header-cell *matHeaderCellDef>Code</th>
              <td mat-cell *matCellDef="let r">{{ r.reasonCode }}</td>
            </ng-container>
            <ng-container matColumnDef="reasonName">
              <th mat-header-cell *matHeaderCellDef>Reason</th>
              <td mat-cell *matCellDef="let r">{{ r.reasonName }}</td>
            </ng-container>
            <ng-container matColumnDef="count">
              <th mat-header-cell *matHeaderCellDef>Occurrences</th>
              <td mat-cell *matCellDef="let r">{{ r.count }}</td>
            </ng-container>
            <ng-container matColumnDef="totalQty">
              <th mat-header-cell *matHeaderCellDef>Total Qty</th>
              <td mat-cell *matCellDef="let r">{{ r.totalQty | number: '1.3-3' }}</td>
            </ng-container>
            <ng-container matColumnDef="pct">
              <th mat-header-cell *matHeaderCellDef>% of Total</th>
              <td mat-cell *matCellDef="let r">{{ r.pct | number: '1.1-1' }}%</td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="paretoColumns"></tr>
            <tr mat-row *matRowDef="let r; columns: paretoColumns"></tr>
          </table>
        </mat-card-content>
      </mat-card>
    }
  `,
  styles: [
    `
      .section-card {
        margin-bottom: 24px;
      }
      .page-header {
        display: flex;
        align-items: center;
        gap: 16px;
        margin-bottom: 16px;
      }
      .page-header h2 {
        flex: 1;
        margin: 0;
      }
    `,
  ],
})
export class RejectionDashboardComponent implements OnInit {
  private http = inject(HttpClient);

  paretoColumns = ['rank', 'reasonCode', 'reasonName', 'count', 'totalQty', 'pct'];
  pareto = signal<ParetoEntryDto[]>([]);
  loading = signal(false);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.http.get<ParetoEntryDto[]>('/api/rejection-reasons/pareto').subscribe({
      next: (data) => {
        this.pareto.set(data ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
