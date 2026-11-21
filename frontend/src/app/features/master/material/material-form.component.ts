import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpContext } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { applyFieldErrors } from '../../../core/models/problem.model';
import { SKIP_ERROR_TOAST } from '../../../core/http/http-context-tokens';
import {
  MaterialApi,
  MaterialDto,
  SaveMaterialRequest,
  UomDto,
  UomConversionDto,
  SavePurchasingInfoRequest,
  ItemVendorMappingDto,
} from './material.api';
import { LookupApi, LookupOption } from '../../../shared/lookup.api';
import { CommentsPanelComponent } from '../../../shared/components/comments-panel/comments-panel.component';
import { AttachmentsPanelComponent } from '../../../shared/components/attachments-panel/attachments-panel.component';

const SKIP = new HttpContext().set(SKIP_ERROR_TOAST, true);

@Component({
  selector: 'app-material-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatIconModule,
    CommentsPanelComponent,
    AttachmentsPanelComponent,
  ],
  template: `
    <h2 mat-dialog-title>{{ material() ? 'Edit Material' : 'New Material' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="material-form" (ngSubmit)="save()" novalidate class="form-grid">
        <mat-form-field appearance="outline">
          <mat-label>Code</mat-label>
          <input
            matInput
            formControlName="materialCode"
            [placeholder]="material() ? '' : 'Auto-generated'"
            [readonly]="!!material()"
            data-testid="code"
          />
          @if (!material()) {
            <mat-hint>Leave blank to auto-generate</mat-hint>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-span">
          <mat-label>Material Stock Code</mat-label>
          <input matInput formControlName="name" data-testid="name" />
          @if (form.controls.name.hasError('required') && form.controls.name.touched) {
            <mat-error>Name is required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Material Type</mat-label>
          <input matInput formControlName="materialType" data-testid="materialType" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>UOM</mat-label>
          <mat-select formControlName="uom" data-testid="uom">
            @for (u of uoms(); track u.code) {
              <mat-option [value]="u.code">{{ u.name }} ({{ u.code }})</mat-option>
            }
          </mat-select>
          @if (form.controls.uom.hasError('required') && form.controls.uom.touched) {
            <mat-error>Unit of measure is required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>HSN Code</mat-label>
          <input matInput formControlName="hsnCode" maxlength="8" />
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
          <mat-label>Min Order Qty</mat-label>
          <input matInput type="number" formControlName="minOrderQty" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Min Stock</mat-label>
          <input matInput type="number" formControlName="minStock" data-testid="minStock" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Reorder Level</mat-label>
          <input matInput type="number" formControlName="reorderLevel" data-testid="reorderLevel" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Max Stock</mat-label>
          <input matInput type="number" formControlName="maxStock" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Standard Cost (₹)</mat-label>
          <input matInput type="number" formControlName="standardCost" data-testid="standardCost" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Source Type</mat-label>
          <mat-select formControlName="sourceType">
            <mat-option value="LOCAL">Local</mat-option>
            <mat-option value="IMPORT">Import</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-span">
          <mat-label>Description</mat-label>
          <textarea matInput formControlName="description" rows="2"></textarea>
        </mat-form-field>

        @if (errorMessage()) {
          <p class="form-error mat-body-2">{{ errorMessage() }}</p>
        }

        @if (material()) {
          <div class="save-row full-span">
            <button mat-flat-button color="primary" type="submit" [disabled]="saving()">
              @if (saving()) {
                <mat-spinner diameter="18" />
              } @else {
                Save
              }
            </button>
          </div>
        }
      </form>

      <mat-accordion class="panels">
        <mat-expansion-panel>
          <mat-expansion-panel-header>
            <mat-panel-title>UOM Conversions</mat-panel-title>
          </mat-expansion-panel-header>
          <table mat-table [dataSource]="conversions()" class="full-table">
            <ng-container matColumnDef="baseUom">
              <th mat-header-cell *matHeaderCellDef>Base UOM</th>
              <td mat-cell *matCellDef="let r">{{ r.baseUom }}</td>
            </ng-container>
            <ng-container matColumnDef="altUom">
              <th mat-header-cell *matHeaderCellDef>Alt UOM</th>
              <td mat-cell *matCellDef="let r">{{ r.altUom }}</td>
            </ng-container>
            <ng-container matColumnDef="factor">
              <th mat-header-cell *matHeaderCellDef>1 alt = N base</th>
              <td mat-cell *matCellDef="let r">{{ r.factorToBase }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let r">
                <button
                  mat-icon-button
                  color="warn"
                  aria-label="Remove conversion"
                  (click)="removeConversion(r.altUom)"
                >
                  <mat-icon aria-hidden="true">delete</mat-icon>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="convColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: convColumns"></tr>
          </table>
          @if (conversions().length === 0) {
            <p class="empty-hint mat-body-2">No alternate UOMs defined.</p>
          }
          <form [formGroup]="convForm" (ngSubmit)="addConversion()" novalidate class="inline-add-form">
            <mat-form-field appearance="outline">
              <mat-label>Alt UOM</mat-label>
              <mat-select formControlName="altUom">
                @for (u of uoms(); track u.code) {
                  <mat-option [value]="u.code">{{ u.name }} ({{ u.code }})</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>1 alt = N base</mat-label>
              <input matInput type="number" formControlName="factorToBase" />
            </mat-form-field>
            <button mat-flat-button color="primary" type="submit" [disabled]="convSaving() || !material()">Add</button>
          </form>
          @if (convError()) {
            <p class="form-error mat-body-2">{{ convError() }}</p>
          }
        </mat-expansion-panel>

        <mat-expansion-panel>
          <mat-expansion-panel-header>
            <mat-panel-title>Purchasing</mat-panel-title>
          </mat-expansion-panel-header>
          <form [formGroup]="purchForm" novalidate class="form-grid purch-grid">
            <mat-form-field appearance="outline">
              <mat-label>Purchase UOM</mat-label>
              <mat-select formControlName="purchaseUom">
                <mat-option [value]="null">— none —</mat-option>
                @for (u of uoms(); track u.code) {
                  <mat-option [value]="u.code">{{ u.name }} ({{ u.code }})</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Tax code</mat-label>
              <input matInput formControlName="purchaseTaxCode" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-span">
              <mat-label>Preferred supplier</mat-label>
              <mat-select formControlName="preferredSupplierId">
                <mat-option [value]="null">— none —</mat-option>
                @for (s of suppliers(); track s.id) {
                  <mat-option [value]="s.id">{{ s.name }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            @if (purchError()) {
              <p class="form-error mat-body-2">{{ purchError() }}</p>
            }
          </form>
          <div class="purch-actions">
            <button
              mat-flat-button
              color="primary"
              (click)="savePurchasing()"
              [disabled]="purchSaving() || !material()"
            >
              @if (purchSaving()) {
                <mat-spinner diameter="18" />
              } @else {
                Save
              }
            </button>
          </div>
        </mat-expansion-panel>

        <mat-expansion-panel>
          <mat-expansion-panel-header>
            <mat-panel-title>Vendors</mat-panel-title>
          </mat-expansion-panel-header>
          <table mat-table [dataSource]="vendorMappings()" class="full-table">
            <ng-container matColumnDef="supplier">
              <th mat-header-cell *matHeaderCellDef>Supplier</th>
              <td mat-cell *matCellDef="let r">{{ r.supplierName }}</td>
            </ng-container>
            <ng-container matColumnDef="supplierCode">
              <th mat-header-cell *matHeaderCellDef>Supplier Item Code</th>
              <td mat-cell *matCellDef="let r">{{ r.supplierItemCode }}</td>
            </ng-container>
            <ng-container matColumnDef="lastPrice">
              <th mat-header-cell *matHeaderCellDef>Last Price</th>
              <td mat-cell *matCellDef="let r">{{ r.lastPrice }}</td>
            </ng-container>
            <ng-container matColumnDef="moq">
              <th mat-header-cell *matHeaderCellDef>MOQ</th>
              <td mat-cell *matCellDef="let r">{{ r.moq }}</td>
            </ng-container>
            <ng-container matColumnDef="leadTime">
              <th mat-header-cell *matHeaderCellDef>Lead Time (days)</th>
              <td mat-cell *matCellDef="let r">{{ r.leadTimeDays }}</td>
            </ng-container>
            <ng-container matColumnDef="preferred">
              <th mat-header-cell *matHeaderCellDef>Preferred</th>
              <td mat-cell *matCellDef="let r">{{ r.preferred ? 'Yes' : 'No' }}</td>
            </ng-container>
            <ng-container matColumnDef="vendorActions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let r">
                <button
                  mat-icon-button
                  color="warn"
                  aria-label="Remove vendor mapping"
                  (click)="removeVendorMapping(r)"
                >
                  <mat-icon aria-hidden="true">delete</mat-icon>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="vendorCols"></tr>
            <tr mat-row *matRowDef="let row; columns: vendorCols"></tr>
          </table>
          @if (vendorMappings().length === 0) {
            <p class="empty-hint mat-body-2">No vendor mappings defined.</p>
          }
          <form [formGroup]="vendorForm" (ngSubmit)="addVendorMapping()" novalidate class="inline-add-form">
            <mat-form-field appearance="outline">
              <mat-label>Supplier</mat-label>
              <mat-select formControlName="supplierId">
                @for (s of suppliers(); track s.id) {
                  <mat-option [value]="s.id">{{ s.name }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Supplier Item Code</mat-label>
              <input matInput formControlName="supplierItemCode" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Last Price</mat-label>
              <input matInput type="number" formControlName="lastPrice" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>MOQ</mat-label>
              <input matInput type="number" formControlName="moq" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Lead Time (days)</mat-label>
              <input matInput type="number" formControlName="leadTimeDays" />
            </mat-form-field>
            <button mat-flat-button color="primary" type="submit" [disabled]="vendorSaving() || !material()">
              Add
            </button>
          </form>
          @if (vendorError()) {
            <p class="form-error mat-body-2">{{ vendorError() }}</p>
          }
        </mat-expansion-panel>

        <mat-expansion-panel>
          <mat-expansion-panel-header>
            <mat-panel-title>Comments</mat-panel-title>
          </mat-expansion-panel-header>
          @if (material()) {
            <app-comments-panel [entityType]="'MATERIAL'" [entityId]="material()!.id"></app-comments-panel>
          } @else {
            <p class="empty-hint mat-body-2">Save the material first to add comments.</p>
          }
        </mat-expansion-panel>

        <mat-expansion-panel>
          <mat-expansion-panel-header>
            <mat-panel-title>Attachments</mat-panel-title>
          </mat-expansion-panel-header>
          @if (material()) {
            <app-attachments-panel [entityType]="'MATERIAL'" [entityId]="material()!.id"></app-attachments-panel>
          } @else {
            <p class="empty-hint mat-body-2">Save the material first to upload attachments.</p>
          }
        </mat-expansion-panel>
      </mat-accordion>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (material()) {
        <button mat-button (click)="close()">Close</button>
      } @else {
        <button mat-button (click)="close()">Cancel</button>
        <button mat-flat-button color="primary" type="button" (click)="save()" [disabled]="saving()">
          @if (saving()) {
            <mat-spinner diameter="18" />
          } @else {
            Save
          }
        </button>
      }
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
        grid-column: 1 / -1;
      }
      .form-error {
        color: var(--mat-warn-color, #f44336);
        grid-column: 1 / -1;
      }
      .save-row {
        display: flex;
        justify-content: flex-end;
        padding: 4px 0 8px;
      }
      .panels {
        margin-top: 8px;
      }
      .purch-grid {
        padding: 8px 0 0;
      }
      .purch-actions {
        display: flex;
        justify-content: flex-end;
        padding: 8px 0;
      }
      .full-table {
        width: 100%;
      }
      .empty-hint {
        color: rgba(0, 0, 0, 0.6);
        margin: 8px 0;
      }
      .inline-add-form {
        display: flex;
        align-items: center;
        gap: 12px;
        margin-top: 12px;
        flex-wrap: wrap;
      }
      .inline-add-form mat-form-field {
        flex: 1;
        min-width: 120px;
      }
    `,
  ],
})
export class MaterialFormComponent implements OnInit {
  protected readonly material = signal<MaterialDto | undefined>(inject<MaterialDto | undefined>(MAT_DIALOG_DATA));
  protected readonly uoms = signal<UomDto[]>([]);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');

