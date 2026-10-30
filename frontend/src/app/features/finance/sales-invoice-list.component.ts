import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
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
  invoiceType: string;
  lutArn: string | null;
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
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
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
    <div style="margin-bottom:16px">
      <mat-button-toggle-group [(ngModel)]="typeFilter" aria-label="Invoice type filter">
        <mat-button-toggle value="">All</mat-button-toggle>
        <mat-button-toggle value="STANDARD">Standard</mat-button-toggle>
        <mat-button-toggle value="PROFORMA">Proforma</mat-button-toggle>
        <mat-button-toggle value="AD_HOC">Ad Hoc</mat-button-toggle>
        <mat-button-toggle value="EXPORT">Export</mat-button-toggle>
        <mat-button-toggle value="DIRECT">Direct</mat-button-toggle>
      </mat-button-toggle-group>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <table mat-table [dataSource]="filteredInvoices()" class="mat-elevation-z2 full-width">
        <ng-container matColumnDef="docNo">
          <th mat-header-cell *matHeaderCellDef>Doc No</th>
          <td mat-cell *matCellDef="let row">{{ row.docNo }}</td>
        </ng-container>
        <ng-container matColumnDef="docDate">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let row">{{ row.docDate | istDate }}</td>
        </ng-container>
        <ng-container matColumnDef="invoiceType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let row">{{ row.invoiceType }}</td>
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
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let row">
            <a
              mat-icon-button
              [href]="'/api/finance/sales-invoices/' + row.id + '/pdf'"
              target="_blank"
              aria-label="Download PDF"
              ><mat-icon aria-hidden="true">picture_as_pdf</mat-icon></a
            >
          </td>
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

  columns = [
    'docNo',
    'docDate',
    'invoiceType',
    'status',
    'invoiceKind',
    'currency',
    'grandTotal',
    'balanceInr',
    'actions',
  ];
  invoices = signal<SalesInvoiceDto[]>([]);
  loading = signal(true);
  typeFilter = '';

  filteredInvoices = computed(() =>
    this.typeFilter ? this.invoices().filter((inv) => inv.invoiceType === this.typeFilter) : this.invoices(),
  );

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
