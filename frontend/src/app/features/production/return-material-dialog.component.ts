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

export interface ReturnDialogData {
  orderId: number;
  orderNumber: string;
  material: ProductionOrderMaterialDto;
}

/** Sends issued material that was not used back to raw-material stock. */
@Component({
  selector: 'app-return-material-dialog',
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
    <h2 mat-dialog-title>Return material from {{ data.orderNumber }}</h2>
    <mat-dialog-content>
      <p class="mat-body-1">
        <strong>{{ label }}</strong> has {{ leftover | number: '1.0-3' }} {{ data.material.uom }} issued but not used.
        Returning it puts that quantity back in raw-material stock at the cost it was issued at.
      </p>
      <form [formGroup]="form" id="return-form" (ngSubmit)="save()" novalidate>
        <mat-form-field appearance="outline" class="wide">
          <mat-label>Quantity to return</mat-label>
          <input matInput type="number" step="0.001" formControlName="quantity" />
          @if (form.controls.quantity.invalid && form.controls.quantity.touched) {
            <mat-error>Enter between 0.001 and {{ leftover | number: '1.0-3' }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="wide">
          <mat-label>Remarks</mat-label>
          <input matInput formControlName="remarks" />
        </mat-form-field>
        @if (errorMessage()) {
          <p class="form-error mat-body-2" role="alert">{{ errorMessage() }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" form="return-form" type="submit" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          Return material
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .wide {
        width: 100%;
      }
      .form-error {
        color: var(--mat-warn-color, #f44336);
      }
    `,
  ],
})
export class ReturnMaterialDialogComponent {
  protected readonly data = inject<ReturnDialogData>(MAT_DIALOG_DATA);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly leftover = leftoverQty(this.data.material);
  protected readonly label = [this.data.material.materialCode, this.data.material.materialName]
    .filter(Boolean)
    .join(' · ');

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ProductionExecutionApi);
  private readonly dialogRef = inject(MatDialogRef<ReturnMaterialDialogComponent>);

  protected readonly form = this.fb.nonNullable.group({
    quantity: this.fb.control<number | null>(this.leftover, [
      Validators.required,
      Validators.min(0.001),
      Validators.max(this.leftover),
    ]),
    remarks: [''],
  });

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set('');
    this.api
      .returnMaterial(this.data.orderId, {
        materialId: this.data.material.materialId,
        quantity: Number(raw.quantity),
        remarks: raw.remarks || null,
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.dialogRef.close(true);
        },
        error: (err) => {
          this.saving.set(false);
          this.errorMessage.set(problemMessage(err, 'The material could not be returned.'));
        },
      });
  }
}
