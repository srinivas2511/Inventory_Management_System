import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FinanceApi, FinanceDashboardDto } from './finance.api';

@Component({
  selector: 'app-finance-dashboard',
  standalone: true,
  imports: [DecimalPipe, MatCardModule, MatProgressSpinnerModule],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Finance Dashboard</h2>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else if (data()) {
      <div class="kpi-grid">
        <mat-card>
          <mat-card-content>
            <div class="kpi-label">Open Period</div>
            <div class="kpi-value">{{ data()!.openPeriodName }}</div>
          </mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-content>
            <div class="kpi-label">AP Outstanding</div>
            <div class="kpi-value">{{ data()!.totalApOutstanding | number: '1.0-0' }}</div>
            <div class="kpi-sub">Overdue: {{ data()!.apOverdue | number: '1.0-0' }}</div>
          </mat-card-content>
        </mat-card>
        <mat-card>
          <mat-card-content>
            <div class="kpi-label">AR Outstanding</div>
            <div class="kpi-value">{{ data()!.totalArOutstanding | number: '1.0-0' }}</div>
            <div class="kpi-sub">Overdue: {{ data()!.arOverdue | number: '1.0-0' }}</div>
          </mat-card-content>
        </mat-card>
      </div>
    }
  `,
  styles: [
    `
      .kpi-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
        gap: 16px;
      }
      .kpi-label {
        font-size: 0.85rem;
        color: #666;
      }
      .kpi-value {
        font-size: 1.8rem;
        font-weight: 600;
      }
      .kpi-sub {
        font-size: 0.85rem;
        color: #c00;
      }
    `,
  ],
})
export class FinanceDashboardComponent implements OnInit {
  private api = inject(FinanceApi);

  data = signal<FinanceDashboardDto | null>(null);
  loading = signal(false);

  ngOnInit() {
    this.loading.set(true);
    this.api.getDashboard().subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
