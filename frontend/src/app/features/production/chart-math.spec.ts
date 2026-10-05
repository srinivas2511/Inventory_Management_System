import { barCentres, clamp, linePath, paretoBars, percent, seriesPoints } from './chart-math';

describe('chart-math', () => {
  describe('seriesPoints', () => {
    it('spreads values evenly across the width with the largest at the top', () => {
      expect(seriesPoints([0, 0.5, 1], 100, 200)).toEqual([
        { x: 0, y: 200 },
        { x: 50, y: 100 },
        { x: 100, y: 0 },
      ]);
    });

    it('centres a single value and gives nothing for an empty series', () => {
      expect(seriesPoints([0.25], 100, 200)).toEqual([{ x: 50, y: 150 }]);
      expect(seriesPoints([], 100, 200)).toEqual([]);
    });

    it('keeps values outside 0..max on the chart', () => {
      expect(seriesPoints([-0.5, 1.5], 10, 100)).toEqual([
        { x: 0, y: 100 },
        { x: 10, y: 0 },
      ]);
    });

    it('scales to a different maximum', () => {
      expect(seriesPoints([5, 10], 10, 100, 10)).toEqual([
        { x: 0, y: 50 },
        { x: 10, y: 0 },
      ]);
    });
  });

  it('writes a polyline as SVG path data', () => {
    expect(
      linePath([
        { x: 0, y: 200 },
        { x: 50.126, y: 100.004 },
      ]),
    ).toBe('M0,200 L50.13,100');
    expect(linePath([])).toBe('');
  });

  describe('paretoBars', () => {
    it('makes bar heights proportional to the largest value, left to right', () => {
      const bars = paretoBars([50, 25, 0], 300, 100, 10);

      expect(bars).toEqual([
        { x: 5, y: 0, width: 90, height: 100 },
        { x: 105, y: 50, width: 90, height: 50 },
        { x: 205, y: 100, width: 90, height: 0 },
      ]);
      expect(barCentres(bars)).toEqual([50, 150, 250]);
    });

    it('has no bars for no values and flat bars when every value is zero', () => {
      expect(paretoBars([], 100, 100)).toEqual([]);
      expect(paretoBars([0, 0], 100, 100, 0).map((b) => b.height)).toEqual([0, 0]);
    });
  });

  it('formats a fraction as a percentage with one decimal, and a dash when there is none', () => {
    expect(percent(0.7407)).toBe('74.1%');
    expect(percent(1)).toBe('100.0%');
    expect(percent(0)).toBe('0.0%');
    expect(percent(null)).toBe('-');
    expect(percent(undefined)).toBe('-');
    expect(percent(NaN)).toBe('-');
  });

  it('clamps', () => {
    expect(clamp(5, 0, 1)).toBe(1);
    expect(clamp(-5, 0, 1)).toBe(0);
    expect(clamp(0.4, 0, 1)).toBe(0.4);
  });
});
