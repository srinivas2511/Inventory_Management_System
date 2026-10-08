import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatTableModule } from '@angular/material/table';
import { FinanceApi, SettlementDto } from './finance.api';

export interface SettlementHistoryData {
  kind: 'AP' | 'AR';
  invoice: { id: number; invoiceNumber: string; currencyCode: string };
}

/**
 * The payments (or receipts) of one invoice. For an invoice in a foreign currency each line shows the rate it was
 * settled at and the realised exchange gain or loss booked with it (FIN-009); in rupees there is none.
 */
@Component({
  selector: 'app-settlement-history-dialog',
  standalone: true,
  imports: [DecimalPipe, MatButtonModule, MatDialogModule, MatTableModule, RouterLink],
  template: `
    <h2 mat-dialog-title>{{ data.kind === 'AP' ? 'Payments' : 'Receipts' }} on {{ data.invoice.invoiceNumber }}</h2>
    <mat-dialog-content>
      @if (loaded() && rows().length === 0) {
        <p>Nothing has been {{ data.kind === 'AP' ? 'paid' : 'received' }} on this invoice yet.</p>
      }
      @if (rows().length > 0) {
        <table mat-table [dataSource]="rows()" data-testid="settlement-table">
          <ng-container matColumnDef="date">
            <th mat-header-cell *matHeaderCellDef>Date</th>
            <td mat-cell *matCellDef="let r">{{ r.date }}</td>
          </ng-container>
          <ng-container matColumnDef="reference">
            <th mat-header-cell *matHeaderCellDef>Reference</th>
            <td mat-cell *matCellDef="let r">{{ r.reference }}</td>
          </ng-container>
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef>Amount ({{ data.invoice.currencyCode }})</th>
            <td mat-cell *matCellDef="let r">{{ r.amount | number: '1.2-4' }}</td>
          </ng-container>
          <ng-container matColumnDef="rate">
            <th mat-header-cell *matHeaderCellDef>Rate</th>
            <td mat-cell *matCellDef="let r">{{ r.exchangeRate | number: '1.2-6' }}</td>
          </ng-container>
          <ng-container matColumnDef="carrying">
            <th mat-header-cell *matHeaderCellDef>Settled (INR)</th>
            <td mat-cell *matCellDef="let r">{{ r.carryingInr | number: '1.2-4' }}</td>
          </ng-container>
          <ng-container matColumnDef="fx">
            <th mat-header-cell *matHeaderCellDef>Exchange gain / loss (INR)</th>
            <td mat-cell *matCellDef="let r" [class.loss]="r.fxDifference < 0" data-testid="fx-difference">
              @if (r.fxDifference === 0) {
                none
              } @else if (r.fxDifference > 0) {
                Gain {{ r.fxDifference | number: '1.2-4' }}
              } @else {
                Loss {{ -r.fxDifference | number: '1.2-4' }}
              }
            </td>
          </ng-container>
          <ng-container matColumnDef="journal">
            <th mat-header-cell *matHeaderCellDef>Journal</th>
            <td mat-cell *matCellDef="let r">
              @if (r.journalHeaderId) {
                <a [routerLink]="['/finance/journals', r.journalHeaderId]" mat-dialog-close>View</a>
              }
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .loss {
        color: var(--mat-sys-error, #b3261e);
      }
    `,
  ],
})
export class SettlementHistoryDialogComponent implements OnInit {
  protected readonly data = inject<SettlementHistoryData>(MAT_DIALOG_DATA);
  protected readonly columns = ['date', 'reference', 'amount', 'rate', 'carrying', 'fx', 'journal'];
  protected readonly rows = signal<SettlementDto[]>([]);
  protected readonly loaded = signal(false);
  private readonly api = inject(FinanceApi);

  ngOnInit(): void {
    const call =
      this.data.kind === 'AP'
        ? this.api.getApPayments(this.data.invoice.id)
        : this.api.getArReceipts(this.data.invoice.id);
    call.subscribe({
      next: (r) => {
        this.rows.set(r);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }
}
