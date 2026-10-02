import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ToastService } from '../../../core/notification/toast.service';
import { DialogService } from '../../../shared/dialogs/dialog.service';
import { collectAttributeValues, enforceRequired, syncAttributeControls } from '../../../shared/forms/dynamic-spec';
import { applyFieldErrors, errorText, problemOf } from '../../../shared/forms/server-errors';
import { codePattern, nonNegative } from '../../../shared/forms/validators';
import { DynamicSpecFormComponent } from '../../../shared/ui/dynamic-spec-form.component';
import { LookupPickerComponent } from '../../../shared/ui/lookup-picker.component';
import { LookupsApi } from '../api/lookups.api';
import { ProductsApi } from '../api/products.api';
import {
  AttributeDefinition,
  CORE_ATTRIBUTE_CODES,
  PartnerRef,
  ProductResponse,
  SpringType,
  SpringTypeInfo,
} from '../models';
import { SPRING_TYPE_LABELS } from './products-page.component';

/**
 * Create or edit a product. The fields for the spring type come from the attribute catalogue
 * ({@code GET /api/spring-types/{type}/attributes}), so adding an attribute server-side adds the input here.
 * A draft may be saved incomplete; activating needs every required attribute.
 */
@Component({
  selector: 'app-product-form-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    DynamicSpecFormComponent,
    LookupPickerComponent,
  ],
  template: `
    <header class="head">
      <h1>{{ product() ? 'Edit ' + product()!.code : 'New product' }}</h1>
    </header>
    @if (loadError()) {
      <p class="error" role="alert" data-testid="load-error">{{ loadError() }}</p>
    } @else {
      @if (product() && !canEdit()) {
        <p class="note" data-testid="read-only">
          This product is {{ product()!.status.toLowerCase() }} and cannot be edited. Activate it from the product list
          to edit it again.
        </p>
      }
      <form [formGroup]="form" (ngSubmit)="submit(false)" novalidate>
        <fieldset class="section">
          <legend>Identity</legend>
          <div class="grid">
            <mat-form-field appearance="outline">
              <mat-label>Product code</mat-label>
              <input matInput formControlName="productCode" autocomplete="off" data-testid="code" />
              @if (form.controls.productCode.touched && form.controls.productCode.invalid) {
                <mat-error>{{ error(form.controls.productCode) }}</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Name</mat-label>
              <input matInput formControlName="name" data-testid="name" />
              @if (form.controls.name.touched && form.controls.name.invalid) {
                <mat-error>{{ error(form.controls.name) }}</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Spring type</mat-label>
              <mat-select
                formControlName="springType"
                (selectionChange)="typeChanged($event.value)"
                data-testid="spring-type"
              >
                @for (t of types(); track t.type) {
                  <mat-option [value]="t.type">{{ t.label }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <app-lookup-picker
              label="Primary material"
              [search]="searchMaterials"
              formControlName="primaryMaterial"
              testId="material"
            />
          </div>
        </fieldset>

        <fieldset class="section">
          <legend>{{ typeLabel() }} specification</legend>
          @if (loadingAttributes()) {
            <p>Loading the attributes of this spring type…</p>
          }
          <app-dynamic-spec-form [form]="form" [attributes]="attributes()" />
          @if (isFreeForm()) {
            <p class="hint">Custom springs have no fixed attributes. Add the ones that describe this spring.</p>
            @for (row of custom.controls; track row; let i = $index) {
              <div class="custom-row" [formGroup]="row">
                <mat-form-field appearance="outline">
                  <mat-label>Attribute</mat-label>
                  <input matInput formControlName="key" [attr.data-testid]="'custom-key-' + i" />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Value</mat-label>
                  <input matInput formControlName="value" [attr.data-testid]="'custom-value-' + i" />
                </mat-form-field>
                <button
                  mat-icon-button
                  type="button"
                  (click)="removeCustom(i)"
                  [attr.aria-label]="'Remove attribute ' + (i + 1)"
                >
                  <mat-icon>delete</mat-icon>
                </button>
              </div>
            }
            <button mat-stroked-button type="button" (click)="addCustom()" data-testid="add-custom">
              <mat-icon>add</mat-icon> Add attribute
            </button>
          }
        </fieldset>

        <fieldset class="section">
          <legend>Treatment, drawing and commercial</legend>
          <div class="grid">
            @for (f of otherFields; track f.name) {
              <mat-form-field appearance="outline">
                <mat-label>{{ f.label }}</mat-label>
                <input
                  matInput
                  [type]="f.type ?? 'text'"
                  [step]="f.type === 'number' ? 'any' : null"
                  [formControlName]="f.name"
                  [attr.data-testid]="f.name"
                />
                @if (form.get(f.name)?.touched && form.get(f.name)?.invalid) {
                  <mat-error>{{ error(form.get(f.name)) }}</mat-error>
                }
              </mat-form-field>
            }
            <app-lookup-picker
              label="Customer"
              [search]="searchCustomers"
              formControlName="customer"
              testId="customer"
            />
          </div>
        </fieldset>

        @if (message()) {
          <p class="error" role="alert" data-testid="form-error">{{ message() }}</p>
        }
        @if (canEdit()) {
          <div class="actions">
            <a mat-button routerLink="/master/products">Cancel</a>
            <button mat-stroked-button type="submit" [disabled]="busy()" data-testid="save">
              {{ product() && product()!.status === 'ACTIVE' ? 'Save' : 'Save draft' }}
            </button>
            @if (!product() || product()!.status === 'DRAFT') {
              <button
                mat-flat-button
                color="primary"
                type="button"
                [disabled]="busy()"
                (click)="submit(true)"
                data-testid="save-activate"
              >
                Save &amp; activate
              </button>
            }
          </div>
        } @else {
          <div class="actions"><a mat-button routerLink="/master/products">Back</a></div>
        }
      </form>
    }
  `,
  styles: `
    .section {
      border: 1px solid #cfd8dc;
      border-radius: 6px;
      margin: 0 0 16px;
      padding: 8px 16px 16px;
    }
    .grid {
      display: grid;
      gap: 0 16px;
      grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    }
    .custom-row {
      align-items: baseline;
      display: grid;
      gap: 12px;
      grid-template-columns: 1fr 1fr auto;
    }
    .actions {
      display: flex;
      gap: 8px;
      justify-content: flex-end;
    }
    .error {
      color: #c62828;
    }
    .note,
    .hint {
      opacity: 0.8;
    }
  `,
})
export class ProductFormPageComponent implements OnInit {
  /** The route's {@code :id}; absent when creating. */
  readonly id = input<string | undefined>();