  protected readonly form = inject(FormBuilder).nonNullable.group({
    materialCode: [''],
    name: ['', [Validators.required, Validators.maxLength(150)]],
    materialType: [''],
    uom: ['', Validators.required],
    hsnCode: [''],
    grade: [''],
    diameterMm: [null as number | null],
    minOrderQty: [null as number | null],
    minStock: [null as number | null],
    reorderLevel: [null as number | null],
    maxStock: [null as number | null],
    standardCost: [null as number | null],
    sourceType: ['LOCAL'],
    description: [''],
  });

  // GC-1 UOM conversions
  protected readonly conversions = signal<UomConversionDto[]>([]);
  protected readonly convColumns = ['baseUom', 'altUom', 'factor', 'actions'];
  protected readonly convSaving = signal(false);
  protected readonly convError = signal('');
  protected readonly convForm = inject(FormBuilder).nonNullable.group({
    altUom: ['', Validators.required],
    factorToBase: [null as number | null, [Validators.required, Validators.min(0.000001)]],
  });

  // GC-2 purchasing info
  protected readonly purchSaving = signal(false);
  protected readonly purchError = signal('');
  protected readonly suppliers = signal<LookupOption[]>([]);
  protected readonly purchForm = inject(FormBuilder).group({
    purchaseUom: [null as string | null],
    purchaseTaxCode: [null as string | null],
    preferredSupplierId: [null as number | null],
  });

