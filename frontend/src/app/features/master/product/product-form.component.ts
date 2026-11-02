import { Component, OnInit, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { DynamicSpecFormComponent } from './dynamic-spec-form.component';
import {
  CustomerRef,
  MaterialRef,
  ProductApi,
  ProductDto,
  ProductTypeDto,
  SaveProductRequest,
  AttributeDefinitionDto,
  ProductVariant,
} from './product.api';
import { LookupApi, LookupItem, LookupOption } from '../../../shared/lookup.api';

@Component({
  selector: 'app-product-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    MatTabsModule,
    MatChipsModule,
    MatIconModule,
    DynamicSpecFormComponent,
    NgTemplateOutlet,
  ],
  template: `
    <h2 mat-dialog-title>{{ product ? 'Edit Product' : 'New Product' }}</h2>
    <mat-dialog-content>
      @if (product) {
        <mat-tab-group>
          <mat-tab label="General">
            <ng-container *ngTemplateOutlet="generalForm" />
          </mat-tab>
          <mat-tab label="Customers">
            <div class="customers-tab">
              <div class="customers-header">
                <span class="mat-body-2">Approved Customers</span>
                <mat-form-field appearance="outline" class="add-customer-field">
                  <mat-label>Add customer</mat-label>
                  <mat-select [value]="null" (selectionChange)="addCustomer($event.value)">
                    <mat-option [value]="null">— Select —</mat-option>
                    @for (c of availableCustomers(); track c.id) {
                      @if (!isApproved(c.id)) {
                        <mat-option [value]="c.id">{{ c.code }} – {{ c.name }}</mat-option>
                      }
                    }
                  </mat-select>
                </mat-form-field>
              </div>
              <mat-chip-set aria-label="Approved customers">
                @for (c of approvedCustomers(); track c.id) {
                  <mat-chip (removed)="removeCustomer(c.id)">
                    {{ c.code }} – {{ c.name }}
                    <button matChipRemove [attr.aria-label]="'Remove ' + c.name">
                      <mat-icon>cancel</mat-icon>
                    </button>
                  </mat-chip>
                }
                @if (approvedCustomers().length === 0) {
                  <span class="mat-body-2 empty-hint">No customers linked yet.</span>
                }
              </mat-chip-set>
            </div>
          </mat-tab>

          <mat-tab label="Raw Materials">
            <div class="customers-tab">
              <div class="customers-header">
                <span class="mat-body-2">Linked Raw Materials</span>
                <mat-form-field appearance="outline" class="add-customer-field">
                  <mat-label>Add raw material</mat-label>
                  <mat-select [value]="null" (selectionChange)="addMaterial($event.value)">
                    <mat-option [value]="null">— Select —</mat-option>
                    @for (m of availableMaterials(); track m.id) {
                      @if (!isMaterialLinked(m.id)) {
                        <mat-option [value]="m.id">{{ m.code }} – {{ m.name }}</mat-option>
                      }
                    }
                  </mat-select>
                </mat-form-field>
              </div>
              <mat-chip-set aria-label="Linked raw materials">
                @for (m of linkedMaterials(); track m.id) {
                  <mat-chip (removed)="removeMaterial(m.id)">
                    {{ m.code }} – {{ m.name }}
                    <button matChipRemove [attr.aria-label]="'Remove ' + m.name">
                      <mat-icon>cancel</mat-icon>
                    </button>
                  </mat-chip>
                }
                @if (linkedMaterials().length === 0) {
                  <span class="mat-body-2 empty-hint">No raw materials linked yet.</span>
                }
              </mat-chip-set>
            </div>
          </mat-tab>
        </mat-tab-group>
      } @else {
        <ng-container *ngTemplateOutlet="generalForm" />
      }

      <ng-template #generalForm>
        <form [formGroup]="form" id="prod-form" (ngSubmit)="save()" novalidate>
          <div class="form-grid">
            <mat-form-field appearance="outline">
              <mat-label>Code</mat-label>
              <input matInput formControlName="productCode" [readonly]="!!product" data-testid="code" />
              @if (form.controls.productCode.hasError('required') && form.controls.productCode.touched) {
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
              <mat-label>Product type</mat-label>
              <mat-select formControlName="springType" data-testid="springType">
                @for (t of productTypes(); track t.code) {
                  <mat-option [value]="t.code">{{ t.name }}</mat-option>
                }
              </mat-select>
              @if (form.controls.springType.hasError('required') && form.controls.springType.touched) {
                <mat-error>Required</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Raw Material (Primary)</mat-label>
              <mat-select formControlName="primaryMaterialId">
                <mat-option [value]="null">— None —</mat-option>
                @for (m of materials(); track m.id) {
                  <mat-option [value]="m.id">{{ m.code }} – {{ m.name }}</mat-option>
                }
              </mat-select>
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
            <mat-form-field appearance="outline">
              <mat-label>Work centre</mat-label>
              <mat-select formControlName="workCentreId">
                <mat-option [value]="null">— None —</mat-option>
                @for (wc of workCentres(); track wc.id) {
                  <mat-option [value]="wc.id">{{ wc.code }} – {{ wc.name }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Weight per 1000 kgs</mat-label>
              <input matInput type="number" formControlName="weightPer1000Kgs" />
            </mat-form-field>
          </div>

          <mat-divider />

          <fieldset formGroupName="specification" class="spec-section">
            <legend class="mat-body-2">Core Geometry</legend>
            <div class="spec-grid">
              <mat-form-field appearance="outline">
                <mat-label>Wire Ø (mm)</mat-label>
                <input matInput type="number" formControlName="wireDiameter" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Outer Ø (mm)</mat-label>
                <input matInput type="number" formControlName="outerDiameter" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Free length (mm)</mat-label>
                <input matInput type="number" formControlName="freeLength" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Total coils</mat-label>
                <input matInput type="number" formControlName="numberOfCoils" />
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

          @if (errorMessage()) {
            <p class="form-error mat-body-2">{{ errorMessage() }}</p>
          }
        </form>
      </ng-template>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      @if (product?.status === 'DRAFT') {
        <button mat-stroked-button color="primary" type="button" (click)="saveAndActivate()" [disabled]="saving()">
          Save &amp; Activate
        </button>
      }
      <button mat-flat-button color="primary" form="prod-form" type="submit" [disabled]="saving()">
        @if (saving()) {
          <mat-spinner diameter="18" />
        } @else {
          Save Draft
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
      .spec-section {
        border: none;
        padding: 8px 0;
      }
      .spec-section legend {
        color: rgba(0, 0, 0, 0.54);
        font-size: 0.875rem;
        margin-bottom: 4px;
      }
      .spec-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
        gap: 0 12px;
      }
      .spec-grid mat-form-field {
        width: 100%;
      }
      .form-error {
        color: var(--mat-warn-color, #f44336);
      }
      .customers-tab {
        padding: 16px 0;
      }
      .customers-header {
        display: flex;
        align-items: center;
        gap: 16px;
        margin-bottom: 12px;
      }
      .add-customer-field {
        min-width: 260px;
      }
      .empty-hint {
        color: rgba(0, 0, 0, 0.54);
      }
    `,
  ],
})
export class ProductFormComponent implements OnInit {
  protected readonly productTypes = signal<ProductTypeDto[]>([]);
  protected readonly workCentres = signal<LookupOption[]>([]);
  protected readonly materials = signal<LookupItem[]>([]);
  protected readonly availableCustomers = signal<LookupOption[]>([]);
  protected readonly approvedCustomers = signal<CustomerRef[]>([]);
  protected readonly linkedMaterials = signal<MaterialRef[]>([]);
  protected readonly product = inject<ProductDto | undefined>(MAT_DIALOG_DATA);
  protected readonly attrDefs = signal<AttributeDefinitionDto[]>([]);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');

