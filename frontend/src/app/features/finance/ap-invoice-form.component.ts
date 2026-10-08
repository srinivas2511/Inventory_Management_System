import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { problemMessage } from '../../core/models/problem.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { InvoiceableReceipt, LookupApi, LookupOption } from '../../shared/lookup.api';
import { inDaysIso, todayIso } from './finance-dates';
import { debounceTime, merge } from 'rxjs';
import { ApInvoiceCreateRequest, ApInvoiceDto, DeterminationDto, FinanceApi, SlabDto } from './finance.api';

/**
 * Enters a supplier invoice against the goods receipt it bills. The server matches it to the receipt straight away,
 * so the dialog moves on to show the result: a matching invoice can be opened, one that does not stays a draft.
 */
@Component({
  selector: 'app-ap-invoice-form',
  standalone: true,
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatButtonModule,
    StatusBadgeComponent,
  ],
  template: `
    @if (created(); as inv) {
      <h2 mat-dialog-title>Invoice {{ inv.invoiceNumber }} saved</h2>
      <mat-dialog-content>
        <p class="result-line">
          <app-status-badge [status]="inv.matchStatus" />
          @if (inv.matchStatus === 'MATCHED') {
            It matches its goods receipt.
          } @else if (inv.matchStatus === 'EXCEPTION') {
            It does not match its goods receipt.
          }
        </p>
        <dl class="figures">
          <dt>Still open on the receipt</dt>
          <dd>{{ inv.matchExpectedAmount ?? 0 | number: '1.2-4' }}</dd>
          <dt>Invoice subtotal</dt>
          <dd>{{ inv.subtotal ?? 0 | number: '1.2-4' }}</dd>
          <dt>Difference</dt>
          <dd>{{ (inv.subtotal ?? 0) - (inv.matchExpectedAmount ?? 0) | number: '1.2-4' }}</dd>
          @for (l of inv.taxLines ?? []; track l.taxCode) {
            <dt>{{ l.taxType }} {{ l.ratePct | number: '1.0-2' }}%</dt>
            <dd>{{ l.taxAmount | number: '1.2-4' }}</dd>
          }
          @if (inv.currencyCode && inv.currencyCode !== 'INR') {
            <dt>In INR at {{ inv.exchangeRate | number: '1.2-6' }}</dt>
            <dd data-testid="result-inr">{{ inv.totalInr | number: '1.2-4' }}</dd>
          }
        </dl>
        @if (inv.matchStatus === 'EXCEPTION') {
          <p class="exception" role="alert">
            {{ inv.matchMessage }}
          </p>
          <p>
            The invoice is saved as a draft and cannot be opened or paid. A finance manager other than the person who
            entered it has to approve the variance, or you can cancel it and enter a corrected invoice.
          </p>
        } @else {
          <p>The invoice is a draft until you open it. Opening posts it to the ledger.</p>
        }
        @if (errorMessage()) {
          <p class="exception" role="alert">{{ errorMessage() }}</p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        @if (inv.matchStatus === 'EXCEPTION') {
          <button mat-button type="button" [disabled]="saving()" (click)="cancelInvoice(inv)">
            Cancel this invoice
          </button>
        }
        <button mat-button type="button" [disabled]="saving()" (click)="close()">Close</button>
        @if (inv.matchStatus !== 'EXCEPTION') {
          <button mat-flat-button color="primary" type="button" [disabled]="saving()" (click)="openInvoice(inv)">
            Open invoice
          </button>
        }
      </mat-dialog-actions>
    } @else {
      <h2 mat-dialog-title>New supplier invoice</h2>
      <mat-dialog-content>
        <form [formGroup]="form" id="ap-invoice-form" (ngSubmit)="save()" novalidate>
          <div class="grid">
            <mat-form-field appearance="outline">
              <mat-label>Supplier</mat-label>
              <mat-select formControlName="supplierId">
                @for (s of suppliers(); track s.id) {
                  <mat-option [value]="s.id">{{ s.code }} · {{ s.name }}</mat-option>
                }
              </mat-select>
              @if (form.controls.supplierId.hasError('required') && form.controls.supplierId.touched) {
                <mat-error>Choose a supplier</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Goods receipt being billed</mat-label>
              <mat-select formControlName="goodsReceiptId">
                @for (r of receipts(); track r.id) {
                  <mat-option [value]="r.id">
                    {{ r.grNumber }} · {{ r.poNumber }} · open {{ r.openValue | number: '1.2-4' }}
                  </mat-option>
                }
              </mat-select>
              @if (form.controls.goodsReceiptId.hasError('required') && form.controls.goodsReceiptId.touched) {
                <mat-error>Choose the goods receipt this invoice bills</mat-error>
              }
              @if (selected(); as r) {
                <mat-hint>
                  Received {{ r.receivedValue | number: '1.2-4' }} · already invoiced
                  {{ r.invoicedValue | number: '1.2-4' }}
                </mat-hint>
              } @else if (form.controls.supplierId.value && !loadingReceipts() && receipts().length === 0) {
                <mat-hint>This supplier has no posted receipt with value left to invoice.</mat-hint>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Supplier's invoice number</mat-label>
              <input matInput formControlName="supplierInvoiceNo" maxlength="40" />
            </mat-form-field>
            <div class="dates">
              <mat-form-field appearance="outline">
                <mat-label>Invoice date</mat-label>
                <input matInput type="date" formControlName="invoiceDate" />
                @if (form.controls.invoiceDate.hasError('required') && form.controls.invoiceDate.touched) {
                  <mat-error>Choose a date</mat-error>
                }
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Due date</mat-label>
                <input matInput type="date" formControlName="dueDate" />
                @if (form.controls.dueDate.hasError('required') && form.controls.dueDate.touched) {
                  <mat-error>Choose a date</mat-error>
                }
              </mat-form-field>
            </div>
            <mat-form-field appearance="outline">
              <mat-label>Subtotal</mat-label>
              <input matInput type="number" step="0.01" formControlName="subtotal" />
              @if (form.controls.subtotal.hasError('required') && form.controls.subtotal.touched) {
                <mat-error>Enter the subtotal</mat-error>
              } @else if (form.controls.subtotal.hasError('min') && form.controls.subtotal.touched) {
                <mat-error>The subtotal must be greater than zero</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>GST slab</mat-label>
              <mat-select formControlName="taxSlabCode">
                <mat-option value="">I will enter the tax amount</mat-option>
                @for (s of slabs(); track s.slabCode) {
                  <mat-option [value]="s.slabCode">{{ s.slabCode }} · {{ s.description }}</mat-option>
                }
              </mat-select>
              <mat-hint>Taxed by place of supply, from the supplier's GSTIN</mat-hint>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Currency</mat-label>
              <input matInput formControlName="currencyCode" maxlength="3" placeholder="Supplier's currency" />
              @if (form.controls.currencyCode.hasError('pattern')) {
                <mat-error>Use a three-letter code such as USD</mat-error>
              }
              <mat-hint>Leave empty for the supplier's own currency</mat-hint>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Tax</mat-label>
              <input matInput type="number" step="0.01" formControlName="taxTotal" />
              @if (form.controls.taxTotal.hasError('min')) {
                <mat-error>Tax cannot be negative</mat-error>
              }
              <mat-hint>Invoice total {{ total() | number: '1.2-4' }}</mat-hint>
            </mat-form-field>
          </div>
        </form>
        @if (preview(); as p) {
          <div class="preview" data-testid="tax-preview" role="status">
            <strong>{{ supplyLabel(p.supplyType) }}</strong>
            @if (p.partnerStateAssumed) {
              <span> · the supplier has no GSTIN, so its state is assumed to be the company state</span>
            }
            @for (l of p.lines; track l.taxCode) {
              <div>
                {{ l.taxType }} {{ l.ratePct | number: '1.0-2' }}% on {{ l.taxableAmount | number: '1.2-4' }} =
                {{ l.taxAmount | number: '1.2-4' }}
              </div>
            }
            <div>Tax {{ p.taxTotal | number: '1.2-4' }}</div>
          </div>
        }
        @if (errorMessage()) {
          <p class="exception" role="alert">{{ errorMessage() }}</p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close [disabled]="saving()">Cancel</button>
        <button mat-flat-button color="primary" form="ap-invoice-form" type="submit" [disabled]="saving()">
          Save invoice
        </button>
      </mat-dialog-actions>
    }
  `,
  styles: [
    `
      .grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 0 12px;
        padding-top: 4px;
      }
      .dates {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 0 8px;
      }
      mat-form-field {
        width: 100%;
      }
      .result-line {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .figures {
        display: grid;
        grid-template-columns: auto auto;
        justify-content: start;
        gap: 4px 24px;
        margin: 0 0 12px;
      }
      .figures dd {
        margin: 0;
        text-align: right;
      }
      .exception {
        color: var(--mat-sys-error, #b3261e);
      }
      .preview {
        margin-top: 8px;
        padding: 8px 12px;
        border-left: 3px solid var(--mat-sys-primary, #6750a4);
      }
    `,
  ],
})
export class ApInvoiceFormComponent implements OnInit {
  protected readonly suppliers = signal<LookupOption[]>([]);
  protected readonly receipts = signal<InvoiceableReceipt[]>([]);
  protected readonly loadingReceipts = signal(false);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');
  protected readonly created = signal<ApInvoiceDto | null>(null);
  protected readonly selected = signal<InvoiceableReceipt | null>(null);
  protected readonly slabs = signal<SlabDto[]>([]);
  protected readonly preview = signal<DeterminationDto | null>(null);

