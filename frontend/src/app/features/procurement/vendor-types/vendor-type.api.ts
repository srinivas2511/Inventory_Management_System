import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../../../core/config/app-config.service';

export interface VendorTypeDto {
  id: number;
  code: string;
  description: string;
  active: boolean;
  version: number;
}

export interface SaveVendorTypeRequest {
  code: string;
  description: string;
  active?: boolean;
  version?: number | null;
}

@Injectable({ providedIn: 'root' })
export class VendorTypeApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(AppConfigService).apiBaseUrl;

  list(): Observable<VendorTypeDto[]> {
    return this.http.get<VendorTypeDto[]>(`${this.base}/api/procurement/vendor-types`);
  }

  create(req: SaveVendorTypeRequest): Observable<VendorTypeDto> {
    return this.http.post<VendorTypeDto>(`${this.base}/api/procurement/vendor-types`, req);
  }

  update(id: number, req: SaveVendorTypeRequest): Observable<VendorTypeDto> {
    return this.http.put<VendorTypeDto>(`${this.base}/api/procurement/vendor-types/${id}`, req);
  }
}
