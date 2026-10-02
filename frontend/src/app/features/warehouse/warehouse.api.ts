import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppConfigService } from '../../core/config/app-config.service';

export interface WarehouseDto {
  id: number;
  warehouseCode: string;
  name: string;
  address?: string;
  active: boolean;
  version: number;
}

export interface WarehouseLocationDto {
  id: number;
  warehouseId: number;
  locationCode: string;
  name: string;
  locationType: string;
  active: boolean;
  version: number;
}

export interface WarehouseHealthDto {
  warehouseId: number;
  warehouseCode: string;
  missingLocationTypes: string[];
  healthy: boolean;
}

export interface SaveWarehouseRequest {
  warehouseCode: string;
  name: string;
  address?: string;
}

export interface SaveLocationRequest {
  locationCode: string;
  name: string;
  locationType: string;
}

@Injectable({ providedIn: 'root' })
export class WarehouseApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(AppConfigService).apiBaseUrl;

  list(): Observable<WarehouseDto[]> {
    return this.http.get<WarehouseDto[]>(`${this.base}/api/warehouses`);
  }

  search(q: string): Observable<WarehouseDto[]> {
    const params = q ? new HttpParams().set('q', q) : undefined;
    return this.http.get<WarehouseDto[]>(`${this.base}/api/warehouses`, { params });
  }

  getById(id: number): Observable<WarehouseDto> {
    return this.http.get<WarehouseDto>(`${this.base}/api/warehouses/${id}`);
  }

  create(req: SaveWarehouseRequest): Observable<WarehouseDto> {
    return this.http.post<WarehouseDto>(`${this.base}/api/warehouses`, req);
  }

  update(id: number, req: SaveWarehouseRequest): Observable<WarehouseDto> {
    return this.http.put<WarehouseDto>(`${this.base}/api/warehouses/${id}`, req);
  }

  deactivate(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/warehouses/${id}`);
  }

  getLocations(warehouseId: number): Observable<WarehouseLocationDto[]> {
    return this.http.get<WarehouseLocationDto[]>(`${this.base}/api/warehouses/${warehouseId}/locations`);
  }

  createLocation(warehouseId: number, req: SaveLocationRequest): Observable<WarehouseLocationDto> {
    return this.http.post<WarehouseLocationDto>(`${this.base}/api/warehouses/${warehouseId}/locations`, req);
  }

  updateLocation(warehouseId: number, locId: number, req: SaveLocationRequest): Observable<WarehouseLocationDto> {
    return this.http.put<WarehouseLocationDto>(`${this.base}/api/warehouses/${warehouseId}/locations/${locId}`, req);
  }

  deleteLocation(warehouseId: number, locId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/warehouses/${warehouseId}/locations/${locId}`);
  }

  health(): Observable<WarehouseHealthDto[]> {
    return this.http.get<WarehouseHealthDto[]>(`${this.base}/api/warehouses/health`);
  }
}
