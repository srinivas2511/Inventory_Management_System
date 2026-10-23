import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { problemMessage } from '../../core/models/problem.model';
import { todayIso } from './finance-dates';
import { ApDebitNoteDto, CreateDebitNoteRequest, FinanceApi } from './finance.api';

export interface DebitNoteDialogData {
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
 * AP debit note dialog: creates a draft debit note against an open supplier invoice.
 * Mirrors the AR credit note dialog on the payables side.
 * The note is a draft until someone other than its creator approves it.
 */
@Component({
  selector: 'app-debit-note-dialog',
  standalone: true,
  imports: [DecimalPipe, ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>Debit note for {{ data.invoice.invoiceNumber }}</h2>
    <mat-dialog-content>
      <p class="muted">
        {{ data.invoice.counterparty }} · invoice {{ data.invoice.subtotal | number: '1.2-4' }}
        {{ data.invoice.currencyCode }} plus tax {{ data.invoice.taxTotal | number: '1.2-4' }}
      </p>
      <form [formGroup]="form" id="debit-note-form" (ngSubmit)="save()" novalidate>
        <div class="grid">
          <mat-form-field appearance="outline">
            <mat-label>Debit date</mat-label>
            <input matInput type="date" formControlName="debitDate" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Amount to debit, before tax</mat-label>
            <input matInput type="number" step="0.01" formControlName="subtotal" />
            @if (form.controls.subtotal.hasError('min') || form.controls.subtotal.hasError('required')) {
              <mat-error>Enter an amount greater than zero</mat-error>
            }
            @if (reversedTax() !== null) {
              <mat-hint data-testid="tax-reversal">
                Tax reversed {{ reversedTax() | number: '1.2-4' }}, debit total
                {{ debitTotal() | number: '1.2-4' }}
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
      </form>
      @if (errorMessage()) {
        <p class="error" role="alert">{{ errorMessage() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close [disabled]="saving()">Cancel</button>
      <button mat-flat-button color="primary" form="debit-note-form" type="submit" [disabled]="saving()">
        Create debit note
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
export class DebitNoteDialogComponent {
  protected readonly data = inject<DebitNoteDialogData>(MAT_DIALOG_DATA);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(FinanceApi);
  private readonly dialogRef = inject(MatDialogRef<DebitNoteDialogComponent, ApDebitNoteDto>);

  protected readonly form = this.fb.group({
    debitDate: [todayIso(), Validators.required],
    subtotal: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.0001)]),
    reason: ['', [Validators.required, Validators.pattern(/\S/)]],
  });

  /** The tax the debit reverses: the invoice's tax in proportion to the part being debited. */
  protected reversedTax(): number | null {
    const debit = Number(this.form.controls.subtotal.value ?? 0);
    const invoice = this.data.invoice;
    if (debit <= 0 || invoice.subtotal <= 0) {
      return null;
    }
    return Math.round((invoice.taxTotal * debit * 10000) / invoice.subtotal) / 10000;
  }

  protected debitTotal(): number {
    return Number(this.form.controls.subtotal.value ?? 0) + (this.reversedTax() ?? 0);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: CreateDebitNoteRequest = {
      apInvoiceId: this.data.invoice.id,
      debitDate: v.debitDate as string,
      subtotal: Number(v.subtotal),
      reason: (v.reason ?? '').trim(),
    };
    this.saving.set(true);
    this.errorMessage.set('');
    this.api.createDebitNote(request).subscribe({
      next: (note) => this.dialogRef.close(note),
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The debit note could not be created.'));
      },
    });
  }
}
