import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { problemMessage } from '../../core/models/problem.model';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { BudgetVsActualDto, FinanceApi, FiscalPeriodDto, GlAccountDto } from './finance.api';

/**
 * Actual against budget by GL account (FIN-010). Actual follows the account's normal balance, variance is actual less
 * budget, and a row is marked when an expense is over budget or revenue is under it. Budgets come from the form or from
 * a CSV file with the header account_code,period,amount where period is the period number (1 to 12) of the year.
 */
@Component({
  selector: 'app-budget-vs-actual',
  standalone: true,
  imports: [
    DecimalPipe,
    HasPermissionDirective,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    ReactiveFormsModule,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Budget against actual</h2>
    </div>

    <div class="row">
      <mat-form-field appearance="outline">
        <mat-label>Fiscal year</mat-label>
        <mat-select [value]="year()" (selectionChange)="yearChanged($event.value)">
          @for (y of years(); track y) {
            <mat-option [value]="y">{{ y }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Period</mat-label>
        <mat-select [value]="periodId()" (selectionChange)="periodChanged($event.value)">
          <mat-option [value]="null">Whole year</mat-option>
          @for (p of yearPeriods(); track p.id) {
            <mat-option [value]="p.id">{{ p.periodName }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </div>

    @if (report(); as r) {
      <table mat-table [dataSource]="r.rows" class="mat-elevation-z1" data-testid="bva-table">
        <ng-container matColumnDef="account">
          <th mat-header-cell *matHeaderCellDef>Account</th>
          <td mat-cell *matCellDef="let row">{{ row.accountCode }} · {{ row.accountName }}</td>
        </ng-container>
        <ng-container matColumnDef="budgeted">
          <th mat-header-cell *matHeaderCellDef class="num">Budget</th>
          <td mat-cell *matCellDef="let row" class="num">{{ row.budgeted | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="actual">
          <th mat-header-cell *matHeaderCellDef class="num">Actual</th>
          <td mat-cell *matCellDef="let row" class="num">{{ row.actual | number: '1.2-2' }}</td>
        </ng-container>
        <ng-container matColumnDef="variance">
          <th mat-header-cell *matHeaderCellDef class="num">Variance</th>
          <td mat-cell *matCellDef="let row" class="num" [class.bad]="!row.favourable">
            {{ row.variance | number: '1.2-2' }}
          </td>
        </ng-container>
        <ng-container matColumnDef="pct">
          <th mat-header-cell *matHeaderCellDef class="num">%</th>
          <td mat-cell *matCellDef="let row" class="num" [class.bad]="!row.favourable">
            {{ row.variancePct === null ? 'no budget' : (row.variancePct | number: '1.2-2') + '%' }}
          </td>
        </ng-container>
        <ng-container matColumnDef="verdict">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let row">
            @if (row.budgeted !== 0 && !row.favourable) {
              {{ row.normalBalance === 'DEBIT' ? 'Over budget' : 'Under budget' }}
            }
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      @if (r.rows.length === 0) {
        <p class="note">Nothing is budgeted or posted for this year yet.</p>
      } @else {
        <p class="totals">
          Budget {{ r.totalBudgeted | number: '1.2-2' }} · Actual {{ r.totalActual | number: '1.2-2' }}
        </p>
      }
    }
    @if (loadError()) {
      <p class="error" role="alert">{{ loadError() }}</p>
    }

    <ng-container *appHasPermission="'BUDGET_MANAGE'">
      <h3 class="mat-h3">Set a budget</h3>
      <form [formGroup]="entry" (ngSubmit)="saveEntry()" class="row" novalidate>
        <mat-form-field appearance="outline">
          <mat-label>Account</mat-label>
          <mat-select formControlName="glAccountId">
            @for (a of accounts(); track a.id) {
              <mat-option [value]="a.id">{{ a.accountCode }} · {{ a.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Period</mat-label>
          <mat-select formControlName="periodId">
            @for (p of yearPeriods(); track p.id) {
              <mat-option [value]="p.id">{{ p.periodName }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Budget amount</mat-label>
          <input matInput type="number" step="0.01" formControlName="amount" />
          @if (entry.controls.amount.hasError('min')) {
            <mat-error>The budget cannot be negative</mat-error>
          }
        </mat-form-field>
        <button mat-flat-button color="primary" type="submit" [disabled]="entry.invalid">Save budget</button>
      </form>

      <h3 class="mat-h3">Import budgets from a CSV file</h3>
      <p class="note">
        First line: account_code,period,amount. Period is 1 to 12. One bad line refuses the whole file.
      </p>
      <div class="row">
        <input type="file" accept=".csv,text/csv" (change)="fileChosen($event)" data-testid="budget-file" />
        <button mat-stroked-button type="button" [disabled]="!file() || importing()" (click)="importFile()">
          Import
        </button>
      </div>
      @if (importMessage()) {
        <p role="status" data-testid="budget-import-result">{{ importMessage() }}</p>
      }
      @if (importError()) {
        <p class="error" role="alert">{{ importError() }}</p>
      }
    </ng-container>
  `,
  styles: [
    `
      .row {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        align-items: baseline;
      }
      .num {
        text-align: right;
      }
      .bad {
        color: var(--mat-sys-error, #b3261e);
        font-weight: 500;
      }
      .note {
        color: var(--mat-sys-on-surface-variant, #49454f);
      }
      .totals {
        font-weight: 500;
      }
      .error {
        color: var(--mat-sys-error, #b3261e);
      }
    `,
  ],
})
export class BudgetVsActualComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(FinanceApi);
  private readonly snack = inject(MatSnackBar);

  protected readonly columns = ['account', 'budgeted', 'actual', 'variance', 'pct', 'verdict'];
  protected readonly periods = signal<FiscalPeriodDto[]>([]);
  protected readonly accounts = signal<GlAccountDto[]>([]);
  protected readonly year = signal<number | null>(null);
  protected readonly periodId = signal<number | null>(null);
  protected readonly report = signal<BudgetVsActualDto | null>(null);
  protected readonly loadError = signal('');
  protected readonly file = signal<File | null>(null);
  protected readonly importing = signal(false);
  protected readonly importMessage = signal('');
  protected readonly importError = signal('');

  protected readonly years = computed(() =>
    Array.from(new Set(this.periods().map((p) => p.fiscalYear))).sort((a, b) => b - a),
  );
  protected readonly yearPeriods = computed(() =>
    this.periods()
      .filter((p) => p.fiscalYear === this.year())
      .sort((a, b) => a.periodNumber - b.periodNumber),
  );

  protected readonly entry = this.fb.group({
    glAccountId: this.fb.control<number | null>(null, Validators.required),
    periodId: this.fb.control<number | null>(null, Validators.required),
    amount: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
  });

  ngOnInit(): void {
    this.api.getGlAccounts().subscribe((a) => this.accounts.set(a));
    this.api.getPeriods().subscribe((p) => {
      this.periods.set(p);
      const open = p.find((x) => x.status === 'OPEN') ?? p[0];
      if (open) {
        this.year.set(open.fiscalYear);
        this.load();
      }
    });
  }

  protected yearChanged(year: number): void {
    this.year.set(year);
    this.periodId.set(null);
    this.load();
  }

  protected periodChanged(periodId: number | null): void {
    this.periodId.set(periodId);
    this.load();
  }

  protected saveEntry(): void {
    const v = this.entry.getRawValue();
    this.api
      .saveBudget({
        glAccountId: v.glAccountId as number,
        fiscalYear: this.year() as number,
        periodId: v.periodId as number,
        budgetedAmount: Number(v.amount),
        budgetName: null,
      })
      .subscribe({
        next: () => {
          this.snack.open('Budget saved', 'OK', { duration: 4000 });
          this.entry.controls.amount.reset(null);
          this.load();
        },
        error: (err) =>
          this.snack.open(problemMessage(err, 'The budget could not be saved.'), 'OK', { duration: 6000 }),
      });
  }

  protected fileChosen(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file.set(input.files && input.files.length > 0 ? input.files[0] : null);
    this.importMessage.set('');
    this.importError.set('');
  }

  protected importFile(): void {
    const file = this.file();
    const year = this.year();
    if (!file || !year) return;
    this.importing.set(true);
    this.importMessage.set('');
    this.importError.set('');
    this.api.importBudgetCsv(year, file).subscribe({
      next: (r) => {
        this.importing.set(false);
        this.importMessage.set(`Imported ${r.imported} lines: ${r.created} new, ${r.updated} replaced.`);
        this.load();
      },
      error: (err) => {
        this.importing.set(false);
        this.importError.set(problemMessage(err, 'The file could not be imported.'));
      },
    });
  }

  private load(): void {
    const year = this.year();
    if (!year) return;
    this.loadError.set('');
    this.api.getBudgetVsActual(year, this.periodId() ?? undefined).subscribe({
      next: (r) => this.report.set(r),
      error: (err) => this.loadError.set(problemMessage(err, 'The report could not be loaded.')),
    });
  }
}
