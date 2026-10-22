import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../../../core/config/app-config.service';
import { Page } from '../material/material.api';

export interface ApprovedProductDto {
  id: number;
  code: string;
  name: string;
}

export interface CustomerSpecOverrideDto {
  id: number;
  customerId: number;
  productId: number;
  parameterName: string;
  lowerLimit?: number;
  upperLimit?: number;
  nominal?: number;
  active: boolean;
}

export interface SaveSpecOverrideRequest {
  parameterName: string;
  lowerLimit?: number | null;
  upperLimit?: number | null;
  nominal?: number | null;
}

export interface CustomerDto {
  id: number;
  customerCode: string;
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

export interface SaveCustomerRequest {
  customerCode: string;
  name: string;
  contactPerson?: string;
  email?: string;
  phone?: string;
  address?: string;
  gstNumber?: string;
  paymentTerms?: string | null;
}

@Injectable({ providedIn: 'root' })
export class CustomerApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(AppConfigService).apiBaseUrl;

  search(q: string, active: boolean | null, page: number, size: number): Observable<Page<CustomerDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (active !== null) params = params.set('status', active ? 'ACTIVE' : 'INACTIVE');
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

  getApprovedProducts(customerId: number): Observable<ApprovedProductDto[]> {
    return this.http.get<ApprovedProductDto[]>(`${this.base}/api/customers/${customerId}/approved-products`);
  }

  setApprovedProducts(customerId: number, ids: number[]): Observable<void> {
    return this.http.put<void>(`${this.base}/api/customers/${customerId}/approved-products`, { ids });
  }

  getProductSpecOverrides(customerId: number, productId: number): Observable<CustomerSpecOverrideDto[]> {
    return this.http.get<CustomerSpecOverrideDto[]>(
      `${this.base}/api/customers/${customerId}/product-specs/${productId}/overrides`,
    );
  }

  saveProductSpecOverride(
    customerId: number,
    productId: number,
    req: SaveSpecOverrideRequest,
  ): Observable<CustomerSpecOverrideDto> {
    return this.http.put<CustomerSpecOverrideDto>(
      `${this.base}/api/customers/${customerId}/product-specs/${productId}/overrides`,
      req,
    );
  }

  deleteProductSpecOverride(customerId: number, productId: number, parameterName: string): Observable<void> {
    const params = new HttpParams().set('parameterName', parameterName);
    return this.http.delete<void>(`${this.base}/api/customers/${customerId}/product-specs/${productId}/overrides`, {
      params,
    });
  }
}
