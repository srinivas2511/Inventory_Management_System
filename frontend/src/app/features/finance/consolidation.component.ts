import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

interface ConsolidationLine {
  accountCode: string;
  accountName: string;
  accountGroup: string;
  amount: number;
}

interface ConsolidationReport {
  from: string;
  to: string;
  lines: ConsolidationLine[];
  eliminations: unknown[];
  totalRevenue: number;
  totalExpense: number;
  netIncome: number;
}

@Component({
  selector: 'app-consolidation',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatDatepickerModule,
    MatNativeDateModule,
  ],
  template: `
    <mat-card>
      <mat-card-header>
        <mat-card-title i18n="@@consolidation.title">Consolidation Report</mat-card-title>
        <mat-card-subtitle i18n="@@consolidation.subtitle">Multi-company consolidated financials</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="load()" class="filter-row">
          <mat-form-field appearance="outline">
            <mat-label i18n="@@consolidation.from">From</mat-label>
            <input matInput [matDatepicker]="dp1" formControlName="from" />
            <mat-datepicker-toggle matIconSuffix [for]="dp1"></mat-datepicker-toggle>
            <mat-datepicker #dp1></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label i18n="@@consolidation.to">To</mat-label>
            <input matInput [matDatepicker]="dp2" formControlName="to" />
            <mat-datepicker-toggle matIconSuffix [for]="dp2"></mat-datepicker-toggle>
            <mat-datepicker #dp2></mat-datepicker>
          </mat-form-field>
          <button mat-raised-button color="primary" type="submit" [disabled]="form.invalid">
            <mat-icon aria-hidden="true">summarize</mat-icon>
            <span i18n="@@consolidation.run">Run</span>
          </button>
        </form>

        @if (loading) {
          <div class="center"><mat-spinner diameter="40"></mat-spinner></div>
        } @else if (report) {
          <div class="summary-row">
            <div class="stat">
              <span class="label" i18n="@@consolidation.revenue">Revenue</span>
              <span class="value">₹{{ report.totalRevenue | number: '1.2-2' }}</span>
            </div>
            <div class="stat">
              <span class="label" i18n="@@consolidation.expense">Expense</span>
              <span class="value">₹{{ report.totalExpense | number: '1.2-2' }}</span>
            </div>
            <div class="stat">
              <span class="label" i18n="@@consolidation.netIncome">Net Income</span>
              <span class="value" [class.positive]="report.netIncome >= 0" [class.negative]="report.netIncome < 0">
                ₹{{ report.netIncome | number: '1.2-2' }}
              </span>
            </div>
          </div>

          <table mat-table [dataSource]="report.lines" class="full-width">
            <ng-container matColumnDef="code">
              <th mat-header-cell *matHeaderCellDef i18n="@@consolidation.col.code">Code</th>
              <td mat-cell *matCellDef="let l">{{ l.accountCode }}</td>
            </ng-container>
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef i18n="@@consolidation.col.name">Account</th>
              <td mat-cell *matCellDef="let l">{{ l.accountName }}</td>
            </ng-container>
            <ng-container matColumnDef="group">
              <th mat-header-cell *matHeaderCellDef i18n="@@consolidation.col.group">Group</th>
              <td mat-cell *matCellDef="let l">{{ l.accountGroup }}</td>
            </ng-container>
            <ng-container matColumnDef="amount">
              <th mat-header-cell *matHeaderCellDef i18n="@@consolidation.col.amount">Amount (₹)</th>
              <td mat-cell *matCellDef="let l" class="amount-cell">{{ l.amount | number: '1.2-2' }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [
    `
      .filter-row {
        display: flex;
        gap: 1rem;
        align-items: center;
        flex-wrap: wrap;
        margin-bottom: 1rem;
      }
      .summary-row {
        display: flex;
        gap: 2rem;
        margin-bottom: 1.5rem;
        flex-wrap: wrap;
      }
      .stat {
        display: flex;
        flex-direction: column;
      }
      .label {
        font-size: 0.75rem;
        color: var(--mat-sys-on-surface-variant);
      }
      .value {
        font-size: 1.1rem;
        font-weight: 600;
      }
      .positive {
        color: #2e7d32;
      }
      .negative {
        color: #c62828;
      }
      .center {
        display: flex;
        justify-content: center;
        padding: 2rem;
      }
      .full-width {
        width: 100%;
      }
      .amount-cell {
        text-align: right;
        font-variant-numeric: tabular-nums;
      }
    `,
  ],
})
export class ConsolidationComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);

  report: ConsolidationReport | null = null;
  loading = false;
  readonly columns = ['code', 'name', 'group', 'amount'];

  readonly form = this.fb.group({
    from: [this.defaultFrom(), Validators.required],
    to: [this.defaultTo(), Validators.required],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    if (this.form.invalid) return;
    const from = this.fmt(this.form.value.from as Date);
    const to = this.fmt(this.form.value.to as Date);
    this.loading = true;
    this.http.get<ConsolidationReport>(`/api/finance/consolidation/report?from=${from}&to=${to}`).subscribe({
      next: (r) => {
        this.report = r;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  private defaultFrom(): Date {
    const d = new Date();
    d.setMonth(3, 1);
    d.setFullYear(d.getFullYear() - (d.getMonth() < 3 ? 1 : 0));
    return d;
  }
  private defaultTo(): Date {
    const d = new Date();
    return d;
  }
  private fmt(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}
