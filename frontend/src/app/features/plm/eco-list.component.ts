import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
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
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ECO_STATUSES, EcoStatus, EcoSummaryDto, PlmApi } from './plm.api';

@Component({
  selector: 'app-eco-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
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
      <h2 class="mat-h2">Engineering Change Orders</h2>
      <a *appHasPermission="'ECO_CREATE'" mat-flat-button color="primary" routerLink="/plm/ecos/new">
        <mat-icon>add</mat-icon> New ECO
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
      <table mat-table [dataSource]="ecos()" class="mat-elevation-z1">
        <ng-container matColumnDef="ecoNumber">
          <th mat-header-cell *matHeaderCellDef>ECO</th>
          <td mat-cell *matCellDef="let r">{{ r.ecoNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="title">
          <th mat-header-cell *matHeaderCellDef>Title</th>
          <td mat-cell *matCellDef="let r">{{ r.title }}</td>
        </ng-container>
        <ng-container matColumnDef="category">
          <th mat-header-cell *matHeaderCellDef>Category</th>
          <td mat-cell *matCellDef="let r">{{ r.changeCategory }}</td>
        </ng-container>
        <ng-container matColumnDef="urgency">
          <th mat-header-cell *matHeaderCellDef>Urgency</th>
          <td mat-cell *matCellDef="let r">{{ r.urgency }}</td>
        </ng-container>
        <ng-container matColumnDef="items">
          <th mat-header-cell *matHeaderCellDef>Items</th>
          <td mat-cell *matCellDef="let r">{{ r.itemCount }}</td>
        </ng-container>
        <ng-container matColumnDef="effectivity">
          <th mat-header-cell *matHeaderCellDef>Effectivity</th>
          <td mat-cell *matCellDef="let r">{{ r.effectivityDate | date: 'dd MMM yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns" class="clickable" (click)="open(row)"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No engineering change orders found
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
export class EcoListComponent implements OnInit {
  private api = inject(PlmApi);
  private snack = inject(MatSnackBar);
  private router = inject(Router);

  statuses = ECO_STATUSES;
  columns = ['ecoNumber', 'title', 'category', 'urgency', 'items', 'effectivity', 'status'];
  ecos = signal<EcoSummaryDto[]>([]);
  loading = signal(false);

  statusCtrl = new FormControl<EcoStatus | null>(null);
  searchCtrl = new FormControl('', { nonNullable: true });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.listEcos(this.statusCtrl.value, this.searchCtrl.value.trim() || null).subscribe({
      next: (page) => {
        this.ecos.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load engineering change orders', 'Dismiss', { duration: 3000 });
      },
    });
  }

  open(eco: EcoSummaryDto): void {
    this.router.navigate(['/plm/ecos', eco.id]);
  }
}
