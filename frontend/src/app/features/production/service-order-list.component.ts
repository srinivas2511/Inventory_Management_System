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

export interface ServiceOrderDto {
  id: number;
  docNo: string;
  docDate: string | null;
  status: string;
  serviceKind: string;
  vendorId: number | null;
  customerId: number | null;
  description: string | null;
  scheduledDate: string | null;
  completedDate: string | null;
  serviceCharge: number | null;
  taxAmount: number | null;
  grandTotal: number | null;
  remarks: string | null;
}

@Component({
  selector: 'app-service-order-list',
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
      <h2 i18n="@@nav.service_orders">Service Orders</h2>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <table mat-table [dataSource]="orders()" class="mat-elevation-z2 full-width">
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
        <ng-container matColumnDef="serviceKind">
          <th mat-header-cell *matHeaderCellDef>Kind</th>
          <td mat-cell *matCellDef="let row">{{ row.serviceKind }}</td>
        </ng-container>
        <ng-container matColumnDef="description">
          <th mat-header-cell *matHeaderCellDef>Description</th>
          <td mat-cell *matCellDef="let row">{{ row.description }}</td>
        </ng-container>
        <ng-container matColumnDef="scheduledDate">
          <th mat-header-cell *matHeaderCellDef>Scheduled</th>
          <td mat-cell *matCellDef="let row">{{ row.scheduledDate | istDate }}</td>
        </ng-container>
        <ng-container matColumnDef="grandTotal">
          <th mat-header-cell *matHeaderCellDef>Total</th>
          <td mat-cell *matCellDef="let row">{{ row.grandTotal | number: '1.2-2' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
})
export class ServiceOrderListComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);

  columns = ['docNo', 'docDate', 'status', 'serviceKind', 'description', 'scheduledDate', 'grandTotal'];
  orders = signal<ServiceOrderDto[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.http.get<ServiceOrderDto[]>('/api/production/service-orders').subscribe({
      next: (data) => {
        this.orders.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load service orders', 'Close', { duration: 3000 });
      },
    });
  }
}