  // vendor mappings
  protected readonly vendorMappings = signal<ItemVendorMappingDto[]>([]);
  protected readonly vendorCols = [
    'supplier',
    'supplierCode',
    'lastPrice',
    'moq',
    'leadTime',
    'preferred',
    'vendorActions',
  ];
  protected readonly vendorSaving = signal(false);
  protected readonly vendorError = signal('');
  protected readonly vendorForm = inject(FormBuilder).group({
    supplierId: [null as number | null, Validators.required],
    supplierItemCode: [null as string | null],
    lastPrice: [null as number | null],
    moq: [0],
    leadTimeDays: [0],
  });

  private readonly api = inject(MaterialApi);
  private readonly lookupApi = inject(LookupApi);
  private readonly dialogRef = inject(MatDialogRef<MaterialFormComponent>);

  ngOnInit(): void {
    this.api.listUoms().subscribe((list) => this.uoms.set(list));
    this.lookupApi.suppliers().subscribe({
      next: (list) => this.suppliers.set(list),
      // eslint-disable-next-line @typescript-eslint/no-empty-function
      error: () => {},
    });
    if (this.material()) {
      this.patchForm(this.material()!);
      this.form.controls.materialCode.disable();
      this.loadEditData();
    }
  }

  close(): void {
    this.dialogRef.close(false);
  }

