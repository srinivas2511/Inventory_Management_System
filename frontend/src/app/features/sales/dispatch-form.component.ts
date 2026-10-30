import { Component, OnInit, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { problemMessage } from '../../core/models/problem.model';
import { DispatchableItem, DispatchableOrder, FgStock, LookupApi } from '../../shared/lookup.api';
import { CreateDispatchRequest, SalesApi } from './sales.api';

@Component({
  selector: 'app-dispatch-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>New dispatch</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="dispatch-form" (ngSubmit)="save()" novalidate>
        <div class="head-grid">
          <mat-form-field appearance="outline" class="full-span">
            <mat-label>Sales order</mat-label>
            <mat-select formControlName="salesOrderId" (selectionChange)="orderChosen($event.value)">
              @for (o of orders(); track o.id) {
                <mat-option [value]="o.id">{{ o.orderNumber }} · {{ o.customerName }}</mat-option>
              } @empty {
                <mat-option disabled>No approved order has quantity left to dispatch</mat-option>
              }
            </mat-select>
            @if (form.controls.salesOrderId.hasError('required') && form.controls.salesOrderId.touched) {
              <mat-error>Choose a sales order</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Dispatch date</mat-label>
            <input matInput type="date" formControlName="dispatchDate" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Transporter</mat-label>
            <input matInput formControlName="transporter" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Vehicle no.</mat-label>
            <input matInput formControlName="vehicleNo" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>LR number</mat-label>
            <input matInput formControlName="lrNumber" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="full-span">
            <mat-label>Delivery address</mat-label>
            <textarea matInput formControlName="deliveryAddress" rows="2"></textarea>
            @if (form.controls.deliveryAddress.hasError('required') && form.controls.deliveryAddress.touched) {
              <mat-error>The delivery address is required</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Invoice reference</mat-label>
            <input matInput formControlName="invoiceReference" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Notes</mat-label>
            <input matInput formControlName="notes" />
          </mat-form-field>
        </div>

        <details class="packaging-section">
          <summary class="mat-body-2">Packaging (optional)</summary>
          <div class="head-grid" formGroupName="packaging">
            <mat-form-field appearance="outline">
              <mat-label>Packages</mat-label>
              <input matInput type="number" formControlName="packages" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Gross weight (kg)</mat-label>
              <input matInput type="number" step="0.001" formControlName="grossWeightKg" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Net weight (kg)</mat-label>
              <input matInput type="number" step="0.001" formControlName="netWeightKg" />
            </mat-form-field>
          </div>
        </details>

        @if (items().length) {
          <h3 class="mat-h3">Lines (leave the quantity empty to skip a line)</h3>
        }
        <div formArrayName="lines">
          @for (line of lines.controls; track $index; let i = $index) {
            <div class="line" [formGroupName]="i">
              <div class="material mat-body-2">
                <strong>{{ items()[i].productCode }}</strong> {{ items()[i].productName }}<br />
                Open: {{ items()[i].remainingQty }}
              </div>
              <mat-form-field appearance="outline">
                <mat-label>Batch and location</mat-label>
                <mat-select formControlName="stockKey">
                  @for (s of stockFor(items()[i].productId); track key(s)) {
                    <mat-option [value]="key(s)">
                      {{ s.batchNumber }} · {{ s.locationCode }} · {{ s.available }} free
                    </mat-option>
                  } @empty {
                    <mat-option disabled>No released stock</mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Quantity</mat-label>
                <input matInput type="number" step="0.001" formControlName="quantity" />
                @if (line.controls['quantity'].hasError('max')) {
                  <mat-error>More than the open quantity</mat-error>
                }
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Unit price</mat-label>
                <input matInput type="number" step="0.0001" formControlName="unitPrice" />
              </mat-form-field>
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
      <button mat-flat-button color="primary" form="dispatch-form" type="submit" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          Dispatch
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
        grid-template-columns: 1.4fr 2fr 1fr 1fr;
        gap: 0 8px;
        align-items: center;
      }
      mat-form-field {
        width: 100%;
      }
      .form-error {
        color: var(--mat-warn-color, #f44336);
      }
      .packaging-section {
        margin: 8px 0;
      }
      .packaging-section summary {
        cursor: pointer;
        padding: 4px 0;
        color: rgba(0, 0, 0, 0.6);
      }
    `,
  ],
})
export class DispatchFormComponent implements OnInit {
  protected readonly orders = signal<DispatchableOrder[]>([]);
  protected readonly items = signal<DispatchableItem[]>([]);
  protected readonly stock = signal<FgStock[]>([]);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(SalesApi);
  private readonly lookups = inject(LookupApi);
  private readonly dialogRef = inject(MatDialogRef<DispatchFormComponent>);

  protected readonly form = this.fb.nonNullable.group({
    salesOrderId: this.fb.control<number | null>(null, Validators.required),
    dispatchDate: [new Date().toISOString().slice(0, 10), Validators.required],
    transporter: [''],
    vehicleNo: [''],
    lrNumber: [''],
    deliveryAddress: ['', Validators.required],
    invoiceReference: [''],
    notes: [''],
    packaging: this.fb.group({
      packages: this.fb.control<number | null>(null),
      grossWeightKg: this.fb.control<number | null>(null),
      netWeightKg: this.fb.control<number | null>(null),
    }),
    lines: this.fb.array([] as FormGroup[]),
  });

  get lines(): FormArray<FormGroup> {
    return this.form.controls.lines;
  }

  ngOnInit(): void {
    this.lookups.dispatchableOrders().subscribe((o) => this.orders.set(o));
    this.lookups.finishedGoodsStock().subscribe((s) => this.stock.set(s));
  }

  protected key(s: FgStock): string {
    return `${s.fgBatchId}:${s.warehouseId}:${s.locationId}`;
  }

  protected stockFor(productId: number): FgStock[] {
    return this.stock().filter((s) => s.productId === productId);
  }

  orderChosen(orderId: number): void {
    const order = this.orders().find((o) => o.id === orderId);
    this.items.set(order?.items ?? []);
    this.form.patchValue({ deliveryAddress: order?.deliveryAddress ?? '' });
    this.lines.clear();
    for (const item of order?.items ?? []) {
      const options = this.stockFor(item.productId);
      this.lines.push(
        this.fb.group({
          stockKey: this.fb.control<string | null>(options.length ? this.key(options[0]) : null),
          quantity: this.fb.control<number | null>(null, [Validators.min(0.001), Validators.max(item.remainingQty)]),
          unitPrice: this.fb.control<number | null>(item.unitPrice),
        }),
      );
    }
  }

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.controls.salesOrderId.invalid || this.form.controls.deliveryAddress.invalid) {
      return;
    }
    const items = this.items();
    const chosen = this.lines.controls
      .map((line, i) => ({ value: line.value as Record<string, unknown>, item: items[i], valid: line.valid }))
      .filter((l) => l.value['quantity'] !== null && l.value['quantity'] !== '' && Number(l.value['quantity']) > 0);
    if (!chosen.length) {
      this.errorMessage.set('Enter a quantity on at least one line.');
      return;
    }
    if (chosen.some((l) => !l.valid || !l.value['stockKey'])) {
      this.errorMessage.set(
        'Every dispatched line needs a batch and location, and a quantity within the open quantity.',
      );
      return;
    }
    const raw = this.form.getRawValue();
    const pkg = raw.packaging;
    const hasPackaging = pkg.packages !== null || pkg.grossWeightKg !== null || pkg.netWeightKg !== null;
    const request: CreateDispatchRequest = {
      salesOrderId: raw.salesOrderId as number,
      dispatchDate: raw.dispatchDate,
      transporter: raw.transporter || null,
      vehicleNo: raw.vehicleNo || null,
      lrNumber: raw.lrNumber || null,
      deliveryAddress: raw.deliveryAddress,
      invoiceReference: raw.invoiceReference || null,
      notes: raw.notes || null,
      ...(hasPackaging ? { packagingDetails: {
        packages: pkg.packages,
        gross_weight_kg: pkg.grossWeightKg,
        net_weight_kg: pkg.netWeightKg,
      }} : {}),
      lines: chosen.map((l) => {
        const stock = this.stock().find((s) => this.key(s) === l.value['stockKey'])!;
        return {
          salesOrderItemId: l.item.salesOrderItemId,
          fgBatchId: stock.fgBatchId,
          quantity: Number(l.value['quantity']),
          warehouseId: stock.warehouseId,
          locationId: stock.locationId,
          unitPrice: l.value['unitPrice'] === null || l.value['unitPrice'] === '' ? null : Number(l.value['unitPrice']),
        };
      }),
    };
    this.saving.set(true);
    this.errorMessage.set('');
    this.api.createDispatch(request).subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The dispatch could not be created.'));
      },
    });
  }
}
