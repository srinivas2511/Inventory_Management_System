import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';

export interface OcDto {
  id: number;
  docNo: string;
  docDate: string | null;
  status: string;
  ocKind: string;
  customerId: number;
  customerPoNo: string | null;
  deliveryDate: string | null;
  currency: string;
  grandTotal: number | null;
  items: OcItemDto[];
}

export interface OcItemDto {
  id: number;
  lineNo: number;
  productId: number | null;
  description: string | null;
  qty: number;
  uom: string | null;
  rate: number | null;
  lineTotal: number | null;
  qtyDispatched: number | null;
}

@Component({
  selector: 'app-order-confirmation-list',
  standalone: true,
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatSnackBarModule,
    IstDatePipe,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.order_confirmations">Order Confirmations</h2>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <table mat-table [dataSource]="ocs()" class="mat-elevation-z2 full-width">
        <ng-container matColumnDef="docNo">
          <th mat-header-cell *matHeaderCellDef>Doc No</th>
          <td mat-cell *matCellDef="let row">{{ row.docNo }}</td>
        </ng-container>
        <ng-container matColumnDef="docDate">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let row">{{ row.docDate | istDate }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let row"><app-status-badge [status]="row.status" /></td>
        </ng-container>
        <ng-container matColumnDef="customerPoNo">
          <th mat-header-cell *matHeaderCellDef>Customer PO</th>
          <td mat-cell *matCellDef="let row">{{ row.customerPoNo }}</td>
        </ng-container>
        <ng-container matColumnDef="deliveryDate">
          <th mat-header-cell *matHeaderCellDef>Delivery Date</th>
          <td mat-cell *matCellDef="let row">{{ row.deliveryDate | istDate }}</td>
        </ng-container>
        <ng-container matColumnDef="grandTotal">
          <th mat-header-cell *matHeaderCellDef>Total</th>
          <td mat-cell *matCellDef="let row">{{ row.grandTotal | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="items">
          <th mat-header-cell *matHeaderCellDef>Lines</th>
          <td mat-cell *matCellDef="let row">{{ row.items?.length ?? 0 }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
})
export class OrderConfirmationListComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);

  columns = ['docNo', 'docDate', 'status', 'customerPoNo', 'deliveryDate', 'grandTotal', 'items'];
  ocs = signal<OcDto[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.http.get<OcDto[]>('/api/sales/order-confirmations').subscribe({
      next: (data) => {
        this.ocs.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load order confirmations', 'Close', { duration: 3000 });
      },
    });
  }
}
