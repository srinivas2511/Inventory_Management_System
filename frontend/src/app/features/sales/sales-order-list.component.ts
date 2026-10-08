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
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ReservationFormComponent } from './reservation-form.component';
import { SalesOrderFormComponent } from './sales-order-form.component';
import { SalesApi, SalesOrderDto } from './sales.api';
import { PagedList } from '../../shared/paged-list';
import { ListPaginatorComponent } from '../../shared/components/list-paginator.component';
import { SessionStore } from '../../core/auth/session.store';

@Component({
  selector: 'app-sales-order-list',
  standalone: true,
  providers: [SalesApi],
  imports: [
    ListPaginatorComponent,
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
      <button mat-raised-button color="primary" *appHasPermission="'SALES_CREATE'" (click)="createOrder()">
        <mat-icon>add</mat-icon> New order
      </button>
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
            @if (o.status === 'ORDERED' || o.status === 'APPROVED') {
              <button
                mat-icon-button
                matTooltip="Reserve stock"
                aria-label="Reserve stock"
                *appHasPermission="'SALES_CREATE,RESERVE_FG'"
                (click)="reserve(o)"
              >
                <mat-icon>inventory</mat-icon>
              </button>
            }
            @if (o.allowedActions.includes('confirm')) {
              <button
                mat-icon-button
                matTooltip="Confirm"
                aria-label="Confirm"
                *appHasPermission="'SALES_CREATE'"
                (click)="confirm(o)"
              >
                <mat-icon>check_circle</mat-icon>
              </button>
            }
            @if (o.allowedActions.includes('approve')) {
              <span
                *appHasPermission="'SALES_APPROVE'"
                [matTooltip]="
                  ownWork(o) ? 'You created or submitted this order, so another approver must approve it' : 'Approve'
                "
              >
                <button mat-icon-button aria-label="Approve" [disabled]="ownWork(o)" (click)="approve(o)">
                  <mat-icon>thumb_up</mat-icon>
                </button>
              </span>
            }
            @if (o.allowedActions.includes('cancel')) {
              <button
                mat-icon-button
                matTooltip="Cancel"
                aria-label="Cancel"
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
      <app-list-paginator [paged]="paged" (changed)="load()" />
    }
  `,
})
export class SalesOrderListComponent implements OnInit {
  private api = inject(SalesApi);
  private session = inject(SessionStore);
  private snack = inject(MatSnackBar);
  private dialog = inject(MatDialog);

  statusCtrl = new FormControl<string | null>(null);
  orders = signal<SalesOrderDto[]>([]);
  loading = signal(false);
  readonly paged = new PagedList();
  columns = ['orderNumber', 'customer', 'orderDate', 'requiredDate', 'totalValue', 'status', 'actions'];

  ngOnInit(): void {
    this.load();
    this.statusCtrl.valueChanges.subscribe(() => {
      this.paged.reset();
      this.load();
    });
  }

  load(): void {
    this.loading.set(true);
    this.api
      .getSalesOrders(
        this.statusCtrl.value ?? undefined,
        undefined,
        this.paged.pageIndex(),
        this.paged.pageSize(),
        'id,desc',
      )
      .subscribe({
        next: (page) => {
          this.orders.set(this.paged.apply(page));
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.snack.open('Failed to load orders', 'Dismiss', { duration: 3000 });
        },
      });
  }

  createOrder(): void {
    this.dialog
      .open(SalesOrderFormComponent, { width: '960px', maxWidth: '95vw' })
      .afterClosed()
      .subscribe((created) => {
        if (created) {
          this.snack.open('Sales order created', '', { duration: 3000 });
          this.load();
        }
      });
  }

  reserve(order: SalesOrderDto): void {
    this.dialog
      .open(ReservationFormComponent, { width: '640px', maxWidth: '95vw', data: order })
      .afterClosed()
      .subscribe((reserved) => {
        if (reserved) {
          this.snack.open('Stock reserved', '', { duration: 3000 });
          this.load();
        }
      });
  }

  /** Segregation of duties: whoever created or submitted the order cannot approve it (the server enforces this too). */
  ownWork(order: SalesOrderDto): boolean {
    const userId = this.session.user()?.id;
    return userId != null && (order.createdBy === userId || order.submittedBy === userId);
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
