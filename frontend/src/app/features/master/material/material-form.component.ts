import { Component, OnInit, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { applyFieldErrors } from '../../../core/models/problem.model';
import { MaterialApi, MaterialDto, UomDto, UomConversionDto, SavePurchasingInfoRequest } from './material.api';
import { LookupApi, LookupOption } from '../../../shared/lookup.api';
import { CommentsPanelComponent } from '../../../shared/components/comments-panel/comments-panel.component';
import { AttachmentsPanelComponent } from '../../../shared/components/attachments-panel/attachments-panel.component';

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
    MatTabsModule,
    MatTableModule,
    MatIconModule,
    CommentsPanelComponent,
    AttachmentsPanelComponent,
  ],
  template: `
    <h2 mat-dialog-title>{{ material ? 'Edit Material' : 'New Material' }}</h2>
    <mat-dialog-content>
      @if (material) {
        <mat-tab-group>
          <mat-tab label="Details">
            <div class="tab-pad">
              <form [formGroup]="form" id="material-form" (ngSubmit)="save()" novalidate class="form-grid">
                <mat-form-field appearance="outline">
                  <mat-label>Code</mat-label>
                  <input matInput formControlName="materialCode" data-testid="code" readonly />
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
              <div class="tab-actions">
                <button mat-flat-button color="primary" form="material-form" type="submit" [disabled]="saving()">
                  @if (saving()) {
                    <mat-spinner diameter="18" />
                  } @else {
                    Save
                  }
                </button>
              </div>
            </div>
          </mat-tab>

          <mat-tab label="UOM Conversions">
            <div class="tab-pad">
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
                      <mat-icon>delete</mat-icon>
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
                <button mat-flat-button color="primary" type="submit" [disabled]="convSaving()">Add</button>
              </form>
              @if (convError()) {
                <p class="form-error mat-body-2">{{ convError() }}</p>
              }
            </div>
          </mat-tab>

          <mat-tab label="Purchasing">
            <div class="tab-pad">
              <form [formGroup]="purchForm" (ngSubmit)="savePurchasing()" novalidate class="form-grid">
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
              <div class="tab-actions">
                <button mat-flat-button color="primary" (click)="savePurchasing()" [disabled]="purchSaving()">
                  @if (purchSaving()) {
                    <mat-spinner diameter="18" />
                  } @else {
                    Save
                  }
                </button>
              </div>
            </div>
          </mat-tab>

          <mat-tab label="Comments">
            <div class="tab-pad">
              <app-comments-panel
                [entityType]="'MATERIAL'"
                [entityId]="material?.id || 0"
              ></app-comments-panel>
            </div>
          </mat-tab>

          <mat-tab label="Attachments">
            <div class="tab-pad">
              <app-attachments-panel
                [entityType]="'MATERIAL'"
                [entityId]="material?.id || 0"
              ></app-attachments-panel>
            </div>
          </mat-tab>
        </mat-tab-group>
      } @else {
        <form [formGroup]="form" id="material-form" (ngSubmit)="save()" novalidate class="form-grid">
          <mat-form-field appearance="outline">
            <mat-label>Code</mat-label>
            <input matInput formControlName="materialCode" data-testid="code" />
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
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (material) {
        <button mat-button mat-dialog-close>Close</button>
      } @else {
        <button mat-button mat-dialog-close>Cancel</button>
        <button mat-flat-button color="primary" form="material-form" type="submit" [disabled]="saving()">
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
      .tab-pad {
        padding: 16px 0 8px;
      }
      .tab-actions {
        display: flex;
        justify-content: flex-end;
        margin-top: 8px;
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
    minStock: [null as number | null, [Validators.required, Validators.min(0)]],
    reorderLevel: [null as number | null, [Validators.required, Validators.min(0)]],
    maxStock: [null as number | null],
    standardCost: [null as number | null, [Validators.required, Validators.min(0)]],
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

  private readonly api = inject(MaterialApi);
  private readonly lookupApi = inject(LookupApi);
  private readonly dialogRef = inject(MatDialogRef<MaterialFormComponent>);

  ngOnInit(): void {
    this.api.listUoms().subscribe((list) => this.uoms.set(list));
    if (this.material) {
      this.form.patchValue(this.material);
      this.form.controls.materialCode.disable();
      this.loadConversions();
      this.loadPurchasingInfo();
      this.lookupApi.suppliers().subscribe((list) => this.suppliers.set(list));
    }
  }

  private loadConversions(): void {
    if (!this.material) return;
    this.api.getUomConversions(this.material.id).subscribe((list) => this.conversions.set(list));
  }

  private loadPurchasingInfo(): void {
    if (!this.material) return;
    this.api.getPurchasingInfo(this.material.id).subscribe({
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
    if (this.convForm.invalid || !this.material) {
      this.convForm.markAllAsTouched();
      return;
    }
    this.convSaving.set(true);
    this.convError.set('');
    const v = this.convForm.getRawValue();
    this.api.addUomConversion(this.material.id, v.altUom, v.factorToBase!).subscribe({
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
    if (!this.material) return;
    this.api.deleteUomConversion(this.material.id, altUom).subscribe({
      next: () => this.loadConversions(),
      error: (err) => this.convError.set(err?.error?.message ?? 'Delete failed.'),
    });
  }

  savePurchasing(): void {
    if (!this.material) return;
    this.purchSaving.set(true);
    this.purchError.set('');
    const v = this.purchForm.value;
    const req: SavePurchasingInfoRequest = {
      purchaseUom: v.purchaseUom ?? null,
      purchaseTaxCode: v.purchaseTaxCode ?? null,
      preferredSupplierId: v.preferredSupplierId ?? null,
    };
    this.api.savePurchasingInfo(this.material.id, req).subscribe({
      next: () => this.purchSaving.set(false),
      error: (err) => {
        this.purchSaving.set(false);
        this.purchError.set(err?.error?.message ?? 'Save failed.');
      },
    });
  }

  protected numberError(label: string, control: AbstractControl): string {
    if (control.hasError('required')) return `${label} is required`;
    if (control.hasError('min')) return `${label} cannot be negative`;
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
        if (!this.material) this.dialogRef.close(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(applyFieldErrors(this.form, err, 'Save failed.'));
      },
    });
  }
}