  private readonly api = inject(ProductsApi);
  private readonly lookups = inject(LookupsApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly dialogs = inject(DialogService);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly types = signal<SpringTypeInfo[]>(
    (Object.keys(SPRING_TYPE_LABELS) as SpringType[]).map((type) => ({
      type,
      label: SPRING_TYPE_LABELS[type],
      attributeCount: 0,
      freeForm: type === 'CUSTOM',
    })),
  );
  protected readonly product = signal<ProductResponse | null>(null);
  protected readonly attributes = signal<AttributeDefinition[]>([]);
  protected readonly loadingAttributes = signal(false);
  protected readonly loadError = signal('');
  protected readonly busy = signal(false);
  protected readonly message = signal('');
  protected readonly error = errorText;

  protected readonly form = this.fb.group({
    productCode: ['', [Validators.required, codePattern(2, 30)]],
    name: ['', [Validators.required, Validators.maxLength(150)]],
    springType: ['COMPRESSION' as SpringType, [Validators.required]],
    primaryMaterial: [null as PartnerRef | null],
    surfaceTreatment: ['', [Validators.maxLength(60)]],
    heatTreatment: ['', [Validators.maxLength(60)]],
    tolerance: ['', [Validators.maxLength(60)]],
    unitWeightKg: [null as number | null, [nonNegative]],
    uom: ['', [Validators.maxLength(10)]],
    drawingNumber: ['', [Validators.maxLength(40)]],
    drawingRevision: ['', [Validators.maxLength(10)]],
    customer: [null as PartnerRef | null],
    reorderLevel: [null as number | null, [nonNegative]],
    standardCost: [null as number | null, [nonNegative]],
    specifications: this.fb.group({}),
  });
  protected readonly custom = new FormArray<FormGroup<{ key: FormControl<string>; value: FormControl<string> }>>([]);

  protected readonly otherFields: { name: string; label: string; type?: string }[] = [
    { name: 'heatTreatment', label: 'Heat treatment' },
    { name: 'surfaceTreatment', label: 'Surface treatment' },
    { name: 'tolerance', label: 'Tolerance' },
    { name: 'drawingNumber', label: 'Drawing number' },
    { name: 'drawingRevision', label: 'Drawing revision' },
    { name: 'unitWeightKg', label: 'Unit weight (kg)', type: 'number' },
    { name: 'uom', label: 'Unit of measure' },
    { name: 'reorderLevel', label: 'Reorder level (pcs)', type: 'number' },
    { name: 'standardCost', label: 'Standard cost', type: 'number' },
  ];

  protected readonly canEdit = computed(() => {
    const p = this.product();
    return !p || p.allowedActions.includes('EDIT');
  });
  protected readonly typeLabel = computed(() => SPRING_TYPE_LABELS[this.currentType()]);
  protected readonly isFreeForm = computed(
    () => this.types().find((t) => t.type === this.currentType())?.freeForm ?? false,
  );
  private readonly currentType = signal<SpringType>('COMPRESSION');

  protected readonly searchMaterials = (q: string): Observable<PartnerRef[]> => this.lookups.materialsMatching(q);
  protected readonly searchCustomers = (q: string): Observable<PartnerRef[]> => this.lookups.customers(q);

  ngOnInit(): void {
    this.api.springTypes().subscribe({ next: (t) => this.types.set(t), error: () => undefined });
    const id = this.id();
    if (!id) {
      this.loadAttributes('COMPRESSION', null);
      return;
    }
    this.form.controls.productCode.disable();
    this.api.get(Number(id)).subscribe({
      next: (product) => this.show(product),
      error: (e: unknown) => this.loadError.set(problemOf(e)?.detail ?? 'The product could not be loaded.'),
    });
  }

  protected addCustom(key = '', value = ''): void {
    this.custom.push(this.fb.group({ key: [key], value: [value] }));
  }

  protected removeCustom(index: number): void {
    this.custom.removeAt(index);
  }

  /** Changing the type replaces the type-specific block; filled-in values are only dropped after confirmation. */
  protected async typeChanged(next: SpringType): Promise<void> {
    const previous = this.currentType();
    if (next === previous) {
      return;
    }
    if (this.hasTypeSpecificValues()) {
      const ok = await this.dialogs.confirm({
        title: 'Change the spring type?',
        message: 'The values entered for the current spring type will be cleared.',
        confirmLabel: 'Change type',
        danger: true,
      });
      if (!ok) {
        this.form.controls.springType.setValue(previous);
        return;
      }
    }
    this.custom.clear();
    this.loadAttributes(next, null);
  }

  protected submit(activate: boolean): void {
    const editing = this.product();
    enforceRequired(this.form, this.attributes(), activate || editing?.status === 'ACTIVE');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.message.set(
        activate ? 'Fill in every required attribute (marked *) to activate.' : 'Correct the highlighted fields.',
      );
      return;
    }
    this.busy.set(true);
    this.message.set('');
    const request$ = editing ? this.api.update(editing.id, this.body()) : this.api.create(this.body());
    request$.subscribe({
      next: (saved) => (activate ? this.activateSaved(saved) : this.done(saved, 'Product saved.')),
      error: (e: unknown) => this.failed(e, 'The product could not be saved.'),
    });
  }

