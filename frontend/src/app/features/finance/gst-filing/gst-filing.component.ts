import { Component, inject, signal } from '@angular/core';
import { DecimalPipe, DatePipe, JsonPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { IstDatePipe } from '../../../shared/pipes/ist-date.pipe';

interface B2bInvoice {
  customerGstin: string;
  invoiceNo: string;
  invoiceDate: string;
  taxableValue: number;
  igst: number;
  cgst: number;
  sgst: number;
  grandTotal: number;
}

interface Gstr1Response {
  from: string;
  to: string;
  b2b: B2bInvoice[];
  b2c: { stateCode: string; taxableValue: number; taxAmount: number }[];
  hsnSummary: { hsnSac: string; qty: number; taxableValue: number; taxAmount: number }[];
  docSummary: { invoices: number; creditNotes: number };
}

interface Gstr3bResponse {
  from: string;
  to: string;
  outwardTaxableTurnover: number;
  outwardTaxLiability: { igst: number; cgst: number; sgst: number };
  itcAvailable: { igst: number; cgst: number; sgst: number };
  netPayable: number;
}

@Component({
  selector: 'app-gst-filing',
  standalone: true,
  imports: [
    DecimalPipe,
    DatePipe,
    JsonPipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTableModule,
    MatTabsModule,
    IstDatePipe,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.gst_filing">GST Filing Reports</h2>
    </div>

    <div class="filter-row" style="display:flex;gap:16px;align-items:center;margin-bottom:16px;flex-wrap:wrap;">
      <mat-form-field appearance="outline" style="width:160px">
        <mat-label>From</mat-label>
        <input matInput type="date" [(ngModel)]="fromDate" />
      </mat-form-field>
      <mat-form-field appearance="outline" style="width:160px">
        <mat-label>To</mat-label>
        <input matInput type="date" [(ngModel)]="toDate" />
      </mat-form-field>
      <button mat-raised-button color="primary" (click)="loadAll()" [disabled]="loading()">
        <mat-icon aria-hidden="true">search</mat-icon> Load
      </button>
    </div>

    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <mat-tab-group>
        <!-- GSTR-1 Tab -->
        <mat-tab label="GSTR-1">
          @if (gstr1()) {
            <div style="padding:16px">
              <p>
                <strong>B2B Invoices: {{ gstr1()!.b2b.length }}</strong> &nbsp; B2C Lines: {{ gstr1()!.b2c.length }}
              </p>
              <table
                mat-table
                [dataSource]="gstr1()!.b2b"
                class="mat-elevation-z1 full-width"
                style="margin-bottom:16px"
              >
                <ng-container matColumnDef="customerGstin">
                  <th mat-header-cell *matHeaderCellDef>Customer GSTIN</th>
                  <td mat-cell *matCellDef="let row">{{ row.customerGstin }}</td>
                </ng-container>
                <ng-container matColumnDef="invoiceNo">
                  <th mat-header-cell *matHeaderCellDef>Invoice No</th>
                  <td mat-cell *matCellDef="let row">{{ row.invoiceNo }}</td>
                </ng-container>
                <ng-container matColumnDef="invoiceDate">
                  <th mat-header-cell *matHeaderCellDef>Date</th>
                  <td mat-cell *matCellDef="let row">{{ row.invoiceDate | istDate }}</td>
                </ng-container>
                <ng-container matColumnDef="taxableValue">
                  <th mat-header-cell *matHeaderCellDef>Taxable</th>
                  <td mat-cell *matCellDef="let row">{{ row.taxableValue | number: '1.2-2' }}</td>
                </ng-container>
                <ng-container matColumnDef="grandTotal">
                  <th mat-header-cell *matHeaderCellDef>Total</th>
                  <td mat-cell *matCellDef="let row">{{ row.grandTotal | number: '1.2-2' }}</td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="b2bCols"></tr>
                <tr mat-row *matRowDef="let row; columns: b2bCols"></tr>
              </table>
              @if (gstr1()!.b2b.length === 0) {
                <p class="no-data">No B2B invoices for the selected period.</p>
              }
              <button mat-stroked-button (click)="fileGstr1()">Mark as Filed</button>
            </div>
          }
        </mat-tab>

        <!-- GSTR-3B Tab -->
        <mat-tab label="GSTR-3B">
          @if (gstr3b()) {
            <div style="padding:16px">
              <p>
                <strong>Outward Taxable Turnover:</strong> ₹{{ gstr3b()!.outwardTaxableTurnover | number: '1.2-2' }}
              </p>
              <p>
                <strong>Outward Tax:</strong> IGST {{ gstr3b()!.outwardTaxLiability.igst | number: '1.2-2' }} | CGST
                {{ gstr3b()!.outwardTaxLiability.cgst | number: '1.2-2' }} | SGST
                {{ gstr3b()!.outwardTaxLiability.sgst | number: '1.2-2' }}
              </p>
              <p>
                <strong>ITC Available:</strong> IGST {{ gstr3b()!.itcAvailable.igst | number: '1.2-2' }} | CGST
                {{ gstr3b()!.itcAvailable.cgst | number: '1.2-2' }} | SGST
                {{ gstr3b()!.itcAvailable.sgst | number: '1.2-2' }}
              </p>
              <p><strong>Net Payable:</strong> ₹{{ gstr3b()!.netPayable | number: '1.2-2' }}</p>
              <button mat-stroked-button (click)="fileGstr3b()">Mark as Filed</button>
            </div>
          }
        </mat-tab>

        <!-- E-way Bill Tab -->
        <mat-tab label="E-Way Bill">
          <div style="padding:16px">
            <mat-form-field appearance="outline" style="width:200px">
              <mat-label>Sales Invoice ID</mat-label>
              <input matInput type="number" [(ngModel)]="ewayInvoiceId" placeholder="Invoice ID" />
            </mat-form-field>
            <button mat-raised-button color="accent" (click)="generateEwayBill()" style="margin-left:8px">
              Generate JSON
            </button>
            @if (ewayBillJson()) {
              <pre
                style="background:#f5f5f5;padding:12px;border-radius:4px;overflow:auto;max-height:400px;margin-top:16px"
                >{{ ewayBillJson() | json }}</pre>
            }
          </div>
        </mat-tab>
      </mat-tab-group>
    }
  `,
})
export class GstFilingComponent {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);

  fromDate = `${new Date().getFullYear()}-04-01`;
  toDate = (() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  })();
  ewayInvoiceId: number | null = null;

  gstr1 = signal<Gstr1Response | null>(null);
  gstr3b = signal<Gstr3bResponse | null>(null);
  ewayBillJson = signal<object | null>(null);
  loading = signal(false);

  b2bCols = ['customerGstin', 'invoiceNo', 'invoiceDate', 'taxableValue', 'grandTotal'];

  loadAll(): void {
    this.loading.set(true);
    const params = `?from=${this.fromDate}&to=${this.toDate}`;
    let pending = 2;
    const done = () => {
      if (--pending === 0) this.loading.set(false);
    };

    this.http.get<Gstr1Response>(`/api/finance/gst/gstr1${params}`).subscribe({
      next: (d) => {
        this.gstr1.set(d);
        done();
      },
      error: () => {
        this.snackBar.open('Failed to load GSTR-1', 'Close', { duration: 3000 });
        done();
      },
    });

    this.http.get<Gstr3bResponse>(`/api/finance/gst/gstr3b${params}`).subscribe({
      next: (d) => {
        this.gstr3b.set(d);
        done();
      },
      error: () => {
        this.snackBar.open('Failed to load GSTR-3B', 'Close', { duration: 3000 });
        done();
      },
    });
  }

  generateEwayBill(): void {
    if (!this.ewayInvoiceId) return;
    this.http.post<object>('/api/finance/gst/eway-bill', { salesInvoiceId: this.ewayInvoiceId }).subscribe({
      next: (d) => this.ewayBillJson.set(d),
      error: () => this.snackBar.open('Failed to generate e-way bill', 'Close', { duration: 3000 }),
    });
  }

  fileGstr1(): void {
    const params = `?from=${this.fromDate}&to=${this.toDate}`;
    this.http.post(`/api/finance/gst/gstr1/file${params}`, {}).subscribe({
      next: () => this.snackBar.open('GSTR-1 marked as filed', 'Close', { duration: 3000 }),
      error: () => this.snackBar.open('Failed to file GSTR-1', 'Close', { duration: 3000 }),
    });
  }

  fileGstr3b(): void {
    const params = `?from=${this.fromDate}&to=${this.toDate}`;
    this.http.post(`/api/finance/gst/gstr3b/file${params}`, {}).subscribe({
      next: () => this.snackBar.open('GSTR-3B marked as filed', 'Close', { duration: 3000 }),
      error: () => this.snackBar.open('Failed to file GSTR-3B', 'Close', { duration: 3000 }),
    });
  }
}
