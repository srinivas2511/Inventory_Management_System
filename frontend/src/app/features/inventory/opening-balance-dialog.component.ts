import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { applyFieldErrors } from '../../core/models/problem.model';
import { LookupApi, LookupItem, LookupLocation, LookupOption } from '../../shared/lookup.api';
import { InventoryApi, PostingResultDto } from './inventory.api';

const STATUSES = [
  { value: 'AVAILABLE', label: 'Available' },
  { value: 'QUARANTINE', label: 'Quarantine' },
];

/** UAT STK-04: record opening stock for a material (quantity, where it sits, what it cost). */
@Component({
  selector: 'app-opening-balance-dialog',
  standalone: true,
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Opening balance</h2>
    @if (result(); as r) {
      <mat-dialog-content>
        <p data-testid="opening-result">
          Recorded <strong>{{ r.transactionNumber }}</strong
          >. Balance after: {{ r.balanceAfter | number: '1.0-3' }} at ₹{{ r.unitCost | number: '1.2-4' }} a unit.
        </p>
        <p class="mat-caption">The stock journal (Dr stock / Cr Opening Balance Equity) is posted with it.</p>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-flat-button color="primary" [mat-dialog-close]="true">Done</button>
      </mat-dialog-actions>
    } @else {
      <mat-dialog-content>
        <form [formGroup]="form" id="opening-form" (ngSubmit)="save()" novalidate class="form-grid">
          <mat-form-field appearance="outline" class="wide">
            <mat-label>Material</mat-label>
            <mat-select formControlName="materialId" data-testid="ob-material">
              @for (m of materials(); track m.id) {
                <mat-option [value]="m.id">{{ m.code }} · {{ m.name }}</mat-option>
              }
            </mat-select>
            @if (form.controls.materialId.touched && form.controls.materialId.invalid) {
              <mat-error>Choose a material</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Warehouse</mat-label>
            <mat-select formControlName="warehouseId" data-testid="ob-warehouse">
              @for (w of warehouses(); track w.id) {
                <mat-option [value]="w.id">{{ w.code }} · {{ w.name }}</mat-option>
              }
            </mat-select>
            @if (form.controls.warehouseId.touched && form.controls.warehouseId.invalid) {
              <mat-error>Choose a warehouse</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Location</mat-label>
            <mat-select formControlName="locationId" data-testid="ob-location">
              @for (l of locations(); track l.id) {
                <mat-option [value]="l.id">{{ l.code }} · {{ l.name }}</mat-option>
              }
            </mat-select>
            @if (form.controls.locationId.touched && form.controls.locationId.invalid) {
              <mat-error>Choose a location</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Stock status</mat-label>
            <mat-select formControlName="stockStatus" data-testid="ob-status">
              @for (s of statuses; track s.value) {
                <mat-option [value]="s.value">{{ s.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Quantity ({{ uom() || 'unit' }})</mat-label>
            <input matInput type="number" formControlName="quantity" data-testid="ob-quantity" />
            @if (form.controls.quantity.touched && form.controls.quantity.invalid) {
              <mat-error>Enter a quantity above zero</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Unit cost (₹)</mat-label>
            <input matInput type="number" formControlName="unitCost" data-testid="ob-cost" />
            @if (form.controls.unitCost.touched && form.controls.unitCost.invalid) {
              <mat-error>Cost cannot be negative</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="wide">
            <mat-label>Remarks</mat-label>
            <input matInput formControlName="remarks" maxlength="200" />
          </mat-form-field>

          @if (errorMessage()) {
            <p class="form-error mat-body-2" role="alert">{{ errorMessage() }}</p>
          }
        </form>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button mat-dialog-close>Cancel</button>
        <button mat-flat-button color="primary" form="opening-form" type="submit" [disabled]="saving()">
          @if (saving()) {
            <mat-spinner diameter="18" />
          } @else {
            Record opening balance
          }
        </button>
      </mat-dialog-actions>
    }
  `,
  styles: [
    `
      .form-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 0 12px;
        padding: 8px 0;
        min-width: 480px;
      }
      .form-grid mat-form-field {
        width: 100%;
      }
      .wide,
      .form-error {
        grid-column: 1 / -1;
      }
      .form-error {
        color: var(--mat-warn-color, #f44336);
      }
    `,
  ],
})
export class OpeningBalanceDialogComponent implements OnInit {
  private readonly lookups = inject(LookupApi);
  private readonly api = inject(InventoryApi);

  protected readonly statuses = STATUSES;
  protected readonly materials = signal<LookupItem[]>([]);
  protected readonly warehouses = signal<LookupOption[]>([]);
  protected readonly locations = signal<LookupLocation[]>([]);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly result = signal<PostingResultDto | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    materialId: [null as number | null, Validators.required],
    warehouseId: [null as number | null, Validators.required],
    locationId: [null as number | null, Validators.required],
    stockStatus: ['AVAILABLE', Validators.required],
    quantity: [null as number | null, [Validators.required, Validators.min(0.001)]],
    unitCost: [null as number | null, Validators.min(0)],
    remarks: [''],
  });

  ngOnInit(): void {
    this.lookups.materials().subscribe((list) => this.materials.set(list));
    this.lookups.warehouses().subscribe((list) => this.warehouses.set(list));
    this.form.controls.warehouseId.valueChanges.subscribe((warehouseId) => {
      this.form.controls.locationId.reset(null);
      this.locations.set([]);
      if (warehouseId) {
        this.lookups.locations(warehouseId).subscribe((list) => this.locations.set(list));
      }
    });
  }

  protected uom(): string {
    const id = this.form.controls.materialId.value;
    return this.materials().find((m) => m.id === id)?.uom ?? '';
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set('');
    this.api
      .loadOpeningBalances([
        {
          itemType: 'MATERIAL',
          materialId: v.materialId!,
          batchId: null,
          warehouseId: v.warehouseId!,
          locationId: v.locationId!,
          stockStatus: v.stockStatus,
          quantity: v.quantity!,
          uom: this.uom(),
          unitCost: v.unitCost,
          remarks: v.remarks.trim() || undefined,
        },
      ])
      .subscribe({
        next: (results) => {
          this.saving.set(false);
          this.result.set(results[0] ?? null);
        },
        error: (err) => {
          this.saving.set(false);
          this.errorMessage.set(applyFieldErrors(this.form, err, 'The opening balance could not be recorded.'));
        },
      });
  }
}