  private activateSaved(saved: ProductResponse): void {
    this.api.activate(saved.id).subscribe({
      next: (active) => this.done(active, `${active.code} saved and activated.`),
      error: (e: unknown) => {
        // saved as a draft; stay on it so the missing attributes can be completed
        this.toast.info(`${saved.code} was saved as a draft.`);
        if (!this.product()) {
          void this.router.navigate(['/master/products', saved.id, 'edit'], { replaceUrl: true });
        }
        this.failed(e, 'The product could not be activated.');
      },
    });
  }

  private done(saved: ProductResponse, text: string): void {
    this.busy.set(false);
    this.toast.success(text.replace('Product', saved.code));
    void this.router.navigate(['/master/products']);
  }

  private failed(e: unknown, fallback: string): void {
    this.busy.set(false);
    const problem = problemOf(e);
    const unmatched = applyFieldErrors(this.form, problem);
    this.message.set(unmatched.length ? unmatched.join(' ') : (problem?.detail ?? fallback));
  }

  private body(): Record<string, unknown> {
    const v = this.form.getRawValue();
    const current = this.product();
    const { core, specifications } = collectAttributeValues(this.form, this.attributes());
    // a PUT replaces everything: keep core columns this type's catalogue does not describe, if the type is unchanged
    const preserved: Record<string, unknown> = {};
    if (current && current.springType === v.springType) {
      for (const code of CORE_ATTRIBUTE_CODES) {
        if (!(code in core)) {
          preserved[code] = current[code] ?? null;
        }
      }
    }
    if (this.isFreeForm()) {
      for (const row of this.custom.getRawValue()) {
        if (row.key.trim()) {
          specifications[row.key.trim()] = this.scalar(row.value);
        }
      }
    }
    const text = (s: string) => s.trim() || undefined;
    return {
      productCode: current ? current.code : v.productCode.trim(),
      name: v.name.trim(),
      springType: v.springType,
      primaryMaterialId: v.primaryMaterial?.id ?? null,
      ...preserved,
      ...core,
      surfaceTreatment: text(v.surfaceTreatment),
      heatTreatment: text(v.heatTreatment),
      tolerance: text(v.tolerance),
      unitWeightKg: v.unitWeightKg,
      uom: text(v.uom),
      drawingNumber: text(v.drawingNumber),
      drawingRevision: text(v.drawingRevision),
      customerId: v.customer?.id ?? null,
      reorderLevel: v.reorderLevel,
      standardCost: v.standardCost,
      specifications,
      version: current?.version,
    };
  }

