/** Pure geometry of the production Gantt chart: time on the horizontal axis, one row per machine. */

export const MS_PER_HOUR = 3_600_000;

export interface GanttScale {
  /** The instant at x = 0, in epoch milliseconds. */
  startMs: number;
  pxPerHour: number;
}

export interface BarRect {
  left: number;
  width: number;
  top: number;
  height: number;
}

export const MIN_BAR_WIDTH = 6;

export function timeToX(scale: GanttScale, ms: number): number {
  return ((ms - scale.startMs) / MS_PER_HOUR) * scale.pxPerHour;
}

export function xToTime(scale: GanttScale, x: number): number {
  return scale.startMs + (x / scale.pxPerHour) * MS_PER_HOUR;
}

/** Rounds an instant to the nearest multiple of {@code minutes} (counted from the epoch). */
export function snapTime(ms: number, minutes: number): number {
  const step = minutes * 60_000;
  return Math.round(ms / step) * step;
}

/** The row a vertical position falls in, clamped to the rows that exist; -1 when there are none. */
export function rowIndexAt(y: number, rowHeight: number, rowCount: number): number {
  if (rowCount <= 0) return -1;
  return Math.min(Math.max(Math.floor(y / rowHeight), 0), rowCount - 1);
}

export function barRect(
  scale: GanttScale,
  startMs: number,
  endMs: number,
  rowIndex: number,
  rowHeight: number,
  padding = 6,
): BarRect {
  const left = timeToX(scale, startMs);
  const right = timeToX(scale, endMs);
  return {
    left,
    width: Math.max(right - left, MIN_BAR_WIDTH),
    top: rowIndex * rowHeight + padding,
    height: rowHeight - padding * 2,
  };
}

/** Where a bar that started at {@code startMs} starts after being dragged {@code dx} pixels, snapped to the grid. */
export function movedStart(scale: GanttScale, startMs: number, dx: number, snapMinutes: number): number {
  return snapTime(startMs + (dx / scale.pxPerHour) * MS_PER_HOUR, snapMinutes);
}

/** A drag shorter than this many pixels in both directions is a click, not a move. */
export const DRAG_THRESHOLD_PX = 4;

export function isDrag(dx: number, dy: number): boolean {
  return Math.abs(dx) >= DRAG_THRESHOLD_PX || Math.abs(dy) >= DRAG_THRESHOLD_PX;
}
