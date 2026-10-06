import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ProductApi, ProductDto } from '../master/product/product.api';
import { CrmApi, CrmCustomerDto, QuotationDto } from './crm.api';

type LineGroup = FormGroup<{
  productId: FormControl<number>;
  quantity: FormControl<number>;
  unitPrice: FormControl<number | null>;
  discountPct: FormControl<number | null>;
}>;

/** Net price rounded to 4 places, exactly as the server rounds it, so the preview matches the saved total. */
export function netUnitPrice(unitPrice: number, discountPct: number): number {
  return Math.round(unitPrice * (100 - discountPct) * 100) / 10000;
}

@Component({
  selector: 'app-crm-quotation-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">{{ editingId() ? 'Edit quotation' : 'New quotation' }}</h2>
      <a mat-button routerLink="/crm/quotations"><mat-icon>arrow_back</mat-icon> Quotations</a>
    </div>

    <form [formGroup]="form" (ngSubmit)="save()">
      <div class="row">
        <mat-form-field appearance="outline">
          <mat-label>Customer</mat-label>
          <mat-select formControlName="customerId">
            @for (c of customers(); track c.id) {
              <mat-option [value]="c.id">{{ c.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Opportunity ID (optional)</mat-label>
          <input matInput type="number" formControlName="opportunityId" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Valid until</mat-label>
          <input matInput type="date" formControlName="validUntil" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Payment terms</mat-label>
          <input matInput maxlength="60" formControlName="paymentTerms" />
        </mat-form-field>
      </div>
      <mat-form-field appearance="outline" class="full">
        <mat-label>Notes</mat-label>
        <input matInput maxlength="500" formControlName="notes" />
      </mat-form-field>

      <h3 class="mat-h3">Lines</h3>
      <div class="row">
        <mat-form-field appearance="outline">
          <mat-label>Find product (code or name)</mat-label>
          <input matInput [formControl]="searchCtrl" (keyup.enter)="searchProducts()" />
        </mat-form-field>
        <button mat-stroked-button type="button" (click)="searchProducts()"><mat-icon>search</mat-icon> Search</button>
        @for (p of found(); track p.id) {
          <button mat-stroked-button type="button" (click)="addLine(p)">
            <mat-icon>add</mat-icon> {{ p.productCode }} {{ p.name }}
          </button>
        }
      </div>

      <div formArrayName="items">
        @for (line of items.controls; track line; let i = $index) {
          <div class="line" [formGroupName]="i">
            <span class="product">{{ labels()[i] }}</span>
            <mat-form-field appearance="outline" class="narrow">
              <mat-label>Quantity</mat-label>
              <input matInput type="number" min="0" formControlName="quantity" (change)="lookupPrice(i)" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="narrow">
              <mat-label>Unit price</mat-label>
              <input matInput type="number" min="0" formControlName="unitPrice" placeholder="price list" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="narrow">
              <mat-label>Discount %</mat-label>
              <input matInput type="number" min="0" max="100" formControlName="discountPct" />
            </mat-form-field>
            <span class="source">{{ sources()[i] }}</span>
            <span class="total">{{ lineTotal(i) | number: '1.2-2' }}</span>
            <button mat-icon-button type="button" aria-label="Remove line" (click)="removeLine(i)">
              <mat-icon>delete</mat-icon>
            </button>
          </div>
        } @empty {
          <p class="hint">
            Search for a product and click it to add a line. Leave the price blank to use the price list.
          </p>
        }
      </div>

      <div class="footer">
        <strong>Estimated total: {{ total() | number: '1.2-2' }}</strong>
        <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || items.length === 0">
          <mat-icon>save</mat-icon> Save
        </button>
      </div>
    </form>
  `,
  styles: `
    .page-header {
      display: flex;
      align-items: center;
      gap: 16px;
    }
    .row {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
    }
    .full {
      width: 100%;
    }
    .line {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
    }
    .product {
      min-width: 220px;
      font-weight: 500;
    }
    .narrow {
      width: 130px;
    }
    .source {
      min-width: 70px;
      font-size: 12px;
      opacity: 0.7;
    }
    .total {
      min-width: 100px;
      text-align: right;
    }
    .footer {
      display: flex;
      justify-content: flex-end;
      gap: 24px;
      align-items: center;
      padding: 16px 0;
    }
    .hint {
      opacity: 0.7;
    }
  `,
})
export class CrmQuotationFormComponent implements OnInit {
  private api = inject(CrmApi);
  private productApi = inject(ProductApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  customers = signal<CrmCustomerDto[]>([]);
  found = signal<ProductDto[]>([]);
  labels = signal<string[]>([]);
  sources = signal<string[]>([]);
  editingId = signal<number | null>(null);
  version = signal<number | null>(null);
  tick = signal(0);

  searchCtrl = new FormControl('', { nonNullable: true });
  items = new FormArray<LineGroup>([]);
  form = this.fb.group({
    customerId: this.fb.control<number | null>(null, [Validators.required]),
    opportunityId: this.fb.control<number | null>(null),
    validUntil: this.fb.control<string | null>(null),
    paymentTerms: this.fb.control<string | null>(null),
    notes: this.fb.control<string | null>(null),
    items: this.items,
  });

  total = computed(() => {
    this.tick();
    return this.items.controls.reduce((sum, _l, i) => sum + this.lineTotal(i), 0);
  });

  ngOnInit(): void {
    this.api.getCustomers(null, null, 0, 200).subscribe({ next: (p) => this.customers.set(p.content) });
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.editingId.set(Number(id));
      this.api.getQuotation(Number(id)).subscribe({
        next: (q) => this.fill(q),
        error: () => this.snack.open('Failed to load quotation', 'Dismiss', { duration: 3000 }),
      });
    }
    this.form.valueChanges.subscribe(() => this.tick.update((t) => t + 1));
  }

  private newLine(
    productId: number,
    quantity: number,
    unitPrice: number | null,
    discountPct: number | null,
  ): LineGroup {
    return this.fb.group({
      productId: this.fb.nonNullable.control(productId),
      quantity: this.fb.nonNullable.control(quantity, [Validators.required, Validators.min(0.001)]),
      unitPrice: this.fb.control<number | null>(unitPrice),
      discountPct: this.fb.control<number | null>(discountPct),
    });
  }

  private fill(q: QuotationDto): void {
    this.version.set(q.version);
    this.form.patchValue({
      customerId: q.customerId,
      opportunityId: q.opportunityId,
      validUntil: q.validUntil,
      paymentTerms: q.paymentTerms,
      notes: q.notes,
    });
    this.items.clear();
    this.labels.set([]);
    this.sources.set([]);
    for (const i of q.items) {
      this.items.push(this.newLine(i.productId, i.quantity, i.unitPrice, i.discountPct));
      this.labels.update((l) => [...l, `${i.productCode} ${i.productName}`]);
      this.sources.update((s) => [...s, '']);
    }
  }

  searchProducts(): void {
    const q = this.searchCtrl.value.trim();
    if (!q) return;
    this.productApi.search(q, null, null, 0, 8).subscribe({
      next: (page) => this.found.set(page.content),
      error: () => this.snack.open('Product search failed', 'Dismiss', { duration: 3000 }),
    });
  }

  addLine(p: ProductDto): void {
    this.items.push(this.newLine(p.id, 1, null, null));
    this.labels.update((l) => [...l, `${p.productCode} ${p.name}`]);
    this.sources.update((s) => [...s, '']);
    this.found.set([]);
    this.lookupPrice(this.items.length - 1);
  }

  removeLine(i: number): void {
    this.items.removeAt(i);
    this.labels.update((l) => l.filter((_x, idx) => idx !== i));
    this.sources.update((s) => s.filter((_x, idx) => idx !== i));
  }

  /** Pre-fills the unit price from the customer's price list when the customer and product are known. */
  lookupPrice(i: number): void {
    const customerId = this.form.controls.customerId.value;
    const line = this.items.at(i);
    if (!customerId || !line) return;
    const { productId, quantity } = line.getRawValue();
    this.api.resolvePrice(customerId, productId, quantity || 1).subscribe({
      next: (r) => {
        line.patchValue({ unitPrice: r.unitPrice });
        this.sources.update((s) =>
          s.map((x, idx) => (idx === i ? (r.source === 'NONE' ? 'no list price' : r.source.toLowerCase()) : x)),
        );
      },
    });
  }

  lineTotal(i: number): number {
    const line = this.items.at(i);
    if (!line) return 0;
    const v = line.getRawValue();
    if (v.unitPrice == null) return 0;
    return Math.round((v.quantity ?? 0) * netUnitPrice(v.unitPrice, v.discountPct ?? 0) * 10000) / 10000;
  }

  save(): void {
    if (this.form.invalid || this.items.length === 0) return;
    const v = this.form.getRawValue();
    const req = {
      opportunityId: v.opportunityId,
      customerId: v.customerId,
      validUntil: v.validUntil || null,
      paymentTerms: v.paymentTerms || null,
      notes: v.notes || null,
      items: v.items.map((l) => ({
        productId: l.productId,
        quantity: l.quantity,
        unitPrice: l.unitPrice,
        discountPct: l.discountPct,
      })),
      version: this.version(),
    };
    const id = this.editingId();
    const call = id ? this.api.updateQuotation(id, req) : this.api.createQuotation(req);
    call.subscribe({
      next: () => this.router.navigate(['/crm/quotations']),
      error: (err) => this.snack.open(err?.error?.detail ?? 'Could not save quotation', 'Dismiss', { duration: 6000 }),
    });
  }
}
