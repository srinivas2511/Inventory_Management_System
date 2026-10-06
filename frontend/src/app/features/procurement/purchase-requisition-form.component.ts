import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { problemMessage } from '../../core/models/problem.model';
import { LookupApi, LookupItem } from '../../shared/lookup.api';

/** Mirrors CreateRequisitionRequest on the backend. */
export interface CreateRequisitionRequest {
  materialId: number;
  requestedQty: number;
  uom: string;
  neededBy: string | null;
  reason: string | null;
}

@Component({
  selector: 'app-purchase-requisition-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>New purchase requisition</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="pr-form" (ngSubmit)="save()" novalidate class="form-grid">
        <mat-form-field appearance="outline" class="full-span">
          <mat-label>Material</mat-label>
          <mat-select formControlName="materialId" (selectionChange)="materialChosen($event.value)">
            @for (m of materials(); track m.id) {
              <mat-option [value]="m.id">{{ m.code }} · {{ m.name }}</mat-option>
            }
          </mat-select>
          @if (form.controls.materialId.hasError('required') && form.controls.materialId.touched) {
            <mat-error>Choose a material</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Quantity</mat-label>
          <input matInput type="number" step="0.001" formControlName="requestedQty" />
          @if (form.controls.requestedQty.invalid && form.controls.requestedQty.touched) {
            <mat-error>Must be above 0</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Unit</mat-label>
          <input matInput formControlName="uom" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Needed by</mat-label>
          <input matInput type="date" formControlName="neededBy" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="full-span">
          <mat-label>Reason</mat-label>
          <textarea matInput formControlName="reason" rows="2"></textarea>
        </mat-form-field>
        @if (errorMessage()) {
          <p class="form-error mat-body-2 full-span" role="alert">{{ errorMessage() }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" form="pr-form" type="submit" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          Create requisition
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .form-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 0 12px;
        padding: 8px 0;
      }
      .form-grid mat-form-field {
        width: 100%;
      }
      .full-span {
        grid-column: 1/-1;
      }
      .form-error {
        color: var(--mat-warn-color, #f44336);
      }
    `,
  ],
})
export class PurchaseRequisitionFormComponent implements OnInit {
  protected readonly materials = signal<LookupItem[]>([]);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');

  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly lookups = inject(LookupApi);
  private readonly dialogRef = inject(MatDialogRef<PurchaseRequisitionFormComponent>);

  protected readonly form = this.fb.nonNullable.group({
    materialId: this.fb.control<number | null>(null, Validators.required),
    requestedQty: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.001)]),
    uom: ['', Validators.required],
    neededBy: [''],
    reason: [''],
  });

  ngOnInit(): void {
    this.lookups.materials().subscribe((m) => this.materials.set(m));
  }

  materialChosen(materialId: number): void {
    const material = this.materials().find((m) => m.id === materialId);
    if (material) {
      this.form.controls.uom.setValue(material.uom);
    }
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const request: CreateRequisitionRequest = {
      materialId: raw.materialId as number,
      requestedQty: Number(raw.requestedQty),
      uom: raw.uom,
      neededBy: raw.neededBy || null,
      reason: raw.reason || null,
    };
    this.saving.set(true);
    this.errorMessage.set('');
    this.http.post('/api/procurement/requisitions', request).subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The requisition could not be created.'));
      },
    });
  }
}
