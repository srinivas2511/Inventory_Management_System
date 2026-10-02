import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

export interface FgBatchDto {
  id: number;
  batchNumber: string;
  productId: number;
  productCode: string;
  productName: string;
  productionOrderId: number;
  producedQty: number;
  approvedQty: number;
  rejectedQty: number;
  remainingQty: number;
  producedOn: string;
  qualityStatus: string;
  unitCost: number;
  pendingInspectionId: number | null;
}

@Component({
  selector: 'app-fg-batch-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSnackBarModule,
    DecimalPipe,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Finished Goods Batches</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl">
          <mat-option [value]="null">All Statuses</mat-option>
          <mat-option value="QUALITY_PENDING">Quality Pending</mat-option>
          <mat-option value="APPROVED">Approved</mat-option>
          <mat-option value="PARTIALLY_APPROVED">Partially Approved</mat-option>
          <mat-option value="REJECTED">Rejected</mat-option>
          <mat-option value="HOLD">Hold</mat-option>
        </mat-select>
      </mat-form-field>
      <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon> Refresh</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="batchNumber">
          <th mat-header-cell *matHeaderCellDef>Batch #</th>
          <td mat-cell *matCellDef="let r">{{ r.batchNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="product">
          <th mat-header-cell *matHeaderCellDef>Product</th>
          <td mat-cell *matCellDef="let r">{{ r.productCode }} – {{ r.productName }}</td>
        </ng-container>
        <ng-container matColumnDef="producedOn">
          <th mat-header-cell *matHeaderCellDef>Produced On</th>
          <td mat-cell *matCellDef="let r">{{ r.producedOn }}</td>
        </ng-container>
        <ng-container matColumnDef="producedQty">
          <th mat-header-cell *matHeaderCellDef>Produced</th>
          <td mat-cell *matCellDef="let r">{{ r.producedQty | number: '1.3-3' }}</td>
        </ng-container>
        <ng-container matColumnDef="approvedQty">
          <th mat-header-cell *matHeaderCellDef>Approved</th>
          <td mat-cell *matCellDef="let r">{{ r.approvedQty | number: '1.3-3' }}</td>
        </ng-container>
        <ng-container matColumnDef="qualityStatus">
          <th mat-header-cell *matHeaderCellDef>QC Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.qualityStatus" /></td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            @if (r.qualityStatus === 'QUALITY_PENDING' && r.pendingInspectionId) {
              <button
                mat-icon-button
                matTooltip="Record Final Inspection"
                *appHasPermission="'QUALITY_INSPECT'"
                (click)="openFinalDecision(r)"
              >
                <mat-icon>fact_check</mat-icon>
              </button>
            }
            @if (r.qualityStatus === 'HOLD') {
              <button
                mat-icon-button
                matTooltip="Resolve Hold"
                *appHasPermission="'QUALITY_APPROVE'"
                (click)="resolveHold(r)"
              >
                <mat-icon>lock_open</mat-icon>
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
export class FgBatchListComponent implements OnInit {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);

  columns = ['batchNumber', 'product', 'producedOn', 'producedQty', 'approvedQty', 'qualityStatus', 'actions'];
  rows = signal<FgBatchDto[]>([]);
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
    this.http.get<{ content: FgBatchDto[] }>('/api/quality/fg-batches', { params }).subscribe({
      next: (page) => {
        this.rows.set(page.content ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  openFinalDecision(batch: FgBatchDto): void {
    const approvedStr = prompt(`Batch ${batch.batchNumber} — Produced: ${batch.producedQty}\nEnter APPROVED qty:`);
    if (approvedStr == null) return;
    const rejectedStr = prompt('Enter REJECTED qty (remainder goes to hold):');
    if (rejectedStr == null) return;

    const approved = parseFloat(approvedStr) || 0;
    const rejected = parseFloat(rejectedStr) || 0;
    const hold = Math.max(0, batch.producedQty - approved - rejected);

    const body = { approvedQty: approved, rejectedQty: rejected, holdQty: hold, itemResults: [], rejectionReasons: [] };
    this.http.post(`/api/quality/inspections/${batch.pendingInspectionId}/final-decision`, body).subscribe({
      next: () => {
        this.snack.open('Final inspection recorded', 'OK', { duration: 3000 });
        this.load();
      },
      error: (e) => this.snack.open(e.error?.detail ?? 'Error recording decision', 'OK', { duration: 5000 }),
    });
  }

  resolveHold(batch: FgBatchDto): void {
    const approvedStr = prompt(`Batch ${batch.batchNumber} on HOLD\nEnter qty to APPROVE:`);
    if (approvedStr == null) return;
    const reason = prompt('Reason for decision:');
    if (!reason) return;

    const approved = parseFloat(approvedStr) || 0;
    const rejected = Math.max(0, batch.producedQty - approved);

    const body = { approvedQty: approved, rejectedQty: rejected, rejectionReasons: [], reason };
    this.http.post(`/api/quality/fg-batches/${batch.id}/approve-hold`, body).subscribe({
      next: () => {
        this.snack.open('Hold resolved', 'OK', { duration: 3000 });
        this.load();
      },
      error: (e) => this.snack.open(e.error?.detail ?? 'Error resolving hold', 'OK', { duration: 5000 }),
    });
  }
}
