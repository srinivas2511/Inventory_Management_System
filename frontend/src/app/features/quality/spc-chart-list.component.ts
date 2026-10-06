import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ProductApi, ProductDto } from '../master/product/product.api';
import { QualityPlusApi, SPC_CHART_TYPES, SpcChartSummary, SpcChartType } from './quality-plus.api';

@Component({
  selector: 'app-spc-chart-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">SPC Control Charts</h2>
      <button *appHasPermission="'SPC_MANAGE'" mat-flat-button color="primary" (click)="toggleCreate()">
        <mat-icon>add</mat-icon> New chart
      </button>
    </div>

    @if (creating()) {
      <form [formGroup]="form" (ngSubmit)="create()" class="create-form">
        <mat-form-field appearance="outline">
          <mat-label>Product</mat-label>
          <mat-select formControlName="productId">
            @for (p of products(); track p.id) {
              <mat-option [value]="p.id">{{ p.productCode }} - {{ p.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Parameter</mat-label>
          <input matInput formControlName="parameterName" maxlength="60" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Chart type</mat-label>
          <mat-select formControlName="chartType">
            @for (t of chartTypes; track t) {
              <mat-option [value]="t">{{ t }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        @if (form.controls.chartType.value === 'XBAR_R') {
          <mat-form-field appearance="outline">
            <mat-label>Subgroup size (2-10)</mat-label>
            <input matInput type="number" formControlName="subgroupSize" min="2" max="10" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Upper spec limit</mat-label>
            <input matInput type="number" formControlName="usl" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Lower spec limit</mat-label>
            <input matInput type="number" formControlName="lsl" />
          </mat-form-field>
        }
        <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || saving()">Create</button>
      </form>
    }

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Search</mat-label>
        <input matInput [formControl]="searchCtrl" (keyup.enter)="load()" />
      </mat-form-field>
      <button mat-stroked-button (click)="load()"><mat-icon>search</mat-icon> Search</button>
    </div>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="charts()" class="mat-elevation-z1">
        <ng-container matColumnDef="product">
          <th mat-header-cell *matHeaderCellDef>Product</th>
          <td mat-cell *matCellDef="let r">{{ r.productCode }} {{ r.productName }}</td>
        </ng-container>
        <ng-container matColumnDef="parameter">
          <th mat-header-cell *matHeaderCellDef>Parameter</th>
          <td mat-cell *matCellDef="let r">{{ r.parameterName }}</td>
        </ng-container>
        <ng-container matColumnDef="type">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let r">{{ r.chartType }}</td>
        </ng-container>
        <ng-container matColumnDef="points">
          <th mat-header-cell *matHeaderCellDef>Points</th>
          <td mat-cell *matCellDef="let r">{{ r.pointCount }}</td>
        </ng-container>
        <ng-container matColumnDef="ooc">
          <th mat-header-cell *matHeaderCellDef>Out of control</th>
          <td mat-cell *matCellDef="let r" [class.ooc]="r.outOfControlCount > 0">{{ r.outOfControlCount }}</td>
        </ng-container>
        <ng-container matColumnDef="limits">
          <th mat-header-cell *matHeaderCellDef>Limits</th>
          <td mat-cell *matCellDef="let r">{{ r.limitsEstablished ? 'Established' : 'Collecting data' }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns" class="clickable" (click)="open(row)"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No control charts found
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
    .create-form,
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
    .ooc {
      color: #c62828;
      font-weight: 600;
    }
  `,
})
export class SpcChartListComponent implements OnInit {
  private api = inject(QualityPlusApi);
  private productApi = inject(ProductApi);
  private snack = inject(MatSnackBar);
  private router = inject(Router);

  chartTypes = SPC_CHART_TYPES;
  columns = ['product', 'parameter', 'type', 'points', 'ooc', 'limits'];
  charts = signal<SpcChartSummary[]>([]);
  products = signal<ProductDto[]>([]);
  loading = signal(false);
  creating = signal(false);
  saving = signal(false);

  searchCtrl = new FormControl('', { nonNullable: true });
  form = new FormGroup({
    productId: new FormControl<number | null>(null, Validators.required),
    parameterName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(60)],
    }),
    chartType: new FormControl<SpcChartType>('XBAR_R', { nonNullable: true }),
    subgroupSize: new FormControl<number | null>(5),
    usl: new FormControl<number | null>(null),
    lsl: new FormControl<number | null>(null),
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.listCharts(null, this.searchCtrl.value.trim() || null).subscribe({
      next: (rows) => {
        this.charts.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load control charts', 'Dismiss', { duration: 3000 });
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
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.api
      .createChart({
        productId: v.productId as number,
        parameterName: v.parameterName.trim(),
        chartType: v.chartType,
        subgroupSize: v.chartType === 'XBAR_R' ? v.subgroupSize : null,
        usl: v.chartType === 'XBAR_R' ? v.usl : null,
        lsl: v.chartType === 'XBAR_R' ? v.lsl : null,
      })
      .subscribe({
        next: (chart) => {
          this.saving.set(false);
          this.router.navigate(['/quality/spc', chart.summary.id]);
        },
        error: (err) => {
          this.saving.set(false);
          this.snack.open(err?.error?.detail ?? 'Failed to create the chart', 'Dismiss', { duration: 5000 });
        },
      });
  }

  open(chart: SpcChartSummary): void {
    this.router.navigate(['/quality/spc', chart.id]);
  }
}
