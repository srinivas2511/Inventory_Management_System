import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { problemMessage } from '../../core/models/problem.model';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import {
  ControllingApi,
  CostCentreDto,
  CreateInternalOrderRequest,
  InternalOrderCostRow,
  InternalOrderDto,
  SettleInternalOrderRequest,
} from './controlling.api';

// ── Settlement dialog ────────────────────────────────────────────────────────

import { Component as NgComponent } from '@angular/core';

@NgComponent({
  selector: 'app-settle-dialog',
  standalone: true,
  imports: [MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, ReactiveFormsModule],
  template: `
    <h2 mat-dialog-title>Settle Internal Order</h2>
    <mat-dialog-content>
      <form [formGroup]="form" style="display:flex;flex-direction:column;gap:12px;min-width:360px;margin-top:8px">
        <mat-form-field appearance="outline">
          <mat-label>Settlement Cost Centre</mat-label>
          <mat-select formControlName="settlementCostCentreId" required>
            @for (cc of costCentres; track cc.id) {
              <mat-option [value]="cc.id">{{ cc.costCentreCode }} — {{ cc.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Reason</mat-label>
          <textarea matInput formControlName="reason" rows="3" required></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="form.invalid" (click)="confirm()">Settle</button>
    </mat-dialog-actions>
  `,
})
export class SettleDialogComponent {
  private dialogRef = inject(MatDialogRef<SettleDialogComponent>);
  data: { costCentres: CostCentreDto[] } = inject(MAT_DIALOG_DATA);
  costCentres = this.data.costCentres;
  fb = inject(FormBuilder);
  form = this.fb.group({
    settlementCostCentreId: [null as number | null, Validators.required],
    reason: ['', Validators.required],
  });
  confirm() {
    if (this.form.valid) this.dialogRef.close(this.form.value as SettleInternalOrderRequest);
  }
}

// ── Main list component ──────────────────────────────────────────────────────

