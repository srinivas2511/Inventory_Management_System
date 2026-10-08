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
import { CreateSalesOrderRequest, SalesApi } from './sales.api';

@Component({
  selector: 'app-sales-order-form',
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
    <h2 mat-dialog-title>New sales order</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="so-form" (ngSubmit)="save()" novalidate>
        <app-plant-field [control]="form.controls.plantId" />
        <div class="head-grid">
          <mat-form-field appearance="outline">
            <mat-label>Customer</mat-label>
            <mat-select formControlName="customerId">
              @for (c of customers(); track c.id) {
                <mat-option [value]="c.id">{{ c.code }} · {{ c.name }}</mat-option>
              }
            </mat-select>
            @if (form.controls.customerId.hasError('required') && form.controls.customerId.touched) {
              <mat-error>Choose a customer</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Order date</mat-label>
            <input matInput type="date" formControlName="orderDate" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Required by</mat-label>
            <input matInput type="date" formControlName="requiredDate" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Payment terms</mat-label>
            <input matInput formControlName="paymentTerms" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="full-span">
            <mat-label>Delivery address</mat-label>
            <textarea matInput formControlName="deliveryAddress" rows="2"></textarea>
          </mat-form-field>
        </div>

        <h3 class="mat-h3">Items</h3>
        <div formArrayName="items">
          @for (line of lines.controls; track $index; let i = $index) {
            <div class="line" [formGroupName]="i">
              <mat-form-field appearance="outline">
                <mat-label>Product</mat-label>
                <mat-select formControlName="productId">
                  @for (p of products(); track p.id) {
                    <mat-option [value]="p.id">{{ p.code }} · {{ p.name }}</mat-option>
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
                <mat-label>Unit price</mat-label>
                <input matInput type="number" step="0.0001" formControlName="unitPrice" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Customer spec ref</mat-label>
                <input matInput formControlName="customerSpecRef" />
              </mat-form-field>
              <button
                mat-icon-button
                type="button"
                aria-label="Remove line"
                (click)="removeLine(i)"
                [disabled]="lines.length === 1"
              >
                <mat-icon>delete</mat-icon>
              </button>
            </div>
          }
        </div>
        <button mat-button type="button" (click)="addLine()"><mat-icon>add</mat-icon> Add item</button>
        <p class="total mat-body-2">Order value: {{ total() }}</p>
        @if (errorMessage()) {
          <p class="form-error mat-body-2" role="alert">{{ errorMessage() }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" form="so-form" type="submit" [disabled]="saving()">
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
        grid-template-columns: 2fr 1fr 1fr 1.4fr auto;
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
export class SalesOrderFormComponent implements OnInit {
  protected readonly customers = signal<LookupOption[]>([]);
  protected readonly products = signal<LookupItem[]>([]);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(SalesApi);
  private readonly lookups = inject(LookupApi);
  private readonly dialogRef = inject(MatDialogRef<SalesOrderFormComponent>);

  protected readonly form = this.fb.nonNullable.group({
    customerId: this.fb.control<number | null>(null, Validators.required),
    orderDate: [new Date().toISOString().slice(0, 10), Validators.required],
    requiredDate: [''],
    paymentTerms: [''],
    deliveryAddress: [''],
    plantId: this.fb.control<number | null>(null),
    items: this.fb.array([this.newLine()] as FormGroup[]),
  });

  get lines(): FormArray<FormGroup> {
    return this.form.controls.items;
  }

  ngOnInit(): void {
    this.lookups.customers().subscribe((c) => this.customers.set(c));
    this.lookups.products().subscribe((p) => this.products.set(p));
  }

  addLine(): void {
    this.lines.push(this.newLine());
  }

  removeLine(index: number): void {
    if (this.lines.length > 1) {
      this.lines.removeAt(index);
    }
  }

  total(): string {
    const sum = this.lines.controls.reduce(
      (acc, line) => acc + Number(line.value['orderedQty'] ?? 0) * Number(line.value['unitPrice'] ?? 0),
      0,
    );
    return sum.toFixed(2);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const request: CreateSalesOrderRequest = {
      customerId: raw.customerId as number,
      orderDate: raw.orderDate,
      requiredDate: raw.requiredDate || null,
      deliveryAddress: raw.deliveryAddress || null,
      paymentTerms: raw.paymentTerms || null,
      notes: null,
      ...(raw.plantId ? { plantId: raw.plantId } : {}),
      items: this.lines.controls.map((line) => {
        const v = line.value as Record<string, unknown>;
        return {
          productId: v['productId'] as number,
          orderedQty: Number(v['orderedQty']),
          unitPrice: v['unitPrice'] === null || v['unitPrice'] === '' ? null : Number(v['unitPrice']),
          customerSpecRef: (v['customerSpecRef'] as string) || null,
          notes: null,
        };
      }),
    };
    this.saving.set(true);
    this.errorMessage.set('');
    this.api.createOrder(request).subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The sales order could not be created.'));
      },
    });
  }

  private newLine(): FormGroup {
    return this.fb.group({
      productId: this.fb.control<number | null>(null, Validators.required),
      orderedQty: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.001)]),
      unitPrice: this.fb.control<number | null>(null),
      customerSpecRef: [''],
    });
  }
}
