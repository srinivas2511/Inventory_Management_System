import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ToastService } from '../../../core/notification/toast.service';
import { applyFieldErrors, errorText, problemOf } from '../../../shared/forms/server-errors';
import { codePattern, nonNegative } from '../../../shared/forms/validators';
import { LookupsApi } from '../api/lookups.api';
import { MaterialsApi } from '../api/materials.api';
import { MATERIAL_TYPES, MaterialResponse, PartnerRef, Uom } from '../models';
import { LookupPickerComponent } from '../../../shared/ui/lookup-picker.component';
import { Observable, map } from 'rxjs';
import { PartnersApi } from '../api/partners.api';
import { ApiService } from '../../../core/api/api.service';

export interface MaterialFormData {
  /** Absent when creating. */
  material?: MaterialResponse;
}

/** Create a material or edit its fields (the code is fixed once created). */
@Component({
  selector: 'app-material-form-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    LookupPickerComponent,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.material ? 'Edit ' + data.material.code : 'New material' }}</h2>
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <mat-dialog-content class="grid">
        @if (!data.material) {
          <mat-form-field appearance="outline">
            <mat-label>Code</mat-label>
            <input matInput formControlName="code" autocomplete="off" data-testid="code" />
            <mat-hint>Upper case letters, digits, dot, underscore, hyphen</mat-hint>
            @if (form.controls.code.touched && form.controls.code.invalid) {
              <mat-error>{{ error(form.controls.code) }}</mat-error>
            }
          </mat-form-field>
        }
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" data-testid="name" />
          @if (form.controls.name.touched && form.controls.name.invalid) {
            <mat-error>{{ error(form.controls.name) }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Type</mat-label>
          <mat-select formControlName="materialType" data-testid="type">
            @for (t of types; track t.value) {
              <mat-option [value]="t.value">{{ t.label }}</mat-option>
            }
          </mat-select>
          @if (form.controls.materialType.touched && form.controls.materialType.invalid) {
            <mat-error>{{ error(form.controls.materialType) }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Grade</mat-label>
          <input matInput formControlName="grade" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Diameter</mat-label>
          <input matInput type="number" step="any" formControlName="diameterMm" />
          <span matTextSuffix>mm</span>
          @if (form.controls.diameterMm.touched && form.controls.diameterMm.invalid) {
            <mat-error>{{ error(form.controls.diameterMm) }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Unit of measure</mat-label>
          <mat-select formControlName="uom" data-testid="uom">
            @for (u of uoms(); track u.code) {
              <mat-option [value]="u.code">{{ u.code }} — {{ u.name }}</mat-option>
            }
          </mat-select>
          @if (form.controls.uom.touched && form.controls.uom.invalid) {
            <mat-error>{{ error(form.controls.uom) }}</mat-error>
          }
        </mat-form-field>
        <app-lookup-picker
          label="Preferred supplier"
          [search]="searchSuppliers"
          formControlName="preferredSupplier"
          testId="supplier"
        />
        @for (f of stockFields; track f.name) {
          <mat-form-field appearance="outline">
            <mat-label>{{ f.label }}</mat-label>
            <input matInput type="number" step="any" [formControlName]="f.name" [attr.data-testid]="f.name" />
            @if (form.get(f.name)?.touched && form.get(f.name)?.invalid) {
              <mat-error>{{ error(form.get(f.name)) }}</mat-error>
            }
          </mat-form-field>
        }
        <mat-form-field appearance="outline">
          <mat-label>Shelf life (days)</mat-label>
          <input matInput type="number" formControlName="shelfLifeDays" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="wide">
          <mat-label>Description</mat-label>
          <textarea matInput formControlName="description" rows="2"></textarea>
        </mat-form-field>
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
export class MaterialFormDialogComponent implements OnInit {
  protected readonly data = inject<MaterialFormData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<MaterialFormDialogComponent, MaterialResponse>>(MatDialogRef);
  private readonly api = inject(MaterialsApi);
  private readonly toast = inject(ToastService);
  private readonly lookups = inject(LookupsApi);
  private readonly http = inject(ApiService);

  protected readonly types = MATERIAL_TYPES;
  protected readonly uoms = signal<Uom[]>([]);
  protected readonly busy = signal(false);
  protected readonly message = signal('');
  protected readonly error = errorText;
  protected readonly stockFields = [
    { name: 'minStock', label: 'Minimum stock' },
    { name: 'reorderLevel', label: 'Reorder level' },
    { name: 'maxStock', label: 'Maximum stock' },
    { name: 'standardCost', label: 'Standard cost (per unit)' },
  ];

  private readonly m = this.data.material;
  protected readonly form = inject(FormBuilder).nonNullable.group({
    code: [this.m?.code ?? '', this.m ? [] : [Validators.required, codePattern(2, 30)]],
    name: [this.m?.name ?? '', [Validators.required, Validators.maxLength(150)]],
    materialType: [this.m?.materialType ?? null, [Validators.required]],
    grade: [this.m?.grade ?? '', [Validators.maxLength(30)]],
    diameterMm: [this.m?.diameterMm ?? (null as number | null), [nonNegative]],
    uom: [this.m?.uom ?? '', [Validators.required]],
    preferredSupplier: [this.m?.preferredSupplier ?? (null as PartnerRef | null)],
    minStock: [this.m?.minStock ?? (null as number | null), [nonNegative]],
    reorderLevel: [this.m?.reorderLevel ?? (null as number | null), [nonNegative]],
    maxStock: [this.m?.maxStock ?? (null as number | null), [nonNegative]],
    standardCost: [this.m?.standardCost ?? (null as number | null), [nonNegative]],
    shelfLifeDays: [this.m?.shelfLifeDays ?? (null as number | null), [Validators.min(1)]],
    description: [this.m?.description ?? '', [Validators.maxLength(500)]],
  });

  protected readonly searchSuppliers = (q: string): Observable<PartnerRef[]> =>
    new PartnersApi(this.http, '/suppliers')
      .list({ q, page: 0, size: 20, sort: 'name,asc', filters: { active: 'true' } })
      .pipe(map((page) => page.content.map(({ id, code, name }) => ({ id, code, name }))));

  ngOnInit(): void {
    this.api.uoms().subscribe({ next: (u) => this.uoms.set(u), error: () => undefined });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const body = {
      name: v.name.trim(),
      materialType: v.materialType!,
      grade: v.grade.trim() || undefined,
      diameterMm: v.diameterMm,
      uom: v.uom,
      preferredSupplierId: v.preferredSupplier?.id ?? null,
      minStock: v.minStock,
      reorderLevel: v.reorderLevel,
      maxStock: v.maxStock,
      standardCost: v.standardCost,
      shelfLifeDays: v.shelfLifeDays,
      description: v.description.trim() || undefined,
    };
    this.busy.set(true);
    this.message.set('');
    const request$ = this.m
      ? this.api.update(this.m.id, { ...body, version: this.m.version })
      : this.api.create({ ...body, code: v.code.trim() });
    request$.subscribe({
      next: (saved) => {
        this.toast.success(this.m ? 'Material updated.' : `Material ${saved.code} created.`);
        this.ref.close(saved);
      },
      error: (e: unknown) => {
        this.busy.set(false);
        const problem = problemOf(e);
        const unmatched = applyFieldErrors(this.form, problem);
        this.message.set(
          unmatched.length ? unmatched.join(' ') : (problem?.detail ?? 'The material could not be saved.'),
        );
      },
    });
  }
}
