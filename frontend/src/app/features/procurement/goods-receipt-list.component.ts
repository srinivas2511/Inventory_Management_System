import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { GoodsReceiptFormComponent } from './goods-receipt-form.component';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { PagedList } from '../../shared/paged-list';
import { ListPaginatorComponent } from '../../shared/components/list-paginator.component';

export interface GoodsReceiptDto {
  id: number;
  grNumber: string;
  purchaseOrderId: number;
  poNumber: string;
  receiptDate: string;
  status: string;
  receivedBy: number;
}

@Component({
  selector: 'app-goods-receipt-list',
  standalone: true,
  imports: [
    ListPaginatorComponent,
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSnackBarModule,
    IstDatePipe,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Goods Receipts</h2>
      <button mat-raised-button color="primary" *appHasPermission="'INVENTORY_RECEIVE'" (click)="receiveGoods()">
        <mat-icon>add</mat-icon> Receive Goods
      </button>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>PO Number</mat-label>
        <input matInput [formControl]="poCtrl" placeholder="Filter by PO Number" />
      </mat-form-field>
      <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon> Refresh</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="grNumber">
          <th mat-header-cell *matHeaderCellDef>GR Number</th>
          <td mat-cell *matCellDef="let r">{{ r.grNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="poNumber">
          <th mat-header-cell *matHeaderCellDef>PO Number</th>
          <td mat-cell *matCellDef="let r">{{ r.poNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="receiptDate">
          <th mat-header-cell *matHeaderCellDef>Receipt Date</th>
          <td mat-cell *matCellDef="let r">{{ r.receiptDate | istDate: 'dd/MM/yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let r; columns: columns"></tr>
      </table>
      <app-list-paginator [paged]="paged" (changed)="load()" />
    }
  `,
})
export class GoodsReceiptListComponent implements OnInit {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);
  private dialog = inject(MatDialog);

  columns = ['grNumber', 'poNumber', 'receiptDate', 'status'];
  rows = signal<GoodsReceiptDto[]>([]);
  loading = signal(false);
  readonly paged = new PagedList();
  poCtrl = new FormControl<string>('');

  ngOnInit(): void {
    this.load();
  }

  receiveGoods(): void {
    this.dialog
      .open(GoodsReceiptFormComponent, { width: '960px', maxWidth: '95vw' })
      .afterClosed()
      .subscribe((created) => {
        if (created) {
          this.snack.open('Goods receipt posted', 'OK', { duration: 3000 });
          this.load();
        }
      });
  }

  load(): void {
    this.loading.set(true);
    const po = (this.poCtrl.value ?? '').trim();
    this.http
      .get<{ content: GoodsReceiptDto[] }>('/api/procurement/goods-receipts', {
        params: this.paged.params(po ? { po } : {}),
      })
      .subscribe({
        next: (page) => {
          this.rows.set(this.paged.apply(page));
          this.loading.set(false);
        },
        error: () => {
          this.rows.set([]);
          this.loading.set(false);
        },
      });
  }
}
