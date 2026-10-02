import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ToastService } from '../../../core/notification/toast.service';
import { applyFieldErrors, errorText, problemOf } from '../../../shared/forms/server-errors';
import { codePattern, gstNumber, nonNegative } from '../../../shared/forms/validators';
import { PartnersApi } from '../api/partners.api';
import { PartnerResponse } from '../models';

export interface PartnerFormData {
  api: PartnersApi;
  /** "Supplier" or "Customer", for titles and messages. */
  noun: string;
  /** Absent when creating. */
  partner?: PartnerResponse;
}

/** Create or edit a supplier or customer; the two have the same fields. */
@Component({
  selector: 'app-partner-form-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>{{ data.partner ? 'Edit ' + data.partner.code : 'New ' + data.noun.toLowerCase() }}</h2>
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <mat-dialog-content class="grid">
        @if (!data.partner) {
          <mat-form-field appearance="outline">
            <mat-label>Code</mat-label>
            <input matInput formControlName="code" autocomplete="off" data-testid="code" />
            <mat-hint>Upper case letters, digits, dot, underscore, hyphen</mat-hint>
            @if (form.controls.code.touched && form.controls.code.invalid) {
              <mat-error>{{ error(form.controls.code) }}</mat-error>
            }
          </mat-form-field>
        }
        @for (f of fields; track f.name) {
          <mat-form-field appearance="outline" [class.wide]="f.wide">
            <mat-label>{{ f.label }}</mat-label>
            <input matInput [type]="f.type ?? 'text'" [formControlName]="f.name" [attr.data-testid]="f.name" />
            @if (form.get(f.name)?.touched && form.get(f.name)?.invalid) {
              <mat-error>{{ error(form.get(f.name)) }}</mat-error>
            }
          </mat-form-field>
        }
        @if (message()) {
          <p class="message wide" role="alert" data-testid="form-error">{{ message() }}</p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancel</button>
        <button mat-flat-button color="primary" type="submit" [disabled]="busy()" data-testid="save">Save</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .grid {
      display: grid;
      gap: 0 16px;
      grid-template-columns: repeat(2, minmax(200px, 1fr));
      min-width: min(560px, 85vw);
    }
    .wide {
      grid-column: 1 / -1;
    }
    .message {
      color: #c62828;
      margin: 0;
    }
  `,
})
export class PartnerFormDialogComponent {
  protected readonly data = inject<PartnerFormData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<PartnerFormDialogComponent, PartnerResponse>>(MatDialogRef);
  private readonly toast = inject(ToastService);

  protected readonly busy = signal(false);
  protected readonly message = signal('');
  protected readonly error = errorText;
  protected readonly fields: { name: string; label: string; type?: string; wide?: boolean }[] = [
    { name: 'name', label: 'Name', wide: true },
    { name: 'contactPerson', label: 'Contact person' },
    { name: 'phone', label: 'Phone' },
    { name: 'email', label: 'E-mail', type: 'email' },
    { name: 'gstNumber', label: 'GST number' },
    { name: 'paymentTerms', label: 'Payment terms' },
    { name: 'leadTimeDays', label: 'Lead time (days)', type: 'number' },
    { name: 'address', label: 'Address', wide: true },
  ];

  private readonly p = this.data.partner;
  protected readonly form = inject(FormBuilder).nonNullable.group({
    code: [this.p?.code ?? '', this.p ? [] : [Validators.required, codePattern(2, 20)]],
    name: [this.p?.name ?? '', [Validators.required, Validators.maxLength(150)]],
    contactPerson: [this.p?.contactPerson ?? '', [Validators.maxLength(100)]],
    phone: [this.p?.phone ?? '', [Validators.pattern(/^[0-9+() -]{5,20}$/)]],
    email: [this.p?.email ?? '', [Validators.email, Validators.maxLength(160)]],
    gstNumber: [this.p?.gstNumber ?? '', [gstNumber]],
    paymentTerms: [this.p?.paymentTerms ?? '', [Validators.maxLength(60)]],
    leadTimeDays: [this.p?.leadTimeDays ?? (null as number | null), [nonNegative]],
    address: [this.p?.address ?? '', [Validators.maxLength(400)]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const body = {
      name: v.name.trim(),
      contactPerson: v.contactPerson.trim() || undefined,
      phone: v.phone.trim() || undefined,
      email: v.email.trim() || undefined,
      gstNumber: v.gstNumber.trim().toUpperCase() || undefined,
      paymentTerms: v.paymentTerms.trim() || undefined,
      leadTimeDays: v.leadTimeDays,
      address: v.address.trim() || undefined,
    };
    this.busy.set(true);
    this.message.set('');
    const request$ = this.p
      ? this.data.api.update(this.p.id, { ...body, version: this.p.version })
      : this.data.api.create({ ...body, code: v.code.trim() });
    request$.subscribe({
      next: (saved) => {
        this.toast.success(this.p ? `${this.data.noun} updated.` : `${this.data.noun} ${saved.code} created.`);
        this.ref.close(saved);
      },
      error: (e: unknown) => {
        this.busy.set(false);
        const problem = problemOf(e);
        const unmatched = applyFieldErrors(this.form, problem);
        this.message.set(
          unmatched.length
            ? unmatched.join(' ')
            : (problem?.detail ?? `The ${this.data.noun.toLowerCase()} could not be saved.`),
        );
      },
    });
  }
}
