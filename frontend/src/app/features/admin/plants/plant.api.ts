import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Mirrors PlantDtos.PlantDto. */
export interface PlantDto {
  id: number;
  plantCode: string;
  name: string;
  companyId: number;
  address: string | null;
  active: boolean;
}

/** Mirrors PlantDtos.SavePlantRequest. */
export interface SavePlantRequest {
  plantCode: string;
  name: string;
  companyId?: number | null;
  address: string | null;
  active: boolean;
}

/** Mirrors PlantDtos.UserPlantsDto: an empty list (or allAccess) means the user sees every plant. */
export interface UserPlantsDto {
  userId: number;
  plantIds: number[];
  allAccess: boolean;
}

/** One overridable setting with the value in force and the level it comes from. */
export interface PlantSettingDto {
  key: string;
  value: string;
  source: 'PLANT' | 'COMPANY' | 'SYSTEM';
}

/** Mirrors PlantDtos.SaveSettingRequest: omit plantId to set the company default. */
export interface SaveSettingRequest {
  plantId?: number | null;
  key: string;
  value: string;
}

@Injectable({ providedIn: 'root' })
export class PlantApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/plants';

  list(): Observable<PlantDto[]> {
    return this.http.get<PlantDto[]>(this.base);
  }

  create(req: SavePlantRequest): Observable<PlantDto> {
    return this.http.post<PlantDto>(this.base, req);
  }

  update(id: number, req: SavePlantRequest): Observable<PlantDto> {
    return this.http.put<PlantDto>(`${this.base}/${id}`, req);
  }

  effectiveSettings(plantId: number): Observable<PlantSettingDto[]> {
    return this.http.get<PlantSettingDto[]>(`${this.base}/${plantId}/settings`);
  }

  companySettings(): Observable<PlantSettingDto[]> {
    return this.http.get<PlantSettingDto[]>(`${this.base}/company-settings`);
  }

  setSetting(req: SaveSettingRequest): Observable<void> {
    return this.http.put<void>(`${this.base}/settings`, req);
  }

  clearSetting(plantId: number | null, key: string): Observable<void> {
    let params = new HttpParams().set('key', key);
    if (plantId !== null) params = params.set('plantId', plantId);
    return this.http.delete<void>(`${this.base}/settings`, { params });
  }

  userPlants(userId: number): Observable<UserPlantsDto> {
    return this.http.get<UserPlantsDto>(`${this.base}/users/${userId}`);
  }

  assignUserPlants(userId: number, plantIds: number[]): Observable<UserPlantsDto> {
    return this.http.put<UserPlantsDto>(`${this.base}/users/${userId}`, { plantIds });
  }
}
