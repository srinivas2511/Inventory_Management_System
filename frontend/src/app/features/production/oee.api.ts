import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Ratios are fractions (0.8523 is 85.23%); times are minutes. */
export interface OeeDto {
  machineId: number | null;
  machineCode: string;
  machineName: string;
  from: string;
  to: string;
  availableMinutes: number;
  plannedDowntimeMinutes: number;
  unplannedDowntimeMinutes: number;
  operatingMinutes: number;
  idealMinutes: number;
  goodQty: number;
  badQty: number;
  availability: number;
  performance: number;
  quality: number;
  oee: number;
}

export interface OeeTrendPointDto {
  day: string;
  availableMinutes: number;
  operatingMinutes: number;
  goodQty: number;
  badQty: number;
  availability: number;
  performance: number;
  quality: number;
  oee: number;
}

export interface OeeLossDto {
  category: string;
  label: string;
  minutes: number;
  share: number;
  cumulativeShare: number;
}

export interface OeeLossesDto {
  from: string;
  to: string;
  totalLossMinutes: number;
  losses: OeeLossDto[];
}

export interface DowntimeReasonDto {
  reason: string;
  category: string;
  occurrences: number;
  minutes: number;
}

export interface RecordDowntimeBody {
  machineId: number;
  reason: string;
  category: string | null;
  startedAt: string | null;
  endedAt: string | null;
}

export const DOWNTIME_CATEGORIES: { value: string; label: string }[] = [
  { value: 'BREAKDOWN', label: 'Breakdown' },
  { value: 'SETUP_CHANGEOVER', label: 'Setup and changeover' },
  { value: 'MATERIAL_SHORTAGE', label: 'Material shortage' },
  { value: 'NO_OPERATOR', label: 'No operator' },
  { value: 'MINOR_STOP', label: 'Minor stop' },
  { value: 'PLANNED_MAINTENANCE', label: 'Planned maintenance' },
  { value: 'OTHER', label: 'Other' },
];

@Injectable({ providedIn: 'root' })
export class OeeApi {
  private http = inject(HttpClient);
  private base = '/api/oee';

  private range(from?: string | null, to?: string | null, machineId?: number | null): HttpParams {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    if (machineId) params = params.set('machineId', machineId);
    return params;
  }

  summary(from?: string | null, to?: string | null): Observable<OeeDto> {
    return this.http.get<OeeDto>(`${this.base}/summary`, { params: this.range(from, to) });
  }

  machines(from?: string | null, to?: string | null): Observable<OeeDto[]> {
    return this.http.get<OeeDto[]>(`${this.base}/machines`, { params: this.range(from, to) });
  }

  trend(from?: string | null, to?: string | null, machineId?: number | null): Observable<OeeTrendPointDto[]> {
    return this.http.get<OeeTrendPointDto[]>(`${this.base}/trend`, { params: this.range(from, to, machineId) });
  }

  losses(from?: string | null, to?: string | null, machineId?: number | null): Observable<OeeLossesDto> {
    return this.http.get<OeeLossesDto>(`${this.base}/losses`, { params: this.range(from, to, machineId) });
  }

  downtimeReasons(
    from?: string | null,
    to?: string | null,
    machineId?: number | null,
    limit = 5,
  ): Observable<DowntimeReasonDto[]> {
    return this.http.get<DowntimeReasonDto[]>(`${this.base}/downtime-reasons`, {
      params: this.range(from, to, machineId).set('limit', limit),
    });
  }

  recordDowntime(body: RecordDowntimeBody): Observable<unknown> {
    return this.http.post('/api/production/downtime', body);
  }
}
