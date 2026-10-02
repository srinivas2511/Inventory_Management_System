import { Injectable, computed, signal } from '@angular/core';

export interface SessionUser {
  id: number;
  username: string;
  fullName: string;
  email: string;
  roles: string[];
  permissions: string[];
  mustChangePassword: boolean;
}

@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly _user = signal<SessionUser | null>(null);

  readonly user = this._user.asReadonly();
  readonly isLoggedIn = computed(() => this._user() !== null);
  readonly permissions = computed(() => new Set(this._user()?.permissions ?? []));
  readonly mustChangePassword = computed(() => this._user()?.mustChangePassword ?? false);

  setUser(user: SessionUser): void {
    this._user.set(user);
  }

  clear(): void {
    this._user.set(null);
  }

  hasPermission(code: string): boolean {
    return this.permissions().has(code);
  }

  hasAnyPermission(codes: string[]): boolean {
    const perms = this.permissions();
    return codes.some((c) => perms.has(c));
  }

  hasAllPermissions(codes: string[]): boolean {
    const perms = this.permissions();
    return codes.every((c) => perms.has(c));
  }
}