  private patchForm(m: MaterialDto): void {
    this.form.patchValue({
      materialCode: m.materialCode,
      name: m.name,
      materialType: m.materialType ?? '',
      uom: m.uom,
      hsnCode: m.hsnCode ?? '',
      grade: m.grade ?? '',
      diameterMm: m.diameterMm ?? null,
      minOrderQty: m.minOrderQty ?? null,
      minStock: m.minStock ?? null,
      reorderLevel: m.reorderLevel ?? null,
      maxStock: m.maxStock ?? null,
      standardCost: m.standardCost ?? null,
      sourceType: m.sourceType ?? 'LOCAL',
      description: m.description ?? '',
    });
  }

  private loadEditData(): void {
    this.loadConversions();
    this.loadPurchasingInfo();
    this.loadVendorMappings();
  }

  private loadConversions(): void {
    if (!this.material()) return;
    this.api.getUomConversions(this.material()!.id).subscribe({
      next: (list) => this.conversions.set(list),
      // eslint-disable-next-line @typescript-eslint/no-empty-function
      error: () => {},
    });
  }

  private loadPurchasingInfo(): void {
    if (!this.material()) return;
    this.api.getPurchasingInfo(this.material()!.id, SKIP).subscribe({
      next: (info) =>
        this.purchForm.patchValue({
          purchaseUom: info.purchaseUom ?? null,
          purchaseTaxCode: info.purchaseTaxCode ?? null,
          preferredSupplierId: info.preferredSupplierId ?? null,
        }),
      // eslint-disable-next-line @typescript-eslint/no-empty-function
      error: () => {},
    });
  }

