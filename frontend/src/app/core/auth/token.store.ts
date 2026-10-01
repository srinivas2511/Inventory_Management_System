import { Injectable, signal } from '@angular/core';

/**
 * Holds the short-lived access token in memory only (never localStorage - ARCHITECTURE.md section 10.1).
 * Login, refresh and logout flows are added in Phase 1 (PLAN task 1.10).
 */
@Injectable({ providedIn: 'root' })
export class TokenStore {
  private readonly token = signal<string | null>(null);

  readonly accessToken = this.token.asReadonly();

  set(token: string | null): void {
    this.token.set(token);
  }

  clear(): void {
    this.token.set(null);
  }
}
