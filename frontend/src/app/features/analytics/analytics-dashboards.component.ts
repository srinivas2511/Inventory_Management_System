import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AnalyticsChartComponent } from './analytics-chart.component';
import { AnalyticsApi, QueryDescriptor, QueryResult } from './analytics.api';

interface Panel {
  key: string;
  title: string;
  view: 'BAR' | 'LINE' | 'TABLE';
  x: string;
  y: string[];
  months: number;
}

/** The analytical dashboards of ANA-002. A panel is shown only if the caller may run its query. */
export const PANELS: Panel[] = [
  {
    key: 'PRODUCTION_MONTHLY',
    title: 'Production: planned against good output',
    view: 'BAR',
    x: 'month',
    y: ['planned_qty', 'good_qty'],
    months: 12,
  },
  {
    key: 'OEE_DAILY',
    title: 'Production: machine output and downtime, last 7 days',
    view: 'TABLE',
    x: 'day',
    y: [],
    months: 0,
  },
  {
    key: 'INVENTORY_TURNS',
    title: 'Inventory turns (annualised)',
    view: 'LINE',
    x: 'month',
    y: ['annualised_turns'],
    months: 12,
  },
  {
    key: 'SUPPLIER_SCORECARD',
    title: 'Supplier scorecards: this month',
    view: 'TABLE',
    x: 'supplier_name',
    y: [],
    months: 0,
  },
  {
    key: 'CUSTOMER_PROFITABILITY',
    title: 'Customer profitability: last 12 months',
    view: 'BAR',
    x: 'customer_name',
    y: ['revenue', 'gross_margin'],
    months: 12,
  },
  {
    key: 'COST_VARIANCE',
    title: 'Cost variance by production order',
    view: 'BAR',
    x: 'order_number',
    y: ['total_variance'],
    months: 12,
  },
  {
    key: 'FIN_SUMMARY_MONTHLY',
    title: 'Financial summary by period',
    view: 'BAR',
    x: 'period_name',
    y: ['revenue', 'cogs', 'net_profit'],
    months: 12,
  },
];

function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** First day of the month that is {@code months} before today's month; the whole of today's month for 0. */
export function windowStart(today: Date, months: number): string {
  return iso(new Date(today.getFullYear(), today.getMonth() - months, 1));
}

@Component({
  selector: 'app-analytics-dashboards',
  standalone: true,
  imports: [DecimalPipe, MatCardModule, MatSnackBarModule, AnalyticsChartComponent],
  template: `
    <h2 class="mat-h2">Analytical dashboards</h2>
    @if (loaded() && panels().length === 0) {
      <p>You have no analytics available.</p>
    }
    <div class="grid">
      @for (p of panels(); track p.panel.key) {
        <mat-card class="card" data-testid="panel">
          <mat-card-header>
            <mat-card-title>{{ p.panel.title }}</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            @if (p.result; as r) {
              @if (p.panel.view === 'TABLE') {
                <table class="mini" [attr.aria-label]="p.panel.title">
                  <thead>
                    <tr>
                      @for (c of r.columns; track c.name) {
                        <th>{{ c.label }}</th>
                      }
                    </tr>
                  </thead>
                  <tbody>
                    @for (row of r.rows.slice(0, 8); track $index) {
                      <tr>
                        @for (c of r.columns; track c.name) {
                          <td>
                            {{
                              c.type === 'TEXT' || c.type === 'DATE'
                                ? row[c.name]
                                : row[c.name] === null
                                  ? ''
                                  : (asNumber(row[c.name]) | number: '1.0-2')
                            }}
                          </td>
                        }
                      </tr>
                    } @empty {
                      <tr>
                        <td [attr.colspan]="r.columns.length">No data for this period</td>
                      </tr>
                    }
                  </tbody>
                </table>
              } @else {
                <app-analytics-chart
                  [rows]="r.rows"
                  [xColumn]="p.panel.x"
                  [yColumns]="p.panel.y"
                  [type]="p.panel.view === 'LINE' ? 'LINE' : 'BAR'"
                />
              }
            } @else if (p.failed) {
              <p class="error">Could not load this panel</p>
            } @else {
              <p>Loading…</p>
            }
          </mat-card-content>
        </mat-card>
      }
    </div>
  `,
  styles: `
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
      gap: 16px;
    }
    .mini {
      width: 100%;
      border-collapse: collapse;
      font-size: 12px;
    }
    .mini th,
    .mini td {
      padding: 4px 6px;
      border-bottom: 1px solid #e0e0e0;
      text-align: left;
    }
    .error {
      color: #c62828;
    }
  `,
})
export class AnalyticsDashboardsComponent implements OnInit {
  private readonly api = inject(AnalyticsApi);
  private readonly snack = inject(MatSnackBar);

  readonly loaded = signal(false);
  readonly panels = signal<{ panel: Panel; result: QueryResult | null; failed: boolean }[]>([]);

  ngOnInit(): void {
    this.api.catalogue().subscribe({
      next: (catalogue) => this.start(catalogue),
      error: () => {
        this.loaded.set(true);
        this.snack.open('Could not load the dashboards', 'Close', { duration: 4000 });
      },
    });
  }

  private start(catalogue: QueryDescriptor[]): void {
    const allowed = new Set(catalogue.map((q) => q.key));
    const shown = PANELS.filter((p) => allowed.has(p.key)).map((panel) => ({
      panel,
      result: null as QueryResult | null,
      failed: false,
    }));
    this.panels.set(shown);
    this.loaded.set(true);
    const today = new Date();
    shown.forEach((entry, index) => {
      const from =
        entry.panel.months > 0
          ? windowStart(today, entry.panel.months)
          : entry.panel.key === 'OEE_DAILY'
            ? iso(new Date(today.getTime() - 6 * 86_400_000))
            : windowStart(today, 0);
      this.api.query(entry.panel.key, from, iso(today), 500).subscribe({
        next: (result) => this.update(index, { result }),
        error: () => this.update(index, { failed: true }),
      });
    });
  }

  private update(index: number, change: Partial<{ result: QueryResult | null; failed: boolean }>): void {
    this.panels.update((all) => all.map((e, i) => (i === index ? { ...e, ...change } : e)));
  }

  asNumber(value: unknown): number {
    return Number(value ?? 0);
  }
}
