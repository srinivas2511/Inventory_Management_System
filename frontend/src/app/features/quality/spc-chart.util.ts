export interface ChartPointInput {
  value: number;
  outOfControl: boolean;
  label: string;
  /** Per-point control limits (P charts with varying sample sizes); the chart-wide limits apply when absent. */
  ucl?: number | null;
  lcl?: number | null;
}

export interface PlottedPoint {
  x: number;
  y: number;
  outOfControl: boolean;
  label: string;
}

export interface ChartLine {
  key: 'UCL' | 'CL' | 'LCL';
  y: number;
  value: number;
}

export interface ChartPlot {
  width: number;
  height: number;
  left: number;
  right: number;
  top: number;
  bottom: number;
  points: PlottedPoint[];
  polyline: string;
  lines: ChartLine[];
  min: number;
  max: number;
}

export interface PlotOptions {
  width?: number;
  height?: number;
  cl: number | null;
  ucl: number | null;
  lcl: number | null;
}

/**
 * Lays the series and its control lines out on an SVG canvas. The vertical range covers every point and every
 * control line with 8% padding, so a point beyond a limit is always visible; a flat series gets a unit range.
 */
export function buildPlot(points: ChartPointInput[], opts: PlotOptions): ChartPlot {
  const width = opts.width ?? 720;
  const height = opts.height ?? 240;
  const left = 56;
  const right = 16;
  const top = 14;
  const bottom = 28;

  const refs: number[] = points.map((p) => p.value);
  for (const v of [opts.cl, opts.ucl, opts.lcl]) {
    if (v !== null && v !== undefined) refs.push(v);
  }
  let min = refs.length ? Math.min(...refs) : 0;
  let max = refs.length ? Math.max(...refs) : 1;
  if (max === min) {
    min -= 0.5;
    max += 0.5;
  }
  const pad = (max - min) * 0.08;
  min -= pad;
  max += pad;

  const plotW = width - left - right;
  const plotH = height - top - bottom;
  const yOf = (v: number): number => top + (1 - (v - min) / (max - min)) * plotH;
  const xOf = (i: number): number => (points.length <= 1 ? left + plotW / 2 : left + (i / (points.length - 1)) * plotW);

  const plotted: PlottedPoint[] = points.map((p, i) => ({
    x: round(xOf(i)),
    y: round(yOf(p.value)),
    outOfControl: p.outOfControl,
    label: p.label,
  }));
  const lines: ChartLine[] = [];
  if (opts.ucl !== null && opts.ucl !== undefined) lines.push({ key: 'UCL', y: round(yOf(opts.ucl)), value: opts.ucl });
  if (opts.cl !== null && opts.cl !== undefined) lines.push({ key: 'CL', y: round(yOf(opts.cl)), value: opts.cl });
  if (opts.lcl !== null && opts.lcl !== undefined) lines.push({ key: 'LCL', y: round(yOf(opts.lcl)), value: opts.lcl });

  return {
    width,
    height,
    left,
    right,
    top,
    bottom,
    points: plotted,
    polyline: plotted.map((p) => `${p.x},${p.y}`).join(' '),
    lines,
    min,
    max,
  };
}

function round(n: number): number {
  return Math.round(n * 100) / 100;
}

/** Parses "10.1 10.2, 9.9;10" into numbers; returns null when any token is not a number. */
export function parseValues(text: string): number[] | null {
  const tokens = text.split(/[\s,;]+/).filter((t) => t.length > 0);
  if (tokens.length === 0) return null;
  const nums = tokens.map((t) => Number(t));
  return nums.some((n) => !Number.isFinite(n)) ? null : nums;
}
