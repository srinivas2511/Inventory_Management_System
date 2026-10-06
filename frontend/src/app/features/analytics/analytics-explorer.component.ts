import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { SessionStore } from '../../core/auth/session.store';
import { saveBlob } from '../documents/documents.api';
import { AnalyticsChartComponent } from './analytics-chart.component';
import { AnalyticsApi, AnalyticsColumn, QueryDescriptor, QueryResult, toCsv } from './analytics.api';

/**
 * Ad-hoc analytics (ANA-001): pick a query from the catalogue, set the period, choose the columns, the chart and its
 * axes, and download what is on screen. Drag-and-drop layout is not built; this covers the same ground with controls.
 */
@Component({
  selector: 'app-analytics-explorer',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    DecimalPipe,
    MatButtonModule,
    MatButtonToggleModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    AnalyticsChartComponent,
  ],
  template: `
    <h2 class="mat-h2">Analytics explorer</h2>
    <div class="bar">
      <mat-form-field appearance="outline" class="wide">
        <mat-label>Query</mat-label>
        <mat-select [formControl]="queryCtrl" (selectionChange)="onQueryChange()" data-testid="query">
          @for (q of catalogue(); track q.key) {
            <mat-option [value]="q.key">{{ q.category }}: {{ q.title }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>From</mat-label>
        <input matInput type="date" [formControl]="from" data-testid="from" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>To</mat-label>
        <input matInput type="date" [formControl]="to" data-testid="to" />
      </mat-form-field>
      <button
        mat-flat-button
        color="primary"
        type="button"
        [disabled]="!queryCtrl.value || loading()"
        (click)="run()"
        data-testid="run"
      >
        Run
      </button>
      @if (canManage) {
        <button mat-stroked-button type="button" (click)="refreshViews()" data-testid="refresh">
          <mat-icon>refresh</mat-icon> Refresh data
        </button>
      }
    </div>

    @if (selected(); as q) {
      <p class="hint">{{ q.description }}</p>
    }

    @if (result(); as r) {
      <div class="builder">
        <mat-button-toggle-group
          [value]="chartType()"
          (change)="chartType.set($event.value)"
          aria-label="Display as"
          data-testid="chart-type"
        >
          <mat-button-toggle value="TABLE">Table</mat-button-toggle>
          <mat-button-toggle value="BAR">Bar</mat-button-toggle>
          <mat-button-toggle value="LINE">Line</mat-button-toggle>
        </mat-button-toggle-group>
        @if (chartType() !== 'TABLE') {
          <mat-form-field appearance="outline">
            <mat-label>X axis</mat-label>
            <mat-select [value]="xColumn()" (selectionChange)="xColumn.set($event.value)" data-testid="x">
              @for (c of r.columns; track c.name) {
                <mat-option [value]="c.name">{{ c.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Values</mat-label>
            <mat-select multiple [value]="yColumns()" (selectionChange)="yColumns.set($event.value)" data-testid="y">
              @for (c of numeric(); track c.name) {
                <mat-option [value]="c.name">{{ c.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }
        <button mat-button type="button" (click)="download()" data-testid="download">
          <mat-icon>download</mat-icon> CSV
        </button>
      </div>

      @if (chartType() !== 'TABLE') {
        <app-analytics-chart
          [rows]="r.rows"
          [xColumn]="xColumn()"
          [yColumns]="yColumns()"
          [type]="chartType() === 'LINE' ? 'LINE' : 'BAR'"
        />
      }

      <div class="cols" role="group" aria-label="Columns shown">
        @for (c of r.columns; track c.name) {
          <mat-checkbox [checked]="shown().has(c.name)" (change)="toggleColumn(c.name, $event.checked)">{{
            c.label
          }}</mat-checkbox>
        }
      </div>

      <table mat-table [dataSource]="r.rows" class="mat-elevation-z1" data-testid="table">
        @for (c of visible(); track c.name) {
          <ng-container [matColumnDef]="c.name">
            <th mat-header-cell *matHeaderCellDef>{{ c.label }}</th>
            <td mat-cell *matCellDef="let row" [class.num]="c.type !== 'TEXT' && c.type !== 'DATE'">
              @switch (c.type) {
                @case ('MONEY') {
                  {{ asNumber(row[c.name]) | number: '1.2-2' }}
                }
                @case ('PERCENT') {
                  {{ row[c.name] === null ? '' : (asNumber(row[c.name]) | number: '1.1-2') + ' %' }}
                }
                @case ('NUMBER') {
                  {{ row[c.name] === null ? '' : (asNumber(row[c.name]) | number: '1.0-3') }}
                }
                @default {
                  {{ row[c.name] }}
                }
              }
            </td>
          </ng-container>
        }
        <tr mat-header-row *matHeaderRowDef="visibleNames()"></tr>
        <tr mat-row *matRowDef="let row; columns: visibleNames()" data-testid="row"></tr>
        <tr class="mat-row" *matNoDataRow>
          <td class="mat-cell" [attr.colspan]="visibleNames().length || 1" style="text-align:center;padding:2rem">
            No data for this period
          </td>
        </tr>
      </table>
      <p class="hint">
        {{ r.rowCount }} rows from {{ r.from }} to {{ r.to }}
        @if (r.truncated) {
          (limited to the first {{ r.rowCount }}; narrow the period)
        }
        · generated {{ r.generatedAt | date: 'dd MMM HH:mm' }}
      </p>
    }
  `,
  styles: `
    .bar,
    .builder,
    .cols {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px 16px;
      margin-bottom: 8px;
    }
    .wide {
      min-width: 320px;
    }
    .hint {
      color: var(--mat-sys-on-surface-variant, #666);
    }
    .num {
      text-align: right;
    }
  `,
})
export class AnalyticsExplorerComponent implements OnInit {
  private readonly api = inject(AnalyticsApi);
  private readonly snack = inject(MatSnackBar);
  private readonly session = inject(SessionStore);

