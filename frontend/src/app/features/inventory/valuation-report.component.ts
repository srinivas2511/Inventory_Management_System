import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';

import { InventoryApi, ValuationLineDto, ValuationReportDto } from './inventory.api';
import { LookupApi, LookupOption } from '../../shared/lookup.api';

@Component({
  selector: 'app-valuation-report',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatIconModule,
    CurrencyPipe,
    DecimalPipe,
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <h2>Inventory Valuation Report</h2>
        <div class="header-actions">
          @if (report()) {
            <mat-chip-set>
              <mat-chip [class]="report()!.costMethod === 'STANDARD' ? 'chip-standard' : 'chip-avg'">
                {{ report()!.costMethod === 'STANDARD' ? 'Standard Cost' : 'Moving Average' }}
              </mat-chip>
            </mat-chip-set>
          }
          <mat-form-field appearance="outline" class="warehouse-filter">
            <mat-label>Warehouse</mat-label>
            <mat-select [(ngModel)]="selectedWarehouseId" (ngModelChange)="load()">
              <mat-option [value]="null">All Warehouses</mat-option>
              @for (w of warehouses(); track w.id) {
                <mat-option [value]="w.id">{{ w.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <button mat-stroked-button (click)="exportCsv()" [disabled]="!report()">
            <mat-icon aria-hidden="true">download</mat-icon>
            Export CSV
          </button>
        </div>
      </div>

      @if (loading()) {
        <div class="spinner-wrap">
          <mat-progress-spinner mode="indeterminate" diameter="40" />
        </div>
      }

      @if (report() && !loading()) {
        <div class="summary-row">
          <mat-card class="summary-card">
            <mat-card-content>
              <div class="summary-label">Raw Materials</div>
              <div class="summary-value">{{ report()!.rmTotal | currency: 'INR' : 'symbol' : '1.2-2' }}</div>
            </mat-card-content>
          </mat-card>
          <mat-card class="summary-card">
            <mat-card-content>
              <div class="summary-label">Finished Goods</div>
              <div class="summary-value">{{ report()!.fgTotal | currency: 'INR' : 'symbol' : '1.2-2' }}</div>
            </mat-card-content>
          </mat-card>
          <mat-card class="summary-card grand-total">
            <mat-card-content>
              <div class="summary-label">Grand Total</div>
              <div class="summary-value">{{ report()!.grandTotal | currency: 'INR' : 'symbol' : '1.2-2' }}</div>
            </mat-card-content>
          </mat-card>
        </div>

        <table mat-table [dataSource]="report()!.lines" class="full-width mat-elevation-z1">
          <ng-container matColumnDef="itemCode">
            <th mat-header-cell *matHeaderCellDef>Code</th>
            <td mat-cell *matCellDef="let r">{{ r.itemCode }}</td>
          </ng-container>
          <ng-container matColumnDef="itemName">
            <th mat-header-cell *matHeaderCellDef>Name</th>
            <td mat-cell *matCellDef="let r">{{ r.itemName }}</td>
          </ng-container>
          <ng-container matColumnDef="itemType">
            <th mat-header-cell *matHeaderCellDef>Type</th>
            <td mat-cell *matCellDef="let r">
              <span class="type-badge" [class.rm]="r.itemType === 'MATERIAL'" [class.fg]="r.itemType === 'PRODUCT'">
                {{ r.itemType === 'MATERIAL' ? 'RM' : 'FG' }}
              </span>
            </td>
          </ng-container>
          <ng-container matColumnDef="uom">
            <th mat-header-cell *matHeaderCellDef>UOM</th>
            <td mat-cell *matCellDef="let r">{{ r.uom }}</td>
          </ng-container>
          <ng-container matColumnDef="qtyOnHand">
            <th mat-header-cell *matHeaderCellDef class="num">Qty on Hand</th>
            <td mat-cell *matCellDef="let r" class="num">{{ r.qtyOnHand | number: '1.3-3' }}</td>
          </ng-container>
          <ng-container matColumnDef="avgUnitCost">
            <th mat-header-cell *matHeaderCellDef class="num">Avg Cost (₹)</th>
            <td mat-cell *matCellDef="let r" class="num">{{ r.avgUnitCost | number: '1.4-4' }}</td>
          </ng-container>
          <ng-container matColumnDef="standardCost">
            <th mat-header-cell *matHeaderCellDef class="num">Std Cost (₹)</th>
            <td mat-cell *matCellDef="let r" class="num">{{ r.standardCost | number: '1.4-4' }}</td>
          </ng-container>
          <ng-container matColumnDef="totalValue">
            <th mat-header-cell *matHeaderCellDef class="num">Total Value (₹)</th>
            <td mat-cell *matCellDef="let r" class="num">{{ r.totalValue | number: '1.2-2' }}</td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns()"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns()"></tr>
        </table>

        @if (report()!.lines.length === 0) {
          <p class="empty-state">No stock on hand for the selected warehouse.</p>
        }
      }
    </div>
  `,
  styles: [
    `
      .page-container {
        padding: 24px;
        max-width: 1400px;
      }
      .page-header {
        display: flex;
        align-items: center;
        gap: 16px;
        flex-wrap: wrap;
        margin-bottom: 24px;
      }
      .page-header h2 {
        margin: 0;
        flex: 1;
      }
      .header-actions {
        display: flex;
        align-items: center;
        gap: 12px;
        flex-wrap: wrap;
      }
      .warehouse-filter {
        width: 200px;
      }
      .chip-standard {
        background-color: #e8f5e9 !important;
        color: #2e7d32 !important;
      }
      .chip-avg {
        background-color: #e3f2fd !important;
        color: #1565c0 !important;
      }
      .spinner-wrap {
        display: flex;
        justify-content: center;
        padding: 40px;
      }
      .summary-row {
        display: flex;
        gap: 16px;
        margin-bottom: 24px;
        flex-wrap: wrap;
      }
      .summary-card {
        flex: 1;
        min-width: 180px;
      }
      .summary-card.grand-total {
        border-top: 3px solid #1565c0;
      }
      .summary-label {
        font-size: 12px;
        color: rgba(0, 0, 0, 0.6);
        text-transform: uppercase;
        letter-spacing: 0.5px;
      }
      .summary-value {
        font-size: 22px;
        font-weight: 500;
        margin-top: 4px;
      }
      .full-width {
        width: 100%;
      }
      th.num,
      td.num {
        text-align: right;
      }
      th.mat-mdc-header-cell.num,
      td.mat-mdc-cell.num {
        text-align: right;
      }
      .type-badge {
        padding: 2px 8px;
        border-radius: 12px;
        font-size: 11px;
        font-weight: 600;
      }
      .type-badge.rm {
        background: #e3f2fd;
        color: #1565c0;
      }
      .type-badge.fg {
        background: #e8f5e9;
        color: #2e7d32;
      }
      .empty-state {
        padding: 32px;
        text-align: center;
        color: rgba(0, 0, 0, 0.4);
      }
    `,
  ],
})
export class ValuationReportComponent implements OnInit {
  private readonly api = inject(InventoryApi);
  private readonly lookups = inject(LookupApi);

  readonly report = signal<ValuationReportDto | null>(null);
  readonly warehouses = signal<LookupOption[]>([]);
  readonly loading = signal(false);
  selectedWarehouseId: number | null = null;

  displayedColumns(): string[] {
    const base = ['itemCode', 'itemName', 'itemType', 'uom', 'qtyOnHand', 'avgUnitCost'];
    if (this.report()?.costMethod === 'STANDARD') base.push('standardCost');
    base.push('totalValue');
    return base;
  }

  ngOnInit(): void {
    this.lookups.warehouses().subscribe((list) => this.warehouses.set(list));
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getValuationReport(this.selectedWarehouseId ?? undefined).subscribe({
      next: (data) => {
        this.report.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  exportCsv(): void {
    const r = this.report();
    if (!r) return;
    const isStandard = r.costMethod === 'STANDARD';
    const headers = [
      'Code',
      'Name',
      'Type',
      'UOM',
      'Qty on Hand',
      'Avg Cost',
      ...(isStandard ? ['Std Cost'] : []),
      'Total Value',
    ];
    const rows = r.lines.map((l: ValuationLineDto) => [
      l.itemCode,
      `"${l.itemName}"`,
      l.itemType === 'MATERIAL' ? 'RM' : 'FG',
      l.uom,
      l.qtyOnHand,
      l.avgUnitCost,
      ...(isStandard ? [l.standardCost] : []),
      l.totalValue,
    ]);
    const csv = [headers, ...rows].map((row) => row.join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `inventory-valuation-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
}
