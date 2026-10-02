import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CustomerApi, CustomerDto } from './customer.api';

const GST_PATTERN = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/;

@Component({
  selector: 'app-customer-form',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <h2 mat-dialog-title>{{ customer ? 'Edit Customer' : 'New Customer' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="cust-form" (ngSubmit)="save()" novalidate class="form-grid">
        <mat-form-field appearance="outline">
          <mat-label>Code</mat-label>
          <input matInput formControlName="code" [readonly]="!!customer" />
          @if (form.controls.code.hasError('required') && form.controls.code.touched) { <mat-error>Required</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" />
          @if (form.controls.name.hasError('required') && form.controls.name.touched) { <mat-error>Required</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Contact person</mat-label>
          <input matInput formControlName="contactPerson" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Email</mat-label>
          <input matInput type="email" formControlName="email" />
          @if (form.controls.email.hasError('email') && form.controls.email.touched) { <mat-error>Invalid email</mat-error> }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Phone</mat-label>
          <input matInput formControlName="phone" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>GST number</mat-label>
          <input matInput formControlName="gstNumber" />
          @if (form.controls.gstNumber.hasError('pattern') && form.controls.gstNumber.touched) {
            <mat-error>Invalid GST format</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full-span">
          <mat-label>Address</mat-label>
          <textarea matInput formControlName="address" rows="2"></textarea>
        </mat-form-field>
        @if (errorMessage()) { <p class="form-error mat-body-2">{{ errorMessage() }}</p> }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" form="cust-form" type="submit" [disabled]="saving()">
        @if (saving()) { <mat-spinner diameter="18" /> } @else { Save }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 12px;padding:8px 0}
    .form-grid mat-form-field{width:100%}.full-span{grid-column:1/-1}
    .form-error{color:var(--mat-warn-color,#f44336);grid-column:1/-1}`],
})
export class CustomerFormComponent {
  protected readonly customer = inject<CustomerDto | undefined>(MAT_DIALOG_DATA);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly form = inject(FormBuilder).nonNullable.group({
    code: ['', Validators.required],
    name: ['', Validators.required],
    contactPerson: [''],
    email: ['', Validators.email],
    phone: [''],
    gstNumber: ['', Validators.pattern(GST_PATTERN)],
    address: [''],
    creditLimitDays: [null as number | null],
  });
  private readonly api = inject(CustomerApi);
  private readonly dialogRef = inject(MatDialogRef<CustomerFormComponent>);

  constructor() {
    if (this.customer) { this.form.patchValue(this.customer); this.form.controls.code.disable(); }
  }

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.saving.set(true);
    const req = this.form.getRawValue();
    const obs = this.customer ? this.api.update(this.customer.id, req) : this.api.create(req);
    obs.subscribe({
      next: () => { this.saving.set(false); this.dialogRef.close(true); },
      error: (err) => { this.saving.set(false); this.errorMessage.set(err?.error?.detail ?? 'Save failed.'); },
    });
  }
}
