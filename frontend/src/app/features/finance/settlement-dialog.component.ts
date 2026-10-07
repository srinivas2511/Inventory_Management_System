import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Observable } from 'rxjs';
import { problemMessage } from '../../core/models/problem.model';
import { todayIso, toMinorUnits } from './finance-dates';
import { ApInvoiceDto, ArInvoiceDto, FinanceApi } from './finance.api';

export interface SettlementDialogData {
  /** AP: we pay a supplier. AR: a customer pays us. */
  kind: 'AP' | 'AR';
  invoice: { id: number; invoiceNumber: string; balanceAmount: number; counterparty: string };
}

const MODES: Record<'AP' | 'AR', { value: string; label: string }[]> = {
  AP: [
    { value: 'BANK_TRANSFER', label: 'Bank transfer' },
    { value: 'CHEQUE', label: 'Cheque' },
    { value: 'CASH', label: 'Cash' },
  ],
  AR: [
    { value: 'BANK_TRANSFER', label: 'Bank transfer' },
    { value: 'CHEQUE', label: 'Cheque' },
    { value: 'CASH', label: 'Cash' },
    { value: 'UPI', label: 'UPI' },
  ],
};

/** Records a payment against a supplier invoice or a receipt against a customer invoice; the form is the same. */
@Component({
  selector: 'app-settlement-dialog',
  standalone: true,
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ isPayment ? 'Record payment' : 'Record receipt' }}</h2>
    <mat-dialog-content>
      <p class="summary">
        {{ data.invoice.invoiceNumber }} · {{ data.invoice.counterparty }}<br />
        Outstanding {{ data.invoice.balanceAmount | number: '1.2-4' }}
      </p>
      <form [formGroup]="form" id="settlement-form" (ngSubmit)="save()" novalidate>
        <div class="grid">
          <mat-form-field appearance="outline">
            <mat-label>Amount</mat-label>
            <input matInput type="number" step="0.01" formControlName="amount" />
            @if (form.controls.amount.hasError('required') && form.controls.amount.touched) {
              <mat-error>Enter the amount</mat-error>
            } @else if (form.controls.amount.hasError('min') && form.controls.amount.touched) {
              <mat-error>The amount must be greater than zero</mat-error>
            } @else if (form.controls.amount.hasError('overBalance')) {
              <mat-error>More than the outstanding {{ data.invoice.balanceAmount | number: '1.2-4' }}</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>{{ isPayment ? 'Payment date' : 'Receipt date' }}</mat-label>
            <input matInput type="date" formControlName="date" />
            @if (form.controls.date.hasError('required') && form.controls.date.touched) {
              <mat-error>Choose a date</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Method</mat-label>
            <mat-select formControlName="mode">
              @for (m of modes; track m.value) {
                <mat-option [value]="m.value">{{ m.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>{{ isPayment ? 'Bank or cheque reference' : 'Reference' }}</mat-label>
            <input matInput formControlName="reference" maxlength="60" />
          </mat-form-field>
        </div>
      </form>
      @if (errorMessage()) {
        <p class="error" role="alert">{{ errorMessage() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" form="settlement-form" type="submit" [disabled]="saving()">
        {{ isPayment ? 'Record payment' : 'Record receipt' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .summary {
        margin: 0 0 12px;
      }
      .grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 0 12px;
        padding-top: 4px;
      }
      mat-form-field {
        width: 100%;
      }
      .error {
        color: var(--mat-sys-error, #b3261e);
        margin: 4px 0 0;
      }
    `,
  ],
})
export class SettlementDialogComponent {
  protected readonly data = inject<SettlementDialogData>(MAT_DIALOG_DATA);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(FinanceApi);
  private readonly dialogRef = inject(MatDialogRef<SettlementDialogComponent>);

  protected readonly isPayment = this.data.kind === 'AP';
  protected readonly modes = MODES[this.data.kind];

  protected readonly form = this.fb.group({
    amount: this.fb.control<number | null>(this.data.invoice.balanceAmount, [
      Validators.required,
      Validators.min(0.0001),
      (control: AbstractControl): ValidationErrors | null =>
        control.value !== null && toMinorUnits(Number(control.value)) > toMinorUnits(this.data.invoice.balanceAmount)
          ? { overBalance: true }
          : null,
    ]),
    date: [todayIso(), Validators.required],
    mode: ['BANK_TRANSFER'],
    reference: [''],
  });

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const amount = Number(raw.amount);
    const reference = raw.reference?.trim() ? raw.reference.trim() : null;
    const id = this.data.invoice.id;
    const call: Observable<ApInvoiceDto | ArInvoiceDto> = this.isPayment
      ? this.api.recordApPayment(id, {
          paymentDate: raw.date as string,
          amount,
          paymentMode: raw.mode as string,
          referenceNumber: reference,
        })
      : this.api.recordArReceipt(id, {
          receiptDate: raw.date as string,
          amount,
          paymentMode: raw.mode as string,
          referenceNumber: reference,
        });
    this.saving.set(true);
    this.errorMessage.set('');
    call.subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.dialogRef.close(updated);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(
          problemMessage(
            err,
            this.isPayment ? 'The payment could not be recorded.' : 'The receipt could not be recorded.',
          ),
        );
      },
    });
  }
}
