import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AnalyticsChartComponent, CHART_PADDING, chartGeometry, niceMax } from './analytics-chart.component';
import { AnalyticsRow } from './analytics.api';

describe('niceMax', () => {
  it('rounds up to 1, 2, 5 or 10 times a power of ten', () => {
    expect(niceMax(0)).toBe(1);
    expect(niceMax(-4)).toBe(1);
    expect(niceMax(1)).toBe(1);
    expect(niceMax(7)).toBe(10);
    expect(niceMax(12)).toBe(20);
    expect(niceMax(45)).toBe(50);
    expect(niceMax(120)).toBe(200);
    expect(niceMax(0.3)).toBeCloseTo(0.5, 10);
  });
});

describe('chartGeometry', () => {
  // Width 656 and height 256 give a plot of 588 x 200 after the padding (left 56, right 12, top 12, bottom 44).
  const rows: AnalyticsRow[] = [
    { m: 'Jan', a: 10, b: 20 },
    { m: 'Feb', a: 30, b: 0 },
  ];
  const g = chartGeometry(rows, 'm', ['a', 'b'], 656, 256);

  it('scales to a friendly maximum', () => {
    expect(g.max).toBe(50); // the largest value is 30
    expect(g.ticks.map((t) => t.text)).toEqual(['0', '12.5', '25', '37.5', '50']);
    expect(g.ticks.map((t) => t.y)).toEqual([212, 162, 112, 62, 12]);
  });

  it('lays bars out in bands: two series per row share 80% of a 294 wide band', () => {
    const band = 588 / 2;
    const barWidth = (band * 0.8) / 2; // 117.6
    expect(g.bars).toHaveSize(4);
    const [janA, janB, febA, febB] = g.bars;
    expect(janA.x).toBeCloseTo(CHART_PADDING.left + band * 0.1, 6); // 85.4
    expect(janA.width).toBeCloseTo(barWidth, 6);
    expect(janA.y).toBeCloseTo(172, 6); // 212 - 10/50 * 200
    expect(janA.height).toBeCloseTo(40, 6);
    expect(janB.x).toBeCloseTo(janA.x + barWidth, 6);
    expect(janB.y).toBeCloseTo(132, 6);
    expect(janB.height).toBeCloseTo(80, 6);
    expect(febA.x).toBeCloseTo(CHART_PADDING.left + band + band * 0.1, 6); // 379.4
    expect(febA.height).toBeCloseTo(120, 6);
    expect(febB.height).toBe(0);
  });

  it('centres the labels in their bands and builds one line per series', () => {
    expect(g.labels.map((l) => l.x)).toEqual([203, 497]);
    expect(g.labels.map((l) => l.text)).toEqual(['Jan', 'Feb']);
    expect(g.lines).toHaveSize(2);
    expect(g.lines[0].points).toEqual([
      { x: 203, y: 172 },
      { x: 497, y: 92 },
    ]);
  });

  it('draws negative and non-numeric values as zero', () => {
    const odd = chartGeometry([{ m: 'x', a: -5, b: 'n/a' }], 'm', ['a', 'b'], 656, 256);
    expect(odd.bars.map((b) => b.height)).toEqual([0, 0]);
    expect(odd.max).toBe(1);
  });

  it('copes with no rows', () => {
    const empty = chartGeometry([], 'm', ['a'], 656, 256);
    expect(empty.bars).toEqual([]);
    expect(empty.labels).toEqual([]);
  });
});

@Component({
  standalone: true,
  imports: [AnalyticsChartComponent],
  template: `<app-analytics-chart [rows]="rows" xColumn="m" [yColumns]="y" [type]="type" />`,
})
class HostComponent {
  rows: AnalyticsRow[] = [
    { m: 'Jan', a: 10, b: 20 },
    { m: 'Feb', a: 30, b: 0 },
    { m: 'Mar', a: 5, b: 5 },
  ];
  y = ['a', 'b'];
  type: 'BAR' | 'LINE' = 'BAR';
}

describe('AnalyticsChartComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  const all = (id: string) => fixture.nativeElement.querySelectorAll(`[data-testid="${id}"]`);

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('draws one bar per value and describes the chart for screen readers', () => {
    expect(all('bar')).toHaveSize(6);
    const svg: SVGElement = fixture.nativeElement.querySelector('svg');
    expect(svg.getAttribute('role')).toBe('img');
    expect(svg.getAttribute('aria-label')).toContain('Bar chart of a, b by m, 3 points');
  });

  it('draws one polyline per series as a line chart', () => {
    fixture.componentInstance.type = 'LINE';
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(all('line')).toHaveSize(2);
    expect(all('bar')).toHaveSize(0);
  });

  it('says so when there is nothing to draw', () => {
    fixture.componentInstance.rows = [];
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No data for this period');
    expect(all('chart')).toHaveSize(0);
  });
});
