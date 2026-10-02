import { HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';
import { PageResponse } from '../../../core/models/page.model';
import { ListParams } from '../../../shared/table/list-state';
import { MaterialRequest, MaterialResponse, MaterialSummary, Uom } from '../models';

/** Typed client of {@code /api/materials} and {@code /api/uoms}. */
@Injectable({ providedIn: 'root' })
export class MaterialsApi {
  private readonly api = inject(ApiService);

  list(params: ListParams): Observable<PageResponse<MaterialSummary>> {
    let query = new HttpParams().set('page', params.page).set('size', params.size).set('sort', params.sort);
    if (params.q) {
      query = query.set('q', params.q);
    }
    for (const [key, value] of Object.entries(params.filters)) {
      query = query.set(key, value);
    }
    return this.api.get<PageResponse<MaterialSummary>>('/materials', query);
  }

  get(id: number): Observable<MaterialResponse> {
    return this.api.get<MaterialResponse>(`/materials/${id}`);
  }

  create(request: MaterialRequest): Observable<MaterialResponse> {
    return this.api.post<MaterialResponse>('/materials', request);
  }

  update(id: number, request: MaterialRequest): Observable<MaterialResponse> {
    return this.api.put<MaterialResponse>(`/materials/${id}`, request);
  }

  activate(id: number): Observable<MaterialResponse> {
    return this.api.post<MaterialResponse>(`/materials/${id}/activate`, null);
  }

  /** Without {@code force} the server answers 422 DEACTIVATION_BLOCKED while the material is still in use. */
  deactivate(id: number, force: boolean, reason?: string): Observable<void> {
    let query = `?force=${force}`;
    if (reason) {
      query += `&reason=${encodeURIComponent(reason)}`;
    }
    return this.api.delete<void>(`/materials/${id}${query}`);
  }

  uoms(): Observable<Uom[]> {
    return this.api.get<Uom[]>('/uoms');
  }
}
