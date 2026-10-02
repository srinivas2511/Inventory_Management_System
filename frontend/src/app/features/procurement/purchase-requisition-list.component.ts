import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe, DatePipe } from '@angular/common';
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

export interface PurchaseRequisitionDto {
  id: number;
  prNumber: string;
  materialId: number;
  requestedQty: number;
  uom: string;
  neededBy: string;
  status: string;
  requestedBy: number;
}

@Component({
  selector: 'app-purchase-requisition-list',
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
    MatSnackBarModule,
    DecimalPipe,
    DatePipe,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Purchase Requisitions</h2>
      <button mat-raised-button color="primary" *appHasPermission="'PURCHASE_CREATE'">
        <mat-icon>add</mat-icon> New PR
      </button>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl">
          <mat-option [value]="null">All</mat-option>
          <mat-option value="OPEN">Open</mat-option>
          <mat-option value="CONVERTED">Converted</mat-option>
          <mat-option value="CANCELLED">Cancelled</mat-option>
        </mat-select>
      </mat-form-field>
      <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon> Refresh</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="prNumber">
          <th mat-header-cell *matHeaderCellDef>PR Number</th>
          <td mat-cell *matCellDef="let r">{{ r.prNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="materialId">
          <th mat-header-cell *matHeaderCellDef>Material</th>
          <td mat-cell *matCellDef="let r">{{ r.materialId }}</td>
        </ng-container>
        <ng-container matColumnDef="requestedQty">
          <th mat-header-cell *matHeaderCellDef>Qty</th>
          <td mat-cell *matCellDef="let r">{{ r.requestedQty | number: '1.3-3' }} {{ r.uom }}</td>
        </ng-container>
        <ng-container matColumnDef="neededBy">
          <th mat-header-cell *matHeaderCellDef>Needed By</th>
          <td mat-cell *matCellDef="let r">{{ r.neededBy | date: 'dd/MM/yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            @if (r.status === 'OPEN') {
              <button mat-icon-button matTooltip="Cancel" *appHasPermission="'PURCHASE_CREATE'" (click)="cancel(r.id)">
                <mat-icon>cancel</mat-icon>
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
export class PurchaseRequisitionListComponent implements OnInit {
  private http = inject(HttpClient);
  private snack = inject(MatSnackBar);

  columns = ['prNumber', 'materialId', 'requestedQty', 'neededBy', 'status', 'actions'];
  rows = signal<PurchaseRequisitionDto[]>([]);
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
    this.http.get<{ content: PurchaseRequisitionDto[] }>('/api/procurement/requisitions', { params }).subscribe({
      next: (page) => {
        this.rows.set(page.content ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  cancel(id: number): void {
    this.http.post<PurchaseRequisitionDto>(`/api/procurement/requisitions/${id}/cancel`, {}).subscribe({
      next: () => {
        this.snack.open('Requisition cancelled', 'OK', { duration: 2000 });
        this.load();
      },
      error: (e) => this.snack.open(e.error?.detail ?? 'Error', 'OK', { duration: 4000 }),
    });
  }
}
