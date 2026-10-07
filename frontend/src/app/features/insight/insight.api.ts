import { inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DashboardTrendsDto {
  production: { month: string; goodQty: number; rejectedQty: number }[];
  inventoryValue: { month: string; closingValue: number }[];
}

export interface DashboardKpiDto {
  inventory: { itemsWithStock: number; totalValueInr: number };
  production: {
    ordersInProgress: number;
    plannedQtyMonth: number;
    goodQtyMonth: number;
    yieldPct: number;
    monthlyTarget: number;
    targetAttainmentPct: number;
  };
  quality: { pendingInspections: number; passRateMonth: number };
  procurement: { openPOs: number; overduePOs: number };
  sales: { openOrders: number; dispatchedThisMonth: number };
  alerts: { critical: number; high: number; medium: number; low: number; total: number };
}

export interface AlertDto {
  id: number;
  alertType: string;
  severity: string;
  entityType: string;
  entityId: number;
  message: string;
  status: string;
  firstSeenAt: string;
  lastSeenAt: string;
  acknowledgedAt: string | null;
  resolvedAt: string | null;
}

export interface AlertCountDto {
  critical: number;
  high: number;
  medium: number;
  low: number;
  total: number;
}

export interface ReportMeta {
  code: string;
  name: string;
}

export interface ReportResult {
  reportCode: string;
  displayName: string;
  headers: string[];
  rows: Record<string, unknown>[];
  totalRows: number;
}

export interface TraceabilityDto {
  queryType: string;
  batchId: number;
  batchNumber: string;
  trail: { stepType: string; reference: string; description: string; timestamp: string }[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export class InsightApi {
  private http = inject(HttpClient);

  getDashboardKpis(): Observable<DashboardKpiDto> {
    return this.http.get<DashboardKpiDto>('/api/dashboard/kpis');
  }

  getDashboardTrends(months = 6): Observable<DashboardTrendsDto> {
    return this.http.get<DashboardTrendsDto>('/api/dashboard/trends', { params: { months } });
  }

  getAlertCount(): Observable<AlertCountDto> {
    return this.http.get<AlertCountDto>('/api/alerts/count');
  }

  searchAlerts(
    status?: string,
    severity?: string,
    alertType?: string,
    page = 0,
    size = 50,
  ): Observable<PageResponse<AlertDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (severity) params = params.set('severity', severity);
    if (alertType) params = params.set('alertType', alertType);
    return this.http.get<PageResponse<AlertDto>>('/api/alerts', { params });
  }

  acknowledgeAlert(id: number, note?: string): Observable<AlertDto> {
    let params = new HttpParams();
    if (note) params = params.set('note', note);
    return this.http.put<AlertDto>(`/api/alerts/${id}/acknowledge`, null, { params });
  }

  resolveAlert(id: number, note?: string): Observable<AlertDto> {
    let params = new HttpParams();
    if (note) params = params.set('note', note);
    return this.http.put<AlertDto>(`/api/alerts/${id}/resolve`, null, { params });
  }

  listReports(): Observable<ReportMeta[]> {
    return this.http.get<ReportMeta[]>('/api/reports');
  }

  runReport(code: string, from?: string, to?: string, status?: string, page = 0, size = 100): Observable<ReportResult> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    if (status) params = params.set('status', status);
    return this.http.get<ReportResult>(`/api/reports/${code}`, { params });
  }

  traceMaterialBatch(id: number): Observable<TraceabilityDto> {
    return this.http.get<TraceabilityDto>(`/api/traceability/material-batch/${id}`);
  }

  traceFgBatch(id: number): Observable<TraceabilityDto> {
    return this.http.get<TraceabilityDto>(`/api/traceability/fg-batch/${id}`);
  }
}
