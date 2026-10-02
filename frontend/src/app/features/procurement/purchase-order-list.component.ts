import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe, DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

export interface PurchaseOrderItemDto {
  id: number;
  lineNo: number;
  materialId: number;
  orderedQty: number;
  receivedQty: number;
  rate: number;
  taxPct: number;
  lineTotal: number;
}

export interface PurchaseOrderDto {
  id: number;
  poNumber: string;
  supplierId: number;
  poDate: string;
  expectedDeliveryDate: string;
  status: string;
  grandTotal: number;
  currency: string;
  items: PurchaseOrderItemDto[];
  allowedActions: string[];
}

@Component({
  selector: 'app-purchase-order-list',
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
    MatSnackBarModule,
    DecimalPipe,
    DatePipe,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Purchase Orders</h2>
      <button mat-raised-button color="primary" *appHasPermission="'PURCHASE_CREATE'">
        <mat-icon>add</mat-icon> New PO
      </button>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl">
          <mat-option [value]="null">All</mat-option>
          <mat-option value="DRAFT">Draft</mat-option>
          <mat-option value="SUBMITTED">Submitted</mat-option>
          <mat-option value="APPROVED">Approved</mat-option>
          <mat-option value="SENT_TO_SUPPLIER">Sent to Supplier</mat-option>
          <mat-option value="PARTIALLY_RECEIVED">Partially Received</mat-option>
          <mat-option value="RECEIVED">Received</mat-option>
          <mat-option value="CANCELLED">Cancelled</mat-option>
        </mat-select>
      </mat-form-field>
      <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon> Refresh</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="poNumber">
          <th mat-header-cell *matHeaderCellDef>PO Number</th>
          <td mat-cell *matCellDef="let r">{{ r.poNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="supplierId">
          <th mat-header-cell *matHeaderCellDef>Supplier</th>
          <td mat-cell *matCellDef="let r">{{ r.supplierId }}</td>
        </ng-container>
        <ng-container matColumnDef="poDate">
          <th mat-header-cell *matHeaderCellDef>PO Date</th>
          <td mat-cell *matCellDef="let r">{{ r.poDate | date: 'dd/MM/yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="grandTotal">
          <th mat-header-cell *matHeaderCellDef>Total</th>
          <td mat-cell *matCellDef="let r">{{ r.grandTotal | number: '1.2-2' }} {{ r.currency }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            @if (r.allowedActions?.includes('submit')) {
              <button
                mat-icon-button
                matTooltip="Submit"
                *appHasPermission="'PURCHASE_CREATE'"
                (click)="doAction(r.id, 'submit')"
              >
                <mat-icon>send</mat-icon>
              </button>
            }
            @if (r.allowedActions?.includes('approve')) {
              <button
                mat-icon-button
                matTooltip="Approve"
                *appHasPermission="'PURCHASE_APPROVE'"
                (click)="doAction(r.id, 'approve')"
              >
                <mat-icon>check_circle</mat-icon>
              </button>
            }
            @if (r.allowedActions?.includes('send_to_supplier')) {
              <button
                mat-icon-button
                matTooltip="Send to Supplier"
                *appHasPermission="'PURCHASE_APPROVE'"
                (click)="doAction(r.id, 'send-to-supplier')"
              >
                <mat-icon>local_shipping</mat-icon>
              </button>
            }
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let r; columns: columns"></tr>
      </table>
    }
  `,
})
export class PurchaseOrderListComponent implements OnInit {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);

  columns = ['poNumber', 'supplierId', 'poDate', 'grandTotal', 'status', 'actions'];
  rows = signal<PurchaseOrderDto[]>([]);
  loading = signal(false);
  statusCtrl = new FormControl<string | null>(null);

  ngOnInit(): void {
    this.statusCtrl.valueChanges.subscribe(() => this.load());
    this.load();
  }

  load(): void {
    this.loading.set(true);
    const params: Record<string, string> = {};
    const s = this.statusCtrl.value;
    if (s) params['status'] = s;
    this.http.get<{ content: PurchaseOrderDto[] }>('/api/procurement/purchase-orders', { params }).subscribe({
      next: (page) => {
        this.rows.set(page.content ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  doAction(id: number, action: string): void {
    this.http.post<PurchaseOrderDto>(`/api/procurement/purchase-orders/${id}/${action}`, {}).subscribe({
      next: () => {
        this.snack.open('Action completed', 'OK', { duration: 2000 });
        this.load();
      },
      error: (e) => this.snack.open(e.error?.detail ?? 'Error', 'OK', { duration: 4000 }),
    });
  }
}
