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

export interface JwoDto {
  id: number;
  docNo: string;
  docDate: string | null;
  status: string;
  vendorId: number;
  challanNo: string | null;
  issueDate: string | null;
  expectedReturn: string | null;
  actualReturn: string | null;
  jobCharges: number | null;
  scrapQty: number | null;
  remarks: string | null;
  items: JwoItemDto[];
}

export interface JwoItemDto {
  id: number;
  lineNo: number;
  materialId: number | null;
  productId: number | null;
  description: string | null;
  sentQty: number;
  receivedQty: number;
  scrappedQty: number;
  uom: string | null;
}

@Component({
  selector: 'app-job-work-order-list',
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
      <h2 i18n="@@nav.job_work_orders">Job Work Orders</h2>
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
        <ng-container matColumnDef="challanNo">
          <th mat-header-cell *matHeaderCellDef>Challan No</th>
          <td mat-cell *matCellDef="let row">{{ row.challanNo }}</td>
        </ng-container>
        <ng-container matColumnDef="issueDate">
          <th mat-header-cell *matHeaderCellDef>Issue Date</th>
          <td mat-cell *matCellDef="let row">{{ row.issueDate | istDate }}</td>
        </ng-container>
        <ng-container matColumnDef="expectedReturn">
          <th mat-header-cell *matHeaderCellDef>Expected Return</th>
          <td mat-cell *matCellDef="let row">{{ row.expectedReturn | istDate }}</td>
        </ng-container>
        <ng-container matColumnDef="jobCharges">
          <th mat-header-cell *matHeaderCellDef>Job Charges</th>
          <td mat-cell *matCellDef="let row">{{ row.jobCharges | number: '1.2-2' }}</td>
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
export class JobWorkOrderListComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);

  columns = ['docNo', 'docDate', 'status', 'challanNo', 'issueDate', 'expectedReturn', 'jobCharges', 'items'];
  orders = signal<JwoDto[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.http.get<JwoDto[]>('/api/production/job-work-orders').subscribe({
      next: (data) => {
        this.orders.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load job work orders', 'Close', { duration: 3000 });
      },
    });
  }
}
