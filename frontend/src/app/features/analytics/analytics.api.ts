import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type ColumnType = 'TEXT' | 'DATE' | 'NUMBER' | 'MONEY' | 'PERCENT';

export interface AnalyticsColumn {
  name: string;
  label: string;
  type: ColumnType;
}

export interface QueryDescriptor {
  key: string;
  title: string;
  description: string;
  category: string;
  chart: 'TABLE' | 'BAR' | 'LINE';
  xColumn: string;
  yColumns: string[];
  monthly: boolean;
  columns: AnalyticsColumn[];
}

export type AnalyticsRow = Record<string, string | number | boolean | null>;

export interface QueryResult {
  key: string;
  title: string;
  from: string;
  to: string;
  columns: AnalyticsColumn[];
  rows: AnalyticsRow[];
  rowCount: number;
  truncated: boolean;
  generatedAt: string;
}

export interface RefreshResult {
  refreshed: string[];
  failed: Record<string, string>;
}

export interface SweepResult {
  raised: number;
  findings: string[];
  failedDetectors: string[];
}

export interface ProviderInfo {
  active: 'MANUAL' | 'AI';
  aiConfigured: boolean;
  aiEndpointHost: string | null;
}

export interface ForecastResult {
  provider: string;
  aiConfigured: boolean;
  proposed: number;
  created: number;
  updated: number;
  keptManual: number;
  rejected: number;
}

@Injectable({ providedIn: 'root' })
export class AnalyticsApi {
  private readonly http = inject(HttpClient);

  catalogue(): Observable<QueryDescriptor[]> {
    return this.http.get<QueryDescriptor[]>('/api/analytics/catalogue');
  }

  query(key: string, from: string | null, to: string | null, limit: number | null): Observable<QueryResult> {
    let params = new HttpParams().set('key', key);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    if (limit != null) params = params.set('limit', limit);
    return this.http.get<QueryResult>('/api/analytics/query', { params });
  }

  refresh(): Observable<RefreshResult> {
    return this.http.post<RefreshResult>('/api/analytics/refresh', {});
  }

  runAnomalySweep(): Observable<SweepResult> {
    return this.http.post<SweepResult>('/api/analytics/anomalies/run', {});
  }

  forecastProvider(): Observable<ProviderInfo> {
    return this.http.get<ProviderInfo>('/api/analytics/forecast/provider');
  }

  generateForecast(horizonDays: number | null): Observable<ForecastResult> {
    let params = new HttpParams();
    if (horizonDays != null) params = params.set('horizonDays', horizonDays);
    return this.http.post<ForecastResult>('/api/analytics/forecast/generate', {}, { params });
  }
}

/** A CSV cell that a spreadsheet would run as a formula is prefixed with an apostrophe. */
export function csvCell(value: unknown): string {
  if (value === null || value === undefined) return '';
  const text = String(value);
  const risky = typeof value !== 'number' && /^[=+\-@\t\r]/.test(text);
  const safe = risky ? `'${text}` : text;
  return /[",\n\r]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe;
}

export function toCsv(columns: AnalyticsColumn[], rows: AnalyticsRow[]): string {
  const header = columns.map((c) => csvCell(c.label)).join(',');
  const lines = rows.map((r) => columns.map((c) => csvCell(r[c.name])).join(','));
  return [header, ...lines].join('\n') + '\n';
}
