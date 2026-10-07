import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { problemMessage } from '../../core/models/problem.model';
import { ProductionExecutionApi } from './production-execution.api';
import { ProductionOrderMaterialDto, leftoverQty } from './production-order.model';

export interface CloseDialogData {
  orderId: number;
  orderNumber: string;
  materials: ProductionOrderMaterialDto[];
}

/** Closes a completed order. Material still issued but unused must be returned first, or written off with a reason. */
@Component({
  selector: 'app-close-order-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    DecimalPipe,
  ],
  template: `
    <h2 mat-dialog-title>Close {{ data.orderNumber }}</h2>
    <mat-dialog-content>
      @if (leftovers.length === 0) {
        <p class="mat-body-1">
          All issued material is accounted for. Closing the order posts its cost variance and cannot be done twice.
        </p>
      } @else {
        <p class="mat-body-1">
          This material was issued but not used. Return it to stock before closing, or close now and write it off as
          scrap.
        </p>
        <ul class="leftovers">
          @for (m of leftovers; track m.id) {
            <li>{{ m.materialCode }} · {{ m.materialName }}: {{ left(m) | number: '1.0-3' }} {{ m.uom }}</li>
          }
        </ul>
        <form [formGroup]="form" id="close-form" (ngSubmit)="save()" novalidate>
          <mat-form-field appearance="outline" class="wide">
            <mat-label>Reason for writing the material off</mat-label>
            <textarea matInput formControlName="reason" rows="2"></textarea>
            @if (form.controls.reason.invalid && form.controls.reason.touched) {
              <mat-error>A reason is required to write material off</mat-error>
            }
            <mat-hint>The reason is kept in the audit trail. The written-off cost goes to scrap loss.</mat-hint>
          </mat-form-field>
        </form>
      }
      @if (errorMessage()) {
        <p class="form-error mat-body-2" role="alert">{{ errorMessage() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else if (leftovers.length > 0) {
          Write off and close
        } @else {
          Close order
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .wide {
        width: 100%;
      }
      .leftovers {
        margin: 0 0 16px;
      }
      .form-error {
        color: var(--mat-warn-color, #f44336);
      }
    `,
  ],
})
export class CloseOrderDialogComponent {
  protected readonly data = inject<CloseDialogData>(MAT_DIALOG_DATA);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly leftovers = this.data.materials.filter((m) => leftoverQty(m) > 0);

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ProductionExecutionApi);
  private readonly dialogRef = inject(MatDialogRef<CloseOrderDialogComponent>);

  protected readonly form = this.fb.nonNullable.group({
    reason: ['', this.leftovers.length > 0 ? [Validators.required, Validators.pattern(/\S/)] : []],
  });

  protected left(m: ProductionOrderMaterialDto): number {
    return leftoverQty(m);
  }

  protected save(): void {
    if (this.leftovers.length > 0 && this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.errorMessage.set('');
    const reason = this.leftovers.length > 0 ? this.form.getRawValue().reason.trim() : null;
    this.api.close(this.data.orderId, { writeOffReason: reason }).subscribe({
      next: (order) => {
        this.saving.set(false);
        this.dialogRef.close(order);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The order could not be closed.'));
      },
    });
  }
}
