import { DecimalPipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface FgFinalDecisionData {
  batchNumber: string;
  producedQty: number;
}

export interface FgFinalDecision {
  approvedQty: number;
  rejectedQty: number;
  holdQty: number;
  remarks: string;
}

const round3 = (v: number): number => Math.round(v * 1000) / 1000;

/** Approved plus rejected can never exceed what was produced; whatever is left is put on HOLD. */
function withinProduced(produced: number) {
  return (group: AbstractControl): ValidationErrors | null => {
    const approved = Number(group.get('approvedQty')?.value ?? 0);
    const rejected = Number(group.get('rejectedQty')?.value ?? 0);
    return round3(approved + rejected) > produced ? { overAllocated: true } : null;
  };
}

/**
 * Records the final inspection decision of a finished-goods batch: how much passes, how much is rejected, and the
 * remainder, which goes on HOLD until a quality manager resolves it.
 */
@Component({
  selector: 'app-fg-final-decision-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, DecimalPipe, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Final inspection: {{ data.batchNumber }}</h2>
    <mat-dialog-content>
      <p class="mat-body-1">
        {{ data.producedQty | number: '1.3-3' }} produced. Enter what passes and what is rejected; the rest is put on
        hold.
      </p>
      <form [formGroup]="form" id="fg-final-form" (ngSubmit)="confirm()" novalidate>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Quantity approved</mat-label>
          <input matInput type="number" step="0.001" min="0" [max]="data.producedQty" formControlName="approvedQty" />
          @if (form.controls.approvedQty.hasError('required')) {
            <mat-error>Enter the quantity approved (0 approves nothing)</mat-error>
          } @else if (form.controls.approvedQty.hasError('min')) {
            <mat-error>The quantity cannot be negative</mat-error>
          } @else if (form.controls.approvedQty.hasError('max')) {
            <mat-error>At most {{ data.producedQty | number: '1.3-3' }} was produced</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Quantity rejected</mat-label>
          <input matInput type="number" step="0.001" min="0" [max]="data.producedQty" formControlName="rejectedQty" />
          @if (form.controls.rejectedQty.hasError('required')) {
            <mat-error>Enter the quantity rejected (0 rejects nothing)</mat-error>
          } @else if (form.controls.rejectedQty.hasError('min')) {
            <mat-error>The quantity cannot be negative</mat-error>
          } @else if (form.controls.rejectedQty.hasError('max')) {
            <mat-error>At most {{ data.producedQty | number: '1.3-3' }} was produced</mat-error>
          }
        </mat-form-field>
        @if (form.hasError('overAllocated')) {
          <p class="mat-body-2 over" role="alert" data-testid="over-allocated">
            Approved and rejected together exceed the {{ data.producedQty | number: '1.3-3' }} produced.
          </p>
        } @else {
          <p class="mat-body-2" data-testid="hold-preview">On hold: {{ holdQty() | number: '1.3-3' }}</p>
        }
        <mat-form-field appearance="outline" class="full">
          <mat-label>Remarks (optional)</mat-label>
          <textarea matInput rows="2" maxlength="200" formControlName="remarks"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" form="fg-final-form" type="submit">Record decision</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .full {
        width: 100%;
        min-width: 360px;
      }
      .over {
        color: var(--mat-sys-error, #b3261e);
      }
    `,
  ],
})
export class FgFinalDecisionDialogComponent {
  protected readonly data = inject<FgFinalDecisionData>(MAT_DIALOG_DATA);

  private readonly dialogRef = inject(MatDialogRef<FgFinalDecisionDialogComponent, FgFinalDecision>);
  private readonly fb = inject(FormBuilder);

  private readonly quantityRules = [Validators.required, Validators.min(0), Validators.max(this.data.producedQty)];

  protected readonly form = this.fb.group(
    {
      approvedQty: this.fb.control<number | null>(null, this.quantityRules),
      rejectedQty: this.fb.control<number | null>(0, this.quantityRules),
      remarks: this.fb.nonNullable.control(''),
    },
    { validators: [withinProduced(this.data.producedQty)] },
  );

  private readonly values = toSignal(this.form.valueChanges, { initialValue: this.form.value });
  protected readonly holdQty = computed(() => {
    const v = this.values();
    const approved = Number(v.approvedQty ?? 0);
    const rejected = Number(v.rejectedQty ?? 0);
    return Math.max(0, round3(this.data.producedQty - approved - rejected));
  });

  protected confirm(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const approvedQty = round3(Number(this.form.controls.approvedQty.value ?? 0));
    const rejectedQty = round3(Number(this.form.controls.rejectedQty.value ?? 0));
    this.dialogRef.close({
      approvedQty,
      rejectedQty,
      holdQty: round3(this.data.producedQty - approvedQty - rejectedQty),
      remarks: this.form.controls.remarks.value.trim(),
    } satisfies FgFinalDecision);
  }
}
