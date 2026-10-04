import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { DecimalPipe, DatePipe, CurrencyPipe } from '@angular/common';
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
import { SalesApi, SalesOrderDto } from './sales.api';

@Component({
  selector: 'app-sales-order-list',
  standalone: true,
  providers: [SalesApi],
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
    CurrencyPipe,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Sales Orders</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl">
          <mat-option [value]="null">All</mat-option>
          <mat-option value="DRAFT">Draft</mat-option>
          <mat-option value="ORDERED">Ordered</mat-option>
          <mat-option value="APPROVED">Approved</mat-option>
          <mat-option value="ALLOCATED">Allocated</mat-option>
          <mat-option value="PARTIALLY_DISPATCHED">Partially Dispatched</mat-option>
          <mat-option value="DISPATCHED">Dispatched</mat-option>
          <mat-option value="CANCELLED">Cancelled</mat-option>
        </mat-select>
      </mat-form-field>
    </div>

    @if (loading()) {
      <div class="spinner-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="orders()" class="mat-elevation-z2 w-full">
        <ng-container matColumnDef="orderNumber">
          <th mat-header-cell *matHeaderCellDef>Order #</th>
          <td mat-cell *matCellDef="let o">{{ o.orderNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="customer">
          <th mat-header-cell *matHeaderCellDef>Customer</th>
          <td mat-cell *matCellDef="let o">{{ o.customerName }}</td>
        </ng-container>
        <ng-container matColumnDef="orderDate">
          <th mat-header-cell *matHeaderCellDef>Order Date</th>
          <td mat-cell *matCellDef="let o">{{ o.orderDate | date: 'dd MMM yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="requiredDate">
          <th mat-header-cell *matHeaderCellDef>Required By</th>
          <td mat-cell *matCellDef="let o">{{ o.requiredDate ? (o.requiredDate | date: 'dd MMM yyyy') : '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="totalValue">
          <th mat-header-cell *matHeaderCellDef>Value</th>
          <td mat-cell *matCellDef="let o">{{ o.totalValue | currency: 'INR' : 'symbol-narrow' : '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let o">
            <app-status-badge [status]="o.status" />
          </td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let o">
            @if (o.allowedActions.includes('confirm')) {
              <button mat-icon-button matTooltip="Confirm" *appHasPermission="'SALES_CREATE'" (click)="confirm(o)">
                <mat-icon>check_circle</mat-icon>
              </button>
            }
            @if (o.allowedActions.includes('approve')) {
              <button mat-icon-button matTooltip="Approve" *appHasPermission="'SALES_APPROVE'" (click)="approve(o)">
                <mat-icon>thumb_up</mat-icon>
              </button>
            }
            @if (o.allowedActions.includes('cancel')) {
              <button
                mat-icon-button
                matTooltip="Cancel"
                color="warn"
                *appHasPermission="'SALES_UPDATE'"
                (click)="cancel(o)"
              >
                <mat-icon>cancel</mat-icon>
              </button>
            }
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No sales orders found
          </td>
        </tr>
      </table>
    }
  `,
})
export class SalesOrderListComponent implements OnInit {
  private api = inject(SalesApi);
  private snack = inject(MatSnackBar);

  statusCtrl = new FormControl<string | null>(null);
  orders = signal<SalesOrderDto[]>([]);
  loading = signal(false);
  columns = ['orderNumber', 'customer', 'orderDate', 'requiredDate', 'totalValue', 'status', 'actions'];

  ngOnInit(): void {
    this.load();
    this.statusCtrl.valueChanges.subscribe(() => this.load());
  }

  load(): void {
    this.loading.set(true);
    this.api.getSalesOrders(this.statusCtrl.value ?? undefined).subscribe({
      next: (page) => {
        this.orders.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load orders', 'Dismiss', { duration: 3000 });
      },
    });
  }

  confirm(order: SalesOrderDto): void {
    this.api.confirmOrder(order.id).subscribe({
      next: (updated) => {
        this.replaceOrder(updated);
        this.snack.open('Order confirmed', '', { duration: 2000 });
      },
      error: () => this.snack.open('Failed to confirm order', 'Dismiss', { duration: 3000 }),
    });
  }

  approve(order: SalesOrderDto): void {
    this.api.approveOrder(order.id).subscribe({
      next: (updated) => {
        this.replaceOrder(updated);
        this.snack.open('Order approved', '', { duration: 2000 });
      },
      error: () => this.snack.open('Failed to approve order', 'Dismiss', { duration: 3000 }),
    });
  }

  cancel(order: SalesOrderDto): void {
    const reason = prompt('Cancellation reason:');
    if (!reason) return;
    this.api.cancelOrder(order.id, reason).subscribe({
      next: (updated) => {
        this.replaceOrder(updated);
        this.snack.open('Order cancelled', '', { duration: 2000 });
      },
      error: () => this.snack.open('Failed to cancel order', 'Dismiss', { duration: 3000 }),
    });
  }

  private replaceOrder(updated: SalesOrderDto): void {
    this.orders.update((list) => list.map((o) => (o.id === updated.id ? updated : o)));
  }
}
