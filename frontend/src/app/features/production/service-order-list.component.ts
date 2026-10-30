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

export interface ServiceConfirmationDto {
  id: number;
  soId: number;
  confirmedAt: string;
  confirmedBy: number;
  remarks: string | null;
}

interface InwardItemLine {
  materialId: number | null;
  receivedQty: number | null;
  unitPrice: number | null;
}

interface InvoiceLineItem {
  description: string;
  qty: number | null;
  unitPrice: number | null;
  taxRate: number | null;
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
            @if (activeConfirmation() && activeConfirmation()!.soId === row.id) {
              <button mat-icon-button aria-label="Record inward" title="Record Inward GRN" (click)="openInwardDialog(activeConfirmation()!)">
                <mat-icon aria-hidden="true">input</mat-icon>
              </button>
              <button mat-icon-button aria-label="Create invoice" title="Create Invoice" (click)="openInvoiceDialog(activeConfirmation()!)">
                <mat-icon aria-hidden="true">receipt</mat-icon>
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
      <div class="dialog-backdrop" (click)="closeConfirmDialog()" (keydown.escape)="closeConfirmDialog()" tabindex="-1">
        <div class="dialog-panel" (click)="$event.stopPropagation()" (keydown)="$event.stopPropagation()" role="dialog" aria-modal="true" aria-label="Confirm Service" tabindex="-1">
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

    <!-- Record Inward dialog -->
    @if (inwardDialogOpen()) {
      <div class="dialog-backdrop" (click)="closeInwardDialog()" (keydown.escape)="closeInwardDialog()" tabindex="-1">
        <div class="dialog-panel dialog-panel--wide" (click)="$event.stopPropagation()" (keydown)="$event.stopPropagation()" role="dialog" aria-modal="true" aria-label="Record Inward GRN" tabindex="-1">
          <h3>Record Inward — Confirmation #{{ activeConfirmation()?.id }}</h3>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Warehouse ID</mat-label>
            <input matInput type="number" [(ngModel)]="inwardWarehouseId" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Location ID</mat-label>
            <input matInput type="number" [(ngModel)]="inwardLocationId" />
          </mat-form-field>
          <h4>Items</h4>
          @for (item of inwardItems; track $index) {
            <div class="item-row">
              <mat-form-field appearance="outline">
                <mat-label>Material ID</mat-label>
                <input matInput type="number" [(ngModel)]="item.materialId" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Qty</mat-label>
                <input matInput type="number" [(ngModel)]="item.receivedQty" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Unit Price</mat-label>
                <input matInput type="number" [(ngModel)]="item.unitPrice" />
              </mat-form-field>
            </div>
          }
          <button mat-button (click)="addInwardItem()">+ Add Item</button>
          <div class="dialog-actions">
            <button mat-button (click)="closeInwardDialog()">Cancel</button>
            <button mat-flat-button color="primary" (click)="submitInward()">Record Inward</button>
          </div>
        </div>
      </div>
    }

    <!-- Create Invoice dialog -->
    @if (invoiceDialogOpen()) {
      <div class="dialog-backdrop" (click)="closeInvoiceDialog()" (keydown.escape)="closeInvoiceDialog()" tabindex="-1">
        <div class="dialog-panel dialog-panel--wide" (click)="$event.stopPropagation()" (keydown)="$event.stopPropagation()" role="dialog" aria-modal="true" aria-label="Create Invoice" tabindex="-1">
          <h3>Create Invoice — Confirmation #{{ activeConfirmation()?.id }}</h3>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Customer ID</mat-label>
            <input matInput type="number" [(ngModel)]="invoiceCustomerId" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Invoice Date</mat-label>
            <input matInput type="date" [(ngModel)]="invoiceDate" />
          </mat-form-field>
          <h4>Lines</h4>
          @for (line of invoiceLines; track $index) {
            <div class="item-row">
              <mat-form-field appearance="outline">
                <mat-label>Description</mat-label>
                <input matInput [(ngModel)]="line.description" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Qty</mat-label>
                <input matInput type="number" [(ngModel)]="line.qty" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Unit Price</mat-label>
                <input matInput type="number" [(ngModel)]="line.unitPrice" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Tax Rate %</mat-label>
                <input matInput type="number" [(ngModel)]="line.taxRate" />
              </mat-form-field>
            </div>
          }
          <button mat-button (click)="addInvoiceLine()">+ Add Line</button>
          <div class="dialog-actions">
            <button mat-button (click)="closeInvoiceDialog()">Cancel</button>
            <button mat-flat-button color="primary" (click)="submitInvoice()">Create Invoice</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .dialog-backdrop { position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,.4);z-index:1000;display:flex;align-items:center;justify-content:center }
    .dialog-panel { background:#fff;border-radius:8px;padding:24px;min-width:320px;max-width:440px;width:100%;overflow-y:auto;max-height:90vh }
    .dialog-panel--wide { max-width:600px }
    .dialog-actions { display:flex;justify-content:flex-end;gap:8px;margin-top:16px }
    .item-row { display:flex;gap:8px;flex-wrap:wrap }
  `],
})
export class ServiceOrderListComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);

  columns = ['docNo', 'docDate', 'status', 'serviceKind', 'description', 'scheduledDate', 'grandTotal', 'actions'];
  orders = signal<ServiceOrderDto[]>([]);
  loading = signal(true);
  activeOrder = signal<ServiceOrderDto | null>(null);
  activeConfirmation = signal<ServiceConfirmationDto | null>(null);
  confirmDialogOpen = signal(false);
  confirmRemarks = '';

  // Inward dialog state
  inwardDialogOpen = signal(false);
  inwardWarehouseId: number | null = null;
  inwardLocationId: number | null = null;
  inwardItems: InwardItemLine[] = [];

  // Invoice dialog state
  invoiceDialogOpen = signal(false);
  invoiceCustomerId: number | null = null;
  invoiceDate = '';
  invoiceLines: InvoiceLineItem[] = [];

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
    this.http.post<ServiceConfirmationDto>(`/api/production/service-orders/${order.id}/confirm`, body).subscribe({
      next: (conf) => {
        this.snackBar.open('Service confirmed', 'Close', { duration: 3000 });
        this.activeConfirmation.set(conf);
        this.closeConfirmDialog();
        this.loadOrders();
      },
      error: () => this.snackBar.open('Failed to confirm service', 'Close', { duration: 3000 }),
    });
  }

  openInwardDialog(conf: ServiceConfirmationDto): void {
    this.inwardItems = [{ materialId: null, receivedQty: null, unitPrice: null }];
    this.inwardWarehouseId = null;
    this.inwardLocationId = null;
    this.inwardDialogOpen.set(true);
  }

  closeInwardDialog(): void { this.inwardDialogOpen.set(false); }

  addInwardItem(): void {
    this.inwardItems = [...this.inwardItems, { materialId: null, receivedQty: null, unitPrice: null }];
  }

  submitInward(): void {
    const conf = this.activeConfirmation();
    if (!conf) return;
    const body = {
      warehouseId: this.inwardWarehouseId,
      locationId: this.inwardLocationId,
      receivedDate: new Date().toISOString().slice(0, 10),
      items: this.inwardItems,
    };
    this.http.post(`/api/production/service-orders/confirmations/${conf.id}/inward`, body).subscribe({
      next: () => {
        this.snackBar.open('Inward GRN created', 'Close', { duration: 3000 });
        this.closeInwardDialog();
      },
      error: () => this.snackBar.open('Failed to create inward GRN', 'Close', { duration: 3000 }),
    });
  }

  openInvoiceDialog(conf: ServiceConfirmationDto): void {
    this.invoiceLines = [{ description: '', qty: null, unitPrice: null, taxRate: null }];
    this.invoiceCustomerId = null;
    this.invoiceDate = new Date().toISOString().slice(0, 10);
    this.invoiceDialogOpen.set(true);
  }

  closeInvoiceDialog(): void { this.invoiceDialogOpen.set(false); }

  addInvoiceLine(): void {
    this.invoiceLines = [...this.invoiceLines, { description: '', qty: null, unitPrice: null, taxRate: null }];
  }

  submitInvoice(): void {
    const conf = this.activeConfirmation();
    if (!conf) return;
    const body = {
      invoiceDate: this.invoiceDate,
      customerId: this.invoiceCustomerId,
      lines: this.invoiceLines,
    };
    this.http.post(`/api/production/service-orders/confirmations/${conf.id}/invoice`, body).subscribe({
      next: () => {
        this.snackBar.open('Invoice created', 'Close', { duration: 3000 });
        this.closeInvoiceDialog();
      },
      error: () => this.snackBar.open('Failed to create invoice', 'Close', { duration: 3000 }),
    });
  }
}
