import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { WarehouseApi, WarehouseDto } from '../warehouse/warehouse.api';
import { MOVEMENT_LABELS, MovementType, TransferOrderDto, TransferOrderStatus, WmsApi } from './wms.api';

const STATUSES: TransferOrderStatus[] = ['CREATED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'];

/**
 * One list for every kind of transfer order. The route data names the movement type, so the put-away task list,
 * the picking list, the transfers and the cross-docks are the same screen with a different filter and a different
 * way to create work.
 */
@Component({
  selector: 'app-wms-tasks',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2" data-test="title">{{ title() }}</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl" (selectionChange)="load()" data-test="status">
          <mat-option [value]="null">All</mat-option>
          @for (s of statuses; track s) {
            <mat-option [value]="s">{{ s }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Order number</mat-label>
        <input matInput [formControl]="searchCtrl" (keyup.enter)="load()" />
      </mat-form-field>
      <button mat-stroked-button (click)="load()"><mat-icon>search</mat-icon> Search</button>
    </div>

    @switch (movementType()) {
      @case ('PUTAWAY') {
        <form *appHasPermission="'PUTAWAY_MANAGE'" class="edit-form" (ngSubmit)="planPutAway()">
          <mat-form-field appearance="outline">
            <mat-label>Goods receipt id</mat-label>
            <input matInput type="number" min="1" [formControl]="receiptCtrl" data-test="receipt" />
          </mat-form-field>
          <button mat-flat-button color="primary" type="submit"><mat-icon>add_task</mat-icon> Plan put-away</button>
        </form>
      }
      @case ('PICKING') {
        <form *appHasPermission="'PICKING_MANAGE'" class="edit-form" (ngSubmit)="planPicking()">
          <mat-form-field appearance="outline">
            <mat-label>Sales order id</mat-label>
            <input matInput type="number" min="1" [formControl]="orderCtrl" data-test="sales-order" />
          </mat-form-field>
          <button mat-flat-button color="primary" type="submit"><mat-icon>add_task</mat-icon> Plan picking</button>
        </form>
      }
      @case ('CROSS_DOCK') {
        <form *appHasPermission="'PUTAWAY_MANAGE'" class="edit-form" [formGroup]="crossForm" (ngSubmit)="crossDock()">
          <mat-form-field appearance="outline">
            <mat-label>Goods receipt id</mat-label>
            <input matInput type="number" min="1" formControlName="goodsReceiptId" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Target</mat-label>
            <mat-select formControlName="targetType">
              <mat-option value="SALES_ORDER">Sales order</mat-option>
              <mat-option value="PRODUCTION_ORDER">Production order</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Target id</mat-label>
            <input matInput type="number" min="1" formControlName="targetId" />
          </mat-form-field>
          <button mat-flat-button color="primary" type="submit" [disabled]="crossForm.invalid">
            <mat-icon>swap_horiz</mat-icon> Cross-dock
          </button>
        </form>
      }
      @case ('INTERNAL_TRANSFER') {
        <form
          *appHasPermission="'TRANSFER_MANAGE'"
          class="edit-form"
          [formGroup]="transferForm"
          (ngSubmit)="createTransfer()"
        >
          <mat-form-field appearance="outline">
            <mat-label>Warehouse</mat-label>
            <mat-select formControlName="warehouseId" data-test="transfer-warehouse">
              @for (w of warehouses(); track w.id) {
                <mat-option [value]="w.id">{{ w.warehouseCode }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Item type</mat-label>
            <mat-select formControlName="itemType">
              <mat-option value="MATERIAL">Material</mat-option>
              <mat-option value="PRODUCT">Product</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Item id</mat-label>
            <input matInput type="number" min="1" formControlName="itemId" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Batch id</mat-label>
            <input matInput type="number" min="1" formControlName="batchId" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>From bin id</mat-label>
            <input matInput type="number" min="1" formControlName="fromBinId" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>To bin id</mat-label>
            <input matInput type="number" min="1" formControlName="toBinId" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Quantity</mat-label>
            <input matInput type="number" min="0.001" step="0.001" formControlName="quantity" />
          </mat-form-field>
          <button mat-flat-button color="primary" type="submit" [disabled]="transferForm.invalid">
            <mat-icon>add</mat-icon> Create transfer
          </button>
        </form>
      }
    }

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="orders()" class="mat-elevation-z1" data-test="orders">
        <ng-container matColumnDef="toNumber">
          <th mat-header-cell *matHeaderCellDef>Order</th>
          <td mat-cell *matCellDef="let o">{{ o.toNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="type">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let o">{{ label(o.movementType) }}</td>
        </ng-container>
        <ng-container matColumnDef="source">
          <th mat-header-cell *matHeaderCellDef>Source</th>
          <td mat-cell *matCellDef="let o">
            {{ o.sourceDocumentType ? o.sourceDocumentType + ' #' + o.sourceDocumentId : '' }}
          </td>
        </ng-container>
        <ng-container matColumnDef="wave">
          <th mat-header-cell *matHeaderCellDef>Wave</th>
          <td mat-cell *matCellDef="let o">{{ o.waveId ?? '' }}</td>
        </ng-container>
        <ng-container matColumnDef="created">
          <th mat-header-cell *matHeaderCellDef>Created</th>
          <td mat-cell *matCellDef="let o">{{ o.createdAt | date: 'dd MMM yyyy HH:mm' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let o"><app-status-badge [status]="o.status" /></td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns" class="clickable" (click)="open(row)"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No orders found
          </td>
        </tr>
      </table>
    }
  `,
  styles: `
    .filter-bar,
    .edit-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .clickable {
      cursor: pointer;
    }
  `,
})
export class WmsTasksComponent implements OnInit {
  private api = inject(WmsApi);
  private warehouseApi = inject(WarehouseApi);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  statuses = STATUSES;
  columns = ['toNumber', 'type', 'source', 'wave', 'created', 'status'];

  movementType = signal<MovementType | null>(null);
  title = signal('Transfer Orders');
  orders = signal<TransferOrderDto[]>([]);
  warehouses = signal<WarehouseDto[]>([]);
  loading = signal(false);

  statusCtrl = new FormControl<TransferOrderStatus | null>(null);
  searchCtrl = new FormControl('');
  receiptCtrl = new FormControl<number | null>(null);
  orderCtrl = new FormControl<number | null>(null);
  crossForm = this.fb.group({
    goodsReceiptId: new FormControl<number | null>(null, Validators.required),
    targetType: ['SALES_ORDER', Validators.required],
    targetId: new FormControl<number | null>(null, Validators.required),
  });
  transferForm = this.fb.group({
    warehouseId: new FormControl<number | null>(null, Validators.required),
    itemType: ['MATERIAL', Validators.required],
    itemId: new FormControl<number | null>(null, Validators.required),
    batchId: new FormControl<number | null>(null),
    fromBinId: new FormControl<number | null>(null, Validators.required),
    toBinId: new FormControl<number | null>(null, Validators.required),
    quantity: new FormControl<number | null>(null, [Validators.required, Validators.min(0.001)]),
  });

  ngOnInit(): void {
    const type = (this.route.snapshot.data['movementType'] ?? null) as MovementType | null;
    this.movementType.set(type);
    this.title.set(this.route.snapshot.data['title'] ?? 'Transfer Orders');
    if (type === 'INTERNAL_TRANSFER') {
      this.warehouseApi.list().subscribe((ws) => this.warehouses.set(ws.filter((w) => w.active)));
    }
    this.load();
  }

  label(type: MovementType): string {
    return MOVEMENT_LABELS[type] ?? type;
  }

  load(): void {
    this.loading.set(true);
    this.api
      .getTransferOrders(this.movementType(), this.statusCtrl.value, null, this.searchCtrl.value || null)
      .subscribe({
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

  private fail(err: { error?: { detail?: string } }, fallback: string): void {
    this.snack.open(err?.error?.detail ?? fallback, 'Dismiss', { duration: 5000 });
  }

  open(order: TransferOrderDto): void {
    this.router.navigate(['/wms/orders', order.id]);
  }

  planPutAway(): void {
    const id = this.receiptCtrl.value;
    if (!id) return;
    this.api
      .putAwayFromReceipt(id)
      .subscribe({ next: (o) => this.open(o), error: (e) => this.fail(e, 'Could not plan put-away') });
  }

  planPicking(): void {
    const id = this.orderCtrl.value;
    if (!id) return;
    this.api.pickSalesOrder(id).subscribe({
      next: (orders) => {
        if (orders.length === 0) {
          this.snack.open('No approved stock to pick for this order', 'Dismiss', { duration: 5000 });
        }
        this.load();
      },
      error: (e) => this.fail(e, 'Could not plan picking'),
    });
  }

  crossDock(): void {
    if (this.crossForm.invalid) return;
    const v = this.crossForm.getRawValue();
    this.api.crossDock(v.goodsReceiptId as number, v.targetType as string, v.targetId as number).subscribe({
      next: (o) => this.open(o),
      error: (e) => this.fail(e, 'Could not cross-dock'),
    });
  }

  createTransfer(): void {
    if (this.transferForm.invalid) return;
    const v = this.transferForm.getRawValue();
    const material = v.itemType === 'MATERIAL';
    this.api
      .createTransfer(v.warehouseId as number, null, [
        {
          itemType: material ? 'MATERIAL' : 'PRODUCT',
          materialId: material ? v.itemId : null,
          productId: material ? null : v.itemId,
          batchId: v.batchId,
          fromBinId: v.fromBinId,
          fromLocationId: null,
          toBinId: v.toBinId as number,
          quantity: v.quantity as number,
        },
      ])
      .subscribe({ next: (o) => this.open(o), error: (e) => this.fail(e, 'Could not create the transfer') });
  }
}