  readonly canManage = this.session.hasPermission('ANALYTICS_MANAGE');
  readonly catalogue = signal<QueryDescriptor[]>([]);
  readonly result = signal<QueryResult | null>(null);
  readonly loading = signal(false);
  readonly chartType = signal<'TABLE' | 'BAR' | 'LINE'>('TABLE');
  readonly xColumn = signal('');
  readonly yColumns = signal<string[]>([]);
  readonly shown = signal<Set<string>>(new Set());

  readonly queryCtrl = new FormControl<string | null>(null);
  readonly from = new FormControl<string>('', { nonNullable: true });
  readonly to = new FormControl<string>('', { nonNullable: true });

  readonly selected = computed(() => this.catalogue().find((q) => q.key === this.queryCtrl.value) ?? null);
  readonly numeric = computed<AnalyticsColumn[]>(() =>
    (this.result()?.columns ?? []).filter((c) => c.type === 'NUMBER' || c.type === 'MONEY' || c.type === 'PERCENT'),
  );
  readonly visible = computed<AnalyticsColumn[]>(() =>
    (this.result()?.columns ?? []).filter((c) => this.shown().has(c.name)),
  );
  readonly visibleNames = computed(() => this.visible().map((c) => c.name));

  ngOnInit(): void {
    this.api.catalogue().subscribe({
      next: (c) => {
        this.catalogue.set(c);
        if (c.length > 0) {
          this.queryCtrl.setValue(c[0].key);
          this.onQueryChange();
        }
      },
      error: () => this.snack.open('Could not load the query catalogue', 'Close', { duration: 4000 }),
    });
  }

  onQueryChange(): void {
    const q = this.selected();
    if (q) {
      this.chartType.set(q.chart);
      this.xColumn.set(q.xColumn);
      this.yColumns.set(q.yColumns);
    }
  }

  run(): void {
    const key = this.queryCtrl.value;
    if (!key) return;
    this.loading.set(true);
    this.api.query(key, this.from.value || null, this.to.value || null, null).subscribe({
      next: (r) => {
        this.result.set(r);
        this.shown.set(new Set(r.columns.map((c) => c.name)));
        this.loading.set(false);
      },
      error: (e) => {
        this.loading.set(false);
        this.snack.open(e?.status === 403 ? 'You are not allowed to run that query' : 'The query failed', 'Close', {
          duration: 5000,
        });
      },
    });
  }

  toggleColumn(name: string, checked: boolean): void {
    const next = new Set(this.shown());
    if (checked) next.add(name);
    else next.delete(name);
    this.shown.set(next);
  }

  refreshViews(): void {
    this.api.refresh().subscribe({
      next: (r) =>
        this.snack.open(
          Object.keys(r.failed).length === 0
            ? 'Analytics data refreshed'
            : `Refresh failed for ${Object.keys(r.failed).join(', ')}`,
          'Close',
          { duration: 5000 },
        ),
      error: () => this.snack.open('Could not refresh the analytics data', 'Close', { duration: 4000 }),
    });
  }

  download(): void {
    const r = this.result();
    if (!r) return;
    const blob = new Blob([toCsv(this.visible(), r.rows)], { type: 'text/csv;charset=utf-8' });
    saveBlob(blob, `${r.key.toLowerCase()}.csv`);
  }

  asNumber(value: unknown): number {
    return Number(value ?? 0);
  }
}
