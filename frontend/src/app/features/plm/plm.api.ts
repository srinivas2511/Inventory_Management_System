import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export type EcoStatus = 'DRAFT' | 'REVIEW' | 'APPROVED' | 'IMPLEMENTING' | 'IMPLEMENTED' | 'CANCELLED';
export type EcoChangeCategory = 'BOM_CHANGE' | 'ROUTING_CHANGE' | 'SPEC_CHANGE' | 'DRAWING_CHANGE';
export type EcoUrgency = 'CRITICAL' | 'HIGH' | 'NORMAL' | 'LOW';
export type EcoItemType = 'PRODUCT' | 'BOM' | 'ROUTING';
export type ProductLifecycleStatus = 'DEVELOPMENT' | 'PROTOTYPE' | 'APPROVED' | 'ACTIVE' | 'PHASE_OUT' | 'OBSOLETE';

export const ECO_STATUSES: EcoStatus[] = ['DRAFT', 'REVIEW', 'APPROVED', 'IMPLEMENTING', 'IMPLEMENTED', 'CANCELLED'];
export const ECO_CATEGORIES: EcoChangeCategory[] = ['BOM_CHANGE', 'ROUTING_CHANGE', 'SPEC_CHANGE', 'DRAWING_CHANGE'];
export const ECO_URGENCIES: EcoUrgency[] = ['CRITICAL', 'HIGH', 'NORMAL', 'LOW'];
export const ECO_ITEM_TYPES: EcoItemType[] = ['BOM', 'ROUTING', 'PRODUCT'];
export const LIFECYCLE_STATUSES: ProductLifecycleStatus[] = [
  'DEVELOPMENT',
  'PROTOTYPE',
  'APPROVED',
  'ACTIVE',
  'PHASE_OUT',
  'OBSOLETE',
];

export interface EcoSummaryDto {
  id: number;
  ecoNumber: string;
  title: string;
  changeCategory: EcoChangeCategory;
  urgency: EcoUrgency;
  status: EcoStatus;
  effectivityDate: string | null;
  initiatedBy: number;
  itemCount: number;
  createdAt: string;
}

export interface EcoItemDto {
  id: number;
  itemType: EcoItemType;
  productId: number;
  productCode: string | null;
  productName: string | null;
  oldBomId: number | null;
  oldBomLabel: string | null;
  newBomId: number | null;
  newBomLabel: string | null;
  changeDescription: string;
}

export interface EcoDto {
  id: number;
  ecoNumber: string;
  title: string;
  description: string | null;
  reason: string;
  changeCategory: EcoChangeCategory;
  urgency: EcoUrgency;
  effectivityDate: string | null;
  impactAssessment: string | null;
  status: EcoStatus;
  initiatedBy: number;
  approvedBy: number | null;
  approvedAt: string | null;
  implementedAt: string | null;
  statusReason: string | null;
  createdAt: string;
  items: EcoItemDto[];
  allowedActions: string[];
}

export interface SaveEcoItemRequest {
  itemType: EcoItemType;
  productId: number;
  changeDescription: string;
}

export interface SaveEcoRequest {
  title: string;
  description: string | null;
  reason: string;
  changeCategory: EcoChangeCategory;
  urgency: EcoUrgency;
  effectivityDate: string | null;
  impactAssessment: string | null;
  items: SaveEcoItemRequest[];
}

export interface ProductLifecycleRowDto {
  productId: number;
  productCode: string;
  productName: string;
  lifecycleStatus: ProductLifecycleStatus;
  nextStatus: ProductLifecycleStatus | null;
}

export interface LifecycleEventDto {
  id: number;
  productId: number;
  fromStatus: string | null;
  toStatus: string;
  ecoId: number | null;
  changedBy: number | null;
  changedAt: string;
  reason: string;
}

export interface ProductLifecycleDto {
  productId: number;
  productCode: string;
  productName: string;
  lifecycleStatus: ProductLifecycleStatus;
  nextStatus: ProductLifecycleStatus | null;
  history: LifecycleEventDto[];
}

@Injectable({ providedIn: 'root' })
export class PlmApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api';

  listEcos(status: EcoStatus | null, q: string | null, page = 0, size = 50): Observable<Page<EcoSummaryDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (q) params = params.set('q', q);
    return this.http.get<Page<EcoSummaryDto>>(`${this.base}/ecos`, { params });
  }

  getEco(id: number): Observable<EcoDto> {
    return this.http.get<EcoDto>(`${this.base}/ecos/${id}`);
  }

  createEco(req: SaveEcoRequest): Observable<EcoDto> {
    return this.http.post<EcoDto>(`${this.base}/ecos`, req);
  }

  updateEco(id: number, req: SaveEcoRequest): Observable<EcoDto> {
    return this.http.put<EcoDto>(`${this.base}/ecos/${id}`, req);
  }

  submitEco(id: number): Observable<EcoDto> {
    return this.http.post<EcoDto>(`${this.base}/ecos/${id}/submit`, {});
  }

  approveEco(id: number): Observable<EcoDto> {
    return this.http.post<EcoDto>(`${this.base}/ecos/${id}/approve`, {});
  }

  returnEco(id: number, reason: string): Observable<EcoDto> {
    return this.http.post<EcoDto>(`${this.base}/ecos/${id}/return`, { reason });
  }

  cancelEco(id: number, reason: string): Observable<EcoDto> {
    return this.http.post<EcoDto>(`${this.base}/ecos/${id}/cancel`, { reason });
  }

  implementEco(id: number): Observable<EcoDto> {
    return this.http.post<EcoDto>(`${this.base}/ecos/${id}/implement`, {});
  }

  listLifecycle(
    status: ProductLifecycleStatus | null,
    q: string | null,
    page = 0,
    size = 50,
  ): Observable<Page<ProductLifecycleRowDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (q) params = params.set('q', q);
    return this.http.get<Page<ProductLifecycleRowDto>>(`${this.base}/product-lifecycle`, { params });
  }

  getLifecycle(productId: number): Observable<ProductLifecycleDto> {
    return this.http.get<ProductLifecycleDto>(`${this.base}/product-lifecycle/${productId}`);
  }

  transitionLifecycle(
    productId: number,
    target: ProductLifecycleStatus,
    reason: string,
  ): Observable<ProductLifecycleDto> {
    return this.http.post<ProductLifecycleDto>(`${this.base}/product-lifecycle/${productId}/transition`, {
      target,
      reason,
    });
  }
}