  private readonly fb = inject(FormBuilder);
  private readonly api = inject(FinanceApi);
  private readonly lookups = inject(LookupApi);
  private readonly snack = inject(MatSnackBar);
  private readonly dialogRef = inject(MatDialogRef<ApInvoiceFormComponent>);

  protected readonly form = this.fb.group({
    supplierId: this.fb.control<number | null>(null, Validators.required),
    goodsReceiptId: this.fb.control<number | null>(null, Validators.required),
    supplierInvoiceNo: [''],
    invoiceDate: [todayIso(), Validators.required],
    dueDate: [inDaysIso(30), Validators.required],
    subtotal: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.0001)]),
    taxTotal: this.fb.control<number | null>(null, Validators.min(0)),
    taxSlabCode: [''],
    currencyCode: ['', Validators.pattern(/^([A-Za-z]{3})?$/)],
  });

  ngOnInit(): void {
    this.lookups.suppliers().subscribe((s) => this.suppliers.set(s));
    this.form.controls.supplierId.valueChanges.subscribe((supplierId) => this.loadReceipts(supplierId));
    this.form.controls.goodsReceiptId.valueChanges.subscribe((id) => this.pickReceipt(id));
    this.api.getTaxSlabs().subscribe({ next: (list) => this.slabs.set(list), error: () => this.slabs.set([]) });
    this.form.controls.taxSlabCode.valueChanges.subscribe((slab) => {
      if (slab) {
        this.form.controls.taxTotal.reset(null, { emitEvent: false });
        this.form.controls.taxTotal.disable({ emitEvent: false });
      } else {
        this.form.controls.taxTotal.enable({ emitEvent: false });
      }
    });
    merge(
      this.form.controls.taxSlabCode.valueChanges,
      this.form.controls.subtotal.valueChanges,
      this.form.controls.supplierId.valueChanges,
    )
      .pipe(debounceTime(300))
      .subscribe(() => this.refreshPreview());
  }

  protected supplyLabel(type: string): string {
    return type === 'INTER'
      ? 'Another state: IGST'
      : type === 'UT'
        ? 'Union territory: CGST + UTGST'
        : 'Within the company state: CGST + SGST';
  }

  private refreshPreview(): void {
    const raw = this.form.getRawValue();
    const subtotal = Number(raw.subtotal ?? 0);
    if (!raw.taxSlabCode || !(subtotal > 0)) {
      this.preview.set(null);
      return;
    }
    this.api.determineTax('PURCHASE', raw.taxSlabCode, raw.supplierId, subtotal).subscribe({
      next: (d) => this.preview.set(d),
      error: () => this.preview.set(null),
    });
  }

  protected total(): number {
    const raw = this.form.getRawValue();
    const tax = raw.taxSlabCode ? (this.preview()?.taxTotal ?? 0) : Number(raw.taxTotal ?? 0);
    return Number(raw.subtotal ?? 0) + tax;
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const request: ApInvoiceCreateRequest = {
      supplierId: raw.supplierId as number,
      supplierInvoiceNo: raw.supplierInvoiceNo?.trim() ? raw.supplierInvoiceNo.trim() : null,
      invoiceDate: raw.invoiceDate as string,
      dueDate: raw.dueDate as string,
      goodsReceiptId: raw.goodsReceiptId as number,
      subtotal: Number(raw.subtotal),
      taxTotal:
        raw.taxSlabCode || raw.taxTotal === null || (raw.taxTotal as unknown) === '' ? null : Number(raw.taxTotal),
    };
    if (raw.taxSlabCode) {
      request.taxSlabCode = raw.taxSlabCode;
    }
    if (raw.currencyCode && raw.currencyCode.trim()) {
      request.currencyCode = raw.currencyCode.trim().toUpperCase();
    }
    this.saving.set(true);
    this.errorMessage.set('');
    this.api.createApInvoice(request).subscribe({
      next: (invoice) => {
        this.saving.set(false);
        this.created.set(invoice);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The invoice could not be saved.'));
      },
    });
  }

  protected openInvoice(invoice: ApInvoiceDto): void {
    this.saving.set(true);
    this.errorMessage.set('');
    this.api.openApInvoice(invoice.id).subscribe({
      next: (opened) => {
        this.saving.set(false);
        this.snack.open(`Invoice ${opened.invoiceNumber} opened`, 'OK', { duration: 4000 });
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The invoice could not be opened.'));
      },
    });
  }

  protected cancelInvoice(invoice: ApInvoiceDto): void {
    this.saving.set(true);
    this.errorMessage.set('');
    this.api.cancelApInvoice(invoice.id).subscribe({
      next: () => {
        this.saving.set(false);
        this.snack.open(`Invoice ${invoice.invoiceNumber} cancelled`, 'OK', { duration: 4000 });
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.errorMessage.set(problemMessage(err, 'The invoice could not be cancelled.'));
      },
    });
  }

  protected close(): void {
    this.dialogRef.close(this.created() !== null);
  }

  private loadReceipts(supplierId: number | null): void {
    this.form.controls.goodsReceiptId.reset(null);
    this.receipts.set([]);
    this.selected.set(null);
    if (!supplierId) {
      return;
    }
    this.loadingReceipts.set(true);
    this.lookups.invoiceableReceipts(supplierId).subscribe({
      next: (list) => {
        this.receipts.set(list);
        this.loadingReceipts.set(false);
      },
      error: () => this.loadingReceipts.set(false),
    });
  }

  /** Suggests the open value and, for an untouched receipt, its tax; whatever the user typed is left alone. */
  private pickReceipt(id: number | null): void {
    const receipt = this.receipts().find((r) => r.id === id) ?? null;
    this.selected.set(receipt);
    if (!receipt) {
      return;
    }
    if (this.form.controls.subtotal.pristine) {
      this.form.controls.subtotal.setValue(receipt.openValue);
    }
    if (this.form.controls.taxTotal.pristine && receipt.invoicedValue === 0) {
      this.form.controls.taxTotal.setValue(receipt.receivedTax);
    }
  }
}
