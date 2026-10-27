import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { problemMessage } from '../../core/models/problem.model';
import {
  BalanceSheetStatement,
  CashFlowStatement,
  FinanceApi,
  FiscalPeriodDto,
  ProfitLossStatement,
} from './finance.api';

@Component({
  selector: 'app-financial-statements',
  standalone: true,
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    MatTabsModule,
  ],
  styles: [
    `
      .page-header {
        margin-bottom: 8px;
      }
      .controls {
        display: flex;
        gap: 16px;
        align-items: center;
        margin-bottom: 16px;
        flex-wrap: wrap;
      }
      .statement-section {
        margin-bottom: 24px;
      }
      .section-title {
        font-weight: 600;
        padding: 8px 0 4px;
      }
      .subtotal-row td {
        font-weight: 600;
        border-top: 1px solid rgba(0, 0, 0, 0.12);
      }
      .total-row td {
        font-weight: 700;
        border-top: 2px solid rgba(0, 0, 0, 0.3);
        font-size: 1.05em;
      }
      .positive {
        color: #1b5e20;
      }
      .negative {
        color: #b71c1c;
      }
      .error {
        color: #b71c1c;
        margin: 8px 0;
      }
      table {
        width: 100%;
      }
      .num {
        text-align: right;
      }
      .hidden {
        visibility: hidden;
        height: 0;
        overflow: hidden;
      }
      .empty-state {
        padding: 32px 0;
        color: rgba(0, 0, 0, 0.45);
        text-align: center;
      }
    `,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Financial Statements</h2>
    </div>

    <div class="controls">
      <mat-form-field appearance="outline">
        <mat-label>Period</mat-label>
        <mat-select [value]="selectedPeriodId()" (selectionChange)="selectPeriod($event.value)">
          @for (p of periods(); track p.id) {
            <mat-option [value]="p.id">{{ p.periodName }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <button mat-flat-button color="primary" [disabled]="!selectedPeriodId() || loading()" (click)="load()">
        @if (loading()) { Generating… } @else { Generate }
      </button>
    </div>

    @if (errorMessage()) {
      <p class="error" role="alert">{{ errorMessage() }}</p>
    }

    @if (loading()) {
      <div style="display:flex;justify-content:center;padding:48px"><mat-spinner diameter="40" /></div>
    }

    <mat-tab-group [class.hidden]="loading()">
      <!-- ── Profit & Loss ─────────────────────────────────── -->
      <mat-tab label="Profit & Loss">
        @if (!pl() && !loading()) {
          <p class="empty-state">Select a period and click <strong>Generate</strong> to view the Profit &amp; Loss statement.</p>
        }
        @if (pl(); as r) {
          <div style="padding: 16px 0">
            <p class="mat-body-2">Fiscal year {{ r.fiscalYear }} · YTD through {{ r.periodName }}</p>

            <div class="statement-section">
              <div class="section-title">Revenue</div>
              <table mat-table [dataSource]="r.revenue">
                <ng-container matColumnDef="code">
                  <th mat-header-cell *matHeaderCellDef>Account</th>
                  <td mat-cell *matCellDef="let row">{{ row.accountCode }} {{ row.accountName }}</td>
                </ng-container>
                <ng-container matColumnDef="amount">
                  <th mat-header-cell *matHeaderCellDef class="num">Amount (₹)</th>
                  <td mat-cell *matCellDef="let row" class="num">{{ row.balance | number: '1.2-2' }}</td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="['code', 'amount']"></tr>
                <tr mat-row *matRowDef="let row; columns: ['code', 'amount']"></tr>
              </table>
              <table mat-table [dataSource]="[{}]" class="subtotal-row">
                <ng-container matColumnDef="label">
                  <td mat-cell *matCellDef>Total Revenue</td>
                </ng-container>
                <ng-container matColumnDef="amount">
                  <td mat-cell *matCellDef class="num">{{ r.totalRevenue | number: '1.2-2' }}</td>
                </ng-container>
                <tr mat-row *matRowDef="let row; columns: ['label', 'amount']"></tr>
              </table>
            </div>

            <div class="statement-section">
              <div class="section-title">Cost of Goods Sold</div>
              <table mat-table [dataSource]="r.cogs">
                <ng-container matColumnDef="code">
                  <th mat-header-cell *matHeaderCellDef>Account</th>
                  <td mat-cell *matCellDef="let row">{{ row.accountCode }} {{ row.accountName }}</td>
                </ng-container>
                <ng-container matColumnDef="amount">
                  <th mat-header-cell *matHeaderCellDef class="num">Amount (₹)</th>
                  <td mat-cell *matCellDef="let row" class="num">{{ row.balance | number: '1.2-2' }}</td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="['code', 'amount']"></tr>
                <tr mat-row *matRowDef="let row; columns: ['code', 'amount']"></tr>
              </table>
            </div>

            <table mat-table [dataSource]="[{}]" class="subtotal-row">
              <ng-container matColumnDef="label">
                <td mat-cell *matCellDef>Gross Profit</td>
              </ng-container>
              <ng-container matColumnDef="amount">
                <td
                  mat-cell
                  *matCellDef
                  class="num"
                  [class.positive]="r.grossProfit >= 0"
                  [class.negative]="r.grossProfit < 0"
                >
                  {{ r.grossProfit | number: '1.2-2' }}
                </td>
              </ng-container>
              <tr mat-row *matRowDef="let row; columns: ['label', 'amount']"></tr>
            </table>

            <div class="statement-section" style="margin-top:16px">
              <div class="section-title">Operating Expenses</div>
              <table mat-table [dataSource]="r.expenses">
                <ng-container matColumnDef="code">
                  <th mat-header-cell *matHeaderCellDef>Account</th>
                  <td mat-cell *matCellDef="let row">{{ row.accountCode }} {{ row.accountName }}</td>
                </ng-container>
                <ng-container matColumnDef="amount">
                  <th mat-header-cell *matHeaderCellDef class="num">Amount (₹)</th>
                  <td mat-cell *matCellDef="let row" class="num">{{ row.balance | number: '1.2-2' }}</td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="['code', 'amount']"></tr>
                <tr mat-row *matRowDef="let row; columns: ['code', 'amount']"></tr>
              </table>
            </div>

            <table mat-table [dataSource]="[{}]" class="total-row">
              <ng-container matColumnDef="label">
                <td mat-cell *matCellDef>Net Income</td>
              </ng-container>
              <ng-container matColumnDef="amount">
                <td
                  mat-cell
                  *matCellDef
                  class="num"
                  [class.positive]="r.netIncome >= 0"
                  [class.negative]="r.netIncome < 0"
                >
                  {{ r.netIncome | number: '1.2-2' }}
                </td>
              </ng-container>
              <tr mat-row *matRowDef="let row; columns: ['label', 'amount']"></tr>
            </table>
          </div>
        }
      </mat-tab>

      <!-- ── Balance Sheet ─────────────────────────────────── -->
      <mat-tab label="Balance Sheet">
        @if (!bs() && !loading()) {
          <p class="empty-state">Select a period and click <strong>Generate</strong> to view the Balance Sheet.</p>
        }
        @if (bs(); as r) {
          <div style="padding: 16px 0">
            <p class="mat-body-2">As at end of {{ r.periodName }}</p>

            <div class="statement-section">
              <div class="section-title">Assets</div>
              <table mat-table [dataSource]="r.assets">
                <ng-container matColumnDef="code">
                  <th mat-header-cell *matHeaderCellDef>Account</th>
                  <td mat-cell *matCellDef="let row">{{ row.accountCode }} {{ row.accountName }}</td>
                </ng-container>
                <ng-container matColumnDef="amount">
                  <th mat-header-cell *matHeaderCellDef class="num">Amount (₹)</th>
                  <td mat-cell *matCellDef="let row" class="num">{{ row.balance | number: '1.2-2' }}</td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="['code', 'amount']"></tr>
                <tr mat-row *matRowDef="let row; columns: ['code', 'amount']"></tr>
              </table>
              <table mat-table [dataSource]="[{}]" class="subtotal-row">
                <ng-container matColumnDef="label">
                  <td mat-cell *matCellDef>Total Assets</td>
                </ng-container>
                <ng-container matColumnDef="amount">
                  <td mat-cell *matCellDef class="num">{{ r.totalAssets | number: '1.2-2' }}</td>
                </ng-container>
                <tr mat-row *matRowDef="let row; columns: ['label', 'amount']"></tr>
              </table>
            </div>

            <div class="statement-section">
              <div class="section-title">Liabilities</div>
              <table mat-table [dataSource]="r.liabilities">
                <ng-container matColumnDef="code">
                  <th mat-header-cell *matHeaderCellDef>Account</th>
                  <td mat-cell *matCellDef="let row">{{ row.accountCode }} {{ row.accountName }}</td>
                </ng-container>
                <ng-container matColumnDef="amount">
                  <th mat-header-cell *matHeaderCellDef class="num">Amount (₹)</th>
                  <td mat-cell *matCellDef="let row" class="num">{{ row.balance | number: '1.2-2' }}</td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="['code', 'amount']"></tr>
                <tr mat-row *matRowDef="let row; columns: ['code', 'amount']"></tr>
              </table>
            </div>

            <div class="statement-section">
              <div class="section-title">Equity</div>
              <table mat-table [dataSource]="r.equity">
                <ng-container matColumnDef="code">
                  <th mat-header-cell *matHeaderCellDef>Account</th>
                  <td mat-cell *matCellDef="let row">{{ row.accountCode }} {{ row.accountName }}</td>
                </ng-container>
                <ng-container matColumnDef="amount">
                  <th mat-header-cell *matHeaderCellDef class="num">Amount (₹)</th>
                  <td mat-cell *matCellDef="let row" class="num">{{ row.balance | number: '1.2-2' }}</td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="['code', 'amount']"></tr>
                <tr mat-row *matRowDef="let row; columns: ['code', 'amount']"></tr>
              </table>
              <table mat-table [dataSource]="[{}]" class="subtotal-row">
                <ng-container matColumnDef="label">
                  <td mat-cell *matCellDef>Retained Earnings (current)</td>
                </ng-container>
                <ng-container matColumnDef="amount">
                  <td mat-cell *matCellDef class="num">{{ r.retainedEarnings | number: '1.2-2' }}</td>
                </ng-container>
                <tr mat-row *matRowDef="let row; columns: ['label', 'amount']"></tr>
              </table>
              <table mat-table [dataSource]="[{}]" class="subtotal-row">
                <ng-container matColumnDef="label">
                  <td mat-cell *matCellDef>Total Equity</td>
                </ng-container>
                <ng-container matColumnDef="amount">
                  <td mat-cell *matCellDef class="num">{{ r.totalEquity | number: '1.2-2' }}</td>
                </ng-container>
                <tr mat-row *matRowDef="let row; columns: ['label', 'amount']"></tr>
              </table>
            </div>

            <table mat-table [dataSource]="[{}]" class="total-row">
              <ng-container matColumnDef="label">
                <td mat-cell *matCellDef>Total Liabilities &amp; Equity</td>
              </ng-container>
              <ng-container matColumnDef="amount">
                <td mat-cell *matCellDef class="num">{{ r.totalLiabilitiesAndEquity | number: '1.2-2' }}</td>
              </ng-container>
              <tr mat-row *matRowDef="let row; columns: ['label', 'amount']"></tr>
            </table>
          </div>
        }
      </mat-tab>

      <!-- ── Cash Flow ─────────────────────────────────────── -->
      <mat-tab label="Cash Flow">
        @if (!cf() && !loading()) {
          <p class="empty-state">Select a period and click <strong>Generate</strong> to view the Cash Flow statement.</p>
        }
        @if (cf(); as r) {
          <div style="padding: 16px 0">
            <p class="mat-body-2">Cash movements in {{ r.periodName }} · main bank account (1201)</p>

            <table mat-table [dataSource]="r.lines" class="mat-elevation-z1">
              <ng-container matColumnDef="activity">
                <th mat-header-cell *matHeaderCellDef>Activity</th>
                <td mat-cell *matCellDef="let row">{{ row.activity }}</td>
              </ng-container>
              <ng-container matColumnDef="journalType">
                <th mat-header-cell *matHeaderCellDef>Journal Type</th>
                <td mat-cell *matCellDef="let row">{{ row.journalType }}</td>
              </ng-container>
              <ng-container matColumnDef="sourceModule">
                <th mat-header-cell *matHeaderCellDef>Module</th>
                <td mat-cell *matCellDef="let row">{{ row.sourceModule }}</td>
              </ng-container>
              <ng-container matColumnDef="netCash">
                <th mat-header-cell *matHeaderCellDef class="num">Net Cash (₹)</th>
                <td
                  mat-cell
                  *matCellDef="let row"
                  class="num"
                  [class.positive]="row.netCash >= 0"
                  [class.negative]="row.netCash < 0"
                >
                  {{ row.netCash | number: '1.2-2' }}
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="['activity', 'journalType', 'sourceModule', 'netCash']"></tr>
              <tr mat-row *matRowDef="let row; columns: ['activity', 'journalType', 'sourceModule', 'netCash']"></tr>
            </table>

            <div style="margin-top:16px; display:flex; gap:32px; flex-wrap:wrap">
              <div>
                <span class="mat-body-2">Net Operating:</span>&nbsp;
                <strong [class.positive]="r.netOperating >= 0" [class.negative]="r.netOperating < 0">{{
                  r.netOperating | number: '1.2-2'
                }}</strong>
              </div>
              <div>
                <span class="mat-body-2">Net Investing:</span>&nbsp;
                <strong [class.positive]="r.netInvesting >= 0" [class.negative]="r.netInvesting < 0">{{
                  r.netInvesting | number: '1.2-2'
                }}</strong>
              </div>
              <div>
                <span class="mat-body-2">Net Financing:</span>&nbsp;
                <strong [class.positive]="r.netFinancing >= 0" [class.negative]="r.netFinancing < 0">{{
                  r.netFinancing | number: '1.2-2'
                }}</strong>
              </div>
              <div>
                <span class="mat-body-2">Net Change:</span>&nbsp;
                <strong [class.positive]="r.netCashChange >= 0" [class.negative]="r.netCashChange < 0">{{
                  r.netCashChange | number: '1.2-2'
                }}</strong>
              </div>
            </div>
          </div>
        }
      </mat-tab>
    </mat-tab-group>
  `,
})
export class FinancialStatementsComponent implements OnInit {
  private api = inject(FinanceApi);
  private snackBar = inject(MatSnackBar);

  periods = signal<FiscalPeriodDto[]>([]);
  selectedPeriodId = signal<number | null>(null);
  loading = signal(false);
  errorMessage = signal('');

  pl = signal<ProfitLossStatement | null>(null);
  bs = signal<BalanceSheetStatement | null>(null);
  cf = signal<CashFlowStatement | null>(null);

  ngOnInit(): void {
    this.api.getPeriods().subscribe({
      next: (ps) => {
        this.periods.set(ps);
        const open = ps.find((p) => p.status === 'OPEN') ?? ps[ps.length - 1];
        if (open) {
          this.selectedPeriodId.set(open.id);
          this.load();
        }
      },
      error: (err) => this.snackBar.open(problemMessage(err, 'Failed to load periods'), 'Close', { duration: 4000 }),
    });
  }

  selectPeriod(id: number): void {
    this.selectedPeriodId.set(id);
    this.pl.set(null);
    this.bs.set(null);
    this.cf.set(null);
  }

  load(): void {
    const periodId = this.selectedPeriodId();
    if (!periodId) return;
    this.loading.set(true);
    this.errorMessage.set('');

    let remaining = 3;
    const done = () => {
      remaining--;
      if (remaining === 0) this.loading.set(false);
    };

    this.api.getProfitLoss(periodId).subscribe({
      next: (r) => {
        this.pl.set(r);
        done();
      },
      error: (err) => {
        this.errorMessage.set(problemMessage(err, 'Failed to load P&L'));
        done();
      },
    });

    this.api.getBalanceSheet(periodId).subscribe({
      next: (r) => {
        this.bs.set(r);
        done();
      },
      error: (err) => {
        this.errorMessage.set(problemMessage(err, 'Failed to load Balance Sheet'));
        done();
      },
    });

    this.api.getCashFlow(periodId).subscribe({
      next: (r) => {
        this.cf.set(r);
        done();
      },
      error: (err) => {
        this.errorMessage.set(problemMessage(err, 'Failed to load Cash Flow'));
        done();
      },
    });
  }
}