  protected readonly form = inject(FormBuilder).nonNullable.group({
    productCode: ['', Validators.required],
    name: ['', Validators.required],
    springType: ['' as ProductVariant, Validators.required],
    primaryMaterialId: [null as number | null],
    drawingNumber: [''],
    drawingRevision: [''],
    heatTreatment: [''],
    surfaceTreatment: [''],
    workCentreId: [null as number | null],
    weightPer1000Kgs: [null as number | null],
    specification: inject(FormBuilder).group({
      wireDiameter: [null as number | null],
      outerDiameter: [null as number | null],
      freeLength: [null as number | null],
      numberOfCoils: [null as number | null],
      activeCoils: [null as number | null],
    }),
  });

  protected readonly attrGroup = new FormGroup({});

  private readonly api = inject(ProductApi);
  private readonly lookupApi = inject(LookupApi);
  private readonly dialogRef = inject(MatDialogRef<ProductFormComponent>);

  ngOnInit(): void {
    this.lookupApi.workCentres().subscribe((list) => this.workCentres.set(list));
    this.lookupApi.materials().subscribe((list) => this.materials.set(list));
    if (this.product) {
      this.form.patchValue({
        ...this.product,
        primaryMaterialId: this.product.primaryMaterialId ?? null,
      });
      this.form.controls.specification.patchValue(this.product);
      this.form.controls.productCode.disable();
      this.lookupApi.customers().subscribe((list) => this.availableCustomers.set(list));
      this.loadApprovedCustomers();
      this.loadLinkedMaterials();
    }
    this.api.listProductTypes().subscribe((types) => this.productTypes.set(types));
    this.form.controls.springType.valueChanges.subscribe((type) => {
      if (type) this.loadAttrDefs(type);
    });
    const initType = this.form.controls.springType.value;
    if (initType) this.loadAttrDefs(initType);
  }

