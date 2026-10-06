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

/** The backend returns the refresh token in an HttpOnly cookie, never in the body. */
export interface LoginResponse {
  accessToken: string;
  expiresIn: number;
  mustChangePassword: boolean;
  user: Omit<SessionUser, 'email' | 'mustChangePassword'>;
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
        this.sessionStore.setUser({ ...res.user, mustChangePassword: res.mustChangePassword });
      }),
    );
  }

  refresh(): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.base}/refresh`, {}).pipe(
      tap((res) => {
        this.tokenStore.set(res.accessToken);
        this.sessionStore.setUser({ ...res.user, mustChangePassword: res.mustChangePassword });
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
    this.sessionStore.clear();
    this.router.navigate(['/login']);
  }
}
