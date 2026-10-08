import { DecimalPipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface FgHoldDecisionData {
  batchNumber: string;
  heldQty: number;
}

export interface FgHoldDecision {
  approvedQty: number;
  rejectedQty: number;
  reason: string;
}

/** Resolves the quantity of a finished-goods batch that is on HOLD: what is not approved is rejected (scrapped). */
@Component({
  selector: 'app-fg-hold-decision-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, DecimalPipe, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Resolve hold: {{ data.batchNumber }}</h2>
    <mat-dialog-content>
      <p class="mat-body-1">
        {{ data.heldQty | number: '1.3-3' }} on hold. Approve the quantity that passes; the rest is rejected and
        scrapped.
      </p>
      <form [formGroup]="form" id="fg-hold-form" (ngSubmit)="confirm()" novalidate>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Quantity to approve</mat-label>
          <input matInput type="number" step="0.001" min="0" [max]="data.heldQty" formControlName="approvedQty" />
          @if (form.controls.approvedQty.hasError('required')) {
            <mat-error>Enter the quantity to approve (0 rejects everything)</mat-error>
          } @else if (form.controls.approvedQty.hasError('min')) {
            <mat-error>The quantity cannot be negative</mat-error>
          } @else if (form.controls.approvedQty.hasError('max')) {
            <mat-error>At most {{ data.heldQty | number: '1.3-3' }} is on hold</mat-error>
          }
        </mat-form-field>
        <p class="mat-body-2" data-testid="rejected-preview">Rejected: {{ rejectedQty() | number: '1.3-3' }}</p>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Reason for the decision</mat-label>
          <textarea matInput rows="3" maxlength="200" formControlName="reason"></textarea>
          @if (form.controls.reason.hasError('required') || form.controls.reason.hasError('pattern')) {
            <mat-error>A reason is required</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" form="fg-hold-form" type="submit">Resolve hold</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .full {
        width: 100%;
        min-width: 360px;
      }
    `,
  ],
})
export class FgHoldDecisionDialogComponent {
  protected readonly data = inject<FgHoldDecisionData>(MAT_DIALOG_DATA);

  private readonly dialogRef = inject(MatDialogRef<FgHoldDecisionDialogComponent, FgHoldDecision>);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.group({
    approvedQty: this.fb.control<number | null>(null, [
      Validators.required,
      Validators.min(0),
      Validators.max(this.data.heldQty),
    ]),
    reason: this.fb.nonNullable.control('', [Validators.required, Validators.pattern(/\S/)]),
  });

  private readonly approved = toSignal(this.form.controls.approvedQty.valueChanges, { initialValue: null });
  protected readonly rejectedQty = computed(() => {
    const a = this.approved();
    return a == null || a < 0 || a > this.data.heldQty ? this.data.heldQty : this.data.heldQty - a;
  });

  protected confirm(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const approvedQty = this.form.controls.approvedQty.value ?? 0;
    this.dialogRef.close({
      approvedQty,
      rejectedQty: Math.round((this.data.heldQty - approvedQty) * 1000) / 1000,
      reason: this.form.controls.reason.value.trim(),
    } satisfies FgHoldDecision);
  }
}
