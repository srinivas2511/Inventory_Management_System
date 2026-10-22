import { Component, OnInit, inject, signal } from '@angular/core';

import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { COMPLAINT_STATUSES, ComplaintStatus, ComplaintSummary, QualityPlusApi } from './quality-plus.api';

@Component({
  selector: 'app-complaint-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    IstDatePipe,
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
      <h2 class="mat-h2">Customer Complaints</h2>
      <a *appHasPermission="'COMPLAINT_MANAGE'" mat-flat-button color="primary" routerLink="/quality/complaints/new">
        <mat-icon>add</mat-icon> Register complaint
      </a>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Status</mat-label>
        <mat-select [formControl]="statusCtrl" (selectionChange)="load()">
          <mat-option [value]="null">All</mat-option>
          @for (s of statuses; track s) {
            <mat-option [value]="s">{{ s }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Search</mat-label>
        <input matInput [formControl]="searchCtrl" (keyup.enter)="load()" />
      </mat-form-field>
      <button mat-stroked-button (click)="load()"><mat-icon>search</mat-icon> Search</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="complaints()" class="mat-elevation-z1">
        <ng-container matColumnDef="number">
          <th mat-header-cell *matHeaderCellDef>Complaint</th>
          <td mat-cell *matCellDef="let r">{{ r.complaintNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="date">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let r">{{ r.complaintDate | istDate: 'dd MMM yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="customer">
          <th mat-header-cell *matHeaderCellDef>Customer</th>
          <td mat-cell *matCellDef="let r">{{ r.customerName }}</td>
        </ng-container>
        <ng-container matColumnDef="product">
          <th mat-header-cell *matHeaderCellDef>Product</th>
          <td mat-cell *matCellDef="let r">{{ r.productCode }}</td>
        </ng-container>
        <ng-container matColumnDef="batch">
          <th mat-header-cell *matHeaderCellDef>FG batch</th>
          <td mat-cell *matCellDef="let r">{{ r.fgBatchNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="category">
          <th mat-header-cell *matHeaderCellDef>Category</th>
          <td mat-cell *matCellDef="let r">{{ r.category }}</td>
        </ng-container>
        <ng-container matColumnDef="capa">
          <th mat-header-cell *matHeaderCellDef>CAPA</th>
          <td mat-cell *matCellDef="let r">{{ r.capaNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns" class="clickable" (click)="open(row)"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No customer complaints found
          </td>
        </tr>
      </table>
    }
  `,
  styles: `
    .page-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }
    .filter-bar {
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
export class ComplaintListComponent implements OnInit {
  private api = inject(QualityPlusApi);
  private snack = inject(MatSnackBar);
  private router = inject(Router);

  statuses = COMPLAINT_STATUSES;
  columns = ['number', 'date', 'customer', 'product', 'batch', 'category', 'capa', 'status'];
  complaints = signal<ComplaintSummary[]>([]);
  loading = signal(false);

  statusCtrl = new FormControl<ComplaintStatus | null>(null);
  searchCtrl = new FormControl('', { nonNullable: true });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.listComplaints(this.statusCtrl.value, this.searchCtrl.value.trim() || null).subscribe({
      next: (page) => {
        this.complaints.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load customer complaints', 'Dismiss', { duration: 3000 });
      },
    });
  }

  open(c: ComplaintSummary): void {
    this.router.navigate(['/quality/complaints', c.id]);
  }
}
