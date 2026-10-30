import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { applyFieldErrors } from '../../../core/models/problem.model';
import { SupplierApi, SupplierDto, ApprovedMaterialDto } from './supplier.api';
import { LookupApi, LookupItem } from '../../../shared/lookup.api';
import { CommentsPanelComponent } from '../../../shared/components/comments-panel/comments-panel.component';
import { AttachmentsPanelComponent } from '../../../shared/components/attachments-panel/attachments-panel.component';

const GST_PATTERN = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/;

@Component({
  selector: 'app-supplier-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTabsModule,
    MatSelectModule,
    MatChipsModule,
    MatIconModule,
    CommentsPanelComponent,
    AttachmentsPanelComponent,
  ],
  template: `
    <h2 mat-dialog-title>{{ supplier ? 'Edit Supplier' : 'New Supplier' }}</h2>
    <mat-dialog-content>
      @if (supplier) {
        <mat-tab-group>
          <mat-tab label="Details">
            <div class="tab-pad">
              <form [formGroup]="form" id="sup-form" (ngSubmit)="save()" novalidate class="form-grid">
                <mat-form-field appearance="outline">
                  <mat-label>Code</mat-label>
                  <input matInput formControlName="supplierCode" readonly />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Name</mat-label>
                  <input matInput formControlName="name" />
                  @if (form.controls.name.hasError('required') && form.controls.name.touched) {
                    <mat-error>Required</mat-error>
                  }
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Contact person</mat-label>
                  <input matInput formControlName="contactPerson" />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Email</mat-label>
                  <input matInput type="email" formControlName="email" />
                  @if (form.controls.email.hasError('email') && form.controls.email.touched) {
                    <mat-error>Invalid email</mat-error>
                  }
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
                @if (errorMessage()) {
                  <p class="form-error mat-body-2">{{ errorMessage() }}</p>
                }
              </form>
              <div class="tab-actions">
                <button mat-flat-button color="primary" form="sup-form" type="submit" [disabled]="saving()">
                  @if (saving()) {
                    <mat-spinner diameter="18" />
                  } @else {
                    Save
                  }
                </button>
              </div>
            </div>
          </mat-tab>

          <mat-tab label="Approved Materials">
            <div class="tab-pad">
              <p class="mat-body-2 hint">Materials this supplier is approved to supply.</p>
              <mat-chip-set aria-label="Approved materials">
                @for (m of approvedMaterials(); track m.id) {
                  <mat-chip
                    [removable]="true"
                    (removed)="removeApprovedMaterial(m.id)"
                    aria-label="Remove {{ m.name }}"
                  >
                    {{ m.code }} — {{ m.name }}
                    <mat-icon matChipRemove>cancel</mat-icon>
                  </mat-chip>
                }
              </mat-chip-set>
              @if (approvedMaterials().length === 0) {
                <p class="empty-hint mat-body-2">No approved materials.</p>
              }
              <div class="inline-add-form">
                <mat-form-field appearance="outline" class="add-select">
                  <mat-label>Add material</mat-label>
                  <mat-select [(value)]="selectedMaterialId">
                    @for (m of availableMaterials(); track m.id) {
                      <mat-option [value]="m.id">{{ m.code }} — {{ m.name }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <button
                  mat-flat-button
                  color="primary"
                  (click)="addApprovedMaterial()"
                  [disabled]="!selectedMaterialId || approvedSaving()"
                >
                  Add
                </button>
              </div>
              @if (approvedError()) {
                <p class="form-error mat-body-2">{{ approvedError() }}</p>
              }
            </div>
          </mat-tab>

          <mat-tab label="Comments">
            <div class="tab-pad">
              <app-comments-panel [entityType]="'SUPPLIER'" [entityId]="supplier?.id || 0"></app-comments-panel>
            </div>
          </mat-tab>

          <mat-tab label="Attachments">
            <div class="tab-pad">
              <app-attachments-panel [entityType]="'SUPPLIER'" [entityId]="supplier?.id || 0"></app-attachments-panel>
            </div>
          </mat-tab>
        </mat-tab-group>
      } @else {
        <form [formGroup]="form" id="sup-form" (ngSubmit)="save()" novalidate class="form-grid">
          <mat-form-field appearance="outline">
            <mat-label>Code</mat-label>
            <input matInput formControlName="supplierCode" />
            @if (form.controls.supplierCode.hasError('required') && form.controls.supplierCode.touched) {
              <mat-error>Required</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Name</mat-label>
            <input matInput formControlName="name" />
            @if (form.controls.name.hasError('required') && form.controls.name.touched) {
              <mat-error>Required</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Contact person</mat-label>
            <input matInput formControlName="contactPerson" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Email</mat-label>
            <input matInput type="email" formControlName="email" />
            @if (form.controls.email.hasError('email') && form.controls.email.touched) {
              <mat-error>Invalid email</mat-error>
            }
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
          @if (errorMessage()) {
            <p class="form-error mat-body-2">{{ errorMessage() }}</p>
          }
        </form>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      @if (supplier) {
        <button mat-button mat-dialog-close>Close</button>
      } @else {
        <button mat-button mat-dialog-close>Cancel</button>
        <button mat-flat-button color="primary" form="sup-form" type="submit" [disabled]="saving()">
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
        grid-column: 1/-1;
      }
      .form-error {
        color: var(--mat-warn-color, #f44336);
        grid-column: 1/-1;
      }
      .tab-pad {
        padding: 16px 0 8px;
      }
      .tab-actions {
        display: flex;
        justify-content: flex-end;
        margin-top: 8px;
      }
      .hint {
        color: rgba(0, 0, 0, 0.6);
        margin-bottom: 12px;
      }
      .empty-hint {
        color: rgba(0, 0, 0, 0.6);
        margin: 8px 0;
      }
      .inline-add-form {
        display: flex;
        align-items: center;
        gap: 12px;
        margin-top: 16px;
      }
      .add-select {
        flex: 1;
      }
    `,
  ],
})
export class SupplierFormComponent implements OnInit {
  protected readonly supplier = inject<SupplierDto | undefined>(MAT_DIALOG_DATA);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly form = inject(FormBuilder).nonNullable.group({
    supplierCode: ['', Validators.required],
    name: ['', Validators.required],
    contactPerson: [''],
    email: ['', Validators.email],
    phone: [''],
    gstNumber: ['', Validators.pattern(GST_PATTERN)],
    address: [''],
    paymentTerms: [''],
  });

