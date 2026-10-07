import { Component, OnInit, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { applyFieldErrors } from '../../../core/models/problem.model';
import { MaterialApi, MaterialDto, UomDto } from './material.api';

@Component({
  selector: 'app-material-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ material ? 'Edit Material' : 'New Material' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="material-form" (ngSubmit)="save()" novalidate class="form-grid">
        <mat-form-field appearance="outline">
          <mat-label>Code</mat-label>
          <input matInput formControlName="materialCode" data-testid="code" [readonly]="!!material" />
          @if (form.controls.materialCode.hasError('required') && form.controls.materialCode.touched) {
            <mat-error>Required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" data-testid="name" />
          @if (form.controls.name.hasError('required') && form.controls.name.touched) {
            <mat-error>Required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Material type</mat-label>
          <input matInput formControlName="materialType" data-testid="materialType" />
          @if (form.controls.materialType.hasError('required') && form.controls.materialType.touched) {
            <mat-error>Required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>UOM</mat-label>
          <mat-select formControlName="uom" data-testid="uom">
            @for (u of uoms(); track u.code) {
              <mat-option [value]="u.code">{{ u.name }} ({{ u.code }})</mat-option>
            }
          </mat-select>
          @if (form.controls.uom.hasError('required') && form.controls.uom.touched) {
            <mat-error>Required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Grade</mat-label>
          <input matInput formControlName="grade" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Diameter (mm)</mat-label>
          <input matInput type="number" formControlName="diameterMm" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Min stock</mat-label>
          <input matInput type="number" formControlName="minStock" data-testid="minStock" />
          @if (form.controls.minStock.touched && form.controls.minStock.invalid) {
            <mat-error>{{ numberError('Min stock', form.controls.minStock) }}</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Reorder level</mat-label>
          <input matInput type="number" formControlName="reorderLevel" data-testid="reorderLevel" />
          @if (form.controls.reorderLevel.touched && form.controls.reorderLevel.invalid) {
            <mat-error>{{ numberError('Reorder level', form.controls.reorderLevel) }}</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Max stock</mat-label>
          <input matInput type="number" formControlName="maxStock" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Standard cost (₹)</mat-label>
          <input matInput type="number" formControlName="standardCost" data-testid="standardCost" />
          @if (form.controls.standardCost.touched && form.controls.standardCost.invalid) {
            <mat-error>{{ numberError('Standard cost', form.controls.standardCost) }}</mat-error>
          }
        </mat-form-field>

        @if (errorMessage()) {
          <p class="form-error mat-body-2">{{ errorMessage() }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" form="material-form" type="submit" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          Save
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
      .form-error {
        color: var(--mat-warn-color, #f44336);
        grid-column: 1 / -1;
      }
    `,
  ],
})
export class MaterialFormComponent implements OnInit {
  protected readonly material = inject<MaterialDto | undefined>(MAT_DIALOG_DATA);
  protected readonly uoms = signal<UomDto[]>([]);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');

  protected readonly form = inject(FormBuilder).nonNullable.group({
    materialCode: ['', [Validators.required, Validators.maxLength(20)]],
    name: ['', [Validators.required, Validators.maxLength(120)]],
    materialType: ['', Validators.required],
    uom: ['', Validators.required],
    grade: [''],
    diameterMm: [null as number | null],
    // The API requires these three and refuses negatives (SaveMaterialRequest), so say so before submitting.
    minStock: [null as number | null, [Validators.required, Validators.min(0)]],
    reorderLevel: [null as number | null, [Validators.required, Validators.min(0)]],
    maxStock: [null as number | null],
    standardCost: [null as number | null, [Validators.required, Validators.min(0)]],
  });

  private readonly api = inject(MaterialApi);
  private readonly dialogRef = inject(MatDialogRef<MaterialFormComponent>);

  ngOnInit(): void {
    this.api.listUoms().subscribe((list) => this.uoms.set(list));
    if (this.material) {
      this.form.patchValue(this.material);
      this.form.controls.materialCode.disable();
    }
  }

  protected numberError(label: string, control: AbstractControl): string {
    if (control.hasError('required')) {
      return `${label} is required`;
    }
    if (control.hasError('min')) {
      return `${label} cannot be negative`;
    }
    return (control.getError('server') as string | undefined) ?? `${label} is not valid`;
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const req = this.form.getRawValue();
    const obs = this.material ? this.api.update(this.material.id, req) : this.api.create(req);
    obs.subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(applyFieldErrors(this.form, err, 'Save failed.'));
      },
    });
  }
}
