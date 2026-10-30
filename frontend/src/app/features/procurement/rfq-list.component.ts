import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { CommonModule, DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

export interface RfqDto {
  id: number;
  docNo: string;
  status: string;
  dueDate: string | null;
  deliveryLocation: string | null;
  notes: string | null;
  items: RfqItemDto[];
}

export interface RfqItemDto {
  id: number;
  lineNo: number;
  materialId: number | null;
  productId: number | null;
  description: string | null;
  qty: number;
  uomId: number | null;
  requiredDate: string | null;
}

export interface SupplierQuoteDto {
  id: number;
  docNo: string;
  status: string;
  rfqId: number;
  supplierId: number;
  validUntil: string | null;
  leadTimeDays: number | null;
  freight: number;
  notes: string | null;
  awarded: boolean;
  items: SupplierQuoteItemDto[];
}

export interface SupplierQuoteItemDto {
  id: number;
  lineNo: number;
  rfqItemId: number;
  unitPrice: number;
  taxPct: number;
  moq: number | null;
  awardedQty: number;
}

export interface AwardResultDto {
  poId: number;
  poNumber: string;
}

export interface SupplierDto {
  id: number;
  name: string;
}

@Component({
  selector: 'app-rfq-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
    MatTabsModule,
    MatSnackBarModule,
    DecimalPipe,
    IstDatePipe,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.rfqs">RFQs</h2>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <table mat-table [dataSource]="rfqs()" class="mat-elevation-z2 full-width">
        <ng-container matColumnDef="docNo">
          <th mat-header-cell *matHeaderCellDef>Doc No</th>
          <td mat-cell *matCellDef="let row">{{ row.docNo }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let row"><app-status-badge [status]="row.status" /></td>
        </ng-container>
        <ng-container matColumnDef="dueDate">
          <th mat-header-cell *matHeaderCellDef>Due Date</th>
          <td mat-cell *matCellDef="let row">{{ row.dueDate | istDate }}</td>
        </ng-container>
        <ng-container matColumnDef="items">
          <th mat-header-cell *matHeaderCellDef>Lines</th>
          <td mat-cell *matCellDef="let row">{{ row.items?.length ?? 0 }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let row">
            <button
              mat-icon-button
              matTooltip="Record Supplier Quote"
              aria-label="Record Supplier Quote"
              *appHasPermission="'RFQ_UPDATE'"
              (click)="openRecordQuoteDialog(row)"
            >
              <mat-icon aria-hidden="true">note_add</mat-icon>
            </button>
            <button
              mat-icon-button
              matTooltip="View Quotes"
              aria-label="View Quotes"
              *appHasPermission="'RFQ_VIEW'"
              (click)="viewQuotes(row)"
            >
              <mat-icon aria-hidden="true">compare</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }

    <!-- Record Quote Dialog -->
    @if (recordQuoteDialog()) {
      <div
        class="dialog-backdrop"
        role="button"
        tabindex="0"
        aria-label="Close dialog"
        (click)="closeRecordQuoteDialog()"
        (keydown.escape)="closeRecordQuoteDialog()"
      >
        <div class="dialog-panel" role="presentation" (click)="$event.stopPropagation()">
          <h3>Record Supplier Quote — {{ selectedRfq()?.docNo }}</h3>
          <form [formGroup]="quoteForm" (ngSubmit)="submitQuote()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Quote Doc No</mat-label>
              <input matInput formControlName="docNo" placeholder="SQ-001" />
              @if (quoteForm.get('docNo')?.hasError('required')) {
                <mat-error>Doc No is required</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Supplier ID</mat-label>
              <input matInput formControlName="supplierId" type="number" />
              @if (quoteForm.get('supplierId')?.hasError('required')) {
                <mat-error>Supplier is required</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Valid Until</mat-label>
              <input matInput formControlName="validUntil" type="date" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Lead Time (days)</mat-label>
              <input matInput formControlName="leadTimeDays" type="number" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Notes</mat-label>
              <textarea matInput formControlName="notes" rows="2"></textarea>
            </mat-form-field>

            <h4>Line Items</h4>
            @for (item of selectedRfq()?.items; track item.id; let i = $index) {
              <div class="quote-line">
                <span class="line-desc">{{ item.description ?? 'Line ' + item.lineNo }} (qty {{ item.qty }})</span>
                <mat-form-field appearance="outline">
                  <mat-label>Unit Price</mat-label>
                  <input matInput [formControl]="getLineControl(i, 'unitPrice')" type="number" step="0.01" />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Tax %</mat-label>
                  <input matInput [formControl]="getLineControl(i, 'taxPct')" type="number" step="0.01" />
                </mat-form-field>
              </div>
            }

            <div class="dialog-actions">
              <button mat-button type="button" (click)="closeRecordQuoteDialog()">Cancel</button>
              <button mat-raised-button color="primary" type="submit" [disabled]="quoteForm.invalid || savingQuote()">
                Save Quote
              </button>
            </div>
          </form>
        </div>
      </div>
    }

    <!-- Quotes Comparison Dialog -->
    @if (quotesDialog()) {
      <div
        class="dialog-backdrop"
        role="button"
        tabindex="0"
        aria-label="Close dialog"
        (click)="closeQuotesDialog()"
        (keydown.escape)="closeQuotesDialog()"
      >
        <div class="dialog-panel wide" role="presentation" (click)="$event.stopPropagation()">
          <h3>Supplier Quotes — {{ selectedRfq()?.docNo }}</h3>
          <mat-tab-group>
            <mat-tab label="Quotes">
              @if (quotesLoading()) {
                <mat-spinner diameter="32" />
              } @else if (quotes().length === 0) {
                <p class="empty-msg">No quotes recorded yet.</p>
              } @else {
                <table mat-table [dataSource]="quotes()" class="full-width">
                  <ng-container matColumnDef="docNo">
                    <th mat-header-cell *matHeaderCellDef>Quote No</th>
                    <td mat-cell *matCellDef="let q">{{ q.docNo }}</td>
                  </ng-container>
                  <ng-container matColumnDef="supplierId">
                    <th mat-header-cell *matHeaderCellDef>Supplier</th>
                    <td mat-cell *matCellDef="let q">{{ q.supplierId }}</td>
                  </ng-container>
                  <ng-container matColumnDef="leadTimeDays">
                    <th mat-header-cell *matHeaderCellDef>Lead (days)</th>
                    <td mat-cell *matCellDef="let q">{{ q.leadTimeDays ?? '—' }}</td>
                  </ng-container>
                  <ng-container matColumnDef="validUntil">
                    <th mat-header-cell *matHeaderCellDef>Valid Until</th>
                    <td mat-cell *matCellDef="let q">{{ q.validUntil | istDate }}</td>
                  </ng-container>
                  <ng-container matColumnDef="status">
                    <th mat-header-cell *matHeaderCellDef>Status</th>
                    <td mat-cell *matCellDef="let q"><app-status-badge [status]="q.status" /></td>
                  </ng-container>
                  <ng-container matColumnDef="award">
                    <th mat-header-cell *matHeaderCellDef></th>
                    <td mat-cell *matCellDef="let q">
                      @if (!q.awarded) {
                        <button
                          mat-stroked-button
                          color="primary"
                          aria-label="Award this quote"
                          *appHasPermission="'RFQ_AWARD'"
                          (click)="awardQuote(q)"
                        >
                          Award
                        </button>
                      } @else {
                        <span class="awarded-badge">Awarded</span>
                      }
                    </td>
                  </ng-container>
                  <tr mat-header-row *matHeaderRowDef="quoteColumns"></tr>
                  <tr mat-row *matRowDef="let row; columns: quoteColumns"></tr>
                </table>
              }
            </mat-tab>
            <mat-tab label="Price Comparison">
              @if (quotes().length < 2) {
                <p class="empty-msg">At least 2 quotes required for comparison.</p>
              } @else {
                <table class="comparison-table">
                  <thead>
                    <tr>
                      <th>Material / Line</th>
                      @for (q of quotes(); track q.id) {
                        <th>{{ q.docNo }} (Sup {{ q.supplierId }})</th>
                      }
                    </tr>
                  </thead>
                  <tbody>
                    @for (rfqItem of selectedRfq()?.items; track rfqItem.id) {
                      <tr>
                        <td>{{ rfqItem.description ?? 'Line ' + rfqItem.lineNo }}</td>
                        @for (q of quotes(); track q.id) {
                          <td>{{ getQuotePrice(q, rfqItem.id) | number: '1.2-4' }}</td>
                        }
                      </tr>
                    }
                  </tbody>
                </table>
              }
            </mat-tab>
          </mat-tab-group>
          <div class="dialog-actions">
            <button mat-button (click)="closeQuotesDialog()">Close</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [
    `
      .dialog-backdrop {
        position: fixed;
        inset: 0;
        background: rgba(0, 0, 0, 0.4);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 1000;
      }
      .dialog-panel {
        background: #fff;
        border-radius: 8px;
        padding: 24px;
        width: 520px;
        max-height: 80vh;
        overflow-y: auto;
      }
      .dialog-panel.wide {
        width: 720px;
      }
      .dialog-actions {
        display: flex;
        justify-content: flex-end;
        gap: 8px;
        margin-top: 16px;
      }
      .quote-line {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 4px;
      }
      .line-desc {
        flex: 1;
        font-size: 0.9rem;
      }
      .full-width {
        width: 100%;
      }
      .empty-msg {
        padding: 16px;
        color: #666;
      }
      .awarded-badge {
        color: green;
        font-size: 0.85rem;
        font-weight: 500;
      }
      .comparison-table {
        width: 100%;
        border-collapse: collapse;
        margin-top: 12px;
      }
      .comparison-table th,
      .comparison-table td {
        border: 1px solid #ddd;
        padding: 8px;
        text-align: right;
      }
      .comparison-table th:first-child,
      .comparison-table td:first-child {
        text-align: left;
      }
    `,
  ],
})
export class RfqListComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);
  private fb = inject(FormBuilder);

  columns = ['docNo', 'status', 'dueDate', 'items', 'actions'];
  quoteColumns = ['docNo', 'supplierId', 'leadTimeDays', 'validUntil', 'status', 'award'];

  rfqs = signal<RfqDto[]>([]);
  loading = signal(true);

  selectedRfq = signal<RfqDto | null>(null);
  recordQuoteDialog = signal(false);
  quotesDialog = signal(false);
  quotes = signal<SupplierQuoteDto[]>([]);
  quotesLoading = signal(false);
  savingQuote = signal(false);

  quoteForm!: FormGroup;
  lineControls: FormGroup[] = [];

  ngOnInit(): void {
    this.http.get<RfqDto[]>('/api/rfqs').subscribe({
      next: (data) => {
        this.rfqs.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load RFQs', 'Close', { duration: 3000 });
      },
    });
  }

  openRecordQuoteDialog(rfq: RfqDto): void {
    this.selectedRfq.set(rfq);
    this.lineControls = (rfq.items ?? []).map(() =>
      this.fb.group({ unitPrice: [null, Validators.required], taxPct: [0] }),
    );
    this.quoteForm = this.fb.group({
      docNo: ['', Validators.required],
      supplierId: [null, Validators.required],
      validUntil: [null],
      leadTimeDays: [null],
      notes: [''],
    });
    this.recordQuoteDialog.set(true);
  }

  closeRecordQuoteDialog(): void {
    this.recordQuoteDialog.set(false);
    this.selectedRfq.set(null);
  }

  getLineControl(index: number, field: string): import('@angular/forms').FormControl {
    return this.lineControls[index]?.get(field) as import('@angular/forms').FormControl;
  }

  submitQuote(): void {
    if (this.quoteForm.invalid) return;
    const rfq = this.selectedRfq();
    if (!rfq) return;
    this.savingQuote.set(true);
    const items = (rfq.items ?? []).map((item, i) => ({
      rfqItemId: item.id,
      lineNo: item.lineNo,
      unitPrice: this.lineControls[i]?.get('unitPrice')?.value ?? 0,
      taxPct: this.lineControls[i]?.get('taxPct')?.value ?? 0,
      moq: null,
    }));
    const body = { ...this.quoteForm.value, rfqId: rfq.id, items };
    this.http.post<SupplierQuoteDto>(`/api/procurement/rfqs/${rfq.id}/quotes`, body).subscribe({
      next: () => {
        this.snackBar.open('Quote recorded', 'OK', { duration: 3000 });
        this.savingQuote.set(false);
        this.closeRecordQuoteDialog();
      },
      error: (e) => {
        this.snackBar.open(e.error?.detail ?? 'Error saving quote', 'Close', { duration: 4000 });
        this.savingQuote.set(false);
      },
    });
  }

  viewQuotes(rfq: RfqDto): void {
    this.selectedRfq.set(rfq);
    this.quotesLoading.set(true);
    this.quotesDialog.set(true);
    this.http.get<SupplierQuoteDto[]>(`/api/procurement/rfqs/${rfq.id}/quotes`).subscribe({
      next: (data) => {
        this.quotes.set(data);
        this.quotesLoading.set(false);
      },
      error: () => {
        this.quotesLoading.set(false);
        this.snackBar.open('Failed to load quotes', 'Close', { duration: 3000 });
      },
    });
  }

  closeQuotesDialog(): void {
    this.quotesDialog.set(false);
    this.quotes.set([]);
    this.selectedRfq.set(null);
  }

  awardQuote(quote: SupplierQuoteDto): void {
    const rfq = this.selectedRfq();
    if (!rfq) return;
    this.http.post<AwardResultDto>(`/api/procurement/rfqs/${rfq.id}/quotes/${quote.id}/award`, {}).subscribe({
      next: (result) => {
        this.snackBar.open(`Awarded! PO created: ${result.poNumber}`, 'OK', { duration: 5000 });
        this.viewQuotes(rfq);
      },
      error: (e) => this.snackBar.open(e.error?.detail ?? 'Award failed', 'Close', { duration: 4000 }),
    });
  }

  getQuotePrice(quote: SupplierQuoteDto, rfqItemId: number): number | null {
    const item = quote.items?.find((qi) => qi.rfqItemId === rfqItemId);
    return item ? item.unitPrice : null;
  }
}
