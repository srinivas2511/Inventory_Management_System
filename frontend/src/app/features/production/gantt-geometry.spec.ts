import {
  GanttScale,
  MIN_BAR_WIDTH,
  barRect,
  isDrag,
  movedStart,
  rowIndexAt,
  snapTime,
  timeToX,
  xToTime,
} from './gantt-geometry';

const HOUR = 3_600_000;
const START = Date.UTC(2026, 9, 5, 0, 0, 0); // Monday 5 October 2026, 00:00 UTC
const scale: GanttScale = { startMs: START, pxPerHour: 6 };

describe('gantt-geometry', () => {
  it('maps time to pixels and back', () => {
    expect(timeToX(scale, START)).toBe(0);
    expect(timeToX(scale, START + 2 * HOUR)).toBe(12);
    expect(timeToX(scale, START + 24 * HOUR)).toBe(144);
    expect(xToTime(scale, 12)).toBe(START + 2 * HOUR);
    expect(xToTime(scale, timeToX(scale, START + 37 * 60_000))).toBeCloseTo(START + 37 * 60_000, 0);
  });

  it('snaps to the nearest grid step', () => {
    const tenOclock = START + 10 * HOUR;
    expect(snapTime(tenOclock + 7 * 60_000, 15)).toBe(tenOclock);
    expect(snapTime(tenOclock + 8 * 60_000, 15)).toBe(tenOclock + 15 * 60_000);
    expect(snapTime(tenOclock, 15)).toBe(tenOclock);
  });

  it('finds the row under a vertical position and keeps it within the rows that exist', () => {
    expect(rowIndexAt(0, 48, 3)).toBe(0);
    expect(rowIndexAt(47, 48, 3)).toBe(0);
    expect(rowIndexAt(48, 48, 3)).toBe(1);
    expect(rowIndexAt(500, 48, 3)).toBe(2);
    expect(rowIndexAt(-30, 48, 3)).toBe(0);
    expect(rowIndexAt(10, 48, 0)).toBe(-1);
  });

  it('places a bar by its time span and row, and never makes it narrower than a visible sliver', () => {
    const r = barRect(scale, START + 9 * HOUR, START + 11 * HOUR, 2, 48, 6);

    expect(r).toEqual({ left: 54, width: 12, top: 2 * 48 + 6, height: 36 });
    expect(barRect(scale, START, START + 60_000, 0, 48).width).toBe(MIN_BAR_WIDTH);
  });

  it('works out where a dragged bar starts, snapped to the grid', () => {
    const start = START + 9 * HOUR;

    expect(movedStart(scale, start, 18, 15)).toBe(start + 3 * HOUR); // 18 px = 3 h
    expect(movedStart(scale, start, -6, 15)).toBe(start - HOUR);
    expect(movedStart(scale, start, 1, 15)).toBe(start + 15 * 60_000); // 10 min rounds up to the 15 min step
    expect(movedStart(scale, start, 0, 15)).toBe(start);
  });

  it('tells a click from a drag by how far the pointer moved', () => {
    expect(isDrag(0, 0)).toBe(false);
    expect(isDrag(3, -3)).toBe(false);
    expect(isDrag(4, 0)).toBe(true);
    expect(isDrag(0, -4)).toBe(true);
  });
});
