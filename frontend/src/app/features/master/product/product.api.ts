import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../../../core/config/app-config.service';
import { Page } from '../material/material.api';

export type SpringType =
  | 'COMPRESSION'
  | 'EXTENSION'
  | 'TORSION'
  | 'CONICAL'
  | 'DISC_BELLEVILLE'
  | 'WIRE_FORM'
  | 'CUSTOM';

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

  search(q: string, springType: SpringType | null, status: ProductStatus | null, page: number, size: number): Observable<Page<ProductDto>> {
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
    return this.http.post<ProductDto>(`${this.base}/api/products/${id}/obsolete`, {});
  }

  getAttributeDefinitions(springType: SpringType): Observable<SpringAttributeDefinitionDto[]> {
    return this.http.get<SpringAttributeDefinitionDto[]>(
      `${this.base}/api/products/attribute-definitions`,
      { params: new HttpParams().set('springType', springType) },
    );
  }
}