@Component({
  selector: 'app-internal-order-list',
  standalone: true,
  imports: [
    DecimalPipe,
    HasPermissionDirective,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    ReactiveFormsModule,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Internal Orders</h2>
      <button *appHasPermission="'INTERNAL_ORDER_MANAGE'" mat-flat-button color="primary" (click)="newOrder()">
        <mat-icon aria-hidden="true">add</mat-icon> New order
      </button>
    </div>

    @if (editing()) {
      <form class="edit-form mat-elevation-z1" [formGroup]="form" (ngSubmit)="save()">
        <mat-form-field appearance="outline">
          <mat-label>Order number</mat-label>
          <input matInput formControlName="orderNumber" maxlength="20" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Description</mat-label>
          <input matInput formControlName="description" maxlength="200" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Type</mat-label>
          <mat-select formControlName="orderType">
            <mat-option value="CAPEX">CAPEX</mat-option>
            <mat-option value="MAINTENANCE">Maintenance</mat-option>
            <mat-option value="PROJECT">Project</mat-option>
            <mat-option value="OTHER">Other</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Budget (₹)</mat-label>
          <input matInput type="number" min="0" formControlName="budgetAmount" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Start date</mat-label>
          <input matInput type="date" formControlName="startDate" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>End date</mat-label>
          <input matInput type="date" formControlName="endDate" />
        </mat-form-field>
        <div>
          <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">Save</button>
          <button mat-button type="button" (click)="cancelEdit()">Cancel</button>
        </div>
      </form>
    }

    <table mat-table [dataSource]="orders()" class="mat-elevation-z1">
      <ng-container matColumnDef="orderNumber">
        <th mat-header-cell *matHeaderCellDef>Order #</th>
        <td mat-cell *matCellDef="let r">{{ r.orderNumber }}</td>
      </ng-container>
      <ng-container matColumnDef="description">
        <th mat-header-cell *matHeaderCellDef>Description</th>
        <td mat-cell *matCellDef="let r">{{ r.description }}</td>
      </ng-container>
      <ng-container matColumnDef="orderType">
        <th mat-header-cell *matHeaderCellDef>Type</th>
        <td mat-cell *matCellDef="let r">{{ r.orderType }}</td>
      </ng-container>
      <ng-container matColumnDef="budget">
        <th mat-header-cell *matHeaderCellDef style="text-align:right">Budget (₹)</th>
        <td mat-cell *matCellDef="let r" style="text-align:right">{{ r.budgetAmount | number: '1.2-2' }}</td>
      </ng-container>
      <ng-container matColumnDef="actual">
        <th mat-header-cell *matHeaderCellDef style="text-align:right">Actual (₹)</th>
        <td mat-cell *matCellDef="let r" style="text-align:right">{{ r.actualAmount | number: '1.2-2' }}</td>
      </ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Status</th>
        <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let r">
          <ng-container *appHasPermission="'INTERNAL_ORDER_MANAGE'">
            <button mat-icon-button aria-label="Edit" (click)="editOrder(r)" [disabled]="r.status !== 'OPEN'">
              <mat-icon aria-hidden="true">edit</mat-icon>
            </button>
            <button mat-icon-button color="primary" aria-label="View costs" (click)="viewCosts(r)">
              <mat-icon aria-hidden="true">account_tree</mat-icon>
            </button>
            <button
              mat-icon-button
              color="accent"
              aria-label="Settle"
              (click)="settle(r)"
              [disabled]="r.status !== 'OPEN'"
            >
              <mat-icon aria-hidden="true">check_circle</mat-icon>
            </button>
          </ng-container>
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="cols"></tr>
      <tr mat-row *matRowDef="let row; columns: cols"></tr>
    </table>

    @if (selectedOrder(); as o) {
      <div style="margin-top:24px">
        <h3 class="mat-h3">Collected costs — {{ o.orderNumber }}</h3>
        @if (costs().length === 0) {
          <p class="mat-body-2">No journal lines tagged to this order yet.</p>
        } @else {
          <table mat-table [dataSource]="costs()" class="mat-elevation-z1">
            <ng-container matColumnDef="account">
              <th mat-header-cell *matHeaderCellDef>Account</th>
              <td mat-cell *matCellDef="let r">{{ r.accountCode }} {{ r.accountName }}</td>
            </ng-container>
            <ng-container matColumnDef="debit">
              <th mat-header-cell *matHeaderCellDef style="text-align:right">Debit (₹)</th>
              <td mat-cell *matCellDef="let r" style="text-align:right">{{ r.totalDebit | number: '1.2-2' }}</td>
            </ng-container>
            <ng-container matColumnDef="credit">
              <th mat-header-cell *matHeaderCellDef style="text-align:right">Credit (₹)</th>
              <td mat-cell *matCellDef="let r" style="text-align:right">{{ r.totalCredit | number: '1.2-2' }}</td>
            </ng-container>
            <ng-container matColumnDef="net">
              <th mat-header-cell *matHeaderCellDef style="text-align:right">Net (₹)</th>
              <td mat-cell *matCellDef="let r" style="text-align:right">{{ r.netAmount | number: '1.2-2' }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="['account', 'debit', 'credit', 'net']"></tr>
            <tr mat-row *matRowDef="let row; columns: ['account', 'debit', 'credit', 'net']"></tr>
          </table>
        }
      </div>
    }
  `,
})
export class InternalOrderListComponent implements OnInit {
  private api = inject(ControllingApi);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);
  private fb = inject(FormBuilder);

  cols = ['orderNumber', 'description', 'orderType', 'budget', 'actual', 'status', 'actions'];
  orders = signal<InternalOrderDto[]>([]);
  costCentres = signal<CostCentreDto[]>([]);
  editing = signal(false);
  editingId = signal<number | null>(null);
  selectedOrder = signal<InternalOrderDto | null>(null);
  costs = signal<InternalOrderCostRow[]>([]);

  form = this.fb.group({
    orderNumber: ['', [Validators.required, Validators.maxLength(20)]],
    description: ['', [Validators.required, Validators.maxLength(200)]],
    orderType: ['CAPEX', Validators.required],
    budgetAmount: [0, [Validators.required, Validators.min(0)]],
    startDate: [null as string | null],
    endDate: [null as string | null],
  });

  ngOnInit(): void {
    this.loadOrders();
    this.api.getCostCentres().subscribe({
      next: (cc) => this.costCentres.set(cc),
    });
  }

  private loadOrders(): void {
    this.api.getInternalOrders().subscribe({
      next: (list) => this.orders.set(list),
      error: (err) => this.snackBar.open(problemMessage(err, 'Failed to load orders'), 'Close', { duration: 4000 }),
    });
  }

  newOrder(): void {
    this.editingId.set(null);
    this.form.reset({ orderType: 'CAPEX', budgetAmount: 0 });
    this.editing.set(true);
  }

  editOrder(order: InternalOrderDto): void {
    this.editingId.set(order.id);
    this.form.patchValue({
      orderNumber: order.orderNumber,
      description: order.description,
      orderType: order.orderType,
      budgetAmount: order.budgetAmount,
      startDate: order.startDate,
      endDate: order.endDate,
    });
    this.editing.set(true);
  }

  cancelEdit(): void {
    this.editing.set(false);
    this.editingId.set(null);
  }

  save(): void {
    if (this.form.invalid) return;
    const req = this.form.value as CreateInternalOrderRequest;
    const id = this.editingId();
    const obs$ = id ? this.api.updateInternalOrder(id, req) : this.api.createInternalOrder(req);
    obs$.subscribe({
      next: () => {
        this.editing.set(false);
        this.loadOrders();
      },
      error: (err) => this.snackBar.open(problemMessage(err, 'Save failed'), 'Close', { duration: 4000 }),
    });
  }

  viewCosts(order: InternalOrderDto): void {
    this.selectedOrder.set(order);
    this.api.getInternalOrderCosts(order.id).subscribe({
      next: (rows) => this.costs.set(rows),
      error: (err) => this.snackBar.open(problemMessage(err, 'Failed to load costs'), 'Close', { duration: 4000 }),
    });
  }

  settle(order: InternalOrderDto): void {
    this.dialog
      .open(SettleDialogComponent, { data: { costCentres: this.costCentres() } })
      .afterClosed()
      .subscribe((result: SettleInternalOrderRequest | undefined) => {
        if (!result) return;
        this.api.settleInternalOrder(order.id, result).subscribe({
          next: () => {
            this.snackBar.open('Order settled', 'Close', { duration: 3000 });
            this.loadOrders();
          },
          error: (err) => this.snackBar.open(problemMessage(err, 'Settlement failed'), 'Close', { duration: 4000 }),
        });
      });
  }
}
