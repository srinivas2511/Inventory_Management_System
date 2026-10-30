import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';

export interface ChallanDto {
  id: number;
  docNo: string;
  docDate: string | null;
  status: string;
  challanKind: string;
  customerId: number;
  vehicleNo: string | null;
  driverName: string | null;
  lrNo: string | null;
  courier: string | null;
  grandTotal: number | null;
  isReturnable: boolean;
  expectedReturnDate: string | null;
  returnedAt: string | null;
  items: ChallanItemDto[];
}

export interface ChallanItemDto {
  id: number;
  lineNo: number;
  productId: number | null;
  description: string | null;
  qty: number;
  uom: string | null;
  lineTotal: number | null;
}

interface CustomerLookup {
  id: number;
  name: string;
}

const CHALLAN_KINDS = ['DELIVERY', 'NON_RETURNABLE', 'RETURNABLE', 'SERVICE_OUT', 'PURCHASE_RETURN'];

@Component({
  selector: 'app-new-challan-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
  ],
  template: `
    <h2 mat-dialog-title i18n="@@challan.new_dialog_title">New Delivery Challan</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="outline">
          <mat-label i18n="@@challan.field_kind">Challan Kind</mat-label>
          <mat-select formControlName="challanKind">
            @for (k of kinds; track k) {
              <mat-option [value]="k">{{ k }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label i18n="@@challan.field_customer">Customer</mat-label>
          <mat-select formControlName="customerId">
            @for (c of customers(); track c.id) {
              <mat-option [value]="c.id">{{ c.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-checkbox formControlName="isReturnable" i18n="@@challan.field_returnable">
          Returnable
        </mat-checkbox>
        @if (form.value.isReturnable) {
          <mat-form-field appearance="outline">
            <mat-label i18n="@@challan.field_expected_return">Expected Return Date</mat-label>
            <input matInput type="date" formControlName="expectedReturnDate" />
          </mat-form-field>
        }
        <mat-form-field appearance="outline">
          <mat-label i18n="@@challan.field_vehicle_no">Vehicle No.</mat-label>
          <input matInput formControlName="vehicleNo" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label i18n="@@challan.field_driver_name">Driver Name</mat-label>
          <input matInput formControlName="driverName" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label i18n="@@challan.field_remarks">Remarks</mat-label>
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
export class NewChallanDialogComponent implements OnInit {
  private http = inject(HttpClient);
  private dialog = inject(MatDialog);
  private fb = inject(FormBuilder);
  private snackBar = inject(MatSnackBar);

  customers = signal<CustomerLookup[]>([]);
  kinds = CHALLAN_KINDS;

  form = this.fb.group({
    challanKind: ['DELIVERY', Validators.required],
    customerId: [null as number | null, Validators.required],
    isReturnable: [false],
    expectedReturnDate: [''],
    vehicleNo: [''],
    driverName: [''],
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
    const body: Record<string, unknown> = {
      customerId: v.customerId,
      challanKind: v.challanKind,
      isReturnable: v.isReturnable,
      expectedReturnDate: v.isReturnable && v.expectedReturnDate ? v.expectedReturnDate : null,
      vehicleNo: v.vehicleNo || null,
      driverName: v.driverName || null,
      remarks: v.remarks || null,
      items: [],
    };
    this.http.post('/api/sales/delivery-challans', body).subscribe({
      next: () => this.dialog.closeAll(),
      error: () => this.snackBar.open('Failed to create challan', 'Close', { duration: 3000 }),
    });
  }
}

@Component({
  selector: 'app-delivery-challan-list',
  standalone: true,
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatSnackBarModule,
    IstDatePipe,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.delivery_challans">Delivery Challans</h2>
      <button mat-flat-button color="primary" (click)="openNewChallanDialog()" aria-label="New Delivery Challan">
        <mat-icon aria-hidden="true">add</mat-icon>
        <span i18n="@@challan.btn_new">+ New Challan</span>
      </button>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <table mat-table [dataSource]="challans()" class="mat-elevation-z2 full-width">
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
        <ng-container matColumnDef="challanKind">
          <th mat-header-cell *matHeaderCellDef>Kind</th>
          <td mat-cell *matCellDef="let row">{{ row.challanKind }}</td>
        </ng-container>
        <ng-container matColumnDef="lrNo">
          <th mat-header-cell *matHeaderCellDef>LR No</th>
          <td mat-cell *matCellDef="let row">{{ row.lrNo }}</td>
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
              <button mat-stroked-button color="primary"
                      (click)="issueChallan(row)"
                      aria-label="Issue challan"
                      i18n="@@challan.btn_issue">Issue</button>
            }
            @if (row.isReturnable && row.status === 'ISSUED' && !row.returnedAt) {
              <button mat-stroked-button color="warn"
                      (click)="markReturned(row)"
                      aria-label="Mark as returned"
                      i18n="@@challan.btn_mark_returned">Mark Returned</button>
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
})
export class DeliveryChallanListComponent implements OnInit {
  private http = inject(HttpClient);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);

  columns = ['docNo', 'docDate', 'status', 'challanKind', 'lrNo', 'grandTotal', 'items', 'actions'];
  challans = signal<ChallanDto[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.http.get<ChallanDto[]>('/api/sales/delivery-challans').subscribe({
      next: (data) => {
        this.challans.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load delivery challans', 'Close', { duration: 3000 });
      },
    });
  }

  openNewChallanDialog(): void {
    const ref = this.dialog.open(NewChallanDialogComponent, { width: '480px' });
    ref.afterClosed().subscribe(() => this.load());
  }

  issueChallan(challan: ChallanDto): void {
    this.http.post(`/api/sales/delivery-challans/${challan.id}/issue`, {}).subscribe({
      next: () => {
        this.snackBar.open('Challan issued', 'Close', { duration: 2000 });
        this.load();
      },
      error: () => this.snackBar.open('Issue failed', 'Close', { duration: 3000 }),
    });
  }

  markReturned(challan: ChallanDto): void {
    this.http.post(`/api/sales/delivery-challans/${challan.id}/mark-returned`, {}).subscribe({
      next: () => {
        this.snackBar.open('Challan marked as returned', 'Close', { duration: 2000 });
        this.load();
      },
      error: (err) => {
        const msg = err?.error?.message ?? 'Mark returned failed';
        this.snackBar.open(msg, 'Close', { duration: 3000 });
      },
    });
  }
}
