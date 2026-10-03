import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { DemandForecastDto, MrpApi } from './mrp.api';

@Component({
  selector: 'app-demand-forecast-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTableModule,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Demand Forecasts</h2>
    </div>
    <p class="hint">
      A forecast adds planned demand for a make-to-stock product on its target date. Make-to-order products ignore
      forecasts.
    </p>

    <form *appHasPermission="'DEMAND_PLAN'" class="edit-form" [formGroup]="form" (ngSubmit)="add()">
      <mat-form-field appearance="outline">
        <mat-label>Product ID</mat-label>
        <input matInput type="number" formControlName="productId" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Target date</mat-label>
        <input matInput type="date" formControlName="targetDate" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Quantity</mat-label>
        <input matInput type="number" min="0" formControlName="quantity" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Notes</mat-label>
        <input matInput maxlength="300" formControlName="notes" />
      </mat-form-field>
      <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">
        <mat-icon>add</mat-icon> Add forecast
      </button>
    </form>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="forecasts()" class="mat-elevation-z1">
        <ng-container matColumnDef="product">
          <th mat-header-cell *matHeaderCellDef>Product</th>
          <td mat-cell *matCellDef="let r">{{ r.productCode }} {{ r.productName }}</td>
        </ng-container>
        <ng-container matColumnDef="targetDate">
          <th mat-header-cell *matHeaderCellDef>Target date</th>
          <td mat-cell *matCellDef="let r">{{ r.targetDate | date: 'dd MMM yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="quantity">
          <th mat-header-cell *matHeaderCellDef>Quantity</th>
          <td mat-cell *matCellDef="let r">{{ r.quantity | number: '1.0-3' }}</td>
        </ng-container>
        <ng-container matColumnDef="notes">
          <th mat-header-cell *matHeaderCellDef>Notes</th>
          <td mat-cell *matCellDef="let r">{{ r.notes }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            <button *appHasPermission="'DEMAND_PLAN'" mat-button color="warn" (click)="remove(r)">Delete</button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No forecasts entered
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
    .hint {
      opacity: 0.7;
      margin: 0 0 8px;
    }
  `,
})
export class DemandForecastListComponent implements OnInit {
  private api = inject(MrpApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  columns = ['product', 'targetDate', 'quantity', 'notes', 'actions'];
  forecasts = signal<DemandForecastDto[]>([]);
  loading = signal(false);

  form = this.fb.nonNullable.group({
    productId: this.fb.control<number | null>(null, [Validators.required]),
    targetDate: ['', [Validators.required]],
    quantity: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.001)]),
    notes: [''],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getForecasts().subscribe({
      next: (page) => {
        this.forecasts.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load forecasts', 'Dismiss', { duration: 3000 });
      },
    });
  }

  add(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.api
      .createForecast({
        productId: v.productId as number,
        targetDate: v.targetDate,
        quantity: v.quantity as number,
        notes: v.notes || null,
      })
      .subscribe({
        next: () => {
          this.form.reset({ productId: null, targetDate: '', quantity: null, notes: '' });
          this.load();
        },
        error: (err) => this.snack.open(err?.error?.detail ?? 'Could not save forecast', 'Dismiss', { duration: 5000 }),
      });
  }

  remove(f: DemandForecastDto): void {
    this.api.deleteForecast(f.id).subscribe({
      next: () => this.load(),
      error: () => this.snack.open('Could not delete forecast', 'Dismiss', { duration: 3000 }),
    });
  }
}
