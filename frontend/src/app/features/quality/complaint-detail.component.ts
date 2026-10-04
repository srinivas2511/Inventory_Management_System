import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ProductApi, ProductDto } from '../master/product/product.api';
import {
  COMPLAINT_CATEGORIES,
  COMPLAINT_DISPOSITIONS,
  Complaint,
  ComplaintCategory,
  ComplaintDisposition,
  ComplaintStatus,
  CustomerLookup,
  QualityPlusApi,
  TraceView,
} from './quality-plus.api';

@Component({
  selector: 'app-complaint-detail',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <div>
        <a routerLink="/quality/complaints" class="back"><mat-icon>arrow_back</mat-icon> Complaints</a>
        <h2 class="mat-h2">
          @if (complaint(); as c) {
            {{ c.complaintNumber }} <app-status-badge [status]="c.status" />
          } @else {
            Register complaint
          }
        </h2>
      </div>
    </div>

    @if (isNew()) {
      <form [formGroup]="form" (ngSubmit)="register()" class="grid">
        <mat-form-field appearance="outline">
          <mat-label>Customer</mat-label>
          <mat-select formControlName="customerId">
            @for (c of customers(); track c.id) {
              <mat-option [value]="c.id">{{ c.code }} - {{ c.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Find customer</mat-label>
          <input matInput [formControl]="customerSearch" (keyup.enter)="findCustomers()" />
          <button matSuffix mat-icon-button type="button" (click)="findCustomers()" aria-label="Find customer">
            <mat-icon>search</mat-icon>
          </button>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Product</mat-label>
          <mat-select formControlName="productId" (selectionChange)="onProductChange()">
            <mat-option [value]="null">Not specified</mat-option>
            @for (p of products(); track p.id) {
              <mat-option [value]="p.id">{{ p.code }} - {{ p.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>FG batch</mat-label>
          <mat-select formControlName="fgBatchId">
            <mat-option [value]="null">Not specified</mat-option>
            @for (b of batches(); track b.id) {
              <mat-option [value]="b.id">{{ b.batchNumber }}</mat-option>
            }
          </mat-select>
          <mat-hint>Pick a product first to choose one of its batches</mat-hint>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Complaint date</mat-label>
          <input matInput type="date" formControlName="complaintDate" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Category</mat-label>
          <mat-select formControlName="category">
            @for (c of categories; track c) {
              <mat-option [value]="c">{{ c }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Customer reference</mat-label>
          <input matInput formControlName="customerRef" maxlength="60" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Quantity complained</mat-label>
          <input matInput type="number" formControlName="quantityComplained" min="0" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Description</mat-label>
          <textarea matInput rows="4" formControlName="description"></textarea>
        </mat-form-field>
        <div class="full">
          <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || busy()">Register</button>
        </div>
      </form>
    } @else {
      @if (complaint(); as c) {
        <dl class="facts">
          <dt>Customer</dt>
          <dd>{{ c.customerName }}</dd>
          <dt>Date</dt>
          <dd>{{ c.complaintDate | date: 'dd MMM yyyy' }}</dd>
          <dt>Product</dt>
          <dd>{{ c.productCode }} {{ c.productName }}</dd>
          <dt>FG batch</dt>
          <dd>{{ c.fgBatchNumber ?? '-' }}</dd>
          <dt>Category</dt>
          <dd>{{ c.category }}</dd>
          <dt>Customer reference</dt>
          <dd>{{ c.customerRef ?? '-' }}</dd>
          <dt>Quantity complained / returned</dt>
          <dd>{{ c.quantityComplained ?? '-' }} / {{ c.quantityReturned ?? '-' }}</dd>
          <dt>Disposition</dt>
          <dd>{{ c.disposition ?? '-' }}</dd>
          <dt>CAPA</dt>
          <dd>
            @if (c.capaId) {
              <a [routerLink]="['/quality/capa', c.capaId]">{{ c.capaNumber }}</a>
            } @else {
              -
            }
          </dd>
        </dl>
        <p class="description">{{ c.description }}</p>
        @if (c.resolutionNotes) {
          <p><strong>Resolution:</strong> {{ c.resolutionNotes }}</p>
        }

        @if (c.status !== 'CLOSED') {
          <section *appHasPermission="'COMPLAINT_MANAGE'" class="panel">
            <h3 class="mat-h3">Work the complaint</h3>
            @if (c.nextStatuses.includes('RESOLVED')) {
              <form [formGroup]="resolve" class="grid">
                <mat-form-field appearance="outline">
                  <mat-label>Disposition</mat-label>
                  <mat-select formControlName="disposition">
                    @for (d of dispositions; track d) {
                      <mat-option [value]="d">{{ d }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Quantity returned</mat-label>
                  <input matInput type="number" formControlName="quantityReturned" min="0" />
                </mat-form-field>
                <mat-form-field appearance="outline" class="full">
                  <mat-label>Resolution notes</mat-label>
                  <textarea matInput rows="3" formControlName="resolutionNotes"></textarea>
                </mat-form-field>
              </form>
            }
            <div class="row">
              @for (t of c.nextStatuses; track t) {
                <button
                  mat-stroked-button
                  (click)="move(t)"
                  [disabled]="busy() || (t === 'RESOLVED' && resolve.invalid)"
                >
                  {{ label(c.status, t) }}
                </button>
              }
            </div>
          </section>
        }

        <section class="panel">
          <div class="row">
            @if (!c.capaId) {
              <button *appHasPermission="'CAPA_MANAGE'" mat-stroked-button (click)="raiseCapa()" [disabled]="busy()">
                <mat-icon>assignment_late</mat-icon> Raise CAPA
              </button>
            }
            @if (c.fgBatchId) {
              <button mat-stroked-button (click)="loadTrace()" [disabled]="busy()">
                <mat-icon>account_tree</mat-icon> Trace FG batch
              </button>
            }
          </div>
          @if (trace(); as t) {
            <h3 class="mat-h3">Traceability of batch {{ t.batchNumber }}</h3>
            <ol class="trail">
              @for (s of t.trail; track $index) {
                <li>
                  <strong>{{ s.stepType }}</strong> {{ s.reference }} - {{ s.description }}
                  @if (s.timestamp) {
                    <span class="when">({{ s.timestamp | date: 'dd MMM yyyy HH:mm' }})</span>
                  }
                </li>
              } @empty {
                <li>No upstream records were found for this batch.</li>
              }
            </ol>
          }
        </section>
      } @else {
        <div class="loading-center"><mat-spinner diameter="40" /></div>
      }
    }
  `,
  styles: `
    .page-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
    }
    .back {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      text-decoration: none;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: 4px 16px;
      max-width: 900px;
    }
    .full {
      grid-column: 1 / -1;
    }
    .facts {
      display: grid;
      grid-template-columns: max-content 1fr;
      gap: 4px 16px;
      max-width: 700px;
    }
    .facts dt {
      font-weight: 500;
    }
    .facts dd {
      margin: 0;
    }
    .description {
      max-width: 900px;
      white-space: pre-wrap;
    }
    .panel {
      margin: 20px 0;
      max-width: 900px;
    }
    .row {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    .when {
      opacity: 0.7;
    }
  `,
})
export class ComplaintDetailComponent implements OnInit {
  private api = inject(QualityPlusApi);
  private productApi = inject(ProductApi);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private snack = inject(MatSnackBar);

  categories = COMPLAINT_CATEGORIES;
  dispositions = COMPLAINT_DISPOSITIONS;
  complaint = signal<Complaint | null>(null);
  trace = signal<TraceView | null>(null);
  busy = signal(false);
  isNew = signal(false);
  customers = signal<CustomerLookup[]>([]);
  products = signal<ProductDto[]>([]);
  batches = signal<{ id: number; batchNumber: string }[]>([]);

  customerSearch = new FormControl('', { nonNullable: true });
  form = new FormGroup({
    customerId: new FormControl<number | null>(null, Validators.required),
    productId: new FormControl<number | null>(null),
    fgBatchId: new FormControl<number | null>(null),
    complaintDate: new FormControl(new Date().toISOString().slice(0, 10), {
      nonNullable: true,
      validators: Validators.required,
    }),
    category: new FormControl<ComplaintCategory>('OTHER', { nonNullable: true }),
    customerRef: new FormControl('', { nonNullable: true }),
    quantityComplained: new FormControl<number | null>(null, Validators.min(0)),
    description: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });
  resolve = new FormGroup({
    disposition: new FormControl<ComplaintDisposition | null>(null, Validators.required),
    quantityReturned: new FormControl<number | null>(null, Validators.min(0)),
    resolutionNotes: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id || id === 'new') {
      this.isNew.set(true);
      this.findCustomers();
      this.productApi.search('', null, null, 0, 200).subscribe({
        next: (page) => this.products.set(page.content),
        error: () => this.products.set([]),
      });
    } else {
      this.load(Number(id));
    }
  }

  findCustomers(): void {
    this.api.customerLookup(this.customerSearch.value.trim() || null).subscribe({
      next: (rows) => this.customers.set(rows),
      error: () => this.customers.set([]),
    });
  }

  onProductChange(): void {
    this.form.patchValue({ fgBatchId: null });
    const productId = this.form.controls.productId.value;
    if (!productId) {
      this.batches.set([]);
      return;
    }
    this.api.listFgBatches(productId).subscribe({
      next: (page) => this.batches.set(page.content ?? []),
      error: () => this.batches.set([]),
    });
  }

  label(from: ComplaintStatus, to: ComplaintStatus): string {
    if (to === 'IN_PROGRESS') return from === 'RESOLVED' ? 'Reopen' : 'Start investigation';
    if (to === 'RESOLVED') return 'Resolve';
    return 'Close complaint';
  }

  private load(id: number): void {
    this.api.getComplaint(id).subscribe({
      next: (c) => this.complaint.set(c),
      error: (err) => this.fail(err, 'Failed to load the complaint'),
    });
  }

  register(): void {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.api
      .createComplaint({
        customerId: v.customerId as number,
        productId: v.productId,
        fgBatchId: v.fgBatchId,
        complaintDate: v.complaintDate,
        category: v.category,
        description: v.description.trim(),
        customerRef: v.customerRef.trim() || null,
        quantityComplained: v.quantityComplained,
      })
      .subscribe({
        next: (c) => {
          this.busy.set(false);
          this.router.navigate(['/quality/complaints', c.id]);
        },
        error: (err) => this.fail(err, 'Failed to register the complaint'),
      });
  }

  move(target: ComplaintStatus): void {
    const c = this.complaint();
    if (!c) return;
    const r = this.resolve.getRawValue();
    this.busy.set(true);
    this.api
      .transitionComplaint(c.id, {
        target,
        ...(target === 'RESOLVED'
          ? {
              disposition: r.disposition,
              quantityReturned: r.quantityReturned,
              resolutionNotes: r.resolutionNotes.trim(),
            }
          : {}),
      })
      .subscribe({
        next: (updated) => {
          this.busy.set(false);
          this.complaint.set(updated);
        },
        error: (err) => this.fail(err, 'Failed to update the complaint'),
      });
  }

  raiseCapa(): void {
    const c = this.complaint();
    if (!c) return;
    this.busy.set(true);
    this.api.capaFromComplaint(c.id).subscribe({
      next: (capa) => {
        this.busy.set(false);
        this.router.navigate(['/quality/capa', capa.id]);
      },
      error: (err) => this.fail(err, 'Failed to raise the CAPA'),
    });
  }

  loadTrace(): void {
    const c = this.complaint();
    if (!c) return;
    this.busy.set(true);
    this.api.traceComplaint(c.id).subscribe({
      next: (t) => {
        this.busy.set(false);
        this.trace.set(t);
      },
      error: (err) => this.fail(err, 'Failed to load the traceability'),
    });
  }

  private fail(err: { error?: { detail?: string } }, fallback: string): void {
    this.busy.set(false);
    this.snack.open(err?.error?.detail ?? fallback, 'Dismiss', { duration: 5000 });
  }
}
