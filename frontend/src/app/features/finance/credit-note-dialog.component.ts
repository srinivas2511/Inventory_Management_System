import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { problemMessage } from '../../core/models/problem.model';
import { LookupApi, LookupLocation, LookupOption } from '../../shared/lookup.api';
import { todayIso } from './finance-dates';
import { CreateCreditNoteRequest, CreditNoteDto, FinanceApi } from './finance.api';

export interface CreditNoteDialogData {
  invoice: {
    id: number;
    invoiceNumber: string;
    subtotal: number;
    taxTotal: number;
    currencyCode: string;
    counterparty: string;
  };
}

/**
 * Credits part of a customer invoice (FIN-004). The amount is before tax and the tax is reversed in the same
 * proportion; goods that came back are received into quarantine by naming the batch, quantity and place. A credit note
 * is a draft until someone other than its creator approves it.
 */
@Component({
  selector: 'app-credit-note-dialog',
  standalone: true,
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <h2 mat-dialog-title>Credit note for {{ data.invoice.invoiceNumber }}</h2>
    <mat-dialog-content>
      <p class="muted">
        {{ data.invoice.counterparty }} · invoice {{ data.invoice.subtotal | number: '1.2-4' }}
        {{ data.invoice.currencyCode }} plus tax {{ data.invoice.taxTotal | number: '1.2-4' }}
      </p>
      <form [formGroup]="form" id="credit-note-form" (ngSubmit)="save()" novalidate>
        <div class="grid">
          <mat-form-field appearance="outline">
            <mat-label>Credit date</mat-label>
            <input matInput type="date" formControlName="creditDate" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Amount to credit, before tax</mat-label>
            <input matInput type="number" step="0.01" formControlName="subtotal" />
            @if (form.controls.subtotal.hasError('min') || form.controls.subtotal.hasError('required')) {
              <mat-error>Enter an amount greater than zero</mat-error>
            }
            @if (reversedTax() !== null) {
              <mat-hint data-testid="tax-reversal">
                Tax reversed {{ reversedTax() | number: '1.2-4' }}, credit total {{ creditTotal() | number: '1.2-4' }}
              </mat-hint>
            }
          </mat-form-field>
        </div>
        <mat-form-field appearance="outline" class="wide">
          <mat-label>Reason</mat-label>
          <textarea matInput rows="2" formControlName="reason" maxlength="300"></textarea>
          @if (form.controls.reason.hasError('required') && form.controls.reason.touched) {
            <mat-error>A reason is required</mat-error>
          }
        </mat-form-field>
        <mat-checkbox formControlName="returned">The customer sent goods back</mat-checkbox>
        @if (form.controls.returned.value) {
          <div class="grid">
            <mat-form-field appearance="outline">
              <mat-label>Finished goods batch ID</mat-label>
              <input matInput type="number" formControlName="batchId" />
              <mat-hint>A batch that was dispatched on this invoice</mat-hint>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Quantity returned</mat-label>
              <input matInput type="number" step="0.001" formControlName="quantity" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Receive into warehouse</mat-label>
              <mat-select formControlName="warehouseId">
                @for (w of warehouses(); track w.id) {
                  <mat-option [value]="w.id">{{ w.code }} · {{ w.name }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Location</mat-label>
              <mat-select formControlName="locationId">
                @for (l of locations(); track l.id) {
                  <mat-option [value]="l.id">{{ l.code }} · {{ l.name }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          </div>
          <p class="muted">Returned goods are received into quarantine until quality has looked at them.</p>
        }
      </form>
      @if (errorMessage()) {
        <p class="error" role="alert">{{ errorMessage() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" form="credit-note-form" type="submit" [disabled]="saving()">
        Create credit note
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 0 12px;
        padding-top: 4px;
      }
      .wide,
      mat-form-field {
        width: 100%;
      }
      .muted {
        color: var(--mat-sys-on-surface-variant, #49454f);
      }
      .error {
        color: var(--mat-sys-error, #b3261e);
      }
    `,
  ],
})
export class CreditNoteDialogComponent implements OnInit {
  protected readonly data = inject<CreditNoteDialogData>(MAT_DIALOG_DATA);
  protected readonly warehouses = signal<LookupOption[]>([]);
  protected readonly locations = signal<LookupLocation[]>([]);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(FinanceApi);
  private readonly lookups = inject(LookupApi);
  private readonly dialogRef = inject(MatDialogRef<CreditNoteDialogComponent, CreditNoteDto>);

  protected readonly form = this.fb.group({
    creditDate: [todayIso(), Validators.required],
    subtotal: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.0001)]),
    reason: ['', [Validators.required, Validators.pattern(/\S/)]],
    returned: [false],
    batchId: this.fb.control<number | null>(null),
    quantity: this.fb.control<number | null>(null),
    warehouseId: this.fb.control<number | null>(null),
    locationId: this.fb.control<number | null>(null),
  });

  ngOnInit(): void {
    this.form.controls.returned.valueChanges.subscribe((on) => {
      if (on && this.warehouses().length === 0) {
        this.lookups.warehouses().subscribe((w) => this.warehouses.set(w));
      }
    });
    this.form.controls.warehouseId.valueChanges.subscribe((id) => {
      this.form.controls.locationId.reset(null);
      this.locations.set([]);
      if (id) {
        this.lookups.locations(id).subscribe((l) => this.locations.set(l));
      }
    });
  }

  /** The tax the credit reverses: the invoice's tax in proportion to the part of the subtotal being credited. */
  protected reversedTax(): number | null {
    const credit = Number(this.form.controls.subtotal.value ?? 0);
    const invoice = this.data.invoice;
    if (credit <= 0 || invoice.subtotal <= 0) {
      return null;
    }
    return Math.round((invoice.taxTotal * credit * 10000) / invoice.subtotal) / 10000;
  }

  protected creditTotal(): number {
    return Number(this.form.controls.subtotal.value ?? 0) + (this.reversedTax() ?? 0);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: CreateCreditNoteRequest = {
      arInvoiceId: this.data.invoice.id,
      creditDate: v.creditDate as string,
      subtotal: Number(v.subtotal),
      reason: (v.reason ?? '').trim(),
    };
    if (v.returned) {
      if (!v.batchId || !v.quantity || !v.warehouseId || !v.locationId) {
        this.errorMessage.set('For returned goods, give the batch, the quantity, the warehouse and the location.');
        return;
      }
      request.returnFgBatchId = Number(v.batchId);
      request.returnQuantity = Number(v.quantity);
      request.returnWarehouseId = v.warehouseId;
      request.returnLocationId = v.locationId;
    }
    this.saving.set(true);
    this.errorMessage.set('');
    this.api.createCreditNote(request).subscribe({
      next: (note) => this.dialogRef.close(note),
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The credit note could not be created.'));
      },
    });
  }
}