  protected readonly approvedMaterials = signal<ApprovedMaterialDto[]>([]);
  protected readonly allMaterials = signal<LookupItem[]>([]);
  protected readonly approvedSaving = signal(false);
  protected readonly approvedError = signal('');
  protected selectedMaterialId: number | null = null;

  protected get availableMaterials(): () => LookupItem[] {
    return () => this.allMaterials().filter((m) => !this.approvedMaterials().some((a) => a.id === m.id));
  }

  private readonly api = inject(SupplierApi);
  private readonly lookupApi = inject(LookupApi);
  private readonly dialogRef = inject(MatDialogRef<SupplierFormComponent>);

  ngOnInit(): void {
    if (this.supplier) {
      this.form.patchValue(this.supplier);
      this.form.controls.supplierCode.disable();
      this.loadApproved();
      this.lookupApi.materials().subscribe((list) => this.allMaterials.set(list));
    }
  }

  private loadApproved(): void {
    if (!this.supplier) return;
    this.api.getApprovedMaterials(this.supplier.id).subscribe((list) => this.approvedMaterials.set(list));
  }

  addApprovedMaterial(): void {
    if (!this.supplier || !this.selectedMaterialId) return;
    this.approvedSaving.set(true);
    this.approvedError.set('');
    const ids = [...this.approvedMaterials().map((m) => m.id), this.selectedMaterialId];
    this.api.setApprovedMaterials(this.supplier.id, ids).subscribe({
      next: () => {
        this.approvedSaving.set(false);
        this.selectedMaterialId = null;
        this.loadApproved();
      },
      error: (err) => {
        this.approvedSaving.set(false);
        this.approvedError.set(err?.error?.message ?? 'Save failed.');
      },
    });
  }

  removeApprovedMaterial(materialId: number): void {
    if (!this.supplier) return;
    const ids = this.approvedMaterials()
      .filter((m) => m.id !== materialId)
      .map((m) => m.id);
    this.api.setApprovedMaterials(this.supplier.id, ids).subscribe({
      next: () => this.loadApproved(),
      error: (err) => this.approvedError.set(err?.error?.message ?? 'Remove failed.'),
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const raw = this.form.getRawValue();
    // GST is optional: a blank field means 'none', not an empty string the API must reject.
    const req = { ...raw, gstNumber: raw.gstNumber.trim() === '' ? undefined : raw.gstNumber.trim() };
    const obs = this.supplier ? this.api.update(this.supplier.id, req) : this.api.create(req);
    obs.subscribe({
      next: () => {
        this.saving.set(false);
        if (!this.supplier) this.dialogRef.close(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(applyFieldErrors(this.form, err, 'Save failed.'));
      },
    });
  }
}
