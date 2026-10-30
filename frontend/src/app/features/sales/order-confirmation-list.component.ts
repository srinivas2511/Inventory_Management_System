import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';

export interface OcDto {
  id: number;
  docNo: string;
  docDate: string | null;
  status: string;
  ocKind: string;
  customerId: number;
  customerPoNo: string | null;
  deliveryDate: string | null;
  currency: string;
  grandTotal: number | null;
  submittedBy: number | null;
  items: OcItemDto[];
}

export interface OcItemDto {
  id: number;
  lineNo: number;
  productId: number | null;
  description: string | null;
  qty: number;
  uom: string | null;
  rate: number | null;
  lineTotal: number | null;
  qtyDispatched: number | null;
}

interface CustomerLookup {
  id: number;
  name: string;
}

@Component({
  selector: 'app-new-oc-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  template: `
    <h2 mat-dialog-title i18n="@@oc.new_dialog_title">New Order Confirmation</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="outline">
          <mat-label i18n="@@oc.field_customer">Customer</mat-label>
          <mat-select formControlName="customerId">
            @for (c of customers(); track c.id) {
              <mat-option [value]="c.id">{{ c.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label i18n="@@oc.field_customer_po">Customer PO No.</mat-label>
          <input matInput formControlName="customerPoNo" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label i18n="@@oc.field_remarks">Remarks</mat-label>
          <textarea matInput formControlName="remarks" rows="2"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close i18n="@@common.cancel">Cancel</button>
      <button mat-flat-button color="primary"
              [disabled]="form.invalid"
              (click)="save()"
              i18n="@@common.save">Save</button>
    </mat-dialog-actions>
  `,
})
export class NewOcDialogComponent implements OnInit {
  private http = inject(HttpClient);
  private dialog = inject(MatDialog);
  private fb = inject(FormBuilder);

  customers = signal<CustomerLookup[]>([]);

  form = this.fb.group({
    customerId: [null as number | null, Validators.required],
    customerPoNo: [''],
    remarks: [''],
  });

  ngOnInit(): void {
    this.http.get<CustomerLookup[]>('/api/lookups/customers').subscribe({
      next: (data) => this.customers.set(data),
    });
  }

  save(): void {
    if (this.form.invalid) return;
    const v = this.form.value;
    const body = {
      customerId: v.customerId,
      customerPoNo: v.customerPoNo || null,
      remarks: v.remarks || null,
      items: [],
    };
    this.http.post('/api/sales/order-confirmations', body).subscribe({
      next: (oc: any) => this.dialog.closeAll(),
      error: () => {},
    });
  }
}

@Component({
  selector: 'app-order-confirmation-list',
  standalone: true,
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatSnackBarModule,
    MatTooltipModule,
    IstDatePipe,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.order_confirmations">Order Confirmations</h2>
      <button mat-flat-button color="primary" (click)="openNewOcDialog()" aria-label="New Order Confirmation">
        <mat-icon aria-hidden="true">add</mat-icon>
        <span i18n="@@oc.btn_new">+ New OC</span>
      </button>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <table mat-table [dataSource]="ocs()" class="mat-elevation-z2 full-width">
        <ng-container matColumnDef="docNo">
          <th mat-header-cell *matHeaderCellDef>Doc No</th>
          <td mat-cell *matCellDef="let row">{{ row.docNo }}</td>
        </ng-container>
        <ng-container matColumnDef="docDate">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let row">{{ row.docDate | istDate }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let row"><app-status-badge [status]="row.status" /></td>
        </ng-container>
        <ng-container matColumnDef="customerPoNo">
          <th mat-header-cell *matHeaderCellDef>Customer PO</th>
          <td mat-cell *matCellDef="let row">{{ row.customerPoNo }}</td>
        </ng-container>
        <ng-container matColumnDef="deliveryDate">
          <th mat-header-cell *matHeaderCellDef>Delivery Date</th>
          <td mat-cell *matCellDef="let row">{{ row.deliveryDate | istDate }}</td>
        </ng-container>
        <ng-container matColumnDef="grandTotal">
          <th mat-header-cell *matHeaderCellDef>Total</th>
          <td mat-cell *matCellDef="let row">{{ row.grandTotal | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="items">
          <th mat-header-cell *matHeaderCellDef>Lines</th>
          <td mat-cell *matCellDef="let row">{{ row.items?.length ?? 0 }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let row">
            @if (row.status === 'DRAFT') {
              <button mat-stroked-button color="accent"
                      (click)="submitOc(row)"
                      aria-label="Submit OC for approval"
                      i18n="@@oc.btn_submit">Submit</button>
            }
            @if (row.status === 'SUBMITTED') {
              <button mat-stroked-button color="primary"
                      (click)="approveOc(row)"
                      aria-label="Approve OC"
                      i18n="@@oc.btn_approve">Approve</button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
})
export class OrderConfirmationListComponent implements OnInit {
  private http = inject(HttpClient);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);

  columns = ['docNo', 'docDate', 'status', 'customerPoNo', 'deliveryDate', 'grandTotal', 'items', 'actions'];
  ocs = signal<OcDto[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.http.get<OcDto[]>('/api/sales/order-confirmations').subscribe({
      next: (data) => {
        this.ocs.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load order confirmations', 'Close', { duration: 3000 });
      },
    });
  }

  openNewOcDialog(): void {
    const ref = this.dialog.open(NewOcDialogComponent, { width: '480px' });
    ref.afterClosed().subscribe(() => this.load());
  }

  submitOc(oc: OcDto): void {
    this.http.post(`/api/sales/order-confirmations/${oc.id}/submit`, {}).subscribe({
      next: () => {
        this.snackBar.open('OC submitted', 'Close', { duration: 2000 });
        this.load();
      },
      error: () => this.snackBar.open('Submit failed', 'Close', { duration: 3000 }),
    });
  }

  approveOc(oc: OcDto): void {
    this.http.post(`/api/sales/order-confirmations/${oc.id}/approve`, {}).subscribe({
      next: () => {
        this.snackBar.open('OC approved', 'Close', { duration: 2000 });
        this.load();
      },
      error: (err) => {
        const msg = err?.error?.message ?? 'Approve failed (SoD: approver must differ from submitter)';
        this.snackBar.open(msg, 'Close', { duration: 4000 });
      },
    });
  }
}
