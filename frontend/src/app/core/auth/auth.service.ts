import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, catchError, throwError } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';
import { TokenStore } from './token.store';
import { SessionStore, SessionUser } from './session.store';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  user: SessionUser;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(AppConfigService);
  private readonly tokenStore = inject(TokenStore);
  private readonly sessionStore = inject(SessionStore);
  private readonly router = inject(Router);

  private get base(): string {
    return `${this.config.apiBaseUrl}/api/auth`;
  }

  login(req: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.base}/login`, req).pipe(
      tap((res) => {
        this.tokenStore.set(res.accessToken);
        this.saveRefreshToken(res.refreshToken);
        this.sessionStore.setUser(res.user);
      }),
    );
  }

  refresh(): Observable<LoginResponse> {
    const refreshToken = this.loadRefreshToken();
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token'));
    }
    return this.http.post<LoginResponse>(`${this.base}/refresh`, { refreshToken }).pipe(
      tap((res) => {
        this.tokenStore.set(res.accessToken);
        this.saveRefreshToken(res.refreshToken);
        this.sessionStore.setUser(res.user);
      }),
      catchError((err) => {
        this.logout();
        return throwError(() => err);
      }),
    );
  }

  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>(`${this.base}/forgot-password`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.base}/reset-password`, { token, newPassword });
  }

  logout(): void {
    this.tokenStore.clear();
    this.clearRefreshToken();
    this.sessionStore.clear();
    this.router.navigate(['/login']);
  }

  loadRefreshToken(): string | null {
    try {
      return localStorage.getItem('ims_rt');
    } catch {
      return null;
    }
  }

  private saveRefreshToken(token: string): void {
    try {
      localStorage.setItem('ims_rt', token);
    } catch {
      // private browsing — token is lost on page close, user must log in again
    }
  }

  private clearRefreshToken(): void {
    try {
      localStorage.removeItem('ims_rt');
    } catch {
      // ignore
    }
  }
}
