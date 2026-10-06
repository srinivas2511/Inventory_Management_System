import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { problemMessage } from '../../core/models/problem.model';
import { FgStock, LookupApi } from '../../shared/lookup.api';
import { ReserveRequest, SalesApi, SalesOrderDto, SalesOrderItemDto } from './sales.api';

@Component({
  selector: 'app-reservation-form',
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
    <h2 mat-dialog-title>Reserve stock for {{ order.orderNumber }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="res-form" (ngSubmit)="save()" novalidate class="form-grid">
        <mat-form-field appearance="outline" class="full-span">
          <mat-label>Order item</mat-label>
          <mat-select formControlName="itemId" (selectionChange)="itemChosen($event.value)">
            @for (i of order.items; track i.id) {
              <mat-option [value]="i.id">
                {{ i.productCode }} · {{ i.productName }} (still to reserve {{ unreserved(i) }})
              </mat-option>
            }
          </mat-select>
          @if (form.controls.itemId.hasError('required') && form.controls.itemId.touched) {
            <mat-error>Choose an item</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full-span">
          <mat-label>Batch and location</mat-label>
          <mat-select formControlName="stockKey">
            @for (s of stock(); track key(s)) {
              <mat-option [value]="key(s)">
                Batch {{ s.batchNumber }} · {{ s.locationCode }} · {{ s.available }} available
              </mat-option>
            } @empty {
              <mat-option disabled>No released stock for this product yet</mat-option>
            }
          </mat-select>
          @if (form.controls.stockKey.hasError('required') && form.controls.stockKey.touched) {
            <mat-error>Choose the stock to reserve</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Quantity</mat-label>
          <input matInput type="number" step="0.001" formControlName="qty" />
          @if (form.controls.qty.invalid && form.controls.qty.touched) {
            <mat-error>Must be above 0</mat-error>
          }
        </mat-form-field>
        @if (errorMessage()) {
          <p class="form-error mat-body-2 full-span" role="alert">{{ errorMessage() }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" form="res-form" type="submit" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          Reserve stock
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
export class ReservationFormComponent implements OnInit {
  protected readonly order = inject<SalesOrderDto>(MAT_DIALOG_DATA);
  protected readonly stock = signal<FgStock[]>([]);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(SalesApi);
  private readonly lookups = inject(LookupApi);
  private readonly dialogRef = inject(MatDialogRef<ReservationFormComponent>);

  protected readonly form = this.fb.nonNullable.group({
    itemId: this.fb.control<number | null>(null, Validators.required),
    stockKey: this.fb.control<string | null>(null, Validators.required),
    qty: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.001)]),
  });

  ngOnInit(): void {
    const first = this.order.items.find((i) => this.unreserved(i) > 0);
    if (first) {
      this.form.controls.itemId.setValue(first.id);
      this.itemChosen(first.id);
    }
  }

  protected key(s: FgStock): string {
    return `${s.fgBatchId}:${s.warehouseId}:${s.locationId}`;
  }

  protected unreserved(item: SalesOrderItemDto): number {
    return Math.max(0, item.orderedQty - item.dispatchedQty - item.reservedQty);
  }

  itemChosen(itemId: number): void {
    const item = this.order.items.find((i) => i.id === itemId);
    this.stock.set([]);
    this.form.patchValue({ stockKey: null, qty: item ? this.unreserved(item) : null });
    if (item) {
      this.lookups.finishedGoodsStock(item.productId).subscribe((s) => this.stock.set(s));
    }
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const chosen = this.stock().find((s) => this.key(s) === raw.stockKey);
    if (!chosen) {
      return;
    }
    const request: ReserveRequest = {
      salesOrderItemId: raw.itemId as number,
      fgBatchId: chosen.fgBatchId,
      warehouseId: chosen.warehouseId,
      locationId: chosen.locationId,
      qty: Number(raw.qty),
    };
    this.saving.set(true);
    this.errorMessage.set('');
    this.api.reserve(request).subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The stock could not be reserved.'));
      },
    });
  }
}
