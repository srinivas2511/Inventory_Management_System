import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { barCentres, linePath, paretoBars, percent, seriesPoints } from './chart-math';
import { DOWNTIME_CATEGORIES, DowntimeReasonDto, OeeApi, OeeDto, OeeLossesDto, OeeTrendPointDto } from './oee.api';

const TREND_W = 640;
const TREND_H = 200;
const PARETO_W = 520;
const PARETO_H = 200;

function isoDate(d: Date): string {
  const m = `${d.getMonth() + 1}`.padStart(2, '0');
  const day = `${d.getDate()}`.padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

@Component({
  selector: 'app-oee-dashboard',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DecimalPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    HasPermissionDirective,
  ],
  template: `
    <div class="page-header">
      <h2 class="mat-h2">Overall Equipment Effectiveness</h2>
    </div>

    <div class="filter-bar">
      <mat-form-field appearance="outline">
        <mat-label>From</mat-label>
        <input matInput type="date" [formControl]="fromCtrl" data-test="from" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>To</mat-label>
        <input matInput type="date" [formControl]="toCtrl" data-test="to" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Machine</mat-label>
        <mat-select [formControl]="machineCtrl" data-test="machine-filter">
          <mat-option [value]="null">All machines</mat-option>
          @for (m of machines(); track m.machineId) {
            <mat-option [value]="m.machineId">{{ m.machineCode }} {{ m.machineName }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <button mat-flat-button color="primary" (click)="load()" data-test="refresh">
        <mat-icon>refresh</mat-icon> Refresh
      </button>
    </div>

    @if (summary(); as s) {
      <div class="cards" data-test="cards">
        <div class="card oee">
          <span class="label">OEE</span>
          <span class="value" data-test="oee">{{ pct(s.oee) }}</span>
        </div>
        <div class="card">
          <span class="label">Availability</span>
          <span class="value" data-test="availability">{{ pct(s.availability) }}</span>
        </div>
        <div class="card">
          <span class="label">Performance</span>
          <span class="value" data-test="performance">{{ pct(s.performance) }}</span>
        </div>
        <div class="card">
          <span class="label">Quality</span>
          <span class="value" data-test="quality">{{ pct(s.quality) }}</span>
        </div>
      </div>
    }

    <h3 class="mat-h3">By machine</h3>
    <table mat-table [dataSource]="machines()" class="mat-elevation-z1" data-test="machines">
      <ng-container matColumnDef="machine">
        <th mat-header-cell *matHeaderCellDef>Machine</th>
        <td mat-cell *matCellDef="let m">{{ m.machineCode }} {{ m.machineName }}</td>
      </ng-container>
      <ng-container matColumnDef="availability">
        <th mat-header-cell *matHeaderCellDef>Availability</th>
        <td mat-cell *matCellDef="let m">{{ pct(m.availability) }}</td>
      </ng-container>
      <ng-container matColumnDef="performance">
        <th mat-header-cell *matHeaderCellDef>Performance</th>
        <td mat-cell *matCellDef="let m">{{ pct(m.performance) }}</td>
      </ng-container>
      <ng-container matColumnDef="quality">
        <th mat-header-cell *matHeaderCellDef>Quality</th>
        <td mat-cell *matCellDef="let m">{{ pct(m.quality) }}</td>
      </ng-container>
      <ng-container matColumnDef="oee">
        <th mat-header-cell *matHeaderCellDef>OEE</th>
        <td mat-cell *matCellDef="let m">
          <span class="bar-track"><span class="bar-fill" [style.width.%]="m.oee * 100"></span></span>
          <strong>{{ pct(m.oee) }}</strong>
        </td>
      </ng-container>
      <ng-container matColumnDef="downtime">
        <th mat-header-cell *matHeaderCellDef>Downtime (min)</th>
        <td mat-cell *matCellDef="let m">
          {{ m.unplannedDowntimeMinutes | number: '1.0-0' }} unplanned,
          {{ m.plannedDowntimeMinutes | number: '1.0-0' }} planned
        </td>
      </ng-container>
      <ng-container matColumnDef="output">
        <th mat-header-cell *matHeaderCellDef>Good / bad</th>
        <td mat-cell *matCellDef="let m">{{ m.goodQty | number: '1.0-0' }} / {{ m.badQty | number: '1.0-0' }}</td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let row; columns: columns" (click)="machineCtrl.setValue(row.machineId); load()"></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell no-data" [attr.colspan]="columns.length">No machines</td>
      </tr>
    </table>

    <div class="charts">
      <section>
        <h3 class="mat-h3">OEE trend</h3>
        @if (trendLines().oee) {
          <svg
            [attr.viewBox]="'0 0 ' + trendW + ' ' + (trendH + 20)"
            class="chart"
            role="img"
            aria-label="OEE trend by day"
            data-test="trend"
          >
            <line x1="0" [attr.y1]="trendH" [attr.x2]="trendW" [attr.y2]="trendH" class="axis" />
            <line x1="0" [attr.y1]="trendH * 0.15" [attr.x2]="trendW" [attr.y2]="trendH * 0.15" class="guide" />
            <path [attr.d]="trendLines().availability" class="line availability" />
            <path [attr.d]="trendLines().performance" class="line performance" />
            <path [attr.d]="trendLines().quality" class="line quality" />
            <path [attr.d]="trendLines().oee" class="line oee" data-test="trend-oee" />
            <text x="0" [attr.y]="trendH + 14" class="tick">{{ trendFirst() }}</text>
            <text [attr.x]="trendW" [attr.y]="trendH + 14" text-anchor="end" class="tick">{{ trendLast() }}</text>
          </svg>
          <div class="legend">
            <span class="swatch oee"></span> OEE <span class="swatch availability"></span> Availability
            <span class="swatch performance"></span> Performance <span class="swatch quality"></span> Quality
          </div>
        } @else {
          <p class="no-data">No days in the range</p>
        }
      </section>

      <section>
        <h3 class="mat-h3">Losses (Pareto)</h3>
        @if (losses(); as l) {
          @if (l.losses.length > 0) {
            <svg
              [attr.viewBox]="'0 0 ' + paretoW + ' ' + (paretoH + 10)"
              class="chart"
              role="img"
              aria-label="Loss minutes by category with cumulative share"
              data-test="pareto"
            >
              <line x1="0" [attr.y1]="paretoH" [attr.x2]="paretoW" [attr.y2]="paretoH" class="axis" />
              @for (b of pareto().bars; track $index) {
                <rect [attr.x]="b.x" [attr.y]="b.y" [attr.width]="b.width" [attr.height]="b.height" class="pbar" />
              }
              <path [attr.d]="pareto().cumulative" class="line cumulative" />
            </svg>
            <table class="loss-table" data-test="loss-table">
              <tr>
                <th>Loss</th>
                <th>Minutes</th>
                <th>Share</th>
                <th>Cumulative</th>
              </tr>
              @for (x of l.losses; track x.category) {
                <tr>
                  <td>{{ x.label }}</td>
                  <td>{{ x.minutes | number: '1.0-0' }}</td>
                  <td>{{ pct(x.share) }}</td>
                  <td>{{ pct(x.cumulativeShare) }}</td>
                </tr>
              }
            </table>
          } @else {
            <p class="no-data">No losses recorded in the range</p>
          }
        }
      </section>
    </div>

    <h3 class="mat-h3">Top downtime reasons</h3>
    <table mat-table [dataSource]="reasons()" class="mat-elevation-z1" data-test="reasons">
      <ng-container matColumnDef="reason">
        <th mat-header-cell *matHeaderCellDef>Reason</th>
        <td mat-cell *matCellDef="let r">{{ r.reason }}</td>
      </ng-container>
      <ng-container matColumnDef="category">
        <th mat-header-cell *matHeaderCellDef>Category</th>
        <td mat-cell *matCellDef="let r">{{ categoryLabel(r.category) }}</td>
      </ng-container>
      <ng-container matColumnDef="occurrences">
        <th mat-header-cell *matHeaderCellDef>Times</th>
        <td mat-cell *matCellDef="let r">{{ r.occurrences }}</td>
      </ng-container>
      <ng-container matColumnDef="minutes">
        <th mat-header-cell *matHeaderCellDef>Minutes</th>
        <td mat-cell *matCellDef="let r">{{ r.minutes | number: '1.0-0' }}</td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="reasonColumns"></tr>
      <tr mat-row *matRowDef="let row; columns: reasonColumns"></tr>
      <tr class="mat-row" *matNoDataRow>
        <td class="mat-cell no-data" [attr.colspan]="reasonColumns.length">No downtime in the range</td>
      </tr>
    </table>

    <form *appHasPermission="'DOWNTIME_RECORD'" class="edit-form" (ngSubmit)="record()" data-test="downtime-form">
      <h3 class="mat-h3 full">Record downtime</h3>
      <mat-form-field appearance="outline">
        <mat-label>Machine</mat-label>
        <mat-select [formControl]="dtMachine" data-test="dt-machine">
          @for (m of machines(); track m.machineId) {
            <mat-option [value]="m.machineId">{{ m.machineCode }} {{ m.machineName }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Category</mat-label>
        <mat-select [formControl]="dtCategory" data-test="dt-category">
          @for (c of categories; track c.value) {
            <mat-option [value]="c.value">{{ c.label }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" class="wide">
        <mat-label>Reason</mat-label>
        <input matInput maxlength="100" [formControl]="dtReason" data-test="dt-reason" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Started</mat-label>
        <input matInput type="datetime-local" [formControl]="dtStart" data-test="dt-start" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Ended (leave empty if still down)</mat-label>
        <input matInput type="datetime-local" [formControl]="dtEnd" data-test="dt-end" />
      </mat-form-field>
      <button mat-flat-button color="primary" type="submit" [disabled]="!canRecord()" data-test="dt-save">
        <mat-icon>add</mat-icon> Record
      </button>
    </form>
  `,
  styles: `
    .filter-bar,
    .edit-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
    }
    .full {
      flex-basis: 100%;
      margin: 8px 0 0;
    }
    .wide {
      min-width: 240px;
    }
    .cards {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      padding: 8px 0 16px;
    }
    .card {
      min-width: 150px;
      padding: 12px 16px;
      border-radius: 8px;
      background: rgba(63, 81, 181, 0.08);
      display: flex;
      flex-direction: column;
    }
    .card.oee {
      background: rgba(63, 81, 181, 0.18);
    }
    .label {
      font-size: 0.8rem;
      opacity: 0.7;
    }
    .value {
      font-size: 1.8rem;
      font-weight: 600;
    }
    .bar-track {
      display: inline-block;
      width: 80px;
      height: 8px;
      margin-right: 8px;
      border-radius: 4px;
      background: rgba(0, 0, 0, 0.1);
      vertical-align: middle;
    }
    .bar-fill {
      display: block;
      height: 100%;
      border-radius: 4px;
      background: #3f51b5;
    }
    .charts {
      display: flex;
      flex-wrap: wrap;
      gap: 24px;
      padding-top: 8px;
    }
    .charts section {
      flex: 1 1 420px;
      min-width: 0;
    }
    .chart {
      width: 100%;
      height: auto;
      max-height: 260px;
    }
    .axis {
      stroke: rgba(0, 0, 0, 0.35);
    }
    .guide {
      stroke: rgba(0, 0, 0, 0.15);
      stroke-dasharray: 4 4;
    }
    .line {
      fill: none;
      stroke-width: 1.5;
    }
    .line.oee {
      stroke: #3f51b5;
      stroke-width: 2.5;
    }
    .line.availability {
      stroke: #43a047;
    }
    .line.performance {
      stroke: #fb8c00;
    }
    .line.quality {
      stroke: #8e24aa;
    }
    .line.cumulative {
      stroke: #e53935;
      stroke-width: 2;
    }
    .pbar {
      fill: #3f51b5;
      opacity: 0.8;
    }
    .tick {
      font-size: 11px;
      fill: rgba(0, 0, 0, 0.6);
    }
    .legend {
      font-size: 0.8rem;
      opacity: 0.8;
    }
    .swatch {
      display: inline-block;
      width: 12px;
      height: 3px;
      margin: 0 4px 3px 12px;
    }
    .swatch.oee {
      background: #3f51b5;
    }
    .swatch.availability {
      background: #43a047;
    }
    .swatch.performance {
      background: #fb8c00;
    }
    .swatch.quality {
      background: #8e24aa;
    }
    .loss-table {
      margin-top: 8px;
      border-collapse: collapse;
      font-size: 0.85rem;
    }
    .loss-table th,
    .loss-table td {
      padding: 2px 12px 2px 0;
      text-align: left;
    }
    .no-data {
      text-align: center;
      padding: 16px;
      color: rgba(0, 0, 0, 0.54);
    }
  `,
})
export class OeeDashboardComponent implements OnInit {
  private api = inject(OeeApi);
  private snack = inject(MatSnackBar);

