import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DecimalPipe } from '@angular/common';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { InventoryApi, MaterialBatchDto } from './inventory.api';
import { Page } from '../master/material/material.api';

@Component({
  selector: 'app-batch-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    IstDatePipe,
    DecimalPipe,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Batch Tracking</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl">
          <mat-option [value]="null">All</mat-option>
          <mat-option value="AVAILABLE">Available</mat-option>
          <mat-option value="QUARANTINE">Quarantine</mat-option>
          <mat-option value="REJECTED">Rejected</mat-option>
          <mat-option value="CONSUMED">Consumed</mat-option>
          <mat-option value="EXPIRED">Expired</mat-option>
        </mat-select>
      </mat-form-field>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="page()?.content ?? []" class="mat-elevation-z1">
        <ng-container matColumnDef="batchNumber">
          <th mat-header-cell *matHeaderCellDef>Batch #</th>
          <td mat-cell *matCellDef="let r">{{ r.batchNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="material">
          <th mat-header-cell *matHeaderCellDef>Material</th>
          <td mat-cell *matCellDef="let r">{{ r.materialCode ?? r.materialId }}</td>
        </ng-container>
        <ng-container matColumnDef="received">
          <th mat-header-cell *matHeaderCellDef>Received</th>
          <td mat-cell *matCellDef="let r">{{ r.receivedDate | istDate: 'dd/MM/yy' }}</td>
        </ng-container>
        <ng-container matColumnDef="expiry">
          <th mat-header-cell *matHeaderCellDef>Expiry</th>
          <td mat-cell *matCellDef="let r">{{ r.expiryDate ? (r.expiryDate | istDate: 'dd/MM/yy') : '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="receivedQty">
          <th mat-header-cell *matHeaderCellDef>Received</th>
          <td mat-cell *matCellDef="let r">{{ r.receivedQty | number: '1.3-3' }}</td>
        </ng-container>
        <ng-container matColumnDef="remainingQty">
          <th mat-header-cell *matHeaderCellDef>Remaining</th>
          <td mat-cell *matCellDef="let r">{{ r.remainingQty | number: '1.3-3' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r">
            <app-status-badge [status]="r.status" />
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let row; columns: cols"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell no-data" [attr.colspan]="cols.length">No batches found.</td>
        </tr>
      </table>

      <mat-paginator
        [length]="page()?.totalElements ?? 0"
        [pageSize]="pageSize"
        [pageSizeOptions]="[20, 50]"
        (page)="onPage($event)"
      />
    }
  `,
})
export class BatchListComponent implements OnInit {
  private readonly api = inject(InventoryApi);

  readonly loading = signal(false);
  readonly page = signal<Page<MaterialBatchDto> | null>(null);
  readonly statusCtrl = new FormControl<string | null>(null);
  readonly cols = ['batchNumber', 'material', 'received', 'expiry', 'receivedQty', 'remainingQty', 'status'];
  pageIndex = 0;
  pageSize = 20;

  ngOnInit(): void {
    this.statusCtrl.valueChanges.subscribe(() => {
      this.pageIndex = 0;
      this.load();
    });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getBatches(undefined, this.statusCtrl.value ?? undefined, this.pageIndex, this.pageSize).subscribe({
      next: (data) => {
        this.page.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.load();
  }
}
