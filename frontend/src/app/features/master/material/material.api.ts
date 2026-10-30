import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../../../core/config/app-config.service';

export interface MaterialDto {
  id: number;
  materialCode: string;
  name: string;
  description?: string;
  uom: string;
  materialType: string;
  grade?: string;
  diameterMm?: number;
  minStock?: number;
  reorderLevel?: number;
  maxStock?: number;
  standardCost?: number;
  preferredSupplierId?: number;
  active: boolean;
  version: number;
}

export interface SaveMaterialRequest {
  materialCode: string;
  name: string;
  description?: string;
  uom: string;
  materialType: string;
  grade?: string;
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

export interface UomConversionDto {
  id: number;
  materialId: number;
  baseUom: string;
  altUom: string;
  factorToBase: number;
}

export interface PurchasingInfoDto {
  materialId: number;
  purchaseUom?: string;
  purchaseTaxCode?: string;
  preferredSupplierId?: number;
  preferredSupplierName?: string;
}

export interface SavePurchasingInfoRequest {
  purchaseUom?: string | null;
  purchaseTaxCode?: string | null;
  preferredSupplierId?: number | null;
}

export interface ItemVendorMappingDto {
  id: number;
  itemId: number;
  supplierId: number;
  supplierName: string;
  supplierItemCode: string | null;
  supplierItemName: string | null;
  lastPrice: number | null;
  moq: number;
  leadTimeDays: number;
  preferred: boolean;
}

export interface SaveVendorMappingRequest {
  supplierId: number;
  supplierItemCode?: string | null;
  supplierItemName?: string | null;
  lastPrice?: number | null;
  moq?: number;
  leadTimeDays?: number;
  preferred?: boolean;
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

  getUomConversions(materialId: number): Observable<UomConversionDto[]> {
    return this.http.get<UomConversionDto[]>(`${this.base}/api/materials/${materialId}/uom-conversions`);
  }

  addUomConversion(materialId: number, altUom: string, factorToBase: number): Observable<UomConversionDto> {
    return this.http.put<UomConversionDto>(`${this.base}/api/materials/${materialId}/uom-conversions`, {
      altUom,
      factorToBase,
    });
  }

  deleteUomConversion(materialId: number, altUom: string): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/api/materials/${materialId}/uom-conversions/${encodeURIComponent(altUom)}`,
    );
  }

  getPurchasingInfo(materialId: number): Observable<PurchasingInfoDto> {
    return this.http.get<PurchasingInfoDto>(`${this.base}/api/materials/${materialId}/purchasing-info`);
  }

  savePurchasingInfo(materialId: number, req: SavePurchasingInfoRequest): Observable<PurchasingInfoDto> {
    return this.http.put<PurchasingInfoDto>(`${this.base}/api/materials/${materialId}/purchasing-info`, req);
  }

  listVendorMappings(materialId: number): Observable<ItemVendorMappingDto[]> {
    return this.http.get<ItemVendorMappingDto[]>(`${this.base}/api/materials/${materialId}/vendor-mappings`);
  }

  addVendorMapping(materialId: number, req: SaveVendorMappingRequest): Observable<ItemVendorMappingDto> {
    return this.http.post<ItemVendorMappingDto>(`${this.base}/api/materials/${materialId}/vendor-mappings`, req);
  }

  deleteVendorMapping(materialId: number, mappingId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/materials/${materialId}/vendor-mappings/${mappingId}`);
  }
}
