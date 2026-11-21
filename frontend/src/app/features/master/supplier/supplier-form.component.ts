import { Component, OnInit, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
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
import { LookupApi, LookupItem, LookupOption, PaymentTermOption } from '../../../shared/lookup.api';
import { CommentsPanelComponent } from '../../../shared/components/comments-panel/comments-panel.component';
import { AttachmentsPanelComponent } from '../../../shared/components/attachments-panel/attachments-panel.component';

const GST_PATTERN = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/;
const PAN_PATTERN = /^[A-Z]{5}[0-9]{4}[A-Z]{1}$/;

@Component({
  selector: 'app-supplier-form',
  standalone: true,
  imports: [
    NgTemplateOutlet,
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
              <ng-container
                [ngTemplateOutlet]="detailsForm"
                [ngTemplateOutletContext]="{ id: 'sup-form' }"
              ></ng-container>
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
        <ng-container [ngTemplateOutlet]="detailsForm" [ngTemplateOutletContext]="{ id: 'sup-form' }"></ng-container>
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

    <ng-template #detailsForm let-id="id">
      <form [formGroup]="form" [id]="id" (ngSubmit)="save()" novalidate class="form-grid">
        <!-- Row 1: Code (auto) + Name -->
        <mat-form-field appearance="outline">
          <mat-label>Code</mat-label>
          <input matInput formControlName="supplierCode" [placeholder]="supplier ? '' : 'Auto-generated'" />
          @if (!supplier) {
            <mat-hint>Leave blank to auto-generate</mat-hint>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" />
          @if (form.controls.name.hasError('required') && form.controls.name.touched) {
            <mat-error>Name is required</mat-error>
          }
        </mat-form-field>

        <!-- Row 2: Contact + Email -->
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

        <!-- Row 3: Phone + GST -->
        <mat-form-field appearance="outline">
          <mat-label>Phone</mat-label>
          <input matInput formControlName="phone" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>GST number</mat-label>
          <input matInput formControlName="gstNumber" />
          @if (form.controls.gstNumber.hasError('pattern') && form.controls.gstNumber.touched) {
            <mat-error>Invalid GST format (e.g. 29ABCDE1234F1Z5)</mat-error>
          }
        </mat-form-field>

        <!-- Row 4: PAN + Vendor Type -->
        <mat-form-field appearance="outline">
          <mat-label>PAN</mat-label>
          <input matInput formControlName="pan" maxlength="10" style="text-transform:uppercase" />
          @if (form.controls.pan.hasError('pattern') && form.controls.pan.touched) {
            <mat-error>Invalid PAN (e.g. ABCDE1234F)</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Vendor Type</mat-label>
          <mat-select formControlName="vendorTypeId">
            <mat-option [value]="null">— None —</mat-option>
            @for (vt of vendorTypes(); track vt.id) {
              <mat-option [value]="vt.id">{{ vt.code }} – {{ vt.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <!-- Row 5: Payment Terms (full width) -->
        <mat-form-field appearance="outline" class="full-span">
          <mat-label>Payment Terms</mat-label>
          <mat-select formControlName="paymentTermsId">
            <mat-option [value]="null">— Select —</mat-option>
            @for (pt of paymentTerms(); track pt.id) {
              <mat-option [value]="pt.id"
                >{{ pt.name }}
                @if (pt.netDays > 0) {
                  ({{ pt.netDays }} days)
                }
              </mat-option>
            }
          </mat-select>
        </mat-form-field>

        <!-- Address section -->
        <p class="section-label full-span">Address</p>
        <mat-form-field appearance="outline" class="full-span">
          <mat-label>Address Line 1</mat-label>
          <input matInput formControlName="addressLine1" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="full-span">
          <mat-label>Address Line 2</mat-label>
          <input matInput formControlName="addressLine2" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>City</mat-label>
          <input matInput formControlName="city" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>State</mat-label>
          <input matInput formControlName="stateName" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>PIN Code</mat-label>
          <input matInput formControlName="pinCode" maxlength="10" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Country</mat-label>
          <input matInput formControlName="country" />
        </mat-form-field>

        <!-- Notes -->
        <mat-form-field appearance="outline" class="full-span">
          <mat-label>Notes / Comments</mat-label>
          <textarea matInput formControlName="notes" rows="3"></textarea>
        </mat-form-field>

        @if (errorMessage()) {
          <p class="form-error mat-body-2">{{ errorMessage() }}</p>
        }
      </form>
    </ng-template>
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
      .section-label {
        grid-column: 1 / -1;
        font-size: 12px;
        font-weight: 500;
        color: rgba(0, 0, 0, 0.6);
        margin: 4px 0 -4px;
        text-transform: uppercase;
        letter-spacing: 0.5px;
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
  protected readonly form = inject(FormBuilder).group({
    supplierCode: [''],
    name: ['', Validators.required],
    contactPerson: [''],
    email: ['', Validators.email],
    phone: [''],
    pan: ['', Validators.pattern(PAN_PATTERN)],
    gstNumber: ['', Validators.pattern(GST_PATTERN)],
    vendorTypeId: [null as number | null],
    paymentTermsId: [null as number | null],
    addressLine1: [''],
    addressLine2: [''],
    city: [''],
    stateName: [''],
    pinCode: [''],
    country: ['India'],
    notes: [''],
  });

  protected readonly vendorTypes = signal<LookupOption[]>([]);
  protected readonly paymentTerms = signal<PaymentTermOption[]>([]);
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
    this.lookupApi.vendorTypes().subscribe((list) => this.vendorTypes.set(list));
    this.lookupApi.paymentTerms().subscribe((list) => this.paymentTerms.set(list.filter((pt) => pt.active)));
    this.lookupApi.materials().subscribe((list) => this.allMaterials.set(list));
    if (this.supplier) {
      this.form.patchValue({
        ...this.supplier,
        vendorTypeId: this.supplier.vendorTypeId ?? null,
        paymentTermsId: this.supplier.paymentTermsId ?? null,
        country: this.supplier.country ?? 'India',
      });
      this.form.controls.supplierCode.disable();
      this.loadApproved();
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
    const req = {
      supplierCode: raw.supplierCode?.trim() || undefined,
      name: raw.name ?? '',
      contactPerson: raw.contactPerson || undefined,
      email: raw.email || undefined,
      phone: raw.phone || undefined,
      pan: raw.pan?.trim().toUpperCase() || undefined,
      gstNumber: raw.gstNumber?.trim() || undefined,
      vendorTypeId: raw.vendorTypeId ?? null,
      paymentTermsId: raw.paymentTermsId ?? null,
      addressLine1: raw.addressLine1 || undefined,
      addressLine2: raw.addressLine2 || undefined,
      city: raw.city || undefined,
      stateName: raw.stateName || undefined,
      pinCode: raw.pinCode || undefined,
      country: raw.country || 'India',
      notes: raw.notes?.trim() || null,
    };
    const obs = this.supplier ? this.api.update(this.supplier.id, req) : this.api.create(req);
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