  private loadApprovedCustomers(): void {
    if (this.product) {
      this.api.getCustomers(this.product.id).subscribe((list) => this.approvedCustomers.set(list));
    }
  }

  private loadLinkedMaterials(): void {
    if (this.product) {
      this.api.getMaterials(this.product.id).subscribe((list) => this.linkedMaterials.set(list));
    }
  }

  protected isApproved(customerId: number): boolean {
    return this.approvedCustomers().some((c) => c.id === customerId);
  }

  protected isMaterialLinked(materialId: number): boolean {
    return this.linkedMaterials().some((m) => m.id === materialId);
  }

  protected availableMaterials(): LookupItem[] {
    return this.materials();
  }

  protected addCustomer(customerId: number | null): void {
    if (!customerId || !this.product) return;
    const ids = [...this.approvedCustomers().map((c) => c.id), customerId];
    this.api.setCustomers(this.product.id, ids).subscribe(() => this.loadApprovedCustomers());
  }

  protected removeCustomer(customerId: number): void {
    if (!this.product) return;
    const ids = this.approvedCustomers().filter((c) => c.id !== customerId).map((c) => c.id);
    this.api.setCustomers(this.product.id, ids).subscribe(() => this.loadApprovedCustomers());
  }

  protected addMaterial(materialId: number | null): void {
    if (!materialId || !this.product) return;
    const ids = [...this.linkedMaterials().map((m) => m.id), materialId];
    this.api.setMaterials(this.product.id, ids).subscribe(() => this.loadLinkedMaterials());
  }

  protected removeMaterial(materialId: number): void {
    if (!this.product) return;
    const ids = this.linkedMaterials().filter((m) => m.id !== materialId).map((m) => m.id);
    this.api.setMaterials(this.product.id, ids).subscribe(() => this.loadLinkedMaterials());
  }

  private loadAttrDefs(type: ProductVariant): void {
    this.api.getAttributeDefinitions(type).subscribe((defs) => {
      this.attrDefs.set(defs);
      if (this.product?.attributes) {
        const attrs = this.product.attributes;
        for (const [k, v] of Object.entries(attrs)) {
          if (this.attrGroup.contains(k)) this.attrGroup.get(k)?.setValue(v);
        }
      }
    });
  }

  save(activate = false): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const raw = this.form.getRawValue();
    const { specification, ...top } = raw;
    const req: SaveProductRequest = {
      ...top,
      ...specification,
      attributes: this.attrDefs().length > 0 ? this.attrGroup.value : undefined,
    };
    const obs = this.product ? this.api.update(this.product.id, req) : this.api.create(req);
    obs.subscribe({
      next: (saved) => {
        if (activate && saved.status === 'DRAFT') {
          this.api.activate(saved.id).subscribe({
            next: () => {
              this.saving.set(false);
              this.dialogRef.close(true);
            },
            error: (err) => {
              this.saving.set(false);
              this.errorMessage.set(err?.error?.detail ?? 'Activate failed.');
            },
          });
        } else {
          this.saving.set(false);
          this.dialogRef.close(true);
        }
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(err?.error?.detail ?? 'Save failed.');
      },
    });
  }

  saveAndActivate(): void {
    this.save(true);
  }
}
