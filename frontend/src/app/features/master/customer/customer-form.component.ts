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
import { MatTableModule } from '@angular/material/table';
import { applyFieldErrors } from '../../../core/models/problem.model';
import { CommentsPanelComponent } from '../../../shared/components/comments-panel/comments-panel.component';
import { AttachmentsPanelComponent } from '../../../shared/components/attachments-panel/attachments-panel.component';
import {
  CustomerApi,
  CustomerDto,
  ApprovedProductDto,
  CustomerSpecOverrideDto,
  SaveSpecOverrideRequest,
} from './customer.api';
import { LookupApi, LookupItem } from '../../../shared/lookup.api';

const GST_PATTERN = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/;

@Component({
  selector: 'app-customer-form',
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
    MatTableModule,
    CommentsPanelComponent,
    AttachmentsPanelComponent,
  ],
  template: `
    <h2 mat-dialog-title>{{ customer ? 'Edit Customer' : 'New Customer' }}</h2>
    <mat-dialog-content>
      @if (customer) {
        <mat-tab-group>
          <mat-tab label="Details">
            <div class="tab-pad">
              <form [formGroup]="form" id="cust-form" (ngSubmit)="save()" novalidate class="form-grid">
                <mat-form-field appearance="outline">
                  <mat-label>Code</mat-label>
                  <input matInput formControlName="customerCode" readonly />
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
                <button mat-flat-button color="primary" form="cust-form" type="submit" [disabled]="saving()">
                  @if (saving()) {
                    <mat-spinner diameter="18" />
                  } @else {
                    Save
                  }
                </button>
              </div>
            </div>
          </mat-tab>

          <mat-tab label="Approved Products">
            <div class="tab-pad">
              <p class="hint mat-body-2">Products this customer is approved to purchase.</p>
              <mat-chip-set aria-label="Approved products">
                @for (p of approvedProducts(); track p.id) {
                  <mat-chip [removable]="true" (removed)="removeApprovedProduct(p.id)" aria-label="Remove {{ p.name }}">
                    {{ p.code }} — {{ p.name }}
                    <mat-icon matChipRemove>cancel</mat-icon>
                  </mat-chip>
                }
              </mat-chip-set>
              @if (approvedProducts().length === 0) {
                <p class="empty-hint mat-body-2">No approved products.</p>
              }
              <div class="inline-add-form">
                <mat-form-field appearance="outline" class="add-select">
                  <mat-label>Add product</mat-label>
                  <mat-select [(value)]="selectedProductId">
                    @for (p of availableProducts(); track p.id) {
                      <mat-option [value]="p.id">{{ p.code }} — {{ p.name }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <button
                  mat-flat-button
                  color="primary"
                  (click)="addApprovedProduct()"
                  [disabled]="!selectedProductId || approvedSaving()"
                >
                  Add
                </button>
              </div>
              @if (approvedError()) {
                <p class="form-error mat-body-2">{{ approvedError() }}</p>
              }
            </div>
          </mat-tab>

          <mat-tab label="Product Spec Overrides">
            <div class="tab-pad">
              <p class="hint mat-body-2">Override inspection tolerances for a product on orders for this customer.</p>
              <mat-form-field appearance="outline">
                <mat-label>Product</mat-label>
                <mat-select [(value)]="specProductId" (valueChange)="onSpecProductChange($event)">
                  @for (p of approvedProducts(); track p.id) {
                    <mat-option [value]="p.id">{{ p.code }} — {{ p.name }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>

              @if (specProductId) {
                <table mat-table [dataSource]="specOverrides()" class="full-table">
                  <ng-container matColumnDef="param">
                    <th mat-header-cell *matHeaderCellDef>Parameter</th>
                    <td mat-cell *matCellDef="let r">{{ r.parameterName }}</td>
                  </ng-container>
                  <ng-container matColumnDef="lower">
                    <th mat-header-cell *matHeaderCellDef>Lower</th>
                    <td mat-cell *matCellDef="let r">{{ r.lowerLimit ?? '—' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="upper">
                    <th mat-header-cell *matHeaderCellDef>Upper</th>
                    <td mat-cell *matCellDef="let r">{{ r.upperLimit ?? '—' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="nominal">
                    <th mat-header-cell *matHeaderCellDef>Nominal</th>
                    <td mat-cell *matCellDef="let r">{{ r.nominal ?? '—' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="actions">
                    <th mat-header-cell *matHeaderCellDef></th>
                    <td mat-cell *matCellDef="let r">
                      <button
                        mat-icon-button
                        color="warn"
                        aria-label="Remove override"
                        (click)="removeSpecOverride(r.parameterName)"
                      >
                        <mat-icon>delete</mat-icon>
                      </button>
                    </td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="specColumns"></tr>
                  <tr mat-row *matRowDef="let row; columns: specColumns"></tr>
                </table>
                @if (specOverrides().length === 0) {
                  <p class="empty-hint mat-body-2">No overrides for this product.</p>
                }

                <form
                  [formGroup]="specForm"
                  (ngSubmit)="addSpecOverride()"
                  novalidate
                  class="inline-add-form spec-add-form"
                >
                  <mat-form-field appearance="outline">
                    <mat-label>Parameter name</mat-label>
                    <input matInput formControlName="parameterName" />
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Lower limit</mat-label>
                    <input matInput type="number" formControlName="lowerLimit" />
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Upper limit</mat-label>
                    <input matInput type="number" formControlName="upperLimit" />
                  </mat-form-field>
                  <mat-form-field appearance="outline">
                    <mat-label>Nominal</mat-label>
                    <input matInput type="number" formControlName="nominal" />
                  </mat-form-field>
                  <button mat-flat-button color="primary" type="submit" [disabled]="specSaving()">Add / Update</button>
                </form>
                @if (specError()) {
                  <p class="form-error mat-body-2">{{ specError() }}</p>
                }
              }
            </div>
          </mat-tab>

          <mat-tab label="Comments">
            <div class="tab-pad">
              <app-comments-panel [entityType]="'CUSTOMER'" [entityId]="customer?.id || 0"></app-comments-panel>
            </div>
          </mat-tab>

          <mat-tab label="Attachments">
            <div class="tab-pad">
              <app-attachments-panel [entityType]="'CUSTOMER'" [entityId]="customer?.id || 0"></app-attachments-panel>
            </div>
          </mat-tab>
        </mat-tab-group>
      } @else {
        <form [formGroup]="form" id="cust-form" (ngSubmit)="save()" novalidate class="form-grid">
          <mat-form-field appearance="outline">
            <mat-label>Code</mat-label>
            <input matInput formControlName="customerCode" />
            @if (form.controls.customerCode.hasError('required') && form.controls.customerCode.touched) {
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
      @if (customer) {
        <button mat-button mat-dialog-close>Close</button>
      } @else {
        <button mat-button mat-dialog-close>Cancel</button>
        <button mat-flat-button color="primary" form="cust-form" type="submit" [disabled]="saving()">
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
        flex-wrap: wrap;
      }
      .spec-add-form mat-form-field {
        flex: 1;
        min-width: 110px;
      }
      .add-select {
        flex: 1;
      }
      .full-table {
        width: 100%;
        margin-top: 12px;
      }
    `,
  ],
})
export class CustomerFormComponent implements OnInit {
  protected readonly customer = inject<CustomerDto | undefined>(MAT_DIALOG_DATA);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly form = inject(FormBuilder).nonNullable.group({
    customerCode: ['', Validators.required],
    name: ['', Validators.required],
    contactPerson: [''],
    email: ['', Validators.email],
    phone: [''],
    gstNumber: ['', Validators.pattern(GST_PATTERN)],
    address: [''],
    paymentTerms: [''],
  });

