import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { DecimalPipe } from '@angular/common';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

export interface PurchaseReturnChallanDto {
  id: number;
  docNo: string;
  docDate: string;
  status: string;
  supplierId: number;
  purchaseReturnId: number | null;
  vehicleNo: string | null;
  lrNo: string | null;
  remarks: string | null;
  grandTotal: number;
  items: PurchaseReturnChallanItemDto[];
}

export interface PurchaseReturnChallanItemDto {
  id: number;
  lineNo: number;
  materialId: number;
  qty: number;
  uom: string;
  unitPrice: number;
}

@Component({
  selector: 'app-purchase-return-challan-list',
  standalone: true,
  imports: [
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatSnackBarModule,
    DecimalPipe,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.item-prc-challans">Purchase Return Challans</h2>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <table mat-table [dataSource]="challans()" class="mat-elevation-z2 full-width">
        <ng-container matColumnDef="docNo">
          <th mat-header-cell *matHeaderCellDef>Doc No</th>
          <td mat-cell *matCellDef="let row">{{ row.docNo }}</td>
        </ng-container>
        <ng-container matColumnDef="docDate">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let row">{{ row.docDate }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let row"><app-status-badge [status]="row.status" /></td>
        </ng-container>
        <ng-container matColumnDef="supplierId">
          <th mat-header-cell *matHeaderCellDef>Supplier</th>
          <td mat-cell *matCellDef="let row">{{ row.supplierId }}</td>
        </ng-container>
        <ng-container matColumnDef="vehicleNo">
          <th mat-header-cell *matHeaderCellDef>Vehicle No</th>
          <td mat-cell *matCellDef="let row">{{ row.vehicleNo ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="grandTotal">
          <th mat-header-cell *matHeaderCellDef>Total</th>
          <td mat-cell *matCellDef="let row">{{ row.grandTotal | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let row">
            @if (row.status === 'DRAFT') {
              <button
                mat-stroked-button
                color="primary"
                aria-label="Dispatch challan"
                *appHasPermission="'PRC_CHALLAN_DISPATCH'"
                (click)="dispatch(row.id)"
              >
                <mat-icon aria-hidden="true">local_shipping</mat-icon> Dispatch
              </button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
})
export class PurchaseReturnChallanListComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);

  challans = signal<PurchaseReturnChallanDto[]>([]);
  loading = signal(true);

  readonly columns = ['docNo', 'docDate', 'status', 'supplierId', 'vehicleNo', 'grandTotal', 'actions'];

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.http.get<PurchaseReturnChallanDto[]>('/api/procurement/purchase-return-challans').subscribe({
      next: (data) => {
        this.challans.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  dispatch(id: number): void {
    this.http.post<PurchaseReturnChallanDto>(`/api/procurement/purchase-return-challans/${id}/dispatch`, {}).subscribe({
      next: (updated) => {
        this.challans.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
        this.snackBar.open('Challan dispatched', 'Close', { duration: 3000 });
      },
      error: () => this.snackBar.open('Dispatch failed', 'Close', { duration: 3000 }),
    });
  }
}
