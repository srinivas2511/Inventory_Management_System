import { Component, Input, OnChanges } from '@angular/core';
import { AnalyticsRow } from './analytics.api';

export interface Bar {
  x: number;
  y: number;
  width: number;
  height: number;
  series: number;
  label: string;
  value: number;
}

export interface LinePoint {
  x: number;
  y: number;
}

export interface ChartGeometry {
  labels: { x: number; text: string }[];
  ticks: { y: number; text: string }[];
  bars: Bar[];
  lines: { series: number; name: string; points: LinePoint[] }[];
  max: number;
}

export const CHART_PADDING = { left: 56, right: 12, top: 12, bottom: 44 };

/** Rounds a maximum up to 1, 2, 5 or 10 times a power of ten so the axis has friendly ticks. */
export function niceMax(value: number): number {
  if (!(value > 0)) return 1;
  const exponent = Math.floor(Math.log10(value));
  const base = Math.pow(10, exponent);
  const fraction = value / base;
  const nice = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10;
  return nice * base;
}

/** Pure layout: where every bar and line point goes in a width x height box. Negative values are drawn as zero. */
export function chartGeometry(
  rows: AnalyticsRow[],
  xColumn: string,
  yColumns: string[],
  width: number,
  height: number,
): ChartGeometry {
  const plotW = width - CHART_PADDING.left - CHART_PADDING.right;
  const plotH = height - CHART_PADDING.top - CHART_PADDING.bottom;
  const values = rows.map((r) => yColumns.map((c) => Math.max(0, Number(r[c]) || 0)));
  const max = niceMax(Math.max(0, ...values.flat()));
  const band = rows.length > 0 ? plotW / rows.length : plotW;
  const seriesCount = Math.max(1, yColumns.length);
  const barWidth = (band * 0.8) / seriesCount;
  const yOf = (v: number) => CHART_PADDING.top + plotH - (v / max) * plotH;

  const labels = rows.map((r, i) => ({
    x: CHART_PADDING.left + band * i + band / 2,
    text: String(r[xColumn] ?? ''),
  }));
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((f) => ({ y: yOf(max * f), text: formatTick(max * f) }));
  const bars: Bar[] = [];
  values.forEach((row, i) => {
    row.forEach((v, s) => {
      const top = yOf(v);
      bars.push({
        x: CHART_PADDING.left + band * i + band * 0.1 + barWidth * s,
        y: top,
        width: barWidth,
        height: CHART_PADDING.top + plotH - top,
        series: s,
        label: `${yColumns[s]} ${labels[i].text}: ${v}`,
        value: v,
      });
    });
  });
  const lines = yColumns.map((name, s) => ({
    series: s,
    name,
    points: values.map((row, i) => ({ x: CHART_PADDING.left + band * i + band / 2, y: yOf(row[s]) })),
  }));
  return { labels, ticks, bars, lines, max };
}

function formatTick(v: number): string {
  if (v >= 1_000_000) return `${+(v / 1_000_000).toFixed(1)}M`;
  if (v >= 1_000) return `${+(v / 1_000).toFixed(1)}k`;
  return `${+v.toFixed(2)}`;
}

/** A dependency-free SVG bar or line chart with a text alternative for screen readers. */
@Component({
  selector: 'app-analytics-chart',
  standalone: true,
  template: `
    @if (rows.length === 0) {
      <p class="empty">No data for this period</p>
    } @else {
      <svg
        [attr.viewBox]="'0 0 ' + width + ' ' + height"
        role="img"
        [attr.aria-label]="summary"
        class="chart"
        data-testid="chart"
      >
        @for (t of geometry.ticks; track t.y) {
          <line [attr.x1]="padLeft" [attr.x2]="width - 12" [attr.y1]="t.y" [attr.y2]="t.y" class="grid" />
          <text [attr.x]="padLeft - 6" [attr.y]="t.y + 4" text-anchor="end" class="tick">{{ t.text }}</text>
        }
        @if (type === 'BAR') {
          @for (b of geometry.bars; track $index) {
            <rect
              [attr.x]="b.x"
              [attr.y]="b.y"
              [attr.width]="b.width"
              [attr.height]="b.height"
              [attr.class]="'bar s' + (b.series % 4)"
              data-testid="bar"
            >
              <title>{{ b.label }}</title>
            </rect>
          }
        } @else {
          @for (l of geometry.lines; track l.series) {
            <polyline
              [attr.points]="polyline(l.points)"
              [attr.class]="'line s' + (l.series % 4)"
              fill="none"
              data-testid="line"
            />
          }
        }
        @for (l of geometry.labels; track $index) {
          <text [attr.x]="l.x" [attr.y]="height - 26" text-anchor="middle" class="tick">{{ shorten(l.text) }}</text>
        }
      </svg>
      <div class="legend">
        @for (y of yColumns; track y; let i = $index) {
          <span class="key"><span [attr.class]="'swatch s' + (i % 4)"></span>{{ y }}</span>
        }
      </div>
    }
  `,
  styles: `
    .chart {
      width: 100%;
      height: auto;
      max-height: 320px;
    }
    .grid {
      stroke: #e0e0e0;
      stroke-width: 1;
    }
    .tick {
      font-size: 10px;
      fill: #616161;
    }
    .s0 {
      fill: #1565c0;
      stroke: #1565c0;
    }
    .s1 {
      fill: #2e7d32;
      stroke: #2e7d32;
    }
    .s2 {
      fill: #ef6c00;
      stroke: #ef6c00;
    }
    .s3 {
      fill: #6a1b9a;
      stroke: #6a1b9a;
    }
    .line {
      stroke-width: 2;
    }
    .legend {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      font-size: 12px;
    }
    .swatch {
      display: inline-block;
      width: 10px;
      height: 10px;
      margin-right: 4px;
    }
    .empty {
      color: #757575;
    }
  `,
})
export class AnalyticsChartComponent implements OnChanges {
  @Input() rows: AnalyticsRow[] = [];
  @Input() xColumn = '';
  @Input() yColumns: string[] = [];
  @Input() type: 'BAR' | 'LINE' = 'BAR';

  readonly width = 640;
  readonly height = 260;
  readonly padLeft = CHART_PADDING.left;
  geometry: ChartGeometry = chartGeometry([], '', [], this.width, this.height);
  summary = '';

  ngOnChanges(): void {
    this.geometry = chartGeometry(this.rows, this.xColumn, this.yColumns, this.width, this.height);
    this.summary = `${this.type === 'BAR' ? 'Bar' : 'Line'} chart of ${this.yColumns.join(', ')} by ${this.xColumn}, ${this.rows.length} points, up to ${this.geometry.max}`;
  }

  polyline(points: { x: number; y: number }[]): string {
    return points.map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ');
  }

  shorten(text: string): string {
    return text.length > 9 ? text.slice(0, 8) + '…' : text;
  }
}
