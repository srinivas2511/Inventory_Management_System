import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, tap } from 'rxjs';
import { ToastService } from '../notification/toast.service';
import { AuthApi } from './auth.api';
import { LoginOutcome, LoginResponse, UserProfile } from './auth.models';
import { TokenStore } from './token.store';

export type SessionStatus = 'unknown' | 'anonymous' | 'authenticated';

/**
 * The signed-in user and their permissions (ARCHITECTURE.md section 13.3). The access token lives in memory only
 * ({@link TokenStore}); the refresh token is an HttpOnly cookie the browser handles. Everything the UI decides from
 * permissions is a convenience: the backend enforces access on every call.
 */
@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly api = inject(AuthApi);
  private readonly tokens = inject(TokenStore);
  private readonly toast = inject(ToastService);

  private readonly statusSignal = signal<SessionStatus>('unknown');
  private readonly userSignal = signal<UserProfile | null>(null);
  /** The one refresh in flight, shared by every caller (several requests may expire together). */
  private refreshing: Observable<boolean> | null = null;
  private expiryAnnounced = false;

  readonly status = this.statusSignal.asReadonly();
  readonly user = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.statusSignal() === 'authenticated');
  readonly permissions = computed(() => new Set(this.userSignal()?.permissions ?? []));

  /** Called once at start-up: resumes a session from the refresh cookie, if there is one. */
  async restore(): Promise<void> {
    await new Promise<void>((resolve) => this.refresh().subscribe(() => resolve()));
    if (this.statusSignal() === 'unknown') {
      this.statusSignal.set('anonymous');
    }
  }

  /** Signs in. Resolves {@code mustChangePassword} when the account needs a new password before it gets a session. */
  login(username: string, password: string): Observable<LoginOutcome> {
    return this.api.login(username, password).pipe(map((response) => this.accept(response)));
  }

  /** Sets a new password using the current one (forced first-login change or voluntary) and signs in. */
  changePassword(username: string, currentPassword: string, newPassword: string): Observable<LoginOutcome> {
    return this.api
      .changePassword(username, currentPassword, newPassword)
      .pipe(map((response) => this.accept(response)));
  }

  /**
   * Gets a new access token from the refresh cookie. Concurrent callers share one request. Emits false (after
   * clearing the session) if the cookie is missing, expired or was revoked.
   */
  refresh(): Observable<boolean> {
    if (!this.refreshing) {
      this.refreshing = this.api.refresh().pipe(
        map((response) => this.accept(response) === 'authenticated'),
        catchError(() => {
          this.clear();
          return of(false);
        }),
        finalize(() => (this.refreshing = null)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.refreshing;
  }

  /** Ends the session on the server (revoking the refresh token family) and here, even if the call fails. */
  logout(): Observable<void> {
    return this.api.logout().pipe(
      catchError(() => of(undefined)),
      tap(() => this.clear()),
    );
  }

  /** Forgets the session locally. */
  clear(): void {
    this.tokens.clear();
    this.userSignal.set(null);
    this.statusSignal.set('anonymous');
  }

  /** Announces an expired session once, however many requests discover it at the same moment. */
  announceExpiry(): void {
    if (!this.expiryAnnounced) {
      this.expiryAnnounced = true;
      this.toast.info('Your session has ended. Please sign in again.');
    }
  }

  private accept(response: LoginResponse): LoginOutcome {
    if (response.mustChangePassword || !response.accessToken || !response.user) {
      return 'mustChangePassword';
    }
    this.tokens.set(response.accessToken);
    this.userSignal.set(response.user);
    this.statusSignal.set('authenticated');
    this.expiryAnnounced = false;
    return 'authenticated';
  }
}
