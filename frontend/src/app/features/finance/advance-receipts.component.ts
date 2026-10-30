import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { problemMessage } from '../../core/models/problem.model';

interface AdvanceReceiptDto {
  id: number;
  customerId: number;
  receiptDate: string;
  amount: number;
  currencyCode: string;
  reference: string | null;
  status: string;
  appliedAmount: number;
  journalId: number | null;
}

@Component({
  selector: 'app-advance-receipts',
  standalone: true,
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSnackBarModule,
    MatTableModule,
    IstDatePipe,
    StatusBadgeComponent,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.item-advance-receipts">Advance Receipts</h2>
    </div>

    <section>
      <h3 class="mat-h3">Create Advance Receipt</h3>
      <form class="form-row" [formGroup]="form" (ngSubmit)="create()">
        <mat-form-field appearance="outline">
          <mat-label>Customer ID</mat-label>
          <input matInput type="number" formControlName="customerId" required />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Receipt Date</mat-label>
          <input matInput type="date" formControlName="receiptDate" required />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Amount (INR)</mat-label>
          <input matInput type="number" step="0.01" formControlName="amount" required />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Reference</mat-label>
          <input matInput formControlName="reference" />
        </mat-form-field>
        <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || saving()">
          <mat-icon aria-hidden="true">add</mat-icon>
          Create
        </button>
      </form>
    </section>

    <section style="margin-top:32px">
      <h3 class="mat-h3">All Advance Receipts</h3>
      @if (receipts().length === 0) {
        <p class="mat-body-2">No advance receipts found.</p>
      } @else {
        <table mat-table [dataSource]="receipts()" class="mat-elevation-z1 full-width">
          <ng-container matColumnDef="id">
            <th mat-header-cell *matHeaderCellDef>#</th>
            <td mat-cell *matCellDef="let r">{{ r.id }}</td>
          </ng-container>
          <ng-container matColumnDef="customer">
            <th mat-header-cell *matHeaderCellDef>Customer</th>
            <td mat-cell *matCellDef="let r">{{ r.customerId }}</td>
          </ng-container>
          <ng-container matColumnDef="receiptDate">
            <th mat-header-cell *matHeaderCellDef>Date</th>
            <td mat-cell *matCellDef="let r">{{ r.receiptDate | istDate }}</td>
          </ng-container>
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef>Amount</th>
            <td mat-cell *matCellDef="let r">{{ r.amount | number: '1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="applied">
            <th mat-header-cell *matHeaderCellDef>Applied</th>
            <td mat-cell *matCellDef="let r">{{ r.appliedAmount | number: '1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let r"><app-status-badge [status]="r.status" /></td>
          </ng-container>
          <ng-container matColumnDef="reference">
            <th mat-header-cell *matHeaderCellDef>Reference</th>
            <td mat-cell *matCellDef="let r">{{ r.reference ?? '—' }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="cols"></tr>
          <tr mat-row *matRowDef="let row; columns: cols"></tr>
        </table>
      }
    </section>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
    .form-row { display: flex; flex-wrap: wrap; gap: 16px; align-items: flex-start; }
  `],
})
export class AdvanceReceiptsComponent implements OnInit {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);
  private fb = inject(FormBuilder);

  cols = ['id', 'customer', 'receiptDate', 'amount', 'applied', 'status', 'reference'];
  receipts = signal<AdvanceReceiptDto[]>([]);
  saving = signal(false);

  form = this.fb.group({
    customerId: [null as number | null, [Validators.required, Validators.min(1)]],
    receiptDate: ['', Validators.required],
    amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    reference: [''],
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.http.get<AdvanceReceiptDto[]>('/api/finance/advance-receipts').subscribe({
      next: (data) => this.receipts.set(data),
      error: (err) => this.snackBar.open(problemMessage(err, 'Failed to load advance receipts'), 'Close', { duration: 4000 }),
    });
  }

  create(): void {
    if (this.form.invalid) return;
    this.saving.set(true);
    const v = this.form.value;
    this.http.post<AdvanceReceiptDto>('/api/finance/advance-receipts', {
      customerId: v.customerId,
      receiptDate: v.receiptDate,
      amount: v.amount,
      currencyCode: 'INR',
      reference: v.reference || null,
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.form.reset();
        this.load();
        this.snackBar.open('Advance receipt created', 'Close', { duration: 3000 });
      },
      error: (err) => {
        this.saving.set(false);
        this.snackBar.open(problemMessage(err, 'Failed to create advance receipt'), 'Close', { duration: 4000 });
      },
    });
  }
}
