import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { LotSizeRule, MrpApi, PlanningParamDto, SavePlanningParamRequest } from './mrp.api';

@Component({
  selector: 'app-planning-params',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DecimalPipe,
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
      <h2 class="mat-h2">Planning Parameters</h2>
    </div>
    <p class="hint">
      Items without parameters plan as make-to-stock with exact lot sizing and the default lead times (see MRP
      settings).
    </p>

    <form *appHasPermission="'DEMAND_PLAN'" class="edit-form" [formGroup]="form" (ngSubmit)="save()">
      <mat-form-field appearance="outline">
        <mat-label>Item type</mat-label>
        <mat-select formControlName="itemType">
          <mat-option value="PRODUCT">Product</mat-option>
          <mat-option value="MATERIAL">Material</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Item ID</mat-label>
        <input matInput type="number" formControlName="itemId" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Strategy</mat-label>
        <mat-select formControlName="planningStrategy">
          <mat-option value="MTS">Make to stock</mat-option>
          <mat-option value="MTO" [disabled]="form.value.itemType === 'MATERIAL'">Make to order</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Lot sizing</mat-label>
        <mat-select formControlName="lotSizeRule">
          <mat-option value="EXACT">Exact</mat-option>
          <mat-option value="FIXED">Fixed lot</mat-option>
          <mat-option value="MIN_MAX">Min / max</mat-option>
          <mat-option value="EOQ">Economic order qty</mat-option>
        </mat-select>
      </mat-form-field>
      @if (form.value.lotSizeRule === 'FIXED') {
        <mat-form-field appearance="outline">
          <mat-label>Fixed lot qty</mat-label>
          <input matInput type="number" min="0" formControlName="fixedLotQty" />
        </mat-form-field>
      }
      @if (form.value.lotSizeRule === 'MIN_MAX') {
        <mat-form-field appearance="outline">
          <mat-label>Min lot qty</mat-label>
          <input matInput type="number" min="0" formControlName="minLotQty" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Max lot qty</mat-label>
          <input matInput type="number" min="0" formControlName="maxLotQty" />
        </mat-form-field>
      }
      @if (form.value.lotSizeRule === 'EOQ') {
        <mat-form-field appearance="outline">
          <mat-label>Annual demand</mat-label>
          <input matInput type="number" min="0" formControlName="eoqAnnualDemand" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Order cost</mat-label>
          <input matInput type="number" min="0" formControlName="eoqOrderCost" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Holding cost / unit</mat-label>
          <input matInput type="number" min="0" formControlName="eoqHoldingCost" />
        </mat-form-field>
      }
      <mat-form-field appearance="outline">
        <mat-label>Lead time (days)</mat-label>
        <input matInput type="number" min="0" formControlName="leadTimeDays" />
      </mat-form-field>
      <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">Save</button>
    </form>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="params()" class="mat-elevation-z1">
        <ng-container matColumnDef="item">
          <th mat-header-cell *matHeaderCellDef>Item</th>
          <td mat-cell *matCellDef="let r">
            {{ r.itemType === 'PRODUCT' ? 'Product' : 'Material' }} {{ r.itemCode }} {{ r.itemName }}
          </td>
        </ng-container>
        <ng-container matColumnDef="strategy">
          <th mat-header-cell *matHeaderCellDef>Strategy</th>
          <td mat-cell *matCellDef="let r">{{ r.planningStrategy }}</td>
        </ng-container>
        <ng-container matColumnDef="lot">
          <th mat-header-cell *matHeaderCellDef>Lot sizing</th>
          <td mat-cell *matCellDef="let r">{{ describeLot(r) }}</td>
        </ng-container>
        <ng-container matColumnDef="lead">
          <th mat-header-cell *matHeaderCellDef>Lead time</th>
          <td mat-cell *matCellDef="let r">{{ r.leadTimeDays === null ? 'default' : r.leadTimeDays + ' days' }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            <button *appHasPermission="'DEMAND_PLAN'" mat-button color="warn" (click)="remove(r)">Remove</button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No planning parameters defined
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
export class PlanningParamsComponent implements OnInit {
  private api = inject(MrpApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);
  private decimal = new DecimalPipe('en-US');

  columns = ['item', 'strategy', 'lot', 'lead', 'actions'];
  params = signal<PlanningParamDto[]>([]);
  loading = signal(false);

  form = this.fb.nonNullable.group({
    itemType: ['PRODUCT' as 'PRODUCT' | 'MATERIAL'],
    itemId: this.fb.control<number | null>(null, [Validators.required]),
    planningStrategy: ['MTS' as 'MTS' | 'MTO'],
    lotSizeRule: ['EXACT' as LotSizeRule],
    fixedLotQty: this.fb.control<number | null>(null),
    minLotQty: this.fb.control<number | null>(null),
    maxLotQty: this.fb.control<number | null>(null),
    eoqAnnualDemand: this.fb.control<number | null>(null),
    eoqOrderCost: this.fb.control<number | null>(null),
    eoqHoldingCost: this.fb.control<number | null>(null),
    leadTimeDays: this.fb.control<number | null>(null),
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getPlanningParams().subscribe({
      next: (list) => {
        this.params.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load planning parameters', 'Dismiss', { duration: 3000 });
      },
    });
  }

  describeLot(p: PlanningParamDto): string {
    const n = (v: number | null) => this.decimal.transform(v ?? 0, '1.0-3');
    switch (p.lotSizeRule) {
      case 'FIXED':
        return `Fixed lot ${n(p.fixedLotQty)}`;
      case 'MIN_MAX':
        return `Min ${n(p.minLotQty)} / max ${p.maxLotQty === null ? '-' : n(p.maxLotQty)}`;
      case 'EOQ':
        return `EOQ (D ${n(p.eoqAnnualDemand)}, S ${n(p.eoqOrderCost)}, H ${n(p.eoqHoldingCost)})`;
      default:
        return 'Exact';
    }
  }

  save(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    const req: SavePlanningParamRequest = {
      materialId: v.itemType === 'MATERIAL' ? v.itemId : null,
      productId: v.itemType === 'PRODUCT' ? v.itemId : null,
      planningStrategy: v.planningStrategy,
      lotSizeRule: v.lotSizeRule,
      fixedLotQty: v.lotSizeRule === 'FIXED' ? v.fixedLotQty : null,
      minLotQty: v.lotSizeRule === 'MIN_MAX' ? v.minLotQty : null,
      maxLotQty: v.lotSizeRule === 'MIN_MAX' ? v.maxLotQty : null,
      eoqAnnualDemand: v.lotSizeRule === 'EOQ' ? v.eoqAnnualDemand : null,
      eoqOrderCost: v.lotSizeRule === 'EOQ' ? v.eoqOrderCost : null,
      eoqHoldingCost: v.lotSizeRule === 'EOQ' ? v.eoqHoldingCost : null,
      leadTimeDays: v.leadTimeDays,
    };
    this.api.savePlanningParam(req).subscribe({
      next: () => this.load(),
      error: (err) =>
        this.snack.open(err?.error?.detail ?? 'Could not save planning parameters', 'Dismiss', { duration: 5000 }),
    });
  }

  remove(p: PlanningParamDto): void {
    this.api.deletePlanningParam(p.id).subscribe({
      next: () => this.load(),
      error: () => this.snack.open('Could not remove planning parameters', 'Dismiss', { duration: 3000 }),
    });
  }
}
