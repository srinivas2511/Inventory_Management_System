import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../../../core/config/app-config.service';
import { Page } from '../material/material.api';

export interface CustomerDto {
  id: number;
  code: string;
  name: string;
  contactPerson?: string;
  email?: string;
  phone?: string;
  address?: string;
  gstNumber?: string;
  creditLimitDays?: number | null;
  active: boolean;
  version: number;
}

export interface SaveCustomerRequest {
  code: string;
  name: string;
  contactPerson?: string;
  email?: string;
  phone?: string;
  address?: string;
  gstNumber?: string;
  creditLimitDays?: number | null;
}

@Injectable({ providedIn: 'root' })
export class CustomerApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(AppConfigService).apiBaseUrl;

  search(q: string, active: boolean | null, page: number, size: number): Observable<Page<CustomerDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (active !== null) params = params.set('active', active);
    return this.http.get<Page<CustomerDto>>(`${this.base}/api/customers`, { params });
  }

  create(req: SaveCustomerRequest): Observable<CustomerDto> {
    return this.http.post<CustomerDto>(`${this.base}/api/customers`, req);
  }

  update(id: number, req: SaveCustomerRequest): Observable<CustomerDto> {
    return this.http.put<CustomerDto>(`${this.base}/api/customers/${id}`, req);
  }

  deactivate(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/customers/${id}`);
  }
}
