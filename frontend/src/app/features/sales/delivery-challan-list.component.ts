import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';

export interface ChallanDto {
  id: number;
  docNo: string;
  docDate: string | null;
  status: string;
  challanKind: string;
  customerId: number;
  lrNo: string | null;
  courier: string | null;
  grandTotal: number | null;
  items: ChallanItemDto[];
}

export interface ChallanItemDto {
  id: number;
  lineNo: number;
  productId: number | null;
  description: string | null;
  qty: number;
  uom: string | null;
  lineTotal: number | null;
}

@Component({
  selector: 'app-delivery-challan-list',
  standalone: true,
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatSnackBarModule,
    IstDatePipe,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.delivery_challans">Delivery Challans</h2>
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
          <td mat-cell *matCellDef="let row">{{ row.docDate | istDate }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let row"><app-status-badge [status]="row.status" /></td>
        </ng-container>
        <ng-container matColumnDef="challanKind">
          <th mat-header-cell *matHeaderCellDef>Kind</th>
          <td mat-cell *matCellDef="let row">{{ row.challanKind }}</td>
        </ng-container>
        <ng-container matColumnDef="lrNo">
          <th mat-header-cell *matHeaderCellDef>LR No</th>
          <td mat-cell *matCellDef="let row">{{ row.lrNo }}</td>
        </ng-container>
        <ng-container matColumnDef="grandTotal">
          <th mat-header-cell *matHeaderCellDef>Total</th>
          <td mat-cell *matCellDef="let row">{{ row.grandTotal | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="items">
          <th mat-header-cell *matHeaderCellDef>Lines</th>
          <td mat-cell *matCellDef="let row">{{ row.items?.length ?? 0 }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
})
export class DeliveryChallanListComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);

  columns = ['docNo', 'docDate', 'status', 'challanKind', 'lrNo', 'grandTotal', 'items'];
  challans = signal<ChallanDto[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.http.get<ChallanDto[]>('/api/sales/delivery-challans').subscribe({
      next: (data) => {
        this.challans.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load delivery challans', 'Close', { duration: 3000 });
      },
    });
  }
}
