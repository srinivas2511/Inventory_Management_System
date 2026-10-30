import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { problemMessage } from '../../core/models/problem.model';

interface LedgerLine {
  date: string;
  reference: string;
  description: string;
  debit: number;
  credit: number;
  balance: number;
}

interface PartyLedgerDto {
  openingBalance: number;
  lines: LedgerLine[];
  closingBalance: number;
}

@Component({
  selector: 'app-party-ledger',
  standalone: true,
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    IstDatePipe,
  ],
  template: `
    <div class="page-header">
      <h2 i18n="@@nav.item-party-ledger">Party Ledger</h2>
    </div>

    <form class="form-row" [formGroup]="form" (ngSubmit)="load()">
      <mat-form-field appearance="outline">
        <mat-label>Party Type</mat-label>
        <mat-select formControlName="partyType" required>
          <mat-option value="CUSTOMER">Customer</mat-option>
          <mat-option value="SUPPLIER">Supplier</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Party ID</mat-label>
        <input matInput type="number" formControlName="partyId" required />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>From Date</mat-label>
        <input matInput type="date" formControlName="fromDate" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>To Date</mat-label>
        <input matInput type="date" formControlName="toDate" />
      </mat-form-field>
      <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || loading()">
        <mat-icon aria-hidden="true">search</mat-icon>
        Show Ledger
      </button>
    </form>

    @if (ledger()) {
      <div style="margin-top:24px">
        <div class="summary-row">
          <span>Opening Balance: <strong>{{ ledger()!.openingBalance | number: '1.2-2' }}</strong></span>
          <span>Closing Balance: <strong>{{ ledger()!.closingBalance | number: '1.2-2' }}</strong></span>
        </div>
        @if (ledger()!.lines.length === 0) {
          <p class="mat-body-2">No transactions found for the selected period.</p>
        } @else {
          <table mat-table [dataSource]="ledger()!.lines" class="mat-elevation-z1 full-width">
            <ng-container matColumnDef="date">
              <th mat-header-cell *matHeaderCellDef>Date</th>
              <td mat-cell *matCellDef="let row">{{ row.date | istDate }}</td>
            </ng-container>
            <ng-container matColumnDef="reference">
              <th mat-header-cell *matHeaderCellDef>Reference</th>
              <td mat-cell *matCellDef="let row">{{ row.reference }}</td>
            </ng-container>
            <ng-container matColumnDef="description">
              <th mat-header-cell *matHeaderCellDef>Description</th>
              <td mat-cell *matCellDef="let row">{{ row.description }}</td>
            </ng-container>
            <ng-container matColumnDef="debit">
              <th mat-header-cell *matHeaderCellDef style="text-align:right">Debit</th>
              <td mat-cell *matCellDef="let row" style="text-align:right">
                {{ row.debit > 0 ? (row.debit | number: '1.2-2') : '' }}
              </td>
            </ng-container>
            <ng-container matColumnDef="credit">
              <th mat-header-cell *matHeaderCellDef style="text-align:right">Credit</th>
              <td mat-cell *matCellDef="let row" style="text-align:right">
                {{ row.credit > 0 ? (row.credit | number: '1.2-2') : '' }}
              </td>
            </ng-container>
            <ng-container matColumnDef="balance">
              <th mat-header-cell *matHeaderCellDef style="text-align:right">Balance</th>
              <td mat-cell *matCellDef="let row" style="text-align:right">
                <strong>{{ row.balance | number: '1.2-2' }}</strong>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="cols"></tr>
            <tr mat-row *matRowDef="let row; columns: cols"></tr>
          </table>
        }
      </div>
    }
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
    .form-row { display: flex; flex-wrap: wrap; gap: 16px; align-items: flex-start; }
    .summary-row { display: flex; gap: 32px; margin-bottom: 16px; font-size: 16px; }
  `],
})
export class PartyLedgerComponent {
  private http = inject(HttpClient);
  private snackBar = inject(MatSnackBar);
  private fb = inject(FormBuilder);

  cols = ['date', 'reference', 'description', 'debit', 'credit', 'balance'];
  ledger = signal<PartyLedgerDto | null>(null);
  loading = signal(false);

  form = this.fb.group({
    partyType: ['CUSTOMER', Validators.required],
    partyId: [null as number | null, [Validators.required, Validators.min(1)]],
    fromDate: [''],
    toDate: [''],
  });

  load(): void {
    if (this.form.invalid) return;
    const v = this.form.value;
    let params = new HttpParams()
      .set('partyType', v.partyType!)
      .set('partyId', String(v.partyId!));
    if (v.fromDate) params = params.set('fromDate', v.fromDate);
    if (v.toDate) params = params.set('toDate', v.toDate);
    this.loading.set(true);
    this.http.get<PartyLedgerDto>('/api/finance/party-ledger', { params }).subscribe({
      next: (data) => {
        this.ledger.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.snackBar.open(problemMessage(err, 'Failed to load party ledger'), 'Close', { duration: 4000 });
      },
    });
  }
}