  addConversion(): void {
    if (this.convForm.invalid || !this.material()) {
      this.convForm.markAllAsTouched();
      return;
    }
    this.convSaving.set(true);
    this.convError.set('');
    const v = this.convForm.getRawValue();
    this.api.addUomConversion(this.material()!.id, v.altUom, v.factorToBase!).subscribe({
      next: () => {
        this.convSaving.set(false);
        this.convForm.reset();
        this.loadConversions();
      },
      error: (err) => {
        this.convSaving.set(false);
        this.convError.set(err?.error?.message ?? 'Save failed.');
      },
    });
  }

  removeConversion(altUom: string): void {
    if (!this.material()) return;
    this.api.deleteUomConversion(this.material()!.id, altUom).subscribe({
      next: () => this.loadConversions(),
      error: (err) => this.convError.set(err?.error?.message ?? 'Delete failed.'),
    });
  }

  private loadVendorMappings(): void {
    if (!this.material()) return;
    this.api.listVendorMappings(this.material()!.id, SKIP).subscribe({
      next: (list) => this.vendorMappings.set(list),
      // eslint-disable-next-line @typescript-eslint/no-empty-function
      error: () => {},
    });
  }

  addVendorMapping(): void {
    if (this.vendorForm.invalid || !this.material()) {
      this.vendorForm.markAllAsTouched();
      return;
    }
    this.vendorSaving.set(true);
    this.vendorError.set('');
    const v = this.vendorForm.getRawValue();
    this.api
      .addVendorMapping(this.material()!.id, {
        supplierId: v.supplierId!,
        supplierItemCode: v.supplierItemCode ?? null,
        lastPrice: v.lastPrice ?? null,
        moq: v.moq ?? 0,
        leadTimeDays: v.leadTimeDays ?? 0,
        preferred: false,
      })
      .subscribe({
        next: () => {
          this.vendorSaving.set(false);
          this.vendorForm.reset({ moq: 0, leadTimeDays: 0 });
          this.loadVendorMappings();
        },
        error: (err) => {
          this.vendorSaving.set(false);
          this.vendorError.set(err?.error?.message ?? 'Save failed.');
        },
      });
  }

  removeVendorMapping(mapping: ItemVendorMappingDto): void {
    if (!this.material()) return;
    this.api.deleteVendorMapping(this.material()!.id, mapping.id).subscribe({
      next: () => this.loadVendorMappings(),
      error: (err) => this.vendorError.set(err?.error?.message ?? 'Delete failed.'),
    });
  }

  savePurchasing(): void {
    if (!this.material()) return;
    this.purchSaving.set(true);
    this.purchError.set('');
    const v = this.purchForm.value;
    const req: SavePurchasingInfoRequest = {
      purchaseUom: v.purchaseUom ?? null,
      purchaseTaxCode: v.purchaseTaxCode ?? null,
      preferredSupplierId: v.preferredSupplierId ?? null,
    };
    this.api.savePurchasingInfo(this.material()!.id, req).subscribe({
      next: () => this.purchSaving.set(false),
      error: (err) => {
        this.purchSaving.set(false);
        this.purchError.set(err?.error?.message ?? 'Save failed.');
      },
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const raw = this.form.getRawValue();
    const toNum = (v: number | null): number | null => (v != null && v !== ('' as unknown) ? +v : null);
    const req: SaveMaterialRequest = {
      materialCode: raw.materialCode?.trim() || undefined,
      name: raw.name,
      materialType: raw.materialType?.trim() || undefined,
      uom: raw.uom,
      hsnCode: raw.hsnCode?.trim() || undefined,
      grade: raw.grade?.trim() || undefined,
      diameterMm: toNum(raw.diameterMm),
      minOrderQty: toNum(raw.minOrderQty),
      minStock: toNum(raw.minStock),
      reorderLevel: toNum(raw.reorderLevel),
      maxStock: toNum(raw.maxStock),
      standardCost: toNum(raw.standardCost),
      sourceType: raw.sourceType || 'LOCAL',
      description: raw.description?.trim() || undefined,
      serviceItem: false,
      customerMaterial: false,
    };
    const obs = this.material() ? this.api.update(this.material()!.id, req) : this.api.create(req);
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
