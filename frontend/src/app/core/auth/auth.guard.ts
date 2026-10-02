import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionStore } from './session.store';

export const authGuard: CanActivateFn = () => {
  const session = inject(SessionStore);
  const router = inject(Router);
  if (session.isLoggedIn()) {
    return true;
  }
  return router.createUrlTree(['/login']);
};

export function permissionGuard(permission: string): CanActivateFn {
  return () => {
    const session = inject(SessionStore);
    const router = inject(Router);
    if (!session.isLoggedIn()) {
      return router.createUrlTree(['/login']);
    }
    if (session.hasPermission(permission)) {
      return true;
    }
    return router.createUrlTree(['/dashboard']);
  };
}
