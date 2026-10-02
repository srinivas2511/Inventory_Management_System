import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../../../core/config/app-config.service';

export interface MaterialDto {
  id: number;
  code: string;
  name: string;
  description?: string;
  uom: string;
  materialType: string;
  grade?: string;
  specificationStandard?: string;
  diameterMm?: number;
  minStock?: number;
  reorderLevel?: number;
  maxStock?: number;
  standardCost?: number;
  preferredSupplierId?: number;
  preferredSupplierName?: string;
  active: boolean;
  version: number;
}

export interface SaveMaterialRequest {
  code: string;
  name: string;
  description?: string;
  uom: string;
  materialType: string;
  grade?: string;
  specificationStandard?: string;
  diameterMm?: number | null;
  minStock?: number | null;
  reorderLevel?: number | null;
  maxStock?: number | null;
  standardCost?: number | null;
  preferredSupplierId?: number | null;
}

export interface UomDto {
  code: string;
  name: string;
  kind: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

@Injectable({ providedIn: 'root' })
export class MaterialApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(AppConfigService).apiBaseUrl;

  search(q: string, active: boolean | null, page: number, size: number): Observable<Page<MaterialDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (active !== null) params = params.set('active', active);
    return this.http.get<Page<MaterialDto>>(`${this.base}/api/materials`, { params });
  }

  getById(id: number): Observable<MaterialDto> {
    return this.http.get<MaterialDto>(`${this.base}/api/materials/${id}`);
  }

  create(req: SaveMaterialRequest): Observable<MaterialDto> {
    return this.http.post<MaterialDto>(`${this.base}/api/materials`, req);
  }

  update(id: number, req: SaveMaterialRequest): Observable<MaterialDto> {
    return this.http.put<MaterialDto>(`${this.base}/api/materials/${id}`, req);
  }

  deactivate(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/materials/${id}`);
  }

  listUoms(): Observable<UomDto[]> {
    return this.http.get<UomDto[]>(`${this.base}/api/materials/uoms`);
  }
}
