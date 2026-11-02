import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../../../core/config/app-config.service';

export interface WorkCentreDto {
  id: number;
  code: string;
  name: string;
  description?: string;
  active: boolean;
  version: number;
}

export interface SaveWorkCentreRequest {
  code: string;
  name: string;
  description?: string | null;
  active?: boolean;
  version?: number | null;
}

@Injectable({ providedIn: 'root' })
export class WorkCentreApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(AppConfigService).apiBaseUrl;

  list(): Observable<WorkCentreDto[]> {
    return this.http.get<WorkCentreDto[]>(`${this.base}/api/masterdata/work-centres`);
  }

  create(req: SaveWorkCentreRequest): Observable<WorkCentreDto> {
    return this.http.post<WorkCentreDto>(`${this.base}/api/masterdata/work-centres`, req);
  }

  update(id: number, req: SaveWorkCentreRequest): Observable<WorkCentreDto> {
    return this.http.put<WorkCentreDto>(`${this.base}/api/masterdata/work-centres/${id}`, req);
  }
}
