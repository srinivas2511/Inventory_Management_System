import { HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../../../core/api/api.service';
import { PageResponse } from '../../../core/models/page.model';
import { CreateUserRequest, UpdateUserRequest, UserListQuery, UserResponse, UserSummary } from '../models';

/** Typed client of {@code /api/users}. Deactivation is a DELETE: users are never removed. */
@Injectable({ providedIn: 'root' })
export class UsersApi {
  private readonly api = inject(ApiService);

  list(query: UserListQuery): Observable<PageResponse<UserSummary>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size).set('sort', query.sort);
    if (query.q?.trim()) {
      params = params.set('q', query.q.trim());
    }
    if (query.role) {
      params = params.set('role', query.role);
    }
    if (query.active !== null && query.active !== undefined) {
      params = params.set('active', query.active);
    }
    return this.api.get<PageResponse<UserSummary>>('/users', params);
  }

  get(id: number): Observable<UserResponse> {
    return this.api.get<UserResponse>(`/users/${id}`);
  }

  create(request: CreateUserRequest): Observable<UserResponse> {
    return this.api.post<UserResponse>('/users', request);
  }

  update(id: number, request: UpdateUserRequest): Observable<UserResponse> {
    return this.api.put<UserResponse>(`/users/${id}`, request);
  }

  setRoles(id: number, roles: string[], reason?: string): Observable<UserResponse> {
    return this.api.put<UserResponse>(`/users/${id}/roles`, { roles, reason: reason || undefined });
  }

  resetPassword(id: number, temporaryPassword: string, reason?: string): Observable<void> {
    return this.api.post<void>(`/users/${id}/reset-password`, { temporaryPassword, reason: reason || undefined });
  }

  activate(id: number): Observable<UserResponse> {
    return this.api.post<UserResponse>(`/users/${id}/activate`, null);
  }

  deactivate(id: number, reason?: string): Observable<void> {
    const query = reason ? `?reason=${encodeURIComponent(reason)}` : '';
    return this.api.delete<void>(`/users/${id}${query}`);
  }
}
