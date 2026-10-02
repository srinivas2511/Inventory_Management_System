import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ControllingApi, VarianceDto } from './controlling.api';

@Component({
  selector: 'app-variance-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
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
      <h2 class="mat-h2">Production Variance</h2>
      <div *appHasPermission="'CONTROLLING_MANAGE'" class="calc">
        <mat-form-field appearance="outline">
          <mat-label>Production order ID</mat-label>
          <input matInput type="number" [formControl]="orderCtrl" />
        </mat-form-field>
        <button mat-flat-button color="primary" [disabled]="orderCtrl.invalid" (click)="calculate()">
          <mat-icon>calculate</mat-icon> Calculate and post
        </button>
      </div>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="variances()" class="mat-elevation-z1">
        <ng-container matColumnDef="order">
          <th mat-header-cell *matHeaderCellDef>Order</th>
          <td mat-cell *matCellDef="let r">{{ r.orderNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="product">
          <th mat-header-cell *matHeaderCellDef>Product</th>
          <td mat-cell *matCellDef="let r">{{ r.productCode }} {{ r.productName }}</td>
        </ng-container>
        <ng-container matColumnDef="standard">
          <th mat-header-cell *matHeaderCellDef>Standard</th>
          <td mat-cell *matCellDef="let r">{{ r.standardCost | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="actual">
          <th mat-header-cell *matHeaderCellDef>Actual</th>
          <td mat-cell *matCellDef="let r">{{ r.actualCost | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="price">
          <th mat-header-cell *matHeaderCellDef>Price</th>
          <td mat-cell *matCellDef="let r">{{ r.priceVariance | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="quantity">
          <th mat-header-cell *matHeaderCellDef>Quantity</th>
          <td mat-cell *matCellDef="let r">{{ r.quantityVariance | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="efficiency">
          <th mat-header-cell *matHeaderCellDef>Efficiency</th>
          <td mat-cell *matCellDef="let r">{{ r.efficiencyVariance | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="total">
          <th mat-header-cell *matHeaderCellDef>Total variance</th>
          <td
            mat-cell
            *matCellDef="let r"
            [class.unfavourable]="r.totalVariance > 0"
            [class.favourable]="r.totalVariance < 0"
          >
            {{ r.totalVariance | number: '1.2-2' }}
            @if (r.variancePct !== null) {
              ({{ r.variancePct | number: '1.1-1' }}%)
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No variances calculated yet
          </td>
        </tr>
      </table>
    }
  `,
  styles: `
    .calc {
      display: flex;
      gap: 12px;
      align-items: center;
    }
    .unfavourable {
      color: #b3261e;
    }
    .favourable {
      color: #1b7a3d;
    }
  `,
})
export class VarianceListComponent implements OnInit {
  private api = inject(ControllingApi);
  private snack = inject(MatSnackBar);

  columns = ['order', 'product', 'standard', 'actual', 'price', 'quantity', 'efficiency', 'total', 'status'];
  variances = signal<VarianceDto[]>([]);
  loading = signal(false);
  orderCtrl = new FormControl<number | null>(null, [Validators.required, Validators.min(1)]);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getVariances().subscribe({
      next: (page) => {
        this.variances.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load variances', 'Dismiss', { duration: 3000 });
      },
    });
  }

  calculate(): void {
    const id = this.orderCtrl.value;
    if (!id) return;
    this.api.calculateVariance(id).subscribe({
      next: () => {
        this.orderCtrl.reset();
        this.load();
      },
      error: (e) => this.snack.open(e?.error?.detail ?? 'Could not calculate variance', 'Dismiss', { duration: 4000 }),
    });
  }
}