  readonly trendW = TREND_W;
  readonly trendH = TREND_H;
  readonly paretoW = PARETO_W;
  readonly paretoH = PARETO_H;
  readonly categories = DOWNTIME_CATEGORIES;
  columns = ['machine', 'availability', 'performance', 'quality', 'oee', 'downtime', 'output'];
  reasonColumns = ['reason', 'category', 'occurrences', 'minutes'];

  summary = signal<OeeDto | null>(null);
  machines = signal<OeeDto[]>([]);
  trend = signal<OeeTrendPointDto[]>([]);
  losses = signal<OeeLossesDto | null>(null);
  reasons = signal<DowntimeReasonDto[]>([]);

  fromCtrl = new FormControl(isoDate(new Date(Date.now() - 29 * 86400000)), { nonNullable: true });
  toCtrl = new FormControl(isoDate(new Date()), { nonNullable: true });
  machineCtrl = new FormControl<number | null>(null);

  dtMachine = new FormControl<number | null>(null);
  dtCategory = new FormControl<string | null>('BREAKDOWN');
  dtReason = new FormControl('', { nonNullable: true });
  dtStart = new FormControl('', { nonNullable: true });
  dtEnd = new FormControl('', { nonNullable: true });

  readonly trendLines = computed(() => {
    const points = this.trend();
    const line = (pick: (p: OeeTrendPointDto) => number) =>
      linePath(
        seriesPoints(
          points.map((p) => pick(p)),
          TREND_W,
          TREND_H,
        ),
      );
    return {
      oee: points.length ? line((p) => p.oee) : '',
      availability: line((p) => p.availability),
      performance: line((p) => p.performance),
      quality: line((p) => p.quality),
    };
  });
  readonly trendFirst = computed(() => this.trend()[0]?.day ?? '');
  readonly trendLast = computed(() => this.trend()[this.trend().length - 1]?.day ?? '');

