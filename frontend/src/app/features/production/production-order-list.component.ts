import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

export interface ProductionOrderMaterialDto {
  materialId: number;
  plannedQty: number;
  issuedQty: number;
  consumedQty: number;
  remainingQty: number;
  uom: string;
}

export interface ProductionOperationDto {
  id: number;
  sequenceNo: number;
  operationId: number;
  status: string;
  inputQty: number;
  goodQty: number;
  rejectedQty: number;
  scrapQty: number;
}

export interface ProductionOrderDto {
  id: number;
  orderNumber: string;
  productId: number;
  plannedQty: number;
  goodQty: number;
  rejectedQty: number;
  scrapQty: number;
  status: string;
  priority: number;
  startDate: string;
  expectedCompletionDate: string;
  materials: ProductionOrderMaterialDto[];
  operations: ProductionOperationDto[];
  allowedActions: string[];
}

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
    DatePipe,
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
                Product: {{ row.productId }} · Planned: {{ row.plannedQty | number: '1.0-3' }} · Good:
                {{ row.goodQty | number: '1.0-3' }}
              </mat-panel-description>
            </mat-expansion-panel-header>

            <div class="order-detail">
              <div class="action-bar">
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
                  <button mat-stroked-button *appHasPermission="'PRODUCTION_CLOSE'" (click)="doAction(row.id, 'close')">
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
                  <ng-container matColumnDef="materialId">
                    <th mat-header-cell *matHeaderCellDef>Material</th>
                    <td mat-cell *matCellDef="let m">{{ m.materialId }}</td>
                  </ng-container>
                  <ng-container matColumnDef="plannedQty">
                    <th mat-header-cell *matHeaderCellDef>Planned</th>
                    <td mat-cell *matCellDef="let m">{{ m.plannedQty | number: '1.0-3' }} {{ m.uom }}</td>
                  </ng-container>
                  <ng-container matColumnDef="issuedQty">
                    <th mat-header-cell *matHeaderCellDef>Issued</th>
                    <td mat-cell *matCellDef="let m">{{ m.issuedQty | number: '1.0-3' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="remainingQty">
                    <th mat-header-cell *matHeaderCellDef>Remaining</th>
                    <td mat-cell *matCellDef="let m">{{ m.remainingQty | number: '1.0-3' }}</td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="materialCols"></tr>
                  <tr mat-row *matRowDef="let m; columns: materialCols"></tr>
                </table>
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
                  <ng-container matColumnDef="operationId">
                    <th mat-header-cell *matHeaderCellDef>Operation</th>
                    <td mat-cell *matCellDef="let o">{{ o.operationId }}</td>
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

  columns = ['orderNumber', 'productId', 'plannedQty', 'status', 'priority', 'actions'];
  materialCols = ['materialId', 'plannedQty', 'issuedQty', 'remainingQty'];
  opCols = ['sequenceNo', 'operationId', 'status', 'goodQty'];
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
    this.snack.open('Production order creation — coming soon', 'OK', { duration: 2000 });
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
}
