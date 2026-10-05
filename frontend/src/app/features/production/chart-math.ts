/** Small, pure helpers for the hand-drawn SVG charts (no chart library is used). */

export interface Pt {
  x: number;
  y: number;
}

export interface Bar {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** Points of a series of fractions (0..max) spread evenly across the width, y inverted so larger is higher. */
export function seriesPoints(values: number[], width: number, height: number, max = 1): Pt[] {
  if (values.length === 0) return [];
  const step = values.length === 1 ? 0 : width / (values.length - 1);
  return values.map((v, i) => ({
    x: values.length === 1 ? width / 2 : i * step,
    y: height - clamp(v / max, 0, 1) * height,
  }));
}

/** SVG path data for a polyline through the points. */
export function linePath(points: Pt[]): string {
  return points.map((p, i) => `${i === 0 ? 'M' : 'L'}${round(p.x)},${round(p.y)}`).join(' ');
}

/** Bars for a Pareto chart: widths are equal, heights are proportional to the largest value. */
export function paretoBars(values: number[], width: number, height: number, gap = 8): Bar[] {
  if (values.length === 0) return [];
  const max = Math.max(...values, 0);
  const slot = width / values.length;
  const barWidth = Math.max(slot - gap, 1);
  return values.map((v, i) => {
    const h = max <= 0 ? 0 : (v / max) * height;
    return { x: i * slot + gap / 2, y: height - h, width: barWidth, height: h };
  });
}

/** The centre of each Pareto bar, for plotting the cumulative line over them. */
export function barCentres(bars: Bar[]): number[] {
  return bars.map((b) => b.x + b.width / 2);
}

/** A fraction as a percentage with one decimal, or a dash when there is nothing to show. */
export function percent(fraction: number | null | undefined): string {
  if (fraction === null || fraction === undefined || Number.isNaN(fraction)) return '-';
  return `${(fraction * 100).toFixed(1)}%`;
}

export function clamp(v: number, min: number, max: number): number {
  return Math.min(Math.max(v, min), max);
}

function round(n: number): number {
  return Math.round(n * 100) / 100;
}
