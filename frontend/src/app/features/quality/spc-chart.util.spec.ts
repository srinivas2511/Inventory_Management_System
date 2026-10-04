import { buildPlot, parseValues } from './spc-chart.util';

describe('buildPlot', () => {
  const points = [
    { value: 10.0, outOfControl: false, label: 'a' },
    { value: 10.2, outOfControl: false, label: 'b' },
    { value: 10.6, outOfControl: true, label: 'c' },
  ];

  it('keeps every point and every control line inside the canvas', () => {
    const plot = buildPlot(points, { cl: 10, ucl: 10.3, lcl: 9.7 });
    const ys = [...plot.points.map((p) => p.y), ...plot.lines.map((l) => l.y)];
    for (const y of ys) {
      expect(y).toBeGreaterThanOrEqual(plot.top);
      expect(y).toBeLessThanOrEqual(plot.height - plot.bottom);
    }
    expect(plot.min).toBeLessThan(9.7);
    expect(plot.max).toBeGreaterThan(10.6);
  });

  it('draws a higher value higher on the canvas (smaller y) and spreads points left to right', () => {
    const plot = buildPlot(points, { cl: 10, ucl: 10.3, lcl: 9.7 });
    expect(plot.points[2].y).toBeLessThan(plot.points[0].y);
    expect(plot.points[0].x).toBeLessThan(plot.points[1].x);
    expect(plot.points[1].x).toBeLessThan(plot.points[2].x);
    const ucl = plot.lines.find((l) => l.key === 'UCL')!;
    const lcl = plot.lines.find((l) => l.key === 'LCL')!;
    expect(ucl.y).toBeLessThan(lcl.y);
  });

  it('flags out-of-control points and builds the polyline', () => {
    const plot = buildPlot(points, { cl: 10, ucl: 10.3, lcl: 9.7 });
    expect(plot.points.map((p) => p.outOfControl)).toEqual([false, false, true]);
    expect(plot.polyline.split(' ').length).toBe(3);
  });

  it('omits control lines that are not set', () => {
    const plot = buildPlot(points, { cl: null, ucl: null, lcl: null });
    expect(plot.lines).toEqual([]);
  });

  it('copes with a single point and with a flat series', () => {
    const single = buildPlot([{ value: 5, outOfControl: false, label: 'x' }], { cl: null, ucl: null, lcl: null });
    expect(single.points.length).toBe(1);
    expect(Number.isFinite(single.points[0].y)).toBe(true);

    const flat = buildPlot(
      [
        { value: 3, outOfControl: false, label: 'a' },
        { value: 3, outOfControl: false, label: 'b' },
      ],
      { cl: 3, ucl: null, lcl: null },
    );
    expect(flat.max).toBeGreaterThan(flat.min);
  });

  it('handles an empty series', () => {
    const plot = buildPlot([], { cl: null, ucl: null, lcl: null });
    expect(plot.points).toEqual([]);
    expect(plot.polyline).toBe('');
  });
});

describe('parseValues', () => {
  it('splits on spaces, commas and semicolons', () => {
    expect(parseValues('10.1 10.2, 9.9;10')).toEqual([10.1, 10.2, 9.9, 10]);
  });

  it('rejects empty input and non-numbers', () => {
    expect(parseValues('')).toBeNull();
    expect(parseValues('   ')).toBeNull();
    expect(parseValues('10.1 abc')).toBeNull();
  });
});
