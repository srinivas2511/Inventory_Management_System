import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ControllingApi, CostEstimateDto } from './controlling.api';

@Component({
  selector: 'app-product-costing-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTableModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Product Costing</h2>
      <button
        *appHasPermission="'CONTROLLING_MANAGE'"
        mat-flat-button
        color="primary"
        (click)="creating.set(!creating())"
      >
        <mat-icon>add</mat-icon> New estimate
      </button>
    </div>

    @if (creating()) {
      <form class="edit-form" [formGroup]="form" (ngSubmit)="create()">
        <mat-form-field appearance="outline">
          <mat-label>Product ID</mat-label>
          <input matInput type="number" formControlName="productId" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>BOM ID (approved or active)</mat-label>
          <input matInput type="number" formControlName="bomId" />
        </mat-form-field>
        <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">Calculate estimate</button>
      </form>
    }

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="estimates()" class="mat-elevation-z1">
        <ng-container matColumnDef="estimateNumber">
          <th mat-header-cell *matHeaderCellDef>Estimate</th>
          <td mat-cell *matCellDef="let r">
            <a [routerLink]="['/controlling/cost-estimates', r.id]">{{ r.estimateNumber }}</a>
          </td>
        </ng-container>
        <ng-container matColumnDef="product">
          <th mat-header-cell *matHeaderCellDef>Product</th>
          <td mat-cell *matCellDef="let r">{{ r.productCode }} {{ r.productName }}</td>
        </ng-container>
        <ng-container matColumnDef="bom">
          <th mat-header-cell *matHeaderCellDef>BOM</th>
          <td mat-cell *matCellDef="let r">{{ r.bomNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="total">
          <th mat-header-cell *matHeaderCellDef>Standard cost (lot)</th>
          <td mat-cell *matCellDef="let r">{{ r.totalStandardCost | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="unit">
          <th mat-header-cell *matHeaderCellDef>Per unit</th>
          <td mat-cell *matCellDef="let r">{{ r.unitStandardCost | number: '1.2-4' }}</td>
        </ng-container>
        <ng-container matColumnDef="validFrom">
          <th mat-header-cell *matHeaderCellDef>Valid from</th>
          <td mat-cell *matCellDef="let r">{{ r.validFrom || '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            @if (r.allowedActions.includes('release')) {
              <button *appHasPermission="'COST_ESTIMATE_RELEASE'" mat-flat-button color="primary" (click)="release(r)">
                Release
              </button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No cost estimates found
          </td>
        </tr>
      </table>
    }
  `,
  styles: `
    .edit-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
  `,
})
export class ProductCostingListComponent implements OnInit {
  private api = inject(ControllingApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  columns = ['estimateNumber', 'product', 'bom', 'total', 'unit', 'validFrom', 'status', 'actions'];
  estimates = signal<CostEstimateDto[]>([]);
  loading = signal(false);
  creating = signal(false);

  form = this.fb.group({
    productId: this.fb.control<number | null>(null, Validators.required),
    bomId: this.fb.control<number | null>(null, Validators.required),
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getEstimates().subscribe({
      next: (page) => {
        this.estimates.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load cost estimates', 'Dismiss', { duration: 3000 });
      },
    });
  }

  create(): void {
    const { productId, bomId } = this.form.value;
    if (!productId || !bomId) return;
    this.api.createEstimate({ productId, bomId, costCentreId: null }).subscribe({
      next: () => {
        this.creating.set(false);
        this.form.reset();
        this.load();
      },
      error: (e) => this.snack.open(e?.error?.detail ?? 'Could not calculate estimate', 'Dismiss', { duration: 4000 }),
    });
  }

  release(estimate: CostEstimateDto): void {
    this.api.releaseEstimate(estimate.id).subscribe({
      next: () => this.load(),
      error: (e) => this.snack.open(e?.error?.detail ?? 'Could not release estimate', 'Dismiss', { duration: 4000 }),
    });
  }
}
