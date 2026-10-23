import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { problemMessage } from '../../core/models/problem.model';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { FinanceApi, FiscalPeriodDto, FxRevaluationDto, FxRevaluationRunResult } from './finance.api';

/**
 * Period-end unrealised FX revaluation: restates open foreign-currency AP and AR invoices
 * at the period closing rate and posts the movement to unrealised gain (4902) or loss (5209).
 */
@Component({
  selector: 'app-fx-revaluation',
  standalone: true,
  imports: [
    DecimalPipe,
    HasPermissionDirective,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">FX Revaluation</h2>
    </div>
    <p class="note">
      Restates open foreign-currency invoices at the period closing rate. The movement posts to unrealised FX gain
      (4902) or unrealised FX loss (5209). Safe to re-run — posts only the incremental adjustment since the last run.
    </p>

    <ng-container *appHasPermission="'FX_REVALUATION_RUN'">
      <div class="row">
        <mat-form-field appearance="outline">
          <mat-label>Revaluation period</mat-label>
          <mat-select [value]="selectedPeriodId()" (selectionChange)="selectPeriod($event.value)">
            @for (p of periods(); track p.id) {
              <mat-option [value]="p.id">{{ p.periodName }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <button
          mat-flat-button
          color="primary"
          [disabled]="!selectedPeriodId() || running()"
          (click)="runRevaluation()"
        >
          Run revaluation
        </button>
      </div>
    </ng-container>

    @if (errorMessage()) {
      <p class="error" role="alert">{{ errorMessage() }}</p>
    }

    @if (lastResult()) {
      <div class="result-summary" role="status">
        <strong>Last run result</strong>
        <div class="result-grid">
          <span>AP invoices revalued: {{ lastResult()!.apCount }}</span>
          <span>AR invoices revalued: {{ lastResult()!.arCount }}</span>
          <span>AP unrealised gain: ₹{{ lastResult()!.apGainInr | number: '1.2-4' }}</span>
          <span>AP unrealised loss: ₹{{ lastResult()!.apLossInr | number: '1.2-4' }}</span>
          <span>AR unrealised gain: ₹{{ lastResult()!.arGainInr | number: '1.2-4' }}</span>
          <span>AR unrealised loss: ₹{{ lastResult()!.arLossInr | number: '1.2-4' }}</span>
        </div>
      </div>
    }

    @if (rows().length) {
      <table mat-table [dataSource]="rows()" class="mat-elevation-z1" data-testid="reval-table">
        <ng-container matColumnDef="documentType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let r">{{ r.documentType === 'AP_INVOICE' ? 'AP' : 'AR' }}</td>
        </ng-container>
        <ng-container matColumnDef="documentNumber">
          <th mat-header-cell *matHeaderCellDef>Invoice</th>
          <td mat-cell *matCellDef="let r">{{ r.documentNumber }}</td>
        </ng-container>
        <ng-container matColumnDef="currencyCode">
          <th mat-header-cell *matHeaderCellDef>Currency</th>
          <td mat-cell *matCellDef="let r">{{ r.currencyCode }}</td>
        </ng-container>
        <ng-container matColumnDef="remainingForeign">
          <th mat-header-cell *matHeaderCellDef>Open balance (foreign)</th>
          <td mat-cell *matCellDef="let r">{{ r.remainingForeign | number: '1.2-4' }}</td>
        </ng-container>
        <ng-container matColumnDef="bookingRate">
          <th mat-header-cell *matHeaderCellDef>Booking rate</th>
          <td mat-cell *matCellDef="let r">{{ r.bookingRate | number: '1.4-6' }}</td>
        </ng-container>
        <ng-container matColumnDef="closingRate">
          <th mat-header-cell *matHeaderCellDef>Closing rate</th>
          <td mat-cell *matCellDef="let r">{{ r.closingRate | number: '1.4-6' }}</td>
        </ng-container>
        <ng-container matColumnDef="differenceInr">
          <th mat-header-cell *matHeaderCellDef>Difference (INR)</th>
          <td mat-cell *matCellDef="let r" [class.gain]="r.differenceInr > 0" [class.loss]="r.differenceInr < 0">
            {{ r.differenceInr | number: '1.2-4' }}
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
      </table>
    }
  `,
  styles: [
    `
      .row {
        display: flex;
        gap: 16px;
        align-items: center;
        flex-wrap: wrap;
        margin-bottom: 16px;
      }
      .result-summary {
        margin: 16px 0;
        padding: 12px 16px;
        background: var(--mat-sys-surface-variant, #f5f5f5);
        border-radius: 4px;
      }
      .result-grid {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 4px 24px;
        margin-top: 8px;
      }
      .gain {
        color: #388e3c;
      }
      .loss {
        color: #d32f2f;
      }
      table {
        width: 100%;
        margin-top: 16px;
      }
    `,
  ],
})
export class FxRevaluationComponent implements OnInit {
  private api = inject(FinanceApi);
  private snack = inject(MatSnackBar);

  periods = signal<FiscalPeriodDto[]>([]);
  selectedPeriodId = signal<number | null>(null);
  running = signal(false);
  lastResult = signal<FxRevaluationRunResult | null>(null);
  rows = signal<FxRevaluationDto[]>([]);
  errorMessage = signal('');

  displayedColumns = [
    'documentType',
    'documentNumber',
    'currencyCode',
    'remainingForeign',
    'bookingRate',
    'closingRate',
    'differenceInr',
  ];

  ngOnInit(): void {
    this.api.getPeriods().subscribe({ next: (ps) => this.periods.set(ps) });
  }

  selectPeriod(id: number): void {
    this.selectedPeriodId.set(id);
    this.errorMessage.set('');
    this.api.listFxRevaluations(id).subscribe({
      next: (rs) => this.rows.set(rs),
      error: () => this.rows.set([]),
    });
  }

  runRevaluation(): void {
    const pid = this.selectedPeriodId();
    if (!pid) return;
    this.running.set(true);
    this.errorMessage.set('');
    this.api.runFxRevaluation(pid).subscribe({
      next: (result) => {
        this.running.set(false);
        this.lastResult.set(result);
        this.snack.open(`Revaluation complete — ${result.apCount + result.arCount} invoice(s) processed`, 'OK', {
          duration: 4000,
        });
        this.api.listFxRevaluations(pid).subscribe({ next: (rs) => this.rows.set(rs) });
      },
      error: (err) => {
        this.running.set(false);
        this.errorMessage.set(problemMessage(err, 'Revaluation failed'));
      },
    });
  }
}
