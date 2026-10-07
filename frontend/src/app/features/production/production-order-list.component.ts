import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AssignOperatorDialogComponent } from './assign-operator-dialog.component';
import { CloseOrderDialogComponent } from './close-order-dialog.component';
import { IssueMaterialDialogComponent } from './issue-material-dialog.component';
import { ProductionOrderFormComponent } from './production-order-form.component';
import {
  ProductionOperationDto,
  ProductionOrderDto,
  ProductionOrderMaterialDto,
  isRunning,
  leftoverQty,
} from './production-order.model';
import { ReturnMaterialDialogComponent } from './return-material-dialog.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

@Component({
  selector: 'app-production-order-list',
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
    MatExpansionModule,
    MatSnackBarModule,
    DecimalPipe,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Production Orders</h2>
      <button mat-raised-button color="primary" *appHasPermission="'PRODUCTION_CREATE'" (click)="createOrder()">
        <mat-icon>add</mat-icon> New Order
      </button>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl">
          <mat-option [value]="null">All</mat-option>
          <mat-option value="DRAFT">Draft</mat-option>
          <mat-option value="APPROVED">Approved</mat-option>
          <mat-option value="RELEASED">Released</mat-option>
          <mat-option value="IN_PROGRESS">In Progress</mat-option>
          <mat-option value="COMPLETED">Completed</mat-option>
          <mat-option value="CLOSED">Closed</mat-option>
          <mat-option value="CANCELLED">Cancelled</mat-option>
        </mat-select>
      </mat-form-field>
      <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon> Refresh</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <mat-accordion>
        @for (row of rows(); track row.id) {
          <mat-expansion-panel>
            <mat-expansion-panel-header>
              <mat-panel-title>
                <strong>{{ row.orderNumber }}</strong
                >&nbsp;
                <app-status-badge [status]="row.status" />
              </mat-panel-title>
              <mat-panel-description>
                Product: {{ row.productCode }} · Priority {{ row.priority }} · Planned:
                {{ row.plannedQty | number: '1.0-3' }} · Good:
                {{ row.goodQty | number: '1.0-3' }}
              </mat-panel-description>
            </mat-expansion-panel-header>

            <div class="order-detail">
              <div class="action-bar">
                @if (running(row)) {
                  <button mat-stroked-button *appHasPermission="'INVENTORY_ISSUE'" (click)="issueMaterial(row)">
                    <mat-icon>inventory_2</mat-icon> Issue material
                  </button>
                  <button mat-stroked-button *appHasPermission="'PRODUCTION_ASSIGN'" (click)="assign(row, null)">
                    <mat-icon>group_add</mat-icon> Assign operator to all
                  </button>
                }
                @if (row.allowedActions?.includes('approve')) {
                  <button
                    mat-stroked-button
                    *appHasPermission="'PRODUCTION_APPROVE'"
                    (click)="doAction(row.id, 'approve')"
                  >
                    <mat-icon>check</mat-icon> Approve
                  </button>
                }
                @if (row.allowedActions?.includes('release')) {
                  <button
                    mat-stroked-button
                    color="primary"
                    *appHasPermission="'PRODUCTION_APPROVE'"
                    (click)="doAction(row.id, 'release')"
                  >
                    <mat-icon>play_arrow</mat-icon> Release
                  </button>
                }
                @if (row.allowedActions?.includes('close')) {
                  <button mat-stroked-button *appHasPermission="'PRODUCTION_CLOSE'" (click)="closeOrder(row)">
                    <mat-icon>lock</mat-icon> Close
                  </button>
                }
                @if (row.allowedActions?.includes('reopen')) {
                  <button
                    mat-stroked-button
                    *appHasPermission="'PRODUCTION_REOPEN'"
                    (click)="doAction(row.id, 'reopen')"
                  >
                    <mat-icon>lock_open</mat-icon> Reopen
                  </button>
                }
                @if (row.allowedActions?.includes('cancel')) {
                  <button
                    mat-stroked-button
                    color="warn"
                    *appHasPermission="'PRODUCTION_UPDATE'"
                    (click)="doAction(row.id, 'cancel')"
                  >
                    <mat-icon>cancel</mat-icon> Cancel
                  </button>
                }
              </div>

              <h4>Materials</h4>
              @if (row.materials?.length) {
                <table mat-table [dataSource]="row.materials" class="mat-elevation-z0 detail-table">
                  <ng-container matColumnDef="material">
                    <th mat-header-cell *matHeaderCellDef>Material</th>
                    <td mat-cell *matCellDef="let m">{{ m.materialCode }} · {{ m.materialName }}</td>
                  </ng-container>
                  <ng-container matColumnDef="plannedQty">
                    <th mat-header-cell *matHeaderCellDef>Planned</th>
                    <td mat-cell *matCellDef="let m">{{ m.plannedQty | number: '1.0-3' }} {{ m.uom }}</td>
                  </ng-container>
                  <ng-container matColumnDef="issuedQty">
                    <th mat-header-cell *matHeaderCellDef>Issued</th>
                    <td mat-cell *matCellDef="let m">{{ m.issuedQty | number: '1.0-3' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="consumedQty">
                    <th mat-header-cell *matHeaderCellDef>Consumed</th>
                    <td mat-cell *matCellDef="let m">{{ m.consumedQty | number: '1.0-3' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="returnedQty">
                    <th mat-header-cell *matHeaderCellDef>Returned</th>
                    <td mat-cell *matCellDef="let m">{{ m.returnedQty | number: '1.0-3' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="scrapQty">
                    <th mat-header-cell *matHeaderCellDef>Written off</th>
                    <td mat-cell *matCellDef="let m">{{ m.scrapQty | number: '1.0-3' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="leftover">
                    <th mat-header-cell *matHeaderCellDef>Left over</th>
                    <td mat-cell *matCellDef="let m">{{ leftover(m) | number: '1.0-3' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="remainingQty">
                    <th mat-header-cell *matHeaderCellDef>Still to issue</th>
                    <td mat-cell *matCellDef="let m">{{ m.remainingQty | number: '1.0-3' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="actions">
                    <th mat-header-cell *matHeaderCellDef></th>
                    <td mat-cell *matCellDef="let m">
                      @if (row.status === 'COMPLETED' && leftover(m) > 0) {
                        <button
                          mat-stroked-button
                          *appHasPermission="'PRODUCTION_CLOSE'"
                          (click)="returnMaterial(row, m)"
                        >
                          Return
                        </button>
                      }
                    </td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="materialCols"></tr>
                  <tr mat-row *matRowDef="let m; columns: materialCols"></tr>
                </table>
                @if (row.status === 'COMPLETED' && hasLeftover(row)) {
                  <p class="reconcile-note">
                    Some issued material was not used. Return it to stock, or close the order and write it off.
                  </p>
                }
              } @else {
                <p class="empty-state">No materials snapshotted yet (order not released).</p>
              }

              <h4>Operations</h4>
              @if (row.operations?.length) {
                <table mat-table [dataSource]="row.operations" class="mat-elevation-z0 detail-table">
                  <ng-container matColumnDef="sequenceNo">
                    <th mat-header-cell *matHeaderCellDef>Seq</th>
                    <td mat-cell *matCellDef="let o">{{ o.sequenceNo }}</td>
                  </ng-container>
                  <ng-container matColumnDef="operation">
                    <th mat-header-cell *matHeaderCellDef>Operation</th>
                    <td mat-cell *matCellDef="let o">
                      {{ o.operationName }}
                      @if (o.machineName) {
                        <span class="mat-caption"> · {{ o.machineName }}</span>
                      }
                    </td>
                  </ng-container>
                  <ng-container matColumnDef="operator">
                    <th mat-header-cell *matHeaderCellDef>Operator</th>
                    <td mat-cell *matCellDef="let o">
                      @if (o.assignedOperatorId) {
                        {{ o.assignedOperatorName ?? 'User ' + o.assignedOperatorId }}
                      } @else {
                        <span class="unassigned">Unassigned</span>
                      }
                      @if (running(row) && o.status !== 'COMPLETED') {
                        <button
                          mat-icon-button
                          *appHasPermission="'PRODUCTION_ASSIGN'"
                          aria-label="Assign operator"
                          matTooltip="Assign operator"
                          (click)="assign(row, o)"
                        >
                          <mat-icon>person_add</mat-icon>
                        </button>
                      }
                    </td>
                  </ng-container>
                  <ng-container matColumnDef="status">
                    <th mat-header-cell *matHeaderCellDef>Status</th>
                    <td mat-cell *matCellDef="let o"><app-status-badge [status]="o.status" /></td>
                  </ng-container>
                  <ng-container matColumnDef="goodQty">
                    <th mat-header-cell *matHeaderCellDef>Good / Rej / Scrap</th>
                    <td mat-cell *matCellDef="let o">
                      {{ o.goodQty | number: '1.0-3' }} / {{ o.rejectedQty | number: '1.0-3' }} /
                      {{ o.scrapQty | number: '1.0-3' }}
                    </td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="opCols"></tr>
                  <tr mat-row *matRowDef="let o; columns: opCols"></tr>
                </table>
              } @else {
                <p class="empty-state">No operations snapshotted yet.</p>
              }
            </div>
          </mat-expansion-panel>
        }
      </mat-accordion>

      @if (rows().length === 0) {
        <p class="empty-state">No production orders found.</p>
      }
    }
  `,
  styles: [
    `
      .order-detail {
        padding: 8px 0;
      }
      .action-bar {
        display: flex;
        gap: 8px;
        margin-bottom: 16px;
        flex-wrap: wrap;
      }
      .detail-table {
        width: 100%;
        margin-bottom: 16px;
      }
      .unassigned {
        color: #888;
      }
      .reconcile-note {
        margin: 0 0 16px;
        color: #8a5a00;
      }
      h4 {
        margin: 16px 0 8px;
        font-size: 14px;
        color: #666;
      }
    `,
  ],
})
export class ProductionOrderListComponent implements OnInit {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);
  private dialog = inject(MatDialog);

  columns = ['orderNumber', 'productId', 'plannedQty', 'status', 'priority', 'actions'];
  materialCols = [
    'material',
    'plannedQty',
    'issuedQty',
    'consumedQty',
    'returnedQty',
    'scrapQty',
    'leftover',
    'remainingQty',
    'actions',
  ];
  opCols = ['sequenceNo', 'operation', 'operator', 'status', 'goodQty'];
  rows = signal<ProductionOrderDto[]>([]);
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
    this.http.get<{ content: ProductionOrderDto[] }>('/api/production-orders', { params }).subscribe({
      next: (page) => {
        this.rows.set(page.content ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  createOrder(): void {
    this.dialog
      .open(ProductionOrderFormComponent, { width: '720px', maxWidth: '95vw' })
      .afterClosed()
      .subscribe((created) => {
        if (created) {
          this.snack.open('Production order created as a draft', 'OK', { duration: 3000 });
          this.load();
        }
      });
  }

  doAction(id: number, action: string): void {
    this.http.post<ProductionOrderDto>(`/api/production-orders/${id}/${action}`, {}).subscribe({
      next: () => {
        this.snack.open('Done', 'OK', { duration: 2000 });
        this.load();
      },
      error: (e) => this.snack.open(e.error?.detail ?? 'Error', 'OK', { duration: 4000 }),
    });
  }

  protected running(order: ProductionOrderDto): boolean {
    return isRunning(order);
  }

  protected leftover(m: ProductionOrderMaterialDto): number {
    return leftoverQty(m);
  }

  protected hasLeftover(order: ProductionOrderDto): boolean {
    return (order.materials ?? []).some((m) => leftoverQty(m) > 0);
  }

  issueMaterial(order: ProductionOrderDto): void {
    this.dialog
      .open(IssueMaterialDialogComponent, {
        width: '820px',
        maxWidth: '95vw',
        data: { orderId: order.id, orderNumber: order.orderNumber, materials: order.materials },
      })
      .afterClosed()
      .subscribe((issued) => {
        if (issued) {
          this.snack.open('Material issued', 'OK', { duration: 3000 });
          this.load();
        }
      });
  }

  returnMaterial(order: ProductionOrderDto, material: ProductionOrderMaterialDto): void {
    this.dialog
      .open(ReturnMaterialDialogComponent, {
        width: '520px',
        maxWidth: '95vw',
        data: { orderId: order.id, orderNumber: order.orderNumber, material },
      })
      .afterClosed()
      .subscribe((returned) => {
        if (returned) {
          this.snack.open('Material returned to stock', 'OK', { duration: 3000 });
          this.load();
        }
      });
  }

  closeOrder(order: ProductionOrderDto): void {
    this.dialog
      .open(CloseOrderDialogComponent, {
        width: '560px',
        maxWidth: '95vw',
        data: { orderId: order.id, orderNumber: order.orderNumber, materials: order.materials ?? [] },
      })
      .afterClosed()
      .subscribe((closed) => {
        if (closed) {
          this.snack.open('Order closed', 'OK', { duration: 3000 });
          this.load();
        }
      });
  }

  assign(order: ProductionOrderDto, operation: ProductionOperationDto | null): void {
    this.dialog
      .open(AssignOperatorDialogComponent, {
        width: '520px',
        maxWidth: '95vw',
        data: { orderId: order.id, orderNumber: order.orderNumber, operation },
      })
      .afterClosed()
      .subscribe((saved) => {
        if (saved) {
          this.snack.open('Assignment saved', 'OK', { duration: 3000 });
          this.load();
        }
      });
  }
}
