import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DecimalPipe } from '@angular/common';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { InventoryApi, InventoryBalanceDto } from './inventory.api';

@Component({
  selector: 'app-stock-balance-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    DecimalPipe,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Current Stock</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Stock Status</mat-label>
        <mat-select [formControl]="statusCtrl" data-testid="status-filter">
          <mat-option [value]="null">All</mat-option>
          <mat-option value="AVAILABLE">Available</mat-option>
          <mat-option value="QUARANTINE">Quarantine</mat-option>
          <mat-option value="REJECTED">Rejected</mat-option>
        </mat-select>
      </mat-form-field>
      <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon> Refresh</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="itemType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let r">{{ r.itemType }}</td>
        </ng-container>
        <ng-container matColumnDef="itemId">
          <th mat-header-cell *matHeaderCellDef>Item ID</th>
          <td mat-cell *matCellDef="let r">{{ r.materialId ?? r.productId }}</td>
        </ng-container>
        <ng-container matColumnDef="warehouse">
          <th mat-header-cell *matHeaderCellDef>WH / Location</th>
          <td mat-cell *matCellDef="let r">{{ r.warehouseId }} / {{ r.locationId }}</td>
        </ng-container>
        <ng-container matColumnDef="stockStatus">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r">
            <app-status-badge [status]="r.stockStatus" />
          </td>
        </ng-container>
        <ng-container matColumnDef="qtyOnHand">
          <th mat-header-cell *matHeaderCellDef>On Hand</th>
          <td mat-cell *matCellDef="let r">{{ r.qtyOnHand | number: '1.3-3' }}</td>
        </ng-container>
        <ng-container matColumnDef="available">
          <th mat-header-cell *matHeaderCellDef>Available</th>
          <td mat-cell *matCellDef="let r">{{ r.availableQty | number: '1.3-3' }}</td>
        </ng-container>
        <ng-container matColumnDef="avgCost">
          <th mat-header-cell *matHeaderCellDef>Avg Cost</th>
          <td mat-cell *matCellDef="let r">₹{{ r.avgUnitCost | number: '1.4-4' }}</td>
        </ng-container>
        <ng-container matColumnDef="value">
          <th mat-header-cell *matHeaderCellDef>Value</th>
          <td mat-cell *matCellDef="let r">₹{{ r.totalValue | number: '1.2-2' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let row; columns: cols"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell no-data" [attr.colspan]="cols.length">No stock records found.</td>
        </tr>
      </table>
    }
  `,
})
export class StockBalanceListComponent implements OnInit {
  private readonly api = inject(InventoryApi);

  readonly loading = signal(false);
  readonly rows = signal<InventoryBalanceDto[]>([]);
  readonly statusCtrl = new FormControl<string | null>(null);
  readonly cols = ['itemType', 'itemId', 'warehouse', 'stockStatus', 'qtyOnHand', 'available', 'avgCost', 'value'];

  ngOnInit(): void {
    this.statusCtrl.valueChanges.subscribe(() => this.load());
    this.load();
  }

  load(): void {
    this.loading.set(true);
    const status = this.statusCtrl.value ?? undefined;
    this.api.getBalances(undefined, undefined, status).subscribe({
      next: (data) => {
        this.rows.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
