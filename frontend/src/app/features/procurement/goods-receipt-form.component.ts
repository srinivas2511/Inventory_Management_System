import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { problemMessage } from '../../core/models/problem.model';
import { LookupApi, LookupLocation, LookupOption, ReceivablePo, ReceivablePoItem } from '../../shared/lookup.api';

/** Mirrors CreateGrRequest on the backend. */
export interface CreateGrRequest {
  purchaseOrderId: number;
  receiptDate: string;
  warehouseId: number;
  supplierChallanNo: string | null;
  vehicleNo: string | null;
  items: {
    poItemId: number;
    receivedQty: number;
    locationId: number;
    heatNumber: string | null;
    certificateNumber: string | null;
    manufacturingDate: string | null;
    inspectionRequired: boolean;
  }[];
}

@Component({
  selector: 'app-goods-receipt-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Receive goods</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="gr-form" (ngSubmit)="save()" novalidate>
        <div class="head-grid">
          <mat-form-field appearance="outline" class="full-span">
            <mat-label>Purchase order</mat-label>
            <mat-select formControlName="purchaseOrderId" (selectionChange)="poChosen($event.value)">
              @for (po of purchaseOrders(); track po.id) {
                <mat-option [value]="po.id">{{ po.poNumber }} · {{ po.supplierName }}</mat-option>
              } @empty {
                <mat-option disabled>No approved purchase order is waiting for goods</mat-option>
              }
            </mat-select>
            @if (form.controls.purchaseOrderId.hasError('required') && form.controls.purchaseOrderId.touched) {
              <mat-error>Choose a purchase order</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Receipt date</mat-label>
            <input matInput type="date" formControlName="receiptDate" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Warehouse</mat-label>
            <mat-select formControlName="warehouseId" (selectionChange)="warehouseChosen($event.value)">
              @for (w of warehouses(); track w.id) {
                <mat-option [value]="w.id">{{ w.code }} · {{ w.name }}</mat-option>
              }
            </mat-select>
            @if (form.controls.warehouseId.hasError('required') && form.controls.warehouseId.touched) {
              <mat-error>Choose a warehouse</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Supplier challan no.</mat-label>
            <input matInput formControlName="supplierChallanNo" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Vehicle no.</mat-label>
            <input matInput formControlName="vehicleNo" />
          </mat-form-field>
        </div>

        @if (poItems().length) {
          <h3 class="mat-h3">Lines (leave the quantity empty to skip a line)</h3>
        }
        <div formArrayName="items">
          @for (line of lines.controls; track $index; let i = $index) {
            <div class="line" [formGroupName]="i">
              <div class="material mat-body-2">
                <strong>{{ poItems()[i].materialCode }}</strong> {{ poItems()[i].materialName }}<br />
                Open: {{ poItems()[i].remainingQty }} {{ poItems()[i].uom }}
              </div>
              <mat-form-field appearance="outline">
                <mat-label>Received</mat-label>
                <input matInput type="number" step="0.001" formControlName="receivedQty" />
                @if (line.controls['receivedQty'].hasError('max')) {
                  <mat-error>More than the open quantity</mat-error>
                }
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Location</mat-label>
                <mat-select formControlName="locationId">
                  @for (l of locations(); track l.id) {
                    <mat-option [value]="l.id">{{ l.code }} · {{ l.name }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Heat no.</mat-label>
                <input matInput formControlName="heatNumber" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Certificate no.</mat-label>
                <input matInput formControlName="certificateNumber" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Manufactured on</mat-label>
                <input matInput type="date" formControlName="manufacturingDate" />
              </mat-form-field>
              <mat-checkbox formControlName="inspectionRequired">Inspect on receipt</mat-checkbox>
            </div>
          }
        </div>
        @if (errorMessage()) {
          <p class="form-error mat-body-2" role="alert">{{ errorMessage() }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" form="gr-form" type="submit" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          Post receipt
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
        grid-template-columns: 1.4fr 1fr 1.2fr 1fr 1fr 1fr auto;
        gap: 0 8px;
        align-items: center;
        margin-bottom: 4px;
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
export class GoodsReceiptFormComponent implements OnInit {
  protected readonly purchaseOrders = signal<ReceivablePo[]>([]);
  protected readonly warehouses = signal<LookupOption[]>([]);
  protected readonly locations = signal<LookupLocation[]>([]);
  protected readonly poItems = signal<ReceivablePoItem[]>([]);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');

  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly lookups = inject(LookupApi);
  private readonly dialogRef = inject(MatDialogRef<GoodsReceiptFormComponent>);

  protected readonly form = this.fb.nonNullable.group({
    purchaseOrderId: this.fb.control<number | null>(null, Validators.required),
    receiptDate: [new Date().toISOString().slice(0, 10), Validators.required],
    warehouseId: this.fb.control<number | null>(null, Validators.required),
    supplierChallanNo: [''],
    vehicleNo: [''],
    items: this.fb.array([] as FormGroup[]),
  });

  get lines(): FormArray<FormGroup> {
    return this.form.controls.items;
  }

  ngOnInit(): void {
    this.lookups.receivablePurchaseOrders().subscribe((p) => this.purchaseOrders.set(p));
    this.lookups.warehouses().subscribe((w) => this.warehouses.set(w));
  }

  poChosen(poId: number): void {
    const po = this.purchaseOrders().find((p) => p.id === poId);
    this.poItems.set(po?.items ?? []);
    this.lines.clear();
    for (const item of po?.items ?? []) {
      this.lines.push(this.newLine(item));
    }
  }

  warehouseChosen(warehouseId: number): void {
    this.lookups.locations(warehouseId).subscribe((l) => {
      this.locations.set(l);
      const preferred = l.find((x) => x.locationType === 'QUARANTINE') ?? l[0];
      for (const line of this.lines.controls) {
        line.patchValue({ locationId: preferred?.id ?? null });
      }
    });
  }

  save(): void {
    this.form.markAllAsTouched();
    const items = this.poItems();
    const chosen = this.lines.controls
      .map((line, i) => ({ value: line.value as Record<string, unknown>, item: items[i], valid: line.valid }))
      .filter(
        (l) => l.value['receivedQty'] !== null && l.value['receivedQty'] !== '' && Number(l.value['receivedQty']) > 0,
      );
    if (this.form.controls.purchaseOrderId.invalid || this.form.controls.warehouseId.invalid) {
      return;
    }
    if (!chosen.length) {
      this.errorMessage.set('Enter a received quantity on at least one line.');
      return;
    }
    if (chosen.some((l) => !l.valid || !l.value['locationId'])) {
      this.errorMessage.set('Every received line needs a quantity within the open quantity and a location.');
      return;
    }
    const raw = this.form.getRawValue();
    const request: CreateGrRequest = {
      purchaseOrderId: raw.purchaseOrderId as number,
      receiptDate: raw.receiptDate,
      warehouseId: raw.warehouseId as number,
      supplierChallanNo: raw.supplierChallanNo || null,
      vehicleNo: raw.vehicleNo || null,
      items: chosen.map((l) => ({
        poItemId: l.item.poItemId,
        receivedQty: Number(l.value['receivedQty']),
        locationId: l.value['locationId'] as number,
        heatNumber: (l.value['heatNumber'] as string) || null,
        certificateNumber: (l.value['certificateNumber'] as string) || null,
        manufacturingDate: (l.value['manufacturingDate'] as string) || null,
        inspectionRequired: !!l.value['inspectionRequired'],
      })),
    };
    this.saving.set(true);
    this.errorMessage.set('');
    this.http.post('/api/procurement/goods-receipts', request).subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The goods receipt could not be posted.'));
      },
    });
  }

  private newLine(item?: ReceivablePoItem) {
    return this.fb.group({
      receivedQty: this.fb.control<number | null>(item ? item.remainingQty : null, [
        Validators.min(0.001),
        Validators.max(item ? item.remainingQty : Number.MAX_SAFE_INTEGER),
      ]),
      locationId: this.fb.control<number | null>(null),
      heatNumber: [''],
      certificateNumber: [''],
      manufacturingDate: [''],
      inspectionRequired: [true],
    });
  }
}
