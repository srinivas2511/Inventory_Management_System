import { HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';
import { PageResponse } from '../../../core/models/page.model';
import { ListParams } from '../../../shared/table/list-state';
import { AttributeDefinition, ProductResponse, ProductSummary, SpringType, SpringTypeInfo } from '../models';

/** Typed client of {@code /api/products} and the attribute catalogue under {@code /api/spring-types}. */
@Injectable({ providedIn: 'root' })
export class ProductsApi {
  private readonly api = inject(ApiService);

  list(params: ListParams): Observable<PageResponse<ProductSummary>> {
    let query = new HttpParams().set('page', params.page).set('size', params.size).set('sort', params.sort);
    if (params.q) {
      query = query.set('q', params.q);
    }
    for (const [key, value] of Object.entries(params.filters)) {
      query = query.set(key, value);
    }
    return this.api.get<PageResponse<ProductSummary>>('/products', query);
  }

  get(id: number): Observable<ProductResponse> {
    return this.api.get<ProductResponse>(`/products/${id}`);
  }

  create(request: Record<string, unknown>): Observable<ProductResponse> {
    return this.api.post<ProductResponse>('/products', request);
  }

  update(id: number, request: Record<string, unknown>): Observable<ProductResponse> {
    return this.api.put<ProductResponse>(`/products/${id}`, request);
  }

  activate(id: number): Observable<ProductResponse> {
    return this.api.post<ProductResponse>(`/products/${id}/activate`, null);
  }

  obsolete(id: number, reason?: string): Observable<void> {
    return this.api.delete<void>(`/products/${id}${reason ? `?reason=${encodeURIComponent(reason)}` : ''}`);
  }

  springTypes(): Observable<SpringTypeInfo[]> {
    return this.api.get<SpringTypeInfo[]>('/spring-types');
  }

  attributes(type: SpringType): Observable<AttributeDefinition[]> {
    return this.api.get<AttributeDefinition[]>(`/spring-types/${type}/attributes`);
  }
}
