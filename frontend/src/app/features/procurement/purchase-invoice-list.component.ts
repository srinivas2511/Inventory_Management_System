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
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

export interface PurchaseInvoiceDto {
  id: number;
  docNo: string;
  status: string;
  supplierId: number;
  supplierInvoiceNo: string;
  supplierInvoiceDate: string;
  total: number;
  paidAmount: number;
  matchStatus: string;
  dueDate: string | null;
}

@Component({
  selector: 'app-purchase-invoice-list',
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
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.purchase.invoices">Purchase Invoices</h2>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <table mat-table [dataSource]="invoices()" class="mat-elevation-z2 full-width">
        <ng-container matColumnDef="docNo">
          <th mat-header-cell *matHeaderCellDef>Doc No</th>
          <td mat-cell *matCellDef="let row">{{ row.docNo }}</td>
        </ng-container>
        <ng-container matColumnDef="supplierInvoiceNo">
          <th mat-header-cell *matHeaderCellDef>Supplier Invoice</th>
          <td mat-cell *matCellDef="let row">{{ row.supplierInvoiceNo }}</td>
        </ng-container>
        <ng-container matColumnDef="supplierInvoiceDate">
          <th mat-header-cell *matHeaderCellDef>Invoice Date</th>
          <td mat-cell *matCellDef="let row">{{ row.supplierInvoiceDate | istDate }}</td>
        </ng-container>
        <ng-container matColumnDef="total">
          <th mat-header-cell *matHeaderCellDef>Total</th>
          <td mat-cell *matCellDef="let row">{{ row.total | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let row"><app-status-badge [status]="row.status" /></td>
        </ng-container>
        <ng-container matColumnDef="matchStatus">
          <th mat-header-cell *matHeaderCellDef>Match</th>
          <td mat-cell *matCellDef="let row"><app-status-badge [status]="row.matchStatus" /></td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
})
export class PurchaseInvoiceListComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);

  columns = ['docNo', 'supplierInvoiceNo', 'supplierInvoiceDate', 'total', 'status', 'matchStatus'];
  invoices = signal<PurchaseInvoiceDto[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.http.get<PurchaseInvoiceDto[]>('/api/purchase-invoices').subscribe({
      next: (data) => {
        this.invoices.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load invoices', 'Close', { duration: 3000 });
      },
    });
  }
}
