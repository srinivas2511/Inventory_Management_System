import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { DynamicSpecFormComponent } from './dynamic-spec-form.component';
import { ProductApi, ProductDto, SpringAttributeDefinitionDto, SpringType } from './product.api';

@Component({
  selector: 'app-product-form',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatProgressSpinnerModule, MatDividerModule, DynamicSpecFormComponent,
  ],
  template: `
    <h2 mat-dialog-title>{{ product ? 'Edit Product' : 'New Spring Product' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="prod-form" (ngSubmit)="save()" novalidate>
        <div class="form-grid">
          <mat-form-field appearance="outline">
            <mat-label>Code</mat-label>
            <input matInput formControlName="code" [readonly]="!!product" data-testid="code" />
            @if (form.controls.code.hasError('required') && form.controls.code.touched) { <mat-error>Required</mat-error> }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Name</mat-label>
            <input matInput formControlName="name" data-testid="name" />
            @if (form.controls.name.hasError('required') && form.controls.name.touched) { <mat-error>Required</mat-error> }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Spring type</mat-label>
            <mat-select formControlName="springType" data-testid="springType">
              @for (t of springTypes; track t) { <mat-option [value]="t">{{ t }}</mat-option> }
            </mat-select>
            @if (form.controls.springType.hasError('required') && form.controls.springType.touched) { <mat-error>Required</mat-error> }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Drawing number</mat-label>
            <input matInput formControlName="drawingNumber" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Drawing revision</mat-label>
            <input matInput formControlName="drawingRevision" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Heat treatment</mat-label>
            <input matInput formControlName="heatTreatment" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Surface treatment</mat-label>
            <input matInput formControlName="surfaceTreatment" />
          </mat-form-field>
        </div>

        <mat-divider />

        <fieldset formGroupName="specification" class="spec-section">
          <legend class="mat-body-2">Core Geometry</legend>
          <div class="spec-grid">
            <mat-form-field appearance="outline">
              <mat-label>Wire Ø (mm)</mat-label>
              <input matInput type="number" formControlName="wireDiameterMm" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Outer Ø (mm)</mat-label>
              <input matInput type="number" formControlName="outerDiameterMm" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Free length (mm)</mat-label>
              <input matInput type="number" formControlName="freeLengthMm" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Total coils</mat-label>
              <input matInput type="number" formControlName="totalCoils" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Active coils</mat-label>
              <input matInput type="number" formControlName="activeCoils" />
            </mat-form-field>
          </div>
        </fieldset>

        @if (attrDefs().length > 0) {
          <app-dynamic-spec-form
            [group]="attrGroup"
            [attributes]="attrDefs()"
            [title]="form.controls.springType.value + ' Specifications'"
          />
        }

        @if (errorMessage()) { <p class="form-error mat-body-2">{{ errorMessage() }}</p> }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      @if (product?.status === 'DRAFT') {
        <button mat-stroked-button color="primary" type="button" (click)="saveAndActivate()" [disabled]="saving()">
          Save &amp; Activate
        </button>
      }
      <button mat-flat-button color="primary" form="prod-form" type="submit" [disabled]="saving()">
        @if (saving()) { <mat-spinner diameter="18" /> } @else { Save Draft }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 12px; padding: 8px 0; }
    .form-grid mat-form-field { width: 100%; }
    .spec-section { border: none; padding: 8px 0; }
    .spec-section legend { color: rgba(0,0,0,.54); font-size: 0.875rem; margin-bottom: 4px; }
    .spec-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 0 12px; }
    .spec-grid mat-form-field { width: 100%; }
    .form-error { color: var(--mat-warn-color, #f44336); }
  `],
})
export class ProductFormComponent implements OnInit {
  protected readonly springTypes: SpringType[] = ['COMPRESSION', 'EXTENSION', 'TORSION', 'CONICAL', 'DISC_BELLEVILLE', 'WIRE_FORM', 'CUSTOM'];
  protected readonly product = inject<ProductDto | undefined>(MAT_DIALOG_DATA);
  protected readonly attrDefs = signal<SpringAttributeDefinitionDto[]>([]);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');

  protected readonly form = inject(FormBuilder).nonNullable.group({
    code: ['', Validators.required],
    name: ['', Validators.required],
    springType: ['' as SpringType, Validators.required],
    drawingNumber: [''],
    drawingRevision: [''],
    heatTreatment: [''],
    surfaceTreatment: [''],
    specification: inject(FormBuilder).group({
      wireDiameterMm: [null as number | null],
      outerDiameterMm: [null as number | null],
      freeLengthMm: [null as number | null],
      totalCoils: [null as number | null],
      activeCoils: [null as number | null],
    }),
  });

  protected readonly attrGroup = new FormGroup({});

  private readonly api = inject(ProductApi);
  private readonly dialogRef = inject(MatDialogRef<ProductFormComponent>);

  ngOnInit(): void {
    if (this.product) {
      this.form.patchValue(this.product);
      if (this.product.specification) {
        this.form.controls.specification.patchValue(this.product.specification);
      }
      this.form.controls.code.disable();
    }
    this.form.controls.springType.valueChanges.subscribe((type) => {
      if (type) this.loadAttrDefs(type);
    });
    const initType = this.form.controls.springType.value;
    if (initType) this.loadAttrDefs(initType);
  }

  private loadAttrDefs(type: SpringType): void {
    this.api.getAttributeDefinitions(type).subscribe((defs) => {
      this.attrDefs.set(defs);
      if (this.product?.specification?.attributes) {
        const attrs = this.product.specification.attributes as Record<string, unknown>;
        for (const [k, v] of Object.entries(attrs)) {
          if (this.attrGroup.contains(k)) this.attrGroup.get(k)?.setValue(v);
        }
      }
    });
  }

  save(activate = false): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.saving.set(true);
    const raw = this.form.getRawValue();
    const req = {
      ...raw,
      specification: {
        ...raw.specification,
        attributes: this.attrDefs().length > 0 ? this.attrGroup.value : undefined,
      },
    };
    const obs = this.product ? this.api.update(this.product.id, req) : this.api.create(req);
    obs.subscribe({
      next: (saved) => {
        if (activate && saved.status === 'DRAFT') {
          this.api.activate(saved.id).subscribe({
            next: () => { this.saving.set(false); this.dialogRef.close(true); },
            error: (err) => { this.saving.set(false); this.errorMessage.set(err?.error?.detail ?? 'Activate failed.'); },
          });
        } else {
          this.saving.set(false);
          this.dialogRef.close(true);
        }
      },
      error: (err) => { this.saving.set(false); this.errorMessage.set(err?.error?.detail ?? 'Save failed.'); },
    });
  }

  saveAndActivate(): void {
    this.save(true);
  }
}
