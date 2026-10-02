import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../../../core/config/app-config.service';
import { Page } from '../material/material.api';

export interface SupplierDto {
  id: number;
  code: string;
  name: string;
  contactPerson?: string;
  email?: string;
  phone?: string;
  address?: string;
  gstNumber?: string;
  paymentTermsDays?: number | null;
  active: boolean;
  version: number;
}

export interface SaveSupplierRequest {
  code: string;
  name: string;
  contactPerson?: string;
  email?: string;
  phone?: string;
  address?: string;
  gstNumber?: string;
  paymentTermsDays?: number | null;
}

@Injectable({ providedIn: 'root' })
export class SupplierApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(AppConfigService).apiBaseUrl;

  search(q: string, active: boolean | null, page: number, size: number): Observable<Page<SupplierDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (active !== null) params = params.set('active', active);
    return this.http.get<Page<SupplierDto>>(`${this.base}/api/suppliers`, { params });
  }

  create(req: SaveSupplierRequest): Observable<SupplierDto> {
    return this.http.post<SupplierDto>(`${this.base}/api/suppliers`, req);
  }

  update(id: number, req: SaveSupplierRequest): Observable<SupplierDto> {
    return this.http.put<SupplierDto>(`${this.base}/api/suppliers/${id}`, req);
  }

  deactivate(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/suppliers/${id}`);
  }
}
