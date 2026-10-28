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

export interface PaymentDto {
  id: number;
  docNo: string;
  docDate: string | null;
  status: string;
  direction: string;
  kind: string;
  partyType: string;
  partyId: number;
  mode: string;
  referenceNo: string | null;
  currency: string;
  amount: number;
  amountInr: number;
  tdsAmount: number | null;
  unallocatedInr: number | null;
  remarks: string | null;
}

@Component({
  selector: 'app-payment-list',
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
      <h2 i18n="@@nav.item-payments">Payments</h2>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <table mat-table [dataSource]="payments()" class="mat-elevation-z2 full-width">
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
        <ng-container matColumnDef="direction">
          <th mat-header-cell *matHeaderCellDef>Direction</th>
          <td mat-cell *matCellDef="let row">{{ row.direction }}</td>
        </ng-container>
        <ng-container matColumnDef="mode">
          <th mat-header-cell *matHeaderCellDef>Mode</th>
          <td mat-cell *matCellDef="let row">{{ row.mode }}</td>
        </ng-container>
        <ng-container matColumnDef="currency">
          <th mat-header-cell *matHeaderCellDef>Currency</th>
          <td mat-cell *matCellDef="let row">{{ row.currency }}</td>
        </ng-container>
        <ng-container matColumnDef="amount">
          <th mat-header-cell *matHeaderCellDef>Amount</th>
          <td mat-cell *matCellDef="let row">{{ row.amount | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="unallocatedInr">
          <th mat-header-cell *matHeaderCellDef>Unallocated (INR)</th>
          <td mat-cell *matCellDef="let row">{{ row.unallocatedInr | number: '1.2-2' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
})
export class PaymentListComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);

  columns = ['docNo', 'docDate', 'status', 'direction', 'mode', 'currency', 'amount', 'unallocatedInr'];
  payments = signal<PaymentDto[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.http.get<PaymentDto[]>('/api/finance/payments').subscribe({
      next: (data) => {
        this.payments.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load payments', 'Close', { duration: 3000 });
      },
    });
  }
}