  readonly pareto = computed(() => {
    const items = this.losses()?.losses ?? [];
    const bars = paretoBars(
      items.map((i) => i.minutes),
      PARETO_W,
      PARETO_H,
    );
    const centres = barCentres(bars);
    const cumulative = linePath(
      items.map((i, idx) => ({
        x: centres[idx],
        y: PARETO_H - Math.min(Math.max(i.cumulativeShare, 0), 1) * PARETO_H,
      })),
    );
    return { bars, cumulative };
  });

  pct = percent;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const from = this.fromCtrl.value;
    const to = this.toCtrl.value;
    const machine = this.machineCtrl.value;
    const fail = () => this.snack.open('Could not load OEE', 'Dismiss', { duration: 4000 });
    this.api.summary(from, to).subscribe({ next: (s) => this.summary.set(s), error: fail });
    this.api.machines(from, to).subscribe({ next: (m) => this.machines.set(m), error: fail });
    this.api.trend(from, to, machine).subscribe({ next: (t) => this.trend.set(t), error: fail });
    this.api.losses(from, to, machine).subscribe({ next: (l) => this.losses.set(l), error: fail });
    this.api.downtimeReasons(from, to, machine, 5).subscribe({ next: (r) => this.reasons.set(r), error: fail });
  }

  categoryLabel(value: string): string {
    return DOWNTIME_CATEGORIES.find((c) => c.value === value)?.label ?? value;
  }

  canRecord(): boolean {
    return !!this.dtMachine.value && this.dtReason.value.trim().length > 0 && !!this.dtStart.value;
  }

  record(): void {
    if (!this.canRecord()) return;
    this.api
      .recordDowntime({
        machineId: this.dtMachine.value as number,
        reason: this.dtReason.value.trim(),
        category: this.dtCategory.value,
        startedAt: new Date(this.dtStart.value).toISOString(),
        endedAt: this.dtEnd.value ? new Date(this.dtEnd.value).toISOString() : null,
      })
      .subscribe({
        next: () => {
          this.dtReason.reset('');
          this.dtStart.reset('');
          this.dtEnd.reset('');
          this.snack.open('Downtime recorded', 'Dismiss', { duration: 3000 });
          this.load();
        },
        error: (e) =>
          this.snack.open(e?.error?.detail ?? 'Could not record the downtime', 'Dismiss', { duration: 5000 }),
      });
  }
}