  // GC-2 approved products
  protected readonly approvedProducts = signal<ApprovedProductDto[]>([]);
  protected readonly allProducts = signal<LookupItem[]>([]);
  protected readonly approvedSaving = signal(false);
  protected readonly approvedError = signal('');
  protected selectedProductId: number | null = null;

  protected get availableProducts(): () => LookupItem[] {
    return () => this.allProducts().filter((p) => !this.approvedProducts().some((a) => a.id === p.id));
  }

  // GC-5 product spec overrides
  protected specProductId: number | null = null;
  protected readonly specOverrides = signal<CustomerSpecOverrideDto[]>([]);
  protected readonly specColumns = ['param', 'lower', 'upper', 'nominal', 'actions'];
  protected readonly specSaving = signal(false);
  protected readonly specError = signal('');
  protected readonly specForm = inject(FormBuilder).nonNullable.group({
    parameterName: ['', Validators.required],
    lowerLimit: [null as number | null],
    upperLimit: [null as number | null],
    nominal: [null as number | null],
  });

  private readonly api = inject(CustomerApi);
  private readonly lookupApi = inject(LookupApi);
  private readonly dialogRef = inject(MatDialogRef<CustomerFormComponent>);

  ngOnInit(): void {
    if (this.customer) {
      this.form.patchValue(this.customer);
      this.form.controls.customerCode.disable();
      this.loadApproved();
      this.lookupApi.products().subscribe((list) => this.allProducts.set(list));
    }
  }

