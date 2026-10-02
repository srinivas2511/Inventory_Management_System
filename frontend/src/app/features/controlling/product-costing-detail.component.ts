import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ControllingApi, CostEstimateDto } from './controlling.api';

@Component({
  selector: 'app-product-costing-detail',
  standalone: true,
  imports: [
    RouterLink,
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTableModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Cost Estimate {{ estimate()?.estimateNumber }}</h2>
      <a mat-stroked-button routerLink="/controlling/cost-estimates"><mat-icon>arrow_back</mat-icon> Back</a>
    </div>

    @if (estimate(); as e) {
      <p>
        {{ e.productCode }} {{ e.productName }} - BOM {{ e.bomNumber }} - lot of {{ e.costLotSize | number: '1.0-3' }}
        <app-status-badge [status]="e.status" />
      </p>

      <div class="breakdown">
        @for (part of breakdown(); track part.label) {
          <div class="breakdown-item">
            <span class="breakdown-label">{{ part.label }}</span>
            <span class="breakdown-value">{{ part.value | number: '1.2-2' }}</span>
          </div>
        }
        <div class="breakdown-item breakdown-total">
          <span class="breakdown-label">Standard cost (lot)</span>
          <span class="breakdown-value">{{ e.totalStandardCost | number: '1.2-2' }}</span>
        </div>
        <div class="breakdown-item breakdown-total">
          <span class="breakdown-label">Per unit</span>
          <span class="breakdown-value">{{ e.unitStandardCost | number: '1.2-4' }}</span>
        </div>
      </div>

      @if (e.allowedActions.includes('release')) {
        <button *appHasPermission="'COST_ESTIMATE_RELEASE'" mat-flat-button color="primary" (click)="release()">
          Release as standard cost
        </button>
      }

      <table mat-table [dataSource]="e.lines" class="mat-elevation-z1">
        <ng-container matColumnDef="element">
          <th mat-header-cell *matHeaderCellDef>Element</th>
          <td mat-cell *matCellDef="let l">{{ l.costElement }}</td>
        </ng-container>
        <ng-container matColumnDef="description">
          <th mat-header-cell *matHeaderCellDef>Item</th>
          <td mat-cell *matCellDef="let l">{{ l.itemDescription }}</td>
        </ng-container>
        <ng-container matColumnDef="quantity">
          <th mat-header-cell *matHeaderCellDef>Quantity</th>
          <td mat-cell *matCellDef="let l">{{ l.quantity | number: '1.0-6' }} {{ l.uom }}</td>
        </ng-container>
        <ng-container matColumnDef="rate">
          <th mat-header-cell *matHeaderCellDef>Rate</th>
          <td mat-cell *matCellDef="let l">{{ l.unitRate | number: '1.2-4' }}</td>
        </ng-container>
        <ng-container matColumnDef="amount">
          <th mat-header-cell *matHeaderCellDef>Amount</th>
          <td mat-cell *matCellDef="let l">{{ l.amount | number: '1.2-2' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    } @else {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    }
  `,
  styles: `
    .breakdown {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      margin: 16px 0;
    }
    .breakdown-item {
      display: flex;
      flex-direction: column;
      min-width: 140px;
    }
    .breakdown-label {
      font-size: 12px;
      opacity: 0.7;
    }
    .breakdown-value {
      font-size: 20px;
    }
    .breakdown-total .breakdown-value {
      font-weight: 600;
    }
  `,
})
export class ProductCostingDetailComponent implements OnInit {
  id = input.required<string>();

  private api = inject(ControllingApi);
  private snack = inject(MatSnackBar);

  columns = ['element', 'description', 'quantity', 'rate', 'amount'];
  estimate = signal<CostEstimateDto | null>(null);
  breakdown = computed(() => {
    const e = this.estimate();
    if (!e) return [];
    return [
      { label: 'Material', value: e.materialCost },
      { label: 'Labour', value: e.labourCost },
      { label: 'Machine', value: e.machineCost },
      { label: 'Overhead', value: e.overheadCost },
    ];
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.getEstimate(Number(this.id())).subscribe({
      next: (e) => this.estimate.set(e),
      error: () => this.snack.open('Failed to load cost estimate', 'Dismiss', { duration: 3000 }),
    });
  }

  release(): void {
    const e = this.estimate();
    if (!e) return;
    this.api.releaseEstimate(e.id).subscribe({
      next: (updated) => this.estimate.set(updated),
      error: (err) =>
        this.snack.open(err?.error?.detail ?? 'Could not release estimate', 'Dismiss', { duration: 4000 }),
    });
  }
}
