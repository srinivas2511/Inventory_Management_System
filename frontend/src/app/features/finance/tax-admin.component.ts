import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { SessionStore } from '../../core/auth/session.store';
import { problemMessage } from '../../core/models/problem.model';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { LookupApi, LookupItem, LookupOption } from '../../shared/lookup.api';
import { DeterminationDto, FinanceApi, SlabDto } from './finance.api';

const SUPPLY_LABELS: Record<string, string> = {
  INTRA: 'Within the company state: CGST + SGST',
  INTER: 'Another state: IGST',
  UT: 'Union territory: CGST + UTGST',
};

/**
 * GST slabs and how they apply. The slabs are data (R9-7.5): this page shows what each slab charges for each kind of
 * supply, previews the tax of an amount for a supplier or customer, and sets the slab of a product. The rates are
 * sample data until finance has checked them against the current GST schedule.
 */
@Component({
  selector: 'app-tax-admin',
  standalone: true,
  imports: [
    DecimalPipe,
    HasPermissionDirective,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    ReactiveFormsModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">GST tax</h2>
    </div>
    <p class="note">
      The tax rates are sample data. Finance must check them against the current GST schedule before invoices are
      issued.
    </p>

    <h3 class="mat-h3">Slabs</h3>
    <table mat-table [dataSource]="slabs()" class="mat-elevation-z1" data-testid="slab-table">
      <ng-container matColumnDef="slabCode">
        <th mat-header-cell *matHeaderCellDef>Slab</th>
        <td mat-cell *matCellDef="let r">{{ r.slabCode }}</td>
      </ng-container>
      <ng-container matColumnDef="description">
        <th mat-header-cell *matHeaderCellDef>Description</th>
        <td mat-cell *matCellDef="let r">{{ r.description }}</td>
      </ng-container>
      <ng-container matColumnDef="components">
        <th mat-header-cell *matHeaderCellDef>Charges</th>
        <td mat-cell *matCellDef="let r">
          @for (supply of supplies(r); track supply) {
            <div>
              <strong>{{ supply }}</strong>
              {{ componentText(r, supply) }}
            </div>
          }
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
    </table>

    <h3 class="mat-h3">Preview the tax on an amount</h3>
    <form [formGroup]="preview" (ngSubmit)="runPreview()" class="row" novalidate>
      <mat-form-field appearance="outline">
        <mat-label>Document</mat-label>
        <mat-select formControlName="direction" (selectionChange)="partnerChanged()">
          <mat-option value="PURCHASE">Supplier invoice (input tax)</mat-option>
          <mat-option value="SALES">Customer invoice (output tax)</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>{{ preview.controls.direction.value === 'SALES' ? 'Customer' : 'Supplier' }}</mat-label>
        <mat-select formControlName="partnerId">
          <mat-option [value]="null">Not chosen (state assumed)</mat-option>
          @for (p of partners(); track p.id) {
            <mat-option [value]="p.id">{{ p.code }} · {{ p.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Slab</mat-label>
        <mat-select formControlName="slab">
          @for (s of slabs(); track s.slabCode) {
            <mat-option [value]="s.slabCode">{{ s.slabCode }} · {{ s.description }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Amount before tax</mat-label>
        <input matInput type="number" step="0.01" formControlName="amount" />
      </mat-form-field>
      <button mat-flat-button color="primary" type="submit" [disabled]="preview.invalid">Show tax</button>
    </form>
    @if (result(); as r) {
      <div class="result" data-testid="tax-result" role="status">
        <p>
          <strong>{{ supplyLabel(r.supplyType) }}</strong>
          @if (r.partnerStateAssumed) {
            <span> · the partner's state is not known, so it is assumed to be the company state</span>
          }
        </p>
        @for (l of r.lines; track l.taxCode) {
          <div>
            {{ l.taxType }} {{ l.ratePct | number: '1.0-2' }}% on {{ l.taxableAmount | number: '1.2-4' }} =
            {{ l.taxAmount | number: '1.2-4' }}
          </div>
        }
        <div>
          <strong>Tax {{ r.taxTotal | number: '1.2-4' }}</strong>
        </div>
      </div>
    }
    @if (previewError()) {
      <p class="error" role="alert">{{ previewError() }}</p>
    }

    <ng-container *appHasPermission="'TAX_MANAGE'">
      <h3 class="mat-h3">Slab of a product</h3>
      <form [formGroup]="assign" (ngSubmit)="saveAssignment()" class="row" novalidate>
        <mat-form-field appearance="outline">
          <mat-label>Product</mat-label>
          <mat-select formControlName="productId" (selectionChange)="loadAssignment()">
            @for (p of products(); track p.id) {
              <mat-option [value]="p.id">{{ p.code }} · {{ p.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Slab</mat-label>
          <mat-select formControlName="slabCode">
            <mat-option [value]="null">No slab</mat-option>
            @for (s of slabs(); track s.slabCode) {
              <mat-option [value]="s.slabCode">{{ s.slabCode }} · {{ s.description }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <button mat-flat-button color="primary" type="submit" [disabled]="assign.invalid">Save slab</button>
      </form>
    </ng-container>
  `,
  styles: [
    `
      .note {
        color: var(--mat-sys-on-surface-variant, #49454f);
      }
      .row {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        align-items: baseline;
      }
      .result {
        margin: 8px 0 16px;
        padding: 12px 16px;
        border-left: 3px solid var(--mat-sys-primary, #6750a4);
      }
      .error {
        color: var(--mat-sys-error, #b3261e);
      }
    `,
  ],
})
export class TaxAdminComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(FinanceApi);
  private readonly lookups = inject(LookupApi);
  private readonly snack = inject(MatSnackBar);
  private readonly session = inject(SessionStore);

  protected readonly columns = ['slabCode', 'description', 'components'];
  protected readonly slabs = signal<SlabDto[]>([]);
  protected readonly partners = signal<LookupOption[]>([]);
  protected readonly products = signal<LookupItem[]>([]);
  protected readonly result = signal<DeterminationDto | null>(null);
  protected readonly previewError = signal('');

  protected readonly preview = this.fb.group({
    direction: this.fb.nonNullable.control<'PURCHASE' | 'SALES'>('SALES'),
    partnerId: this.fb.control<number | null>(null),
    slab: this.fb.control<string | null>(null, Validators.required),
    amount: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.0001)]),
  });

  protected readonly assign = this.fb.group({
    productId: this.fb.control<number | null>(null, Validators.required),
    slabCode: this.fb.control<string | null>(null),
  });

  ngOnInit(): void {
    this.api.getTaxSlabs().subscribe((s) => this.slabs.set(s));
    this.partnerChanged();
    if (this.session.hasPermission('TAX_MANAGE')) {
      this.lookups.products().subscribe({ next: (p) => this.products.set(p), error: () => this.products.set([]) });
    }
  }

  protected supplies(slab: SlabDto): string[] {
    return Array.from(new Set(slab.components.map((c) => c.supplyType)));
  }

  protected componentText(slab: SlabDto, supply: string): string {
    const parts = slab.components.filter((c) => c.supplyType === supply);
    return parts.length === 0 ? '' : parts.map((c) => `${c.taxType} ${c.ratePct}%`).join(' + ');
  }

  protected supplyLabel(type: string): string {
    return SUPPLY_LABELS[type] ?? type;
  }

  protected partnerChanged(): void {
    this.preview.controls.partnerId.reset(null);
    const sales = this.preview.controls.direction.value === 'SALES';
    (sales ? this.lookups.customers() : this.lookups.suppliers()).subscribe({
      next: (list) => this.partners.set(list),
      error: () => this.partners.set([]),
    });
  }

  protected runPreview(): void {
    if (this.preview.invalid) return;
    const v = this.preview.getRawValue();
    this.previewError.set('');
    this.api.determineTax(v.direction, v.slab as string, v.partnerId, Number(v.amount)).subscribe({
      next: (r) => this.result.set(r),
      error: (err) => {
        this.result.set(null);
        this.previewError.set(problemMessage(err, 'The tax could not be determined.'));
      },
    });
  }

  protected loadAssignment(): void {
    const id = this.assign.controls.productId.value;
    if (!id) return;
    this.api.getProductSlab(id).subscribe((p) => this.assign.controls.slabCode.setValue(p.slabCode));
  }

  protected saveAssignment(): void {
    const v = this.assign.getRawValue();
    this.api.assignProductSlab(v.productId as number, v.slabCode).subscribe({
      next: (p) => this.snack.open(p.slabCode ? `Slab ${p.slabCode} set` : 'Slab cleared', 'OK', { duration: 4000 }),
      error: (err) => this.snack.open(problemMessage(err, 'The slab could not be saved.'), 'OK', { duration: 6000 }),
    });
  }
}
