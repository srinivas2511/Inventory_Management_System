import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { problemMessage } from '../../core/models/problem.model';
import { PlantFieldComponent } from '../../shared/components/plant-field.component';
import { LookupApi, LookupItem, LookupOption } from '../../shared/lookup.api';

/** Mirrors CreatePoRequest on the backend. */
export interface CreatePoRequest {
  supplierId: number;
  poDate: string;
  expectedDeliveryDate: string | null;
  paymentTerms: string | null;
  remarks: string | null;
  requisitionId: number | null;
  plantId?: number | null;
  items: { materialId: number; orderedQty: number; rate: number; taxPct: number | null }[];
}

function isoDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

@Component({
  selector: 'app-purchase-order-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    PlantFieldComponent,
  ],
  template: `
    <h2 mat-dialog-title>New purchase order</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="po-form" (ngSubmit)="save()" novalidate>
        <div class="head-grid">
          <mat-form-field appearance="outline">
            <mat-label>Supplier</mat-label>
            <mat-select formControlName="supplierId">
              @for (s of suppliers(); track s.id) {
                <mat-option [value]="s.id">{{ s.code }} · {{ s.name }}</mat-option>
              }
            </mat-select>
            @if (form.controls.supplierId.hasError('required') && form.controls.supplierId.touched) {
              <mat-error>Choose a supplier</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>PO date</mat-label>
            <input matInput type="date" formControlName="poDate" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Expected delivery</mat-label>
            <input matInput type="date" formControlName="expectedDeliveryDate" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Payment terms</mat-label>
            <input matInput formControlName="paymentTerms" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="full-span">
            <mat-label>Remarks</mat-label>
            <input matInput formControlName="remarks" />
          </mat-form-field>
          <app-plant-field class="full-span" [control]="form.controls.plantId" />
        </div>

        <h3 class="mat-h3">Items</h3>
        <div formArrayName="items">
          @for (line of items.controls; track $index; let i = $index) {
            <div class="line" [formGroupName]="i">
              <mat-form-field appearance="outline" class="material">
                <mat-label>Material</mat-label>
                <mat-select formControlName="materialId">
                  @for (m of materials(); track m.id) {
                    <mat-option [value]="m.id">{{ m.code }} · {{ m.name }} ({{ m.uom }})</mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Quantity</mat-label>
                <input matInput type="number" step="0.001" formControlName="orderedQty" />
                @if (line.controls['orderedQty'].hasError('min')) {
                  <mat-error>Must be above 0</mat-error>
                }
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Rate</mat-label>
                <input matInput type="number" step="0.0001" formControlName="rate" />
                @if (line.controls['rate'].hasError('min')) {
                  <mat-error>Must be above 0</mat-error>
                }
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Tax %</mat-label>
                <input matInput type="number" step="0.01" formControlName="taxPct" />
              </mat-form-field>
              <button
                mat-icon-button
                type="button"
                aria-label="Remove line"
                (click)="removeLine(i)"
                [disabled]="items.length === 1"
              >
                <mat-icon>delete</mat-icon>
              </button>
            </div>
          }
        </div>
        <button mat-button type="button" (click)="addLine()"><mat-icon>add</mat-icon> Add line</button>
        <p class="total mat-body-2">Order total (before tax): {{ subtotal() }}</p>
        @if (errorMessage()) {
          <p class="form-error mat-body-2" role="alert">{{ errorMessage() }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" form="po-form" type="submit" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          Create PO
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .head-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 0 12px;
        padding-top: 8px;
      }
      .full-span {
        grid-column: 1/-1;
      }
      .line {
        display: grid;
        grid-template-columns: 2fr 1fr 1fr 1fr auto;
        gap: 0 8px;
        align-items: start;
      }
      mat-form-field {
        width: 100%;
      }
      .form-error {
        color: var(--mat-warn-color, #f44336);
      }
    `,
  ],
})
export class PurchaseOrderFormComponent implements OnInit {
  protected readonly suppliers = signal<LookupOption[]>([]);
  protected readonly materials = signal<LookupItem[]>([]);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');

  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly lookups = inject(LookupApi);
  private readonly dialogRef = inject(MatDialogRef<PurchaseOrderFormComponent>);

  protected readonly form = this.fb.nonNullable.group({
    supplierId: this.fb.control<number | null>(null, Validators.required),
    poDate: [isoDate(new Date()), Validators.required],
    expectedDeliveryDate: [''],
    paymentTerms: [''],
    remarks: [''],
    plantId: this.fb.control<number | null>(null),
    items: this.fb.array([this.newLine()] as FormGroup[]),
  });

  get items(): FormArray<FormGroup> {
    return this.form.controls.items;
  }

  ngOnInit(): void {
    this.lookups.suppliers().subscribe((s) => this.suppliers.set(s));
    this.lookups.materials().subscribe((m) => this.materials.set(m));
  }

  addLine(): void {
    this.items.push(this.newLine());
  }

  removeLine(index: number): void {
    if (this.items.length > 1) {
      this.items.removeAt(index);
    }
  }

  subtotal(): string {
    const total = this.items.controls.reduce(
      (sum, line) => sum + Number(line.value.orderedQty ?? 0) * Number(line.value.rate ?? 0),
      0,
    );
    return total.toFixed(2);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const request: CreatePoRequest = {
      supplierId: raw.supplierId as number,
      poDate: raw.poDate,
      expectedDeliveryDate: raw.expectedDeliveryDate || null,
      paymentTerms: raw.paymentTerms || null,
      remarks: raw.remarks || null,
      requisitionId: null,
      ...(raw.plantId ? { plantId: raw.plantId } : {}),
      items: raw.items.map((l: Record<string, unknown>) => ({
        materialId: l['materialId'] as number,
        orderedQty: Number(l['orderedQty']),
        rate: Number(l['rate']),
        taxPct: l['taxPct'] === null || l['taxPct'] === '' ? null : Number(l['taxPct']),
      })),
    };
    this.saving.set(true);
    this.errorMessage.set('');
    this.http.post('/api/procurement/purchase-orders', request).subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The purchase order could not be created.'));
      },
    });
  }

  private newLine() {
    return this.fb.group({
      materialId: this.fb.control<number | null>(null, Validators.required),
      orderedQty: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.001)]),
      rate: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.0001)]),
      taxPct: this.fb.control<number | null>(null),
    });
  }
}
