import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../../../core/config/app-config.service';
import { Page } from '../material/material.api';

export interface ApprovedMaterialDto {
  id: number;
  code: string;
  name: string;
}

export interface SupplierDto {
  id: number;
  supplierCode: string;
  name: string;
  contactPerson?: string;
  email?: string;
  phone?: string;
  address?: string;
  gstNumber?: string;
  paymentTerms?: string;
  status: string;
  version: number;
}

export interface SaveSupplierRequest {
  supplierCode: string;
  name: string;
  contactPerson?: string;
  email?: string;
  phone?: string;
  address?: string;
  gstNumber?: string;
  paymentTerms?: string | null;
}

@Injectable({ providedIn: 'root' })
export class SupplierApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(AppConfigService).apiBaseUrl;

  search(q: string, active: boolean | null, page: number, size: number): Observable<Page<SupplierDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (active !== null) params = params.set('status', active ? 'ACTIVE' : 'INACTIVE');
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

  getApprovedMaterials(supplierId: number): Observable<ApprovedMaterialDto[]> {
    return this.http.get<ApprovedMaterialDto[]>(`${this.base}/api/suppliers/${supplierId}/approved-materials`);
  }

  setApprovedMaterials(supplierId: number, ids: number[]): Observable<void> {
    return this.http.put<void>(`${this.base}/api/suppliers/${supplierId}/approved-materials`, { ids });
  }
}
