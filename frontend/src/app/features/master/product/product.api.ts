import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { AppConfigService } from '../../../core/config/app-config.service';
import { Page } from '../material/material.api';

/** A product type code from the product_types lookup: the spring types plus any enabled industry vertical's types. */
export type SpringType = string;

export interface ProductTypeDto {
  id: number;
  code: string;
  name: string;
  industry: string;
  displayOrder: number;
}

/** The attribute definition exactly as the backend returns it (GET /api/products/attributes/{type}). */
export interface BackendAttributeDefinitionDto {
  id: number;
  springType: string;
  attributeCode: string;
  label: string;
  dataType: string;
  unit?: string | null;
  required: boolean;
  displayOrder: number;
}

export function toAttributeDefinition(d: BackendAttributeDefinitionDto): SpringAttributeDefinitionDto {
  return {
    id: d.id,
    springType: d.springType,
    attributeKey: d.attributeCode,
    displayName: d.label,
    dataType: d.dataType === 'NUMBER' ? 'DECIMAL' : d.dataType,
    unit: d.unit ?? undefined,
    required: d.required,
    displayOrder: d.displayOrder,
  };
}

export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'OBSOLETE';

export interface SpringAttributeDefinitionDto {
  id: number;
  springType: SpringType;
  attributeKey: string;
  displayName: string;
  dataType: string;
  unit?: string;
  required: boolean;
  displayOrder: number;
}

export interface SpringSpecificationDto {
  wireDiameterMm?: number | null;
  outerDiameterMm?: number | null;
  freeLengthMm?: number | null;
  totalCoils?: number | null;
  activeCoils?: number | null;
  attributes?: Record<string, unknown>;
}

export interface ProductDto {
  id: number;
  code: string;
  name: string;
  description?: string;
  springType: SpringType;
  status: ProductStatus;
  drawingNumber?: string;
  drawingRevision?: string;
  heatTreatment?: string;
  surfaceTreatment?: string;
  customerId?: number;
  customerName?: string;
  specification?: SpringSpecificationDto;
  active: boolean;
  version: number;
}

export interface SaveProductRequest {
  code: string;
  name: string;
  description?: string;
  springType: SpringType;
  drawingNumber?: string;
  drawingRevision?: string;
  heatTreatment?: string;
  surfaceTreatment?: string;
  customerId?: number;
  specification?: SpringSpecificationDto;
}

@Injectable({ providedIn: 'root' })
export class ProductApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(AppConfigService).apiBaseUrl;

  search(
    q: string,
    springType: SpringType | null,
    status: ProductStatus | null,
    page: number,
    size: number,
  ): Observable<Page<ProductDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (springType) params = params.set('springType', springType);
    if (status) params = params.set('status', status);
    return this.http.get<Page<ProductDto>>(`${this.base}/api/products`, { params });
  }

  create(req: SaveProductRequest): Observable<ProductDto> {
    return this.http.post<ProductDto>(`${this.base}/api/products`, req);
  }

  update(id: number, req: SaveProductRequest): Observable<ProductDto> {
    return this.http.put<ProductDto>(`${this.base}/api/products/${id}`, req);
  }

  activate(id: number): Observable<ProductDto> {
    return this.http.post<ProductDto>(`${this.base}/api/products/${id}/activate`, {});
  }

  obsolete(id: number): Observable<ProductDto> {
    return this.http.delete<ProductDto>(`${this.base}/api/products/${id}`);
  }

  listProductTypes(): Observable<ProductTypeDto[]> {
    return this.http.get<ProductTypeDto[]>(`${this.base}/api/product-types`);
  }

  getAttributeDefinitions(springType: SpringType): Observable<SpringAttributeDefinitionDto[]> {
    return this.http
      .get<BackendAttributeDefinitionDto[]>(`${this.base}/api/products/attributes/${encodeURIComponent(springType)}`)
      .pipe(map((defs) => defs.map(toAttributeDefinition)));
  }
}
