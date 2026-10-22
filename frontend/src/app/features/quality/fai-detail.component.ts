import { Component, OnInit, inject, signal } from '@angular/core';

import { FormControl, FormRecord, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { IstDatePipe } from '../../shared/pipes/ist-date.pipe';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { Fai, FaiReport, QualityPlusApi } from './quality-plus.api';

@Component({
  selector: 'app-fai-detail',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    IstDatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  template: `
    @if (fai(); as f) {
      <div class="page-header no-print">
        <div>
          <a routerLink="/quality/fai" class="back"><mat-icon>arrow_back</mat-icon> First article inspections</a>
          <h2 class="mat-h2">{{ f.faiNumber }} <app-status-badge [status]="f.status" /></h2>
        </div>
        @if (report()) {
          <button mat-stroked-button (click)="print()"><mat-icon>print</mat-icon> Print report</button>
        }
      </div>

      <dl class="facts">
        <dt>Product</dt>
        <dd>{{ f.productCode }} {{ f.productName }}</dd>
        <dt>Trigger</dt>
        <dd>{{ f.triggerType }} {{ f.triggerReference }}</dd>
        <dt>Inspection plan</dt>
        <dd>{{ f.planCode ?? '-' }}</dd>
        <dt>Production order</dt>
        <dd>{{ f.productionOrderNumber ?? '-' }}</dd>
        <dt>FG batch</dt>
        <dd>{{ f.fgBatchNumber ?? '-' }}</dd>
        <dt>Inspection</dt>
        <dd>{{ f.inspectionNumber ?? '-' }}</dd>
        @if (f.completedAt) {
          <dt>Completed</dt>
          <dd>{{ f.completedAt | istDate: 'dd MMM yyyy HH:mm' }}</dd>
          <dt>Outcome</dt>
          <dd>{{ f.resultSummary }}</dd>
        }
      </dl>

      @if (f.status === 'REQUIRED') {
        <section *appHasPermission="'QUALITY_INSPECT'" class="panel no-print">
          <h3 class="mat-h3">Start the first article inspection</h3>
          <p>Link the first production order for this product; the product's inspection plan is applied to it.</p>
          <div class="row">
            <mat-form-field appearance="outline">
              <mat-label>Production order ID</mat-label>
              <input matInput type="number" [formControl]="orderCtrl" min="1" />
            </mat-form-field>
            <button mat-flat-button color="primary" (click)="start()" [disabled]="orderCtrl.invalid || busy()">
              Start FAI
            </button>
          </div>
        </section>
      }

      @if (f.status === 'IN_PROGRESS') {
        <section *appHasPermission="'QUALITY_INSPECT'" class="panel no-print">
          <h3 class="mat-h3">Enter measured values</h3>
          <form [formGroup]="results" (ngSubmit)="saveResults()">
            @for (item of report()?.items ?? []; track item.itemId) {
              <mat-form-field appearance="outline" class="item">
                <mat-label
                  >{{ item.parameterName }} ({{ limits(item.lowerLimit, item.upperLimit, item.unit) }})</mat-label
                >
                <input matInput type="number" step="any" [formControlName]="'v' + item.itemId" />
              </mat-form-field>
            }
            <div class="row">
              <button mat-stroked-button type="submit" [disabled]="busy()">Save values</button>
            </div>
          </form>
        </section>
        <section *appHasPermission="'QUALITY_APPROVE'" class="panel no-print">
          <h3 class="mat-h3">Complete</h3>
          <mat-form-field appearance="outline" class="wide">
            <mat-label>Summary (optional)</mat-label>
            <input matInput [formControl]="summaryCtrl" maxlength="1000" />
          </mat-form-field>
          <button mat-flat-button color="primary" (click)="complete()" [disabled]="busy()">Complete FAI</button>
        </section>
      }

      @if (report(); as r) {
        <section class="panel report">
          <h3 class="mat-h3">First article report</h3>
          <table class="report-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Parameter</th>
                <th>Specification</th>
                <th>Limits</th>
                <th>Actual</th>
                <th>Result</th>
              </tr>
            </thead>
            <tbody>
              @for (i of r.items; track i.itemId) {
                <tr [class.fail]="i.result === 'FAIL'">
                  <td>{{ i.seq }}</td>
                  <td>{{ i.parameterName }}</td>
                  <td>{{ i.specification ?? '' }}</td>
                  <td>{{ limits(i.lowerLimit, i.upperLimit, i.unit) }}</td>
                  <td>{{ i.actualValue ?? i.actualText ?? '' }}</td>
                  <td>{{ i.result }}</td>
                </tr>
              }
            </tbody>
          </table>
          <p class="generated">Generated {{ r.generatedAt | istDate: 'dd MMM yyyy HH:mm' }}</p>
        </section>
      }
    } @else {
      <div class="loading-center"><mat-spinner diameter="40" /></div>
    }
  `,
  styles: `
    .page-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
    }
    .back {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      text-decoration: none;
    }
    .facts {
      display: grid;
      grid-template-columns: max-content 1fr;
      gap: 4px 16px;
      max-width: 700px;
    }
    .facts dt {
      font-weight: 500;
    }
    .facts dd {
      margin: 0;
    }
    .panel {
      margin: 20px 0;
      max-width: 900px;
    }
    .row {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
    }
    .item {
      display: block;
      max-width: 420px;
    }
    .wide {
      min-width: 320px;
    }
    .report-table {
      border-collapse: collapse;
      width: 100%;
    }
    .report-table th,
    .report-table td {
      border: 1px solid #bbb;
      padding: 4px 8px;
      text-align: left;
    }
    .report-table tr.fail td {
      color: #c62828;
      font-weight: 600;
    }
    .generated {
      opacity: 0.7;
      font-size: 12px;
    }
    @media print {
      .no-print {
        display: none !important;
      }
    }
  `,
})
export class FaiDetailComponent implements OnInit {
  private api = inject(QualityPlusApi);
  private route = inject(ActivatedRoute);
  private snack = inject(MatSnackBar);

  fai = signal<Fai | null>(null);
  report = signal<FaiReport | null>(null);
  busy = signal(false);

  orderCtrl = new FormControl<number | null>(null, [Validators.required, Validators.min(1)]);
  summaryCtrl = new FormControl('', { nonNullable: true });
  results = new FormRecord<FormControl<number | null>>({});

  ngOnInit(): void {
    this.load();
  }

  private id(): number {
    return Number(this.route.snapshot.paramMap.get('id'));
  }

  limits(lower: number | null, upper: number | null, unit: string | null): string {
    const range =
      lower !== null && upper !== null
        ? `${lower} - ${upper}`
        : lower !== null
          ? `>= ${lower}`
          : upper !== null
            ? `<= ${upper}`
            : '';
    return `${range} ${unit ?? ''}`.trim();
  }

  load(): void {
    this.api.getFai(this.id()).subscribe({
      next: (f) => {
        this.fai.set(f);
        if (f.inspectionId) {
          this.loadReport();
        }
      },
      error: (err) => this.fail(err, 'Failed to load the first article inspection'),
    });
  }

  private loadReport(): void {
    this.api.faiReport(this.id()).subscribe({
      next: (r) => this.showReport(r),
      error: (err) => this.fail(err, 'Failed to load the report'),
    });
  }

  private showReport(r: FaiReport): void {
    this.report.set(r);
    this.fai.set(r.fai);
    for (const key of Object.keys(this.results.controls)) {
      this.results.removeControl(key);
    }
    for (const item of r.items) {
      this.results.addControl('v' + item.itemId, new FormControl<number | null>(item.actualValue));
    }
  }

  start(): void {
    const orderId = this.orderCtrl.value;
    if (!orderId) return;
    this.busy.set(true);
    this.api.startFai(this.id(), orderId).subscribe({
      next: () => {
        this.busy.set(false);
        this.load();
      },
      error: (err) => this.fail(err, 'Failed to start the first article inspection'),
    });
  }

  saveResults(): void {
    const values: { itemId: number; actualValue: number }[] = [];
    for (const item of this.report()?.items ?? []) {
      const v = this.results.controls['v' + item.itemId]?.value;
      if (v !== null && v !== undefined) values.push({ itemId: item.itemId, actualValue: v });
    }
    if (values.length === 0) {
      this.snack.open('Enter at least one measured value', 'Dismiss', { duration: 3000 });
      return;
    }
    this.busy.set(true);
    this.api.submitFaiResults(this.id(), values).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.showReport(r);
        this.snack.open('Values saved', 'Dismiss', { duration: 3000 });
      },
      error: (err) => this.fail(err, 'Failed to save the values'),
    });
  }

  complete(): void {
    this.busy.set(true);
    this.api.completeFai(this.id(), this.summaryCtrl.value.trim() || null).subscribe({
      next: () => {
        this.busy.set(false);
        this.load();
      },
      error: (err) => this.fail(err, 'Failed to complete the first article inspection'),
    });
  }

  print(): void {
    window.print();
  }

  private fail(err: { error?: { detail?: string } }, fallback: string): void {
    this.busy.set(false);
    this.snack.open(err?.error?.detail ?? fallback, 'Dismiss', { duration: 5000 });
  }
}
