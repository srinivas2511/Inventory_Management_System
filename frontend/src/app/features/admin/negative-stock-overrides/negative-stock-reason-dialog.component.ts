import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { NegativeStockOverrideDto } from './negative-stock-overrides.component';

@Component({
  selector: 'app-negative-stock-reason-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>{{ data.allowed ? 'Allow' : 'Disallow' }} Negative Stock</h2>
    <mat-dialog-content>
      <p class="mat-body-2">
        {{ data.allowed ? 'Allowing' : 'Disallowing' }} negative stock for
        <strong>{{ data.item.code }} — {{ data.item.name }}</strong
        >. A reason is required and will be recorded in the audit log.
      </p>
      <form [formGroup]="form" (ngSubmit)="confirm()" novalidate>
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Reason</mat-label>
          <textarea matInput formControlName="reason" rows="3" data-testid="reason"></textarea>
          @if (form.controls.reason.hasError('required') && form.controls.reason.touched) {
            <mat-error>Reason is required</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="cancel()">Cancel</button>
      <button mat-flat-button color="primary" (click)="confirm()">Confirm</button>
    </mat-dialog-actions>
  `,
  styles: ['.full-width { width: 100%; }'],
})
export class NegativeStockReasonDialogComponent {
  protected readonly data = inject<{ item: NegativeStockOverrideDto; allowed: boolean }>(MAT_DIALOG_DATA);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    reason: ['', [Validators.required, Validators.minLength(3)]],
  });
  private readonly dialogRef = inject(MatDialogRef<NegativeStockReasonDialogComponent>);

  confirm(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.dialogRef.close(this.form.value.reason);
  }

  cancel(): void {
    this.dialogRef.close(undefined);
  }
}
