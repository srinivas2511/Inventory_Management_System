import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, shareReplay, switchMap, tap, throwError } from 'rxjs';
import { AppConfigService } from '../config/app-config.service';
import { TokenStore } from './token.store';
import { SessionStore, SessionUser } from './session.store';
import { safeReturnUrl } from './return-url';

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

  /** One refresh at a time: every caller that needs a new access token while one is running shares its result. */
  private inflightRefresh: Observable<LoginResponse> | null = null;

  private get base(): string {
    return `${this.config.apiBaseUrl}/api/auth`;
  }

  login(req: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.base}/login`, req).pipe(tap((res) => this.accept(res)));
  }

  /**
   * Start-up: the access token lives in memory only, so a reload or a deep link begins signed out. The HttpOnly
   * refresh cookie is still there, so ask the server for a new session. It never navigates and never fails: false
   * means nobody is signed in and the router sends the user to the login page with the page they asked for.
   */
  restoreSession(): Observable<boolean> {
    return this.http.post<LoginResponse>(`${this.base}/refresh`, {}).pipe(
      tap((res) => this.accept(res)),
      map(() => true),
      catchError(() => of(false)),
    );
  }

  /** Single-flight refresh. A rejected refresh ends the session once, for every caller that was waiting on it. */
  refresh(): Observable<LoginResponse> {
    if (!this.inflightRefresh) {
      this.inflightRefresh = this.http.post<LoginResponse>(`${this.base}/refresh`, {}).pipe(
        tap((res) => this.accept(res)),
        catchError((err) => {
          this.expireSession();
          return throwError(() => err);
        }),
        finalize(() => {
          this.inflightRefresh = null;
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.inflightRefresh;
  }

  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>(`${this.base}/forgot-password`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.base}/reset-password`, { token, newPassword });
  }

  /**
   * The server ends every other session of the user and stops accepting the access token this one holds, so a
   * successful change is followed by a refresh: the user stays signed in here, with the cleared must-change flag.
   */
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    const request = this.http.post<void>(`${this.base}/change-password`, { currentPassword, newPassword });
    return request.pipe(
      switchMap(() => this.refresh()),
      map(() => undefined),
    );
  }

  /** Asks the server to end the session (it needs only the refresh cookie), then clears the browser state either way. */
  logout(): void {
    const request = this.http.post<void>(`${this.base}/logout`, {});
    request
      .pipe(
        catchError(() => of(undefined)),
        finalize(() => {
          this.clear();
          this.router.navigate(['/login']);
        }),
      )
      .subscribe();
  }

  /** The session ran out (for example the refresh token expired): back to login, remembering where the user was. */
  expireSession(): void {
    const returnUrl = safeReturnUrl(this.router.url);
    this.clear();
    if (returnUrl) {
      this.router.navigate(['/login'], { queryParams: { returnUrl } });
    } else {
      this.router.navigate(['/login']);
    }
  }

  private accept(res: LoginResponse): void {
    this.tokenStore.set(res.accessToken);
    this.sessionStore.setUser({ ...res.user, mustChangePassword: res.mustChangePassword });
  }

  private clear(): void {
    this.tokenStore.clear();
    this.sessionStore.clear();
  }
}
