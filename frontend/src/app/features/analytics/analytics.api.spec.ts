import { AnalyticsColumn, AnalyticsRow, csvCell, toCsv } from './analytics.api';
import { windowStart } from './analytics-dashboards.component';

describe('csvCell', () => {
  it('defuses cells a spreadsheet would run as a formula', () => {
    expect(csvCell('=1+1')).toBe("'=1+1");
    expect(csvCell('+SUM(A1)')).toBe("'+SUM(A1)");
    expect(csvCell('-2+3')).toBe("'-2+3");
    expect(csvCell('@cmd')).toBe("'@cmd");
  });

  it('leaves real numbers and ordinary text alone', () => {
    expect(csvCell(-5)).toBe('-5');
    expect(csvCell(12.5)).toBe('12.5');
    expect(csvCell('Acme')).toBe('Acme');
    expect(csvCell(null)).toBe('');
    expect(csvCell(undefined)).toBe('');
  });

  it('quotes cells with commas, quotes or newlines', () => {
    expect(csvCell('a,b')).toBe('"a,b"');
    expect(csvCell('say "hi"')).toBe('"say ""hi"""');
    expect(csvCell('two\nlines')).toBe('"two\nlines"');
    expect(csvCell('=a,b')).toBe('"\'=a,b"');
  });
});

describe('toCsv', () => {
  const columns: AnalyticsColumn[] = [
    { name: 'name', label: 'Customer', type: 'TEXT' },
    { name: 'revenue', label: 'Revenue', type: 'MONEY' },
  ];
  const rows: AnalyticsRow[] = [
    { name: 'Acme, Inc', revenue: 500 },
    { name: '=HYPERLINK("x")', revenue: null },
  ];

  it('writes a header of labels and one line per row', () => {
    expect(toCsv(columns, rows)).toBe('Customer,Revenue\n"Acme, Inc",500\n"\'=HYPERLINK(""x"")",\n');
  });

  it('writes only the header for no rows', () => {
    expect(toCsv(columns, [])).toBe('Customer,Revenue\n');
  });
});

describe('windowStart', () => {
  it('is the first day of the month the given number of months back', () => {
    expect(windowStart(new Date(2026, 9, 15), 12)).toBe('2025-10-01');
    expect(windowStart(new Date(2026, 9, 15), 0)).toBe('2026-10-01');
    expect(windowStart(new Date(2026, 0, 31), 1)).toBe('2025-12-01');
  });
});
