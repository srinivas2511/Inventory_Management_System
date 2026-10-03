import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { DashboardKpiDto, InsightApi } from '../insight/insight.api';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  providers: [InsightApi],
  imports: [MatCardModule, MatIconModule, MatProgressSpinnerModule, RouterLink, CurrencyPipe, DecimalPipe],
  template: `
    <h1 class="mat-h1">Dashboard</h1>

    @if (loading()) {
      <div class="spinner-center"><mat-spinner diameter="40" /></div>
    }

    @if (kpis(); as k) {
      <div class="kpi-grid">
        <mat-card appearance="outlined" class="kpi-card">
          <mat-card-header>
            <mat-icon mat-card-avatar>inventory_2</mat-icon>
            <mat-card-title>Inventory</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="kpi-row">
              <span>SKUs with stock</span><strong>{{ k.inventory.itemsWithStock }}</strong>
            </div>
            <div class="kpi-row">
              <span>Total value</span
              ><strong>{{ k.inventory.totalValueInr | currency: 'INR' : 'symbol' : '1.0-0' }}</strong>
            </div>
          </mat-card-content>
          <mat-card-actions><a mat-button routerLink="/inventory/stock">View Stock</a></mat-card-actions>
        </mat-card>

        <mat-card appearance="outlined" class="kpi-card">
          <mat-card-header>
            <mat-icon mat-card-avatar>precision_manufacturing</mat-icon>
            <mat-card-title>Production</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="kpi-row">
              <span>In progress</span><strong>{{ k.production.ordersInProgress }}</strong>
            </div>
            <div class="kpi-row">
              <span>Yield this month</span><strong>{{ k.production.yieldPct | number: '1.1-1' }}%</strong>
            </div>
            <div class="kpi-row">
              <span>Good qty</span><strong>{{ k.production.goodQtyMonth | number: '1.0-0' }}</strong>
            </div>
            @if (k.production.monthlyTarget > 0) {
              <div class="kpi-row">
                <span>Target attainment ({{ k.production.monthlyTarget | number: '1.0-0' }})</span
                ><strong>{{ k.production.targetAttainmentPct | number: '1.1-1' }}%</strong>
              </div>
            }
          </mat-card-content>
          <mat-card-actions><a mat-button routerLink="/production/orders">View Orders</a></mat-card-actions>
        </mat-card>

        <mat-card appearance="outlined" class="kpi-card">
          <mat-card-header>
            <mat-icon mat-card-avatar>fact_check</mat-icon>
            <mat-card-title>Quality</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="kpi-row">
              <span>Pending inspections</span
              ><strong [class.warn]="k.quality.pendingInspections > 0">{{ k.quality.pendingInspections }}</strong>
            </div>
            <div class="kpi-row">
              <span>Pass rate (month)</span><strong>{{ k.quality.passRateMonth | number: '1.1-1' }}%</strong>
            </div>
          </mat-card-content>
          <mat-card-actions><a mat-button routerLink="/quality/inspections">View Inspections</a></mat-card-actions>
        </mat-card>

        <mat-card appearance="outlined" class="kpi-card">
          <mat-card-header>
            <mat-icon mat-card-avatar>shopping_cart</mat-icon>
            <mat-card-title>Procurement</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="kpi-row">
              <span>Open POs</span><strong>{{ k.procurement.openPOs }}</strong>
            </div>
            <div class="kpi-row">
              <span>Overdue</span
              ><strong [class.warn]="k.procurement.overduePOs > 0">{{ k.procurement.overduePOs }}</strong>
            </div>
          </mat-card-content>
          <mat-card-actions><a mat-button routerLink="/procurement/purchase-orders">View POs</a></mat-card-actions>
        </mat-card>

        <mat-card appearance="outlined" class="kpi-card">
          <mat-card-header>
            <mat-icon mat-card-avatar>sell</mat-icon>
            <mat-card-title>Sales</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="kpi-row">
              <span>Open orders</span><strong>{{ k.sales.openOrders }}</strong>
            </div>
            <div class="kpi-row">
              <span>Dispatched (month)</span><strong>{{ k.sales.dispatchedThisMonth }}</strong>
            </div>
          </mat-card-content>
          <mat-card-actions><a mat-button routerLink="/sales/orders">View Sales</a></mat-card-actions>
        </mat-card>

        <mat-card appearance="outlined" class="kpi-card alert-card">
          <mat-card-header>
            <mat-icon mat-card-avatar [class.has-alerts]="k.alerts.total > 0">notifications_active</mat-icon>
            <mat-card-title>Alerts</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="kpi-row">
              <span class="critical">Critical</span><strong>{{ k.alerts.critical }}</strong>
            </div>
            <div class="kpi-row">
              <span class="high">High</span><strong>{{ k.alerts.high }}</strong>
            </div>
            <div class="kpi-row">
              <span class="medium">Medium</span><strong>{{ k.alerts.medium }}</strong>
            </div>
          </mat-card-content>
          <mat-card-actions><a mat-button routerLink="/insight/alerts">View Alerts</a></mat-card-actions>
        </mat-card>
      </div>
    }
  `,
  styles: `
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 16px;
      padding: 16px 0;
    }
    .kpi-card mat-card-header {
      margin-bottom: 8px;
    }
    .kpi-row {
      display: flex;
      justify-content: space-between;
      padding: 4px 0;
      font-size: 14px;
    }
    .warn {
      color: #e65100;
    }
    .has-alerts {
      color: #e65100;
    }
    .critical {
      color: #b71c1c;
      font-weight: 500;
    }
    .high {
      color: #e65100;
      font-weight: 500;
    }
    .medium {
      color: #f57c00;
      font-weight: 500;
    }
    .spinner-center {
      display: flex;
      justify-content: center;
      padding: 48px;
    }
  `,
})
export class DashboardComponent implements OnInit {
  private readonly api = inject(InsightApi);

  protected readonly loading = signal(false);
  protected readonly kpis = signal<DashboardKpiDto | null>(null);

  ngOnInit(): void {
    this.loading.set(true);
    this.api.getDashboardKpis().subscribe({
      next: (k) => {
        this.kpis.set(k);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
