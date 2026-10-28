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

export interface SalesInvoiceDto {
  id: number;
  docNo: string;
  docDate: string | null;
  status: string;
  invoiceKind: string;
  customerId: number;
  currency: string;
  grandTotal: number | null;
  balanceInr: number | null;
  remarks: string | null;
  items: SalesInvoiceItemDto[];
}

export interface SalesInvoiceItemDto {
  id: number;
  lineNo: number;
  productId: number | null;
  description: string | null;
  qty: number;
  uom: string | null;
  rate: number | null;
  taxableValue: number | null;
  lineTotal: number | null;
}

@Component({
  selector: 'app-sales-invoice-list',
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
      <h2 i18n="@@nav.item-sales-invoices">Sales Invoices</h2>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <table mat-table [dataSource]="invoices()" class="mat-elevation-z2 full-width">
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
        <ng-container matColumnDef="invoiceKind">
          <th mat-header-cell *matHeaderCellDef>Kind</th>
          <td mat-cell *matCellDef="let row">{{ row.invoiceKind }}</td>
        </ng-container>
        <ng-container matColumnDef="currency">
          <th mat-header-cell *matHeaderCellDef>Currency</th>
          <td mat-cell *matCellDef="let row">{{ row.currency }}</td>
        </ng-container>
        <ng-container matColumnDef="grandTotal">
          <th mat-header-cell *matHeaderCellDef>Total</th>
          <td mat-cell *matCellDef="let row">{{ row.grandTotal | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="balanceInr">
          <th mat-header-cell *matHeaderCellDef>Balance (INR)</th>
          <td mat-cell *matCellDef="let row">{{ row.balanceInr | number: '1.2-2' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
})
export class SalesInvoiceListComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);

  columns = ['docNo', 'docDate', 'status', 'invoiceKind', 'currency', 'grandTotal', 'balanceInr'];
  invoices = signal<SalesInvoiceDto[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.http.get<SalesInvoiceDto[]>('/api/finance/sales-invoices').subscribe({
      next: (data) => {
        this.invoices.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load sales invoices', 'Close', { duration: 3000 });
      },
    });
  }
}
