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
import { MatTableModule } from '@angular/material/table';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';

export interface QuotationDto {
  id: number;
  docNo: string;
  docDate: string | null;
  status: string;
  revisionNo: number;
  customerId: number;
  currency: string;
  grandTotal: number | null;
  remarks: string | null;
  items: QuotationItemDto[];
}

export interface QuotationItemDto {
  id: number;
  lineNo: number;
  productId: number | null;
  description: string | null;
  qty: number;
  uom: string | null;
  rate: number | null;
  lineTotal: number | null;
}

export interface QuotationRevisionDto {
  id: number;
  quotationId: number;
  revisionNumber: number;
  revisedAt: string;
  revisedBy: number | null;
  reason: string | null;
  snapshot: string;
}

@Component({
  selector: 'app-quotation-revisions-dialog',
  standalone: true,
  imports: [
    MatButtonModule,
    MatDialogModule,
    MatTableModule,
    IstDatePipe,
  ],
  template: `
    <h2 mat-dialog-title i18n="@@quotation.revisions_title">Quotation Revision History</h2>
    <mat-dialog-content>
      @if (revisions().length === 0) {
        <p i18n="@@quotation.no_revisions">No revision history available.</p>
      } @else {
        <table mat-table [dataSource]="revisions()" class="full-width">
          <ng-container matColumnDef="revisionNumber">
            <th mat-header-cell *matHeaderCellDef>Rev #</th>
            <td mat-cell *matCellDef="let r">{{ r.revisionNumber }}</td>
          </ng-container>
          <ng-container matColumnDef="revisedAt">
            <th mat-header-cell *matHeaderCellDef>Date</th>
            <td mat-cell *matCellDef="let r">{{ r.revisedAt | istDate }}</td>
          </ng-container>
          <ng-container matColumnDef="reason">
            <th mat-header-cell *matHeaderCellDef>Reason</th>
            <td mat-cell *matCellDef="let r">{{ r.reason }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="['revisionNumber','revisedAt','reason']"></tr>
          <tr mat-row *matRowDef="let r; columns: ['revisionNumber','revisedAt','reason']"></tr>
        </table>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close i18n="@@common.close">Close</button>
    </mat-dialog-actions>
  `,
})
export class QuotationRevisionsDialogComponent implements OnInit {
  private http = inject(HttpClient);

  quotationId = signal<number | null>(null);
  revisions = signal<QuotationRevisionDto[]>([]);

  ngOnInit(): void {
    const id = this.quotationId();
    if (id) {
      this.http.get<QuotationRevisionDto[]>(`/api/sales/quotations/${id}/revisions`).subscribe({
        next: (data) => this.revisions.set(data),
      });
    }
  }
}

@Component({
  selector: 'app-amend-quotation-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSnackBarModule,
  ],
  template: `
    <h2 mat-dialog-title i18n="@@quotation.amend_title">Amend Quotation</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="outline">
          <mat-label i18n="@@quotation.field_amend_reason">Reason for Amendment</mat-label>
          <textarea matInput formControlName="reason" rows="3"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close i18n="@@common.cancel">Cancel</button>
      <button mat-flat-button color="warn"
              [disabled]="form.invalid"
              (click)="save()"
              i18n="@@quotation.btn_amend">Amend</button>
    </mat-dialog-actions>
  `,
})
export class AmendQuotationDialogComponent {
  private http = inject(HttpClient);
  private dialog = inject(MatDialog);
  private fb = inject(FormBuilder);
  private snackBar = inject(MatSnackBar);

  quotationId = signal<number | null>(null);

  form = this.fb.group({
    reason: ['', Validators.required],
  });

  save(): void {
    if (this.form.invalid) return;
    const id = this.quotationId();
    if (!id) return;
    const body = { reason: this.form.value.reason };
    this.http.post(`/api/sales/quotations/${id}/amend`, body).subscribe({
      next: () => this.dialog.closeAll(),
      error: (err) => {
        const msg = err?.error?.message ?? 'Amend failed';
        this.snackBar.open(msg, 'Close', { duration: 4000 });
      },
    });
  }
}

@Component({
  selector: 'app-quotation-list',
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
      <h2 i18n="@@nav.quotations">Quotations</h2>
    </div>
    @if (loading()) {
      <mat-spinner diameter="40" />
    } @else {
      <table mat-table [dataSource]="quotations()" class="mat-elevation-z2 full-width">
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
        <ng-container matColumnDef="currency">
          <th mat-header-cell *matHeaderCellDef>Currency</th>
          <td mat-cell *matCellDef="let row">{{ row.currency }}</td>
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
            @if (row.status === 'APPROVED' || row.status === 'SENT') {
              <button mat-stroked-button color="warn"
                      (click)="openAmendDialog(row)"
                      aria-label="Amend quotation"
                      i18n="@@quotation.btn_amend_short">Amend</button>
            }
            <button mat-icon-button
                    (click)="openRevisionsDialog(row)"
                    aria-label="View revision history"
                    matTooltip="Revision History">
              <mat-icon aria-hidden="true">history</mat-icon>
            </button>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
    }
  `,
})
export class QuotationListComponent implements OnInit {
  private http = inject(HttpClient);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);

  columns = ['docNo', 'docDate', 'status', 'currency', 'grandTotal', 'items', 'actions'];
  quotations = signal<QuotationDto[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.http.get<QuotationDto[]>('/api/sales/quotations').subscribe({
      next: (data) => {
        this.quotations.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Failed to load quotations', 'Close', { duration: 3000 });
      },
    });
  }

  openAmendDialog(quotation: QuotationDto): void {
    const ref = this.dialog.open(AmendQuotationDialogComponent, { width: '480px' });
    const instance = ref.componentInstance as AmendQuotationDialogComponent;
    instance.quotationId.set(quotation.id);
    ref.afterClosed().subscribe(() => this.load());
  }

  openRevisionsDialog(quotation: QuotationDto): void {
    const ref = this.dialog.open(QuotationRevisionsDialogComponent, { width: '600px' });
    const instance = ref.componentInstance as QuotationRevisionsDialogComponent;
    instance.quotationId.set(quotation.id);
  }
}