  /** Free-form values: numbers and true/false keep their type, anything else is text. */
  private scalar(raw: string): string | number | boolean {
    const text = raw.trim();
    if (text === 'true' || text === 'false') {
      return text === 'true';
    }
    return text !== '' && /^-?\d+(\.\d+)?$/.test(text) ? Number(text) : text;
  }

  private hasTypeSpecificValues(): boolean {
    const { core, specifications } = collectAttributeValues(this.form, this.attributes());
    return (
      Object.values(core).some((x) => x !== null) ||
      Object.keys(specifications).length > 0 ||
      this.custom.getRawValue().some((r) => r.key.trim() || r.value.trim())
    );
  }

  private show(product: ProductResponse): void {
    this.product.set(product);
    this.form.patchValue({
      productCode: product.code,
      name: product.name,
      springType: product.springType,
      primaryMaterial: product.primaryMaterial ?? null,
      surfaceTreatment: product.surfaceTreatment ?? '',
      heatTreatment: product.heatTreatment ?? '',
      tolerance: product.tolerance ?? '',
      unitWeightKg: product.unitWeightKg ?? null,
      uom: product.uom ?? '',
      drawingNumber: product.drawingNumber ?? '',
      drawingRevision: product.drawingRevision ?? '',
      customer: product.customer ?? null,
      reorderLevel: product.reorderLevel ?? null,
      standardCost: product.standardCost ?? null,
    });
    this.loadAttributes(product.springType, product);
    if (!product.allowedActions.includes('EDIT')) {
      this.form.disable();
    } else if (product.status !== 'DRAFT') {
      // the server only lets a draft change its spring type (DESIGN.md section 6.3)
      this.form.controls.springType.disable();
    }
  }

  private loadAttributes(type: SpringType, product: ProductResponse | null): void {
    this.currentType.set(type);
    this.loadingAttributes.set(true);
    this.api.attributes(type).subscribe({
      next: (definitions) => {
        this.attributes.set(
          syncAttributeControls(this.form, this.attributes(), definitions, (a) =>
            product ? (a.storage === 'CORE' ? product[a.code] : product.specifications[a.code]) : null,
          ),
        );
        if (product && this.types().find((t) => t.type === type)?.freeForm) {
          this.custom.clear();
          for (const [key, value] of Object.entries(product.specifications)) {
            this.addCustom(key, String(value));
          }
        }
        if (product && !product.allowedActions.includes('EDIT')) {
          this.form.disable();
        }
        this.loadingAttributes.set(false);
      },
      error: (e: unknown) => {
        this.loadingAttributes.set(false);
        this.message.set(problemOf(e)?.detail ?? 'The attributes of this spring type could not be loaded.');
      },
    });
  }
}
