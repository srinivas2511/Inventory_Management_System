import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';
import { PageResponse } from '../../../core/models/page.model';
import { ListParams } from '../../../shared/table/list-state';
import { PartnerRef, PartnerRequest, PartnerResponse, PartnerSummary } from '../models';

/** Suppliers and customers have the same API shape under different base paths. */
export class PartnersApi {
  constructor(
    private readonly api: ApiService,
    private readonly base: '/suppliers' | '/customers',
  ) {}

  list(params: ListParams): Observable<PageResponse<PartnerSummary>> {
    let query = new HttpParams().set('page', params.page).set('size', params.size).set('sort', params.sort);
    if (params.q) {
      query = query.set('q', params.q);
    }
    for (const [key, value] of Object.entries(params.filters)) {
      query = query.set(key, value);
    }
    return this.api.get<PageResponse<PartnerSummary>>(this.base, query);
  }

  get(id: number): Observable<PartnerResponse> {
    return this.api.get<PartnerResponse>(`${this.base}/${id}`);
  }

  create(request: PartnerRequest): Observable<PartnerResponse> {
    return this.api.post<PartnerResponse>(this.base, request);
  }

  update(id: number, request: PartnerRequest): Observable<PartnerResponse> {
    return this.api.put<PartnerResponse>(`${this.base}/${id}`, request);
  }

  activate(id: number): Observable<PartnerResponse> {
    return this.api.post<PartnerResponse>(`${this.base}/${id}/activate`, null);
  }

  deactivate(id: number, reason?: string): Observable<void> {
    return this.api.delete<void>(`${this.base}/${id}${reason ? `?reason=${encodeURIComponent(reason)}` : ''}`);
  }
}

export type { PartnerRef };
