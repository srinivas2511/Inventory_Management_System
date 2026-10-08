import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { problemMessage } from '../../core/models/problem.model';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ExchangeRateDto, FinanceApi, FiscalPeriodDto } from './finance.api';

/**
 * Exchange rates into INR by fiscal period (FIN-009). A foreign invoice is booked at the rate of the period of its
 * date, and a payment at the rate of the period it is paid in; the difference is the realised gain or loss.
 */
@Component({
  selector: 'app-exchange-rates',
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
      <h2 class="mat-h2">Exchange rates</h2>
    </div>
    <p class="note">One unit of the foreign currency in rupees, for each fiscal period.</p>

    <ng-container *appHasPermission="'EXCHANGE_RATE_MANAGE'">
      <form [formGroup]="form" (ngSubmit)="save()" class="row" novalidate>
        <mat-form-field appearance="outline">
          <mat-label>Currency</mat-label>
          <input matInput formControlName="fromCurrency" maxlength="3" placeholder="USD" />
          @if (form.controls.fromCurrency.hasError('pattern') && form.controls.fromCurrency.touched) {
            <mat-error>Use a three-letter code such as USD</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Period</mat-label>
          <mat-select formControlName="periodId">
            @for (p of periods(); track p.id) {
              <mat-option [value]="p.id">{{ p.periodName }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Rate in INR</mat-label>
          <input matInput type="number" step="0.0001" formControlName="rate" />
          @if (form.controls.rate.hasError('min') && form.controls.rate.touched) {
            <mat-error>The rate must be greater than zero</mat-error>
          }
        </mat-form-field>
        <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || saving()">Save rate</button>
      </form>
    </ng-container>
    @if (errorMessage()) {
      <p class="error" role="alert">{{ errorMessage() }}</p>
    }

    <mat-form-field appearance="outline" class="filter">
      <mat-label>Show period</mat-label>
      <mat-select [value]="filterPeriod()" (selectionChange)="filterChanged($event.value)">
        <mat-option [value]="null">All periods</mat-option>
        @for (p of periods(); track p.id) {
          <mat-option [value]="p.id">{{ p.periodName }}</mat-option>
        }
      </mat-select>
    </mat-form-field>
    <table mat-table [dataSource]="rates()" class="mat-elevation-z1" data-testid="rate-table">
      <ng-container matColumnDef="currency">
        <th mat-header-cell *matHeaderCellDef>Currency</th>
        <td mat-cell *matCellDef="let r">{{ r.fromCurrency }} to {{ r.toCurrency }}</td>
      </ng-container>
      <ng-container matColumnDef="period">
        <th mat-header-cell *matHeaderCellDef>Period</th>
        <td mat-cell *matCellDef="let r">{{ r.periodName }}</td>
      </ng-container>
      <ng-container matColumnDef="rate">
        <th mat-header-cell *matHeaderCellDef>Rate</th>
        <td mat-cell *matCellDef="let r">{{ r.rate | number: '1.2-6' }}</td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns"></tr>
    </table>
    @if (rates().length === 0) {
      <p class="note">No rates yet. Add one above to book invoices in a foreign currency.</p>
    }
  `,
  styles: [
    `
      .note {
        color: var(--mat-sys-on-surface-variant, #49454f);
      }
      .row {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        align-items: baseline;
      }
      .filter {
        margin-top: 12px;
      }
      .error {
        color: var(--mat-sys-error, #b3261e);
      }
    `,
  ],
})
export class ExchangeRatesComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(FinanceApi);
  private readonly snack = inject(MatSnackBar);

  protected readonly columns = ['currency', 'period', 'rate'];
  protected readonly rates = signal<ExchangeRateDto[]>([]);
  protected readonly periods = signal<FiscalPeriodDto[]>([]);
  protected readonly filterPeriod = signal<number | null>(null);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal('');

  protected readonly form = this.fb.group({
    fromCurrency: this.fb.nonNullable.control('', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]),
    periodId: this.fb.control<number | null>(null, Validators.required),
    rate: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.000001)]),
  });

  ngOnInit(): void {
    this.api.getPeriods().subscribe((p) => this.periods.set(p));
    this.load();
  }

  protected filterChanged(periodId: number | null): void {
    this.filterPeriod.set(periodId);
    this.load();
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set('');
    this.api
      .saveExchangeRate({
        fromCurrency: v.fromCurrency.toUpperCase(),
        periodId: v.periodId as number,
        rate: Number(v.rate),
      })
      .subscribe({
        next: (saved) => {
          this.saving.set(false);
          this.snack.open(`${saved.fromCurrency} saved for ${saved.periodName}`, 'OK', { duration: 4000 });
          this.form.controls.rate.reset(null);
          this.load();
        },
        error: (err) => {
          this.saving.set(false);
          this.errorMessage.set(problemMessage(err, 'The rate could not be saved.'));
        },
      });
  }

  private load(): void {
    this.api.getExchangeRates(this.filterPeriod() ?? undefined).subscribe((r) => this.rates.set(r));
  }
}
