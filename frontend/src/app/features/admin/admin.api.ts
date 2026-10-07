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

export interface UserDto {
  id: number;
  username: string;
  employeeCode: string | null;
  fullName: string;
  email: string;
  phone: string | null;
  active: boolean;
  mustChangePassword: boolean;
  locked: boolean;
  lockedUntil: string | null;
  lastLoginAt: string | null;
  passwordChangedAt: string | null;
  roles: string[];
  version: number;
}

export interface AdminResetPasswordRequest {
  /** Leave out to have the server generate a one-time password. */
  temporaryPassword?: string;
  reason: string;
}

export interface AdminResetPasswordResponse {
  /** Present only when the server generated it, and only in this response. */
  temporaryPassword: string | null;
  generated: boolean;
}

export interface PermissionDto {
  id: number;
  code: string;
  module: string;
  description: string | null;
}

export interface RoleDto {
  id: number;
  code: string;
  name: string;
  description: string | null;
  systemRole: boolean;
  permissions: PermissionDto[];
}

export interface CreateUserRequest {
  username: string;
  employeeCode: string | null;
  fullName: string;
  email: string;
  phone: string | null;
  password: string;
  roleCodes?: string[];
}

export interface UpdateUserRequest {
  employeeCode: string | null;
  fullName: string;
  email: string;
  phone: string | null;
  version: number;
}

export interface AssignRolesRequest {
  roleCodes: string[];
}

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private http = inject(HttpClient);
  private base = '/api/admin';

  getUsers(q: string | null, active: boolean | null, page = 0, size = 20): Observable<Page<UserDto>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (active !== null) params = params.set('active', active);
    return this.http.get<Page<UserDto>>(`${this.base}/users`, { params });
  }

  createUser(req: CreateUserRequest): Observable<UserDto> {
    return this.http.post<UserDto>(`${this.base}/users`, req);
  }

  updateUser(id: number, req: UpdateUserRequest): Observable<UserDto> {
    return this.http.put<UserDto>(`${this.base}/users/${id}`, req);
  }

  assignRoles(id: number, req: AssignRolesRequest): Observable<UserDto> {
    return this.http.put<UserDto>(`${this.base}/users/${id}/roles`, req);
  }

  deactivateUser(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/users/${id}`);
  }

  reactivateUser(id: number): Observable<UserDto> {
    return this.http.post<UserDto>(`${this.base}/users/${id}/reactivate`, {});
  }

  resetPassword(id: number, req: AdminResetPasswordRequest): Observable<AdminResetPasswordResponse> {
    return this.http.post<AdminResetPasswordResponse>(`${this.base}/users/${id}/reset-password`, req);
  }

  unlockUser(id: number): Observable<UserDto> {
    return this.http.post<UserDto>(`${this.base}/users/${id}/unlock`, {});
  }

  getRoles(): Observable<RoleDto[]> {
    return this.http.get<RoleDto[]>(`${this.base}/roles`);
  }
}
