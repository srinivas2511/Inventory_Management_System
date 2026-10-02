import { Injectable, inject } from '@angular/core';
import { SessionStore } from '../auth/session.store';

export type PermissionMode = 'any' | 'all';

/**
 * Answers "may the signed-in user do X?" from the permission set returned at sign-in. The answers drive menus,
 * routes and buttons only (UX); the backend decides what actually happens. Reads signals, so templates, computed
 * values and effects that call it update when the session changes.
 */
@Injectable({ providedIn: 'root' })
export class PermissionService {
  private readonly session = inject(SessionStore);

  has(code: string): boolean {
    return this.session.permissions().has(code);
  }

  hasAny(codes: readonly string[]): boolean {
    return codes.some((code) => this.has(code));
  }

  hasAll(codes: readonly string[]): boolean {
    return codes.every((code) => this.has(code));
  }

  /** No requirement means allowed; a list means any (default) or all of the codes. */
  check(required: string | readonly string[] | null | undefined, mode: PermissionMode = 'any'): boolean {
    if (required === null || required === undefined) {
      return true;
    }
    const codes = typeof required === 'string' ? [required] : required;
    if (codes.length === 0) {
      return true;
    }
    return mode === 'all' ? this.hasAll(codes) : this.hasAny(codes);
  }
}
