/** Local calendar dates as yyyy-MM-dd, the format the date inputs and the API both use. */
export function isoLocalDate(d: Date): string {
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${month}-${day}`;
}

export function todayIso(): string {
  return isoLocalDate(new Date());
}

export function inDaysIso(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return isoLocalDate(d);
}

/** Money is NUMERIC(18,4) on the server; compare at four decimals so float noise never flips a limit. */
export function toMinorUnits(value: number): number {
  return Math.round(value * 10000);
}
