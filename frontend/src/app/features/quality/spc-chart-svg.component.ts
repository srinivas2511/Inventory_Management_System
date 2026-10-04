import { Component, Input, OnChanges } from '@angular/core';
import { ChartPlot, ChartPointInput, buildPlot } from './spc-chart.util';

/** A control chart drawn as inline SVG: the series, UCL/CL/LCL lines, and out-of-control points in red. */
@Component({
  selector: 'app-spc-chart-svg',
  standalone: true,
  template: `
    <figure class="chart">
      <figcaption>{{ title }}</figcaption>
      @if (plot.points.length === 0) {
        <p class="empty">No data points yet</p>
      } @else {
        <svg [attr.viewBox]="'0 0 ' + plot.width + ' ' + plot.height" role="img" [attr.aria-label]="title">
          @for (l of plot.lines; track l.key) {
            <line
              [attr.x1]="plot.left"
              [attr.x2]="plot.width - plot.right"
              [attr.y1]="l.y"
              [attr.y2]="l.y"
              [attr.class]="'ref ' + l.key"
              stroke-dasharray="6 4"
            />
            <text [attr.x]="plot.left - 6" [attr.y]="l.y + 4" text-anchor="end" class="axis">
              {{ l.key }} {{ l.value }}
            </text>
          }
          <polyline [attr.points]="plot.polyline" fill="none" class="series" />
          @for (p of plot.points; track $index) {
            <circle [attr.cx]="p.x" [attr.cy]="p.y" r="4" [attr.class]="p.outOfControl ? 'pt ooc' : 'pt'">
              <title>{{ p.label }}</title>
            </circle>
          }
        </svg>
      }
    </figure>
  `,
  styles: `
    .chart {
      margin: 0 0 16px;
    }
    figcaption {
      font-weight: 500;
      margin-bottom: 4px;
    }
    svg {
      width: 100%;
      max-width: 760px;
      height: auto;
      background: var(--mat-sys-surface-container-low, #fafafa);
      border: 1px solid var(--mat-sys-outline-variant, #ddd);
    }
    .series {
      stroke: #1565c0;
      stroke-width: 1.5;
    }
    .pt {
      fill: #1565c0;
    }
    .pt.ooc {
      fill: #c62828;
      stroke: #fff;
      stroke-width: 1;
    }
    .ref.UCL,
    .ref.LCL {
      stroke: #c62828;
    }
    .ref.CL {
      stroke: #2e7d32;
    }
    .axis {
      font-size: 10px;
      fill: #555;
    }
    .empty {
      color: #777;
    }
  `,
})
export class SpcChartSvgComponent implements OnChanges {
  @Input() title = '';
  @Input() points: ChartPointInput[] = [];
  @Input() cl: number | null = null;
  @Input() ucl: number | null = null;
  @Input() lcl: number | null = null;

  plot: ChartPlot = buildPlot([], { cl: null, ucl: null, lcl: null });

  ngOnChanges(): void {
    this.plot = buildPlot(this.points, { cl: this.cl, ucl: this.ucl, lcl: this.lcl });
  }
}
