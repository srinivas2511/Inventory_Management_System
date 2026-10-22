import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ProductApi, ProductDto } from '../master/product/product.api';
import { CrmApi, CrmCustomerDto, PriceDto } from './crm.api';

/** Customer-specific and standard prices (CRM-002). A price with no customer applies to everyone. */
@Component({
  selector: 'app-crm-price-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    IstDatePipe,
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Price Lists</h2>
    </div>
    <p class="hint">
      A customer-specific price beats the standard price. Within the same owner the highest minimum-quantity tier that
      the quantity reaches wins, then the most recent valid-from date.
    </p>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>Customer</mat-label>
        <mat-select [formControl]="customerFilter" (selectionChange)="load()">
          <mat-option [value]="null">All</mat-option>
          @for (c of customers(); track c.id) {
            <mat-option [value]="c.id">{{ c.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </div>

    <form *appHasPermission="'PRICE_LIST_MANAGE'" class="edit-form" [formGroup]="form" (ngSubmit)="add()">
      <mat-form-field appearance="outline">
        <mat-label>Customer</mat-label>
        <mat-select formControlName="customerId">
          <mat-option [value]="null">Standard (all customers)</mat-option>
          @for (c of customers(); track c.id) {
            <mat-option [value]="c.id">{{ c.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Find product</mat-label>
        <input matInput [formControl]="searchCtrl" (keyup.enter)="search()" />
      </mat-form-field>
      <button mat-stroked-button type="button" (click)="search()"><mat-icon>search</mat-icon></button>
      <mat-form-field appearance="outline">
        <mat-label>Product</mat-label>
        <mat-select formControlName="productId">
          @for (p of products(); track p.id) {
            <mat-option [value]="p.id">{{ p.productCode }} {{ p.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Unit price</mat-label>
        <input matInput type="number" min="0" formControlName="unitPrice" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Min qty</mat-label>
        <input matInput type="number" min="0" formControlName="minQty" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Valid from</mat-label>
        <input matInput type="date" formControlName="validFrom" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Valid to</mat-label>
        <input matInput type="date" formControlName="validTo" />
      </mat-form-field>
      <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">
        <mat-icon>add</mat-icon> Add price
      </button>
    </form>

    @if (loading()) {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    } @else {
      <table mat-table [dataSource]="prices()" class="mat-elevation-z1">
        <ng-container matColumnDef="customer">
          <th mat-header-cell *matHeaderCellDef>Customer</th>
          <td mat-cell *matCellDef="let r">{{ r.customerName || 'Standard' }}</td>
        </ng-container>
        <ng-container matColumnDef="product">
          <th mat-header-cell *matHeaderCellDef>Product</th>
          <td mat-cell *matCellDef="let r">{{ r.productCode }} {{ r.productName }}</td>
        </ng-container>
        <ng-container matColumnDef="price">
          <th mat-header-cell *matHeaderCellDef>Unit price</th>
          <td mat-cell *matCellDef="let r">{{ r.unitPrice | number: '1.2-4' }}</td>
        </ng-container>
        <ng-container matColumnDef="minQty">
          <th mat-header-cell *matHeaderCellDef>Min qty</th>
          <td mat-cell *matCellDef="let r">{{ r.minQty | number: '1.0-3' }}</td>
        </ng-container>
        <ng-container matColumnDef="validFrom">
          <th mat-header-cell *matHeaderCellDef>Valid from</th>
          <td mat-cell *matCellDef="let r">{{ r.validFrom | istDate: 'dd MMM yyyy' }}</td>
        </ng-container>
        <ng-container matColumnDef="validTo">
          <th mat-header-cell *matHeaderCellDef>Valid to</th>
          <td mat-cell *matCellDef="let r">{{ r.validTo ? (r.validTo | istDate: 'dd MMM yyyy') : 'open' }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let r">
            <button *appHasPermission="'PRICE_LIST_MANAGE'" mat-button color="warn" (click)="remove(r)">Delete</button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="columns.length" style="text-align:center;padding:2rem">
            No prices defined
          </td>
        </tr>
      </table>
    }
  `,
  styles: `
    .filter-bar,
    .edit-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .hint {
      opacity: 0.7;
      margin: 0 0 8px;
    }
  `,
})
export class CrmPriceListComponent implements OnInit {
  private api = inject(CrmApi);
  private productApi = inject(ProductApi);
  private fb = inject(FormBuilder);
  private snack = inject(MatSnackBar);

  columns = ['customer', 'product', 'price', 'minQty', 'validFrom', 'validTo', 'actions'];
  prices = signal<PriceDto[]>([]);
  customers = signal<CrmCustomerDto[]>([]);
  products = signal<ProductDto[]>([]);
  loading = signal(false);

  customerFilter = new FormControl<number | null>(null);
  searchCtrl = new FormControl('', { nonNullable: true });
  form = this.fb.nonNullable.group({
    customerId: this.fb.control<number | null>(null),
    productId: this.fb.control<number | null>(null, [Validators.required]),
    unitPrice: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
    minQty: this.fb.control<number | null>(1),
    validFrom: ['', [Validators.required]],
    validTo: [''],
  });

  ngOnInit(): void {
    this.api.getCustomers(null, null, 0, 200).subscribe({ next: (p) => this.customers.set(p.content) });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getPrices(this.customerFilter.value, null).subscribe({
      next: (page) => {
        this.prices.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snack.open('Failed to load prices', 'Dismiss', { duration: 3000 });
      },
    });
  }

  search(): void {
    const q = this.searchCtrl.value.trim();
    if (!q) return;
    this.productApi.search(q, null, null, 0, 10).subscribe({
      next: (page) => this.products.set(page.content),
      error: () => this.snack.open('Product search failed', 'Dismiss', { duration: 3000 }),
    });
  }

  add(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.api
      .createPrice({
        customerId: v.customerId,
        productId: v.productId as number,
        unitPrice: v.unitPrice as number,
        minQty: v.minQty,
        validFrom: v.validFrom,
        validTo: v.validTo || null,
        active: true,
      })
      .subscribe({
        next: () => {
          this.form.reset({
            customerId: null,
            productId: null,
            unitPrice: null,
            minQty: 1,
            validFrom: '',
            validTo: '',
          });
          this.load();
        },
        error: (err) => this.snack.open(err?.error?.detail ?? 'Could not save price', 'Dismiss', { duration: 5000 }),
      });
  }

  remove(p: PriceDto): void {
    this.api.deletePrice(p.id).subscribe({
      next: () => this.load(),
      error: () => this.snack.open('Could not delete price', 'Dismiss', { duration: 3000 }),
    });
  }
}
