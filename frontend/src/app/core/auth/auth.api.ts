import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';
import { LoginResponse, UserProfile } from './auth.models';

/**
 * Raw calls to {@code /api/auth/*}. The refresh token travels only in an HttpOnly cookie, so every call sends
 * credentials; nothing here reads or stores it.
 */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);

  login(username: string, password: string): Observable<LoginResponse> {
    return this.post('/auth/login', { username, password });
  }

  changePassword(username: string, currentPassword: string, newPassword: string): Observable<LoginResponse> {
    return this.post('/auth/change-password', { username, currentPassword, newPassword });
  }

  refresh(): Observable<LoginResponse> {
    return this.post('/auth/refresh', null);
  }

  logout(): Observable<void> {
    return this.post('/auth/logout', null);
  }

  forgotPassword(email: string): Observable<void> {
    return this.post('/auth/forgot-password', { email });
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.post('/auth/reset-password', { token, newPassword });
  }

  me(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${this.config.apiBaseUrl}/auth/me`, { withCredentials: true });
  }

  private post<T>(path: string, body: unknown): Observable<T> {
    return this.http.post<T>(`${this.config.apiBaseUrl}${path}`, body, { withCredentials: true });
  }
}
