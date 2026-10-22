import { Component, OnInit, inject, signal } from '@angular/core';

import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { SessionStore } from '../../core/auth/session.store';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import {
  LIFECYCLE_STATUSES,
  PlmApi,
  ProductLifecycleDto,
  ProductLifecycleRowDto,
  ProductLifecycleStatus,
} from './plm.api';

@Component({
  selector: 'app-lifecycle-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
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
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Product Lifecycle</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Lifecycle status</mat-label>
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
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1">
        <ng-container matColumnDef="code">
          <th mat-header-cell *matHeaderCellDef>Product</th>
          <td mat-cell *matCellDef="let r">{{ r.productCode }}</td>
        </ng-container>
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Name</th>
          <td mat-cell *matCellDef="let r">{{ r.productName }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Lifecycle</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.lifecycleStatus" /></td>
        </ng-container>
        <ng-container matColumnDef="next">
          <th mat-header-cell *matHeaderCellDef>Next step</th>
          <td mat-cell *matCellDef="let r">{{ r.nextStatus || '-' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns" class="clickable" (click)="select(row)"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No products found
          </td>
        </tr>
      </table>
    }

    @if (selected(); as s) {
      <h3 class="mat-h3">{{ s.productCode }} - {{ s.productName }}</h3>
      @if (canManage() && s.nextStatus) {
        <div class="actions">
          <mat-form-field appearance="outline" class="wide">
            <mat-label>Reason for moving to {{ s.nextStatus }}</mat-label>
            <input matInput maxlength="500" [formControl]="reasonCtrl" />
          </mat-form-field>
          <button mat-flat-button color="primary" [disabled]="!reasonCtrl.value.trim() || busy()" (click)="move()">
            <mat-icon>arrow_forward</mat-icon> Move to {{ s.nextStatus }}
          </button>
        </div>
      }
      <table mat-table [dataSource]="s.history" class="mat-elevation-z1">
        <ng-container matColumnDef="when">
          <th mat-header-cell *matHeaderCellDef>When</th>
          <td mat-cell *matCellDef="let h">{{ h.changedAt | istDate: 'dd MMM yyyy HH:mm' }}</td>
        </ng-container>
        <ng-container matColumnDef="change">
          <th mat-header-cell *matHeaderCellDef>Change</th>
          <td mat-cell *matCellDef="let h">
            @if (h.fromStatus === h.toStatus) {
              {{ h.toStatus }} (no change)
            } @else {
              {{ h.fromStatus }} &rarr; {{ h.toStatus }}
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="reason">
          <th mat-header-cell *matHeaderCellDef>Reason</th>
          <td mat-cell *matCellDef="let h">{{ h.reason }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="historyColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: historyColumns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="historyColumns.length" style="text-align:center;padding:1rem">
            No lifecycle history yet
          </td>
        </tr>
      </table>
    }
  `,
  styles: `
    .filter-bar,
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .wide {
      min-width: 320px;
    }
    .clickable {
      cursor: pointer;
    }
  `,
})
export class LifecycleListComponent implements OnInit {
  private api = inject(PlmApi);
  private snack = inject(MatSnackBar);
  private session = inject(SessionStore);

  statuses = LIFECYCLE_STATUSES;
  columns = ['code', 'name', 'status', 'next'];
  historyColumns = ['when', 'change', 'reason'];
  rows = signal<ProductLifecycleRowDto[]>([]);
  selected = signal<ProductLifecycleDto | null>(null);
  loading = signal(false);
  busy = signal(false);

  statusCtrl = new FormControl<ProductLifecycleStatus | null>(null);
  searchCtrl = new FormControl('', { nonNullable: true });
  reasonCtrl = new FormControl('', { nonNullable: true });

  canManage(): boolean {
    return this.session.hasPermission('LIFECYCLE_MANAGE');
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.listLifecycle(this.statusCtrl.value, this.searchCtrl.value.trim() || null).subscribe({
      next: (page) => {
        this.rows.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load product lifecycle', 'Dismiss', { duration: 3000 });
      },
    });
  }

  select(row: ProductLifecycleRowDto): void {
    this.reasonCtrl.reset('');
    this.api.getLifecycle(row.productId).subscribe({
      next: (dto) => this.selected.set(dto),
      error: () => this.snack.open('Failed to load lifecycle history', 'Dismiss', { duration: 3000 }),
    });
  }

  move(): void {
    const s = this.selected();
    const reason = this.reasonCtrl.value.trim();
    if (!s || !s.nextStatus || !reason) return;
    this.busy.set(true);
    this.api.transitionLifecycle(s.productId, s.nextStatus, reason).subscribe({
      next: (dto) => {
        this.busy.set(false);
        this.selected.set(dto);
        this.reasonCtrl.reset('');
        this.load();
      },
      error: (err) => {
        this.busy.set(false);
        this.snack.open(err?.error?.detail ?? 'Could not change the lifecycle state', 'Dismiss', { duration: 5000 });
      },
    });
  }
}
