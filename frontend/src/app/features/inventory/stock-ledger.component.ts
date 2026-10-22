import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DecimalPipe } from '@angular/common';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { InventoryApi, InventoryTransactionDto } from './inventory.api';
import { Page } from '../master/material/material.api';

@Component({
  selector: 'app-stock-ledger',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    IstDatePipe,
    DecimalPipe,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Stock Ledger</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Transaction Type</mat-label>
        <mat-select [formControl]="typeCtrl">
          <mat-option [value]="null">All</mat-option>
          <mat-option value="GOODS_RECEIPT">Goods Receipt</mat-option>
          <mat-option value="MATERIAL_ISSUE">Material Issue</mat-option>
          <mat-option value="STOCK_ADJUSTMENT">Adjustment</mat-option>
          <mat-option value="STOCK_TRANSFER">Transfer</mat-option>
          <mat-option value="OPENING_BALANCE">Opening Balance</mat-option>
        </mat-select>
      </mat-form-field>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="page()?.content ?? []" class="mat-elevation-z1">
        <ng-container matColumnDef="txNumber">
          <th mat-header-cell *matHeaderCellDef>Txn #</th>
          <td mat-cell *matCellDef="let r">{{ r.transactionNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="date">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let r">{{ r.transactionDate | istDate: 'dd/MM/yy HH:mm' }}</td>
        </ng-container>
        <ng-container matColumnDef="type">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let r">
            <app-status-badge [status]="r.transactionType" />
          </td>
        </ng-container>
        <ng-container matColumnDef="item">
          <th mat-header-cell *matHeaderCellDef>Item</th>
          <td mat-cell *matCellDef="let r">{{ r.materialId ?? r.productId }}</td>
        </ng-container>
        <ng-container matColumnDef="qty">
          <th mat-header-cell *matHeaderCellDef>Qty</th>
          <td mat-cell *matCellDef="let r" [class.text-negative]="r.quantity < 0">
            {{ r.quantity | number: '1.3-3' }}
          </td>
        </ng-container>
        <ng-container matColumnDef="balanceAfter">
          <th mat-header-cell *matHeaderCellDef>Balance After</th>
          <td mat-cell *matCellDef="let r">{{ r.balanceAfter | number: '1.3-3' }}</td>
        </ng-container>
        <ng-container matColumnDef="reference">
          <th mat-header-cell *matHeaderCellDef>Reference</th>
          <td mat-cell *matCellDef="let r">{{ r.referenceNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="reversal">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            @if (r.reversalOfId) {
              <mat-icon matTooltip="This is a reversal" color="warn">undo</mat-icon>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let row; columns: cols"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell no-data" [attr.colspan]="cols.length">No transactions found.</td>
        </tr>
      </table>

      <mat-paginator
        [length]="page()?.totalElements ?? 0"
        [pageSize]="pageSize"
        [pageSizeOptions]="[20, 50, 100]"
        (page)="onPage($event)"
      />
    }
  `,
  styles: ['.text-negative { color: var(--mat-warn-color, red); }'],
})
export class StockLedgerComponent implements OnInit {
  private readonly api = inject(InventoryApi);

  readonly loading = signal(false);
  readonly page = signal<Page<InventoryTransactionDto> | null>(null);
  readonly typeCtrl = new FormControl<string | null>(null);
  readonly cols = ['txNumber', 'date', 'type', 'item', 'qty', 'balanceAfter', 'reference', 'reversal'];
  pageIndex = 0;
  pageSize = 50;

  ngOnInit(): void {
    this.typeCtrl.valueChanges.subscribe(() => {
      this.pageIndex = 0;
      this.load();
    });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api
      .getLedger(undefined, undefined, this.typeCtrl.value ?? undefined, this.pageIndex, this.pageSize)
      .subscribe({
        next: (data) => {
          this.page.set(data);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  onPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.load();
  }
}
