import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
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
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
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
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef>Actions</th>
          <td mat-cell *matCellDef="let row">
            @if (row.status === 'CONFIRMED' || row.status === 'IN_PROGRESS' || row.status === 'DRAFT') {
              <button mat-icon-button aria-label="Confirm service" title="Confirm Service" (click)="openConfirmDialog(row)">
                <mat-icon aria-hidden="true">check_circle</mat-icon>
              </button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }

    <!-- Confirm Service dialog -->
    @if (confirmDialogOpen()) {
      <div class="dialog-backdrop" (click)="closeConfirmDialog()">
        <div class="dialog-panel" (click)="$event.stopPropagation()" role="dialog" aria-modal="true" aria-label="Confirm Service">
          <h3>Confirm Service — {{ activeOrder()?.docNo }}</h3>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Remarks (optional)</mat-label>
            <input matInput [(ngModel)]="confirmRemarks" />
          </mat-form-field>
          <div class="dialog-actions">
            <button mat-button (click)="closeConfirmDialog()">Cancel</button>
            <button mat-flat-button color="primary" (click)="submitConfirm()">Confirm</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .dialog-backdrop { position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,.4);z-index:1000;display:flex;align-items:center;justify-content:center }
    .dialog-panel { background:#fff;border-radius:8px;padding:24px;min-width:320px;max-width:440px;width:100% }
    .dialog-actions { display:flex;justify-content:flex-end;gap:8px;margin-top:16px }
  `],
})
export class ServiceOrderListComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);

  columns = ['docNo', 'docDate', 'status', 'serviceKind', 'description', 'scheduledDate', 'grandTotal', 'actions'];
  orders = signal<ServiceOrderDto[]>([]);
  loading = signal(true);
  activeOrder = signal<ServiceOrderDto | null>(null);
  confirmDialogOpen = signal(false);
  confirmRemarks = '';

  ngOnInit(): void {
    this.loadOrders();
  }

  loadOrders(): void {
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

  openConfirmDialog(order: ServiceOrderDto): void {
    this.activeOrder.set(order);
    this.confirmRemarks = '';
    this.confirmDialogOpen.set(true);
  }

  closeConfirmDialog(): void { this.confirmDialogOpen.set(false); }

  submitConfirm(): void {
    const order = this.activeOrder();
    if (!order) return;
    const body = { remarks: this.confirmRemarks };
    this.http.post(`/api/production/service-orders/${order.id}/confirm`, body).subscribe({
      next: () => {
        this.snackBar.open('Service confirmed', 'Close', { duration: 3000 });
        this.closeConfirmDialog();
        this.loadOrders();
      },
      error: () => this.snackBar.open('Failed to confirm service', 'Close', { duration: 3000 }),
    });
  }
}
