import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
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
import { ProductApi, ProductDto } from '../master/product/product.api';
import { FAI_STATUSES, FaiStatus, FaiSummary, QualityPlusApi } from './quality-plus.api';

@Component({
  selector: 'app-fai-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
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
      <h2 class="mat-h2">First Article Inspections</h2>
      <button *appHasPermission="'QUALITY_INSPECT'" mat-flat-button color="primary" (click)="toggleCreate()">
        <mat-icon>add</mat-icon> Require FAI
      </button>
    </div>
    <p class="hint">
      An FAI is required automatically when an ECO is implemented and when a product is first approved.
    </p>

    @if (creating()) {
      <div class="filter-bar">
        <mat-form-field appearance="outline">
          <mat-label>Product</mat-label>
          <mat-select [formControl]="productCtrl">
            @for (p of products(); track p.id) {
              <mat-option [value]="p.id">{{ p.code }} - {{ p.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <button mat-flat-button color="primary" (click)="create()" [disabled]="productCtrl.invalid || saving()">
          Require FAI
        </button>
      </div>
    }

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
      <table mat-table [dataSource]="fais()" class="mat-elevation-z1">
        <ng-container matColumnDef="number">
          <th mat-header-cell *matHeaderCellDef>FAI</th>
          <td mat-cell *matCellDef="let r">{{ r.faiNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="product">
          <th mat-header-cell *matHeaderCellDef>Product</th>
          <td mat-cell *matCellDef="let r">{{ r.productCode }} {{ r.productName }}</td>
        </ng-container>
        <ng-container matColumnDef="trigger">
          <th mat-header-cell *matHeaderCellDef>Trigger</th>
          <td mat-cell *matCellDef="let r">{{ r.triggerType }} {{ r.triggerReference }}</td>
        </ng-container>
        <ng-container matColumnDef="created">
          <th mat-header-cell *matHeaderCellDef>Required</th>
          <td mat-cell *matCellDef="let r">{{ r.createdAt | date: 'dd MMM yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="completed">
          <th mat-header-cell *matHeaderCellDef>Completed</th>
          <td mat-cell *matCellDef="let r">{{ r.completedAt | date: 'dd MMM yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns" class="clickable" (click)="open(row)"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No first article inspections found
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
    .hint {
      margin: 0 0 8px;
      opacity: 0.75;
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
export class FaiListComponent implements OnInit {
  private api = inject(QualityPlusApi);
  private productApi = inject(ProductApi);
  private snack = inject(MatSnackBar);
  private router = inject(Router);

  statuses = FAI_STATUSES;
  columns = ['number', 'product', 'trigger', 'created', 'completed', 'status'];
  fais = signal<FaiSummary[]>([]);
  products = signal<ProductDto[]>([]);
  loading = signal(false);
  creating = signal(false);
  saving = signal(false);

  statusCtrl = new FormControl<FaiStatus | null>(null);
  searchCtrl = new FormControl('', { nonNullable: true });
  productCtrl = new FormControl<number | null>(null, Validators.required);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.listFais(this.statusCtrl.value, this.searchCtrl.value.trim() || null).subscribe({
      next: (page) => {
        this.fais.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load first article inspections', 'Dismiss', { duration: 3000 });
      },
    });
  }

  toggleCreate(): void {
    this.creating.update((v) => !v);
    if (this.creating() && this.products().length === 0) {
      this.productApi.search('', null, null, 0, 200).subscribe({
        next: (page) => this.products.set(page.content),
        error: () => this.products.set([]),
      });
    }
  }

  create(): void {
    const productId = this.productCtrl.value;
    if (!productId) return;
    this.saving.set(true);
    this.api.createFai(productId).subscribe({
      next: (fai) => {
        this.saving.set(false);
        this.router.navigate(['/quality/fai', fai.id]);
      },
      error: (err) => {
        this.saving.set(false);
        this.snack.open(err?.error?.detail ?? 'Failed to require the FAI', 'Dismiss', { duration: 5000 });
      },
    });
  }

  open(fai: FaiSummary): void {
    this.router.navigate(['/quality/fai', fai.id]);
  }
}