  private loadApproved(): void {
    if (!this.customer) return;
    this.api.getApprovedProducts(this.customer.id).subscribe((list) => this.approvedProducts.set(list));
  }

  addApprovedProduct(): void {
    if (!this.customer || !this.selectedProductId) return;
    this.approvedSaving.set(true);
    this.approvedError.set('');
    const ids = [...this.approvedProducts().map((p) => p.id), this.selectedProductId];
    this.api.setApprovedProducts(this.customer.id, ids).subscribe({
      next: () => {
        this.approvedSaving.set(false);
        this.selectedProductId = null;
        this.loadApproved();
      },
      error: (err) => {
        this.approvedSaving.set(false);
        this.approvedError.set(err?.error?.message ?? 'Save failed.');
      },
    });
  }

  removeApprovedProduct(productId: number): void {
    if (!this.customer) return;
    const ids = this.approvedProducts()
      .filter((p) => p.id !== productId)
      .map((p) => p.id);
    this.api.setApprovedProducts(this.customer.id, ids).subscribe({
      next: () => this.loadApproved(),
      error: (err) => this.approvedError.set(err?.error?.message ?? 'Remove failed.'),
    });
  }

  onSpecProductChange(productId: number): void {
    this.specProductId = productId;
    this.specOverrides.set([]);
    this.specError.set('');
    if (this.customer && productId) {
      this.api.getProductSpecOverrides(this.customer.id, productId).subscribe((list) => this.specOverrides.set(list));
    }
  }

  addSpecOverride(): void {
    if (this.specForm.invalid || !this.customer || !this.specProductId) {
      this.specForm.markAllAsTouched();
      return;
    }
    this.specSaving.set(true);
    this.specError.set('');
    const v = this.specForm.getRawValue();
    const req: SaveSpecOverrideRequest = {
      parameterName: v.parameterName,
      lowerLimit: v.lowerLimit,
      upperLimit: v.upperLimit,
      nominal: v.nominal,
    };
    this.api.saveProductSpecOverride(this.customer.id, this.specProductId, req).subscribe({
      next: () => {
        this.specSaving.set(false);
        this.specForm.reset();
        this.api
          .getProductSpecOverrides(this.customer!.id, this.specProductId!)
          .subscribe((list) => this.specOverrides.set(list));
      },
      error: (err) => {
        this.specSaving.set(false);
        this.specError.set(err?.error?.message ?? 'Save failed.');
      },
    });
  }

  removeSpecOverride(parameterName: string): void {
    if (!this.customer || !this.specProductId) return;
    this.api.deleteProductSpecOverride(this.customer.id, this.specProductId, parameterName).subscribe({
      next: () =>
        this.api
          .getProductSpecOverrides(this.customer!.id, this.specProductId!)
          .subscribe((list) => this.specOverrides.set(list)),
      error: (err) => this.specError.set(err?.error?.message ?? 'Delete failed.'),
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
    const obs = this.customer ? this.api.update(this.customer.id, req) : this.api.create(req);
    obs.subscribe({
      next: () => {
        this.saving.set(false);
        if (!this.customer) this.dialogRef.close(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(applyFieldErrors(this.form, err, 'Save failed.'));
      },
    });
  }
}
