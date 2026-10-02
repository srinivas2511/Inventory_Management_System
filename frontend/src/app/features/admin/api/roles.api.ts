import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';
import { PermissionResponse, RoleResponse, RoleSummary } from '../models';

/** Typed client of {@code /api/roles} and the read-only {@code /api/permissions} catalogue. */
@Injectable({ providedIn: 'root' })
export class RolesApi {
  private readonly api = inject(ApiService);

  list(): Observable<RoleSummary[]> {
    return this.api.get<RoleSummary[]>('/roles');
  }

  get(id: number): Observable<RoleResponse> {
    return this.api.get<RoleResponse>(`/roles/${id}`);
  }

  create(code: string, name: string, description: string | undefined): Observable<RoleResponse> {
    return this.api.post<RoleResponse>('/roles', {
      code,
      name,
      description: description || undefined,
      permissions: [],
    });
  }

  update(id: number, name: string, description: string | undefined, version: number): Observable<RoleResponse> {
    return this.api.put<RoleResponse>(`/roles/${id}`, { name, description: description || undefined, version });
  }

  setPermissions(id: number, permissions: string[], reason?: string): Observable<RoleResponse> {
    return this.api.put<RoleResponse>(`/roles/${id}/permissions`, { permissions, reason: reason || undefined });
  }

  delete(id: number): Observable<void> {
    return this.api.delete<void>(`/roles/${id}`);
  }

  permissions(): Observable<PermissionResponse[]> {
    return this.api.get<PermissionResponse[]>('/permissions');
  }
}
