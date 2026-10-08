import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { problemMessage } from '../../core/models/problem.model';
import { PlantFieldComponent } from '../../shared/components/plant-field.component';
import { ActiveBom, LookupApi, LookupItem } from '../../shared/lookup.api';

/** Mirrors CreateProductionOrderRequest on the backend. */
export interface CreateProductionOrderRequest {
  productId: number;
  bomId: number;
  plannedQty: number;
  startDate: string | null;
  expectedCompletionDate: string | null;
  productionLine: string | null;
  primaryMachineId: null;
  supervisorId: null;
  salesOrderId: null;
  priority: number;
  remarks: string | null;
  plantId?: number | null;
}

@Component({
  selector: 'app-production-order-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    PlantFieldComponent,
  ],
  template: `
    <h2 mat-dialog-title>New production order</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="po-prod-form" (ngSubmit)="save()" novalidate class="form-grid">
        <app-plant-field class="full-span" [control]="form.controls.plantId" />
        <mat-form-field appearance="outline" class="full-span">
          <mat-label>Product</mat-label>
          <mat-select formControlName="productId" (selectionChange)="productChosen($event.value)">
            @for (p of products(); track p.id) {
              <mat-option [value]="p.id">{{ p.code }} · {{ p.name }}</mat-option>
            }
          </mat-select>
          @if (form.controls.productId.hasError('required') && form.controls.productId.touched) {
            <mat-error>Choose a product</mat-error>
          }
        </mat-form-field>
        @if (bom()) {
          <p class="bom-note mat-body-2 full-span">
            BOM {{ bom()!.bomNumber }} revision {{ bom()!.revision }}, base quantity {{ bom()!.baseQuantity }}
          </p>
        } @else if (bomChecked()) {
          <p class="form-error mat-body-2 full-span" role="alert">
            This product has no active BOM. Create and activate one before ordering production.
          </p>
        }
        <mat-form-field appearance="outline">
          <mat-label>Planned quantity</mat-label>
          <input matInput type="number" step="0.001" formControlName="plannedQty" />
          @if (form.controls.plannedQty.invalid && form.controls.plannedQty.touched) {
            <mat-error>Must be above 0</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Priority</mat-label>
          <mat-select formControlName="priority">
            <mat-option [value]="1">1 · Urgent</mat-option>
            <mat-option [value]="2">2 · High</mat-option>
            <mat-option [value]="3">3 · Normal</mat-option>
            <mat-option [value]="4">4 · Low</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Start date</mat-label>
          <input matInput type="date" formControlName="startDate" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Expected completion</mat-label>
          <input matInput type="date" formControlName="expectedCompletionDate" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="full-span">
          <mat-label>Production line</mat-label>
          <input matInput formControlName="productionLine" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="full-span">
          <mat-label>Remarks</mat-label>
          <textarea matInput formControlName="remarks" rows="2"></textarea>
        </mat-form-field>
        @if (errorMessage()) {
          <p class="form-error mat-body-2 full-span" role="alert">{{ errorMessage() }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" form="po-prod-form" type="submit" [disabled]="saving() || !bom()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          Create order
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .form-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 0 12px;
        padding: 8px 0;
      }
      .form-grid mat-form-field {
        width: 100%;
      }
      .full-span {
        grid-column: 1/-1;
      }
      .form-error {
        color: var(--mat-warn-color, #f44336);
      }
    `,
  ],
})
export class ProductionOrderFormComponent implements OnInit {
  protected readonly products = signal<LookupItem[]>([]);
  protected readonly bom = signal<ActiveBom | null>(null);
  protected readonly bomChecked = signal(false);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');

  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly lookups = inject(LookupApi);
  private readonly dialogRef = inject(MatDialogRef<ProductionOrderFormComponent>);

  protected readonly form = this.fb.nonNullable.group({
    productId: this.fb.control<number | null>(null, Validators.required),
    plannedQty: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.001)]),
    priority: [3],
    startDate: [new Date().toISOString().slice(0, 10)],
    expectedCompletionDate: [''],
    productionLine: [''],
    remarks: [''],
    plantId: this.fb.control<number | null>(null),
  });

  ngOnInit(): void {
    this.lookups.products().subscribe((p) => this.products.set(p));
  }

  productChosen(productId: number): void {
    this.bom.set(null);
    this.bomChecked.set(false);
    this.lookups.activeBom(productId).subscribe((b) => {
      this.bom.set(b);
      this.bomChecked.set(true);
    });
  }

  save(): void {
    if (this.form.invalid || !this.bom()) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const request: CreateProductionOrderRequest = {
      productId: raw.productId as number,
      bomId: this.bom()!.id,
      plannedQty: Number(raw.plannedQty),
      startDate: raw.startDate || null,
      expectedCompletionDate: raw.expectedCompletionDate || null,
      productionLine: raw.productionLine || null,
      primaryMachineId: null,
      supervisorId: null,
      salesOrderId: null,
      priority: raw.priority,
      remarks: raw.remarks || null,
      ...(raw.plantId ? { plantId: raw.plantId } : {}),
    };
    this.saving.set(true);
    this.errorMessage.set('');
    this.http.post('/api/production-orders', request).subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The production order could not be created.'));
      },
    });
  }
}
