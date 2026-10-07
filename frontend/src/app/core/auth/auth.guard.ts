import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { SessionStore } from './session.store';
import { safeReturnUrl } from './return-url';

function toLogin(router: Router, url: string): UrlTree {
  const returnUrl = safeReturnUrl(url);
  return returnUrl
    ? router.createUrlTree(['/login'], { queryParams: { returnUrl } })
    : router.createUrlTree(['/login']);
}

/**
 * Signed in. The session is restored from the refresh cookie before the router resolves (see the APP_INITIALIZER),
 * so a reload or a deep link arrives here already signed in; otherwise the login page remembers where the user was
 * going.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const session = inject(SessionStore);
  const router = inject(Router);
  return session.isLoggedIn() ? true : toLogin(router, state.url);
};

export function permissionGuard(permission: string): CanActivateFn {
  return (_route, state) => {
    const session = inject(SessionStore);
    const router = inject(Router);
    if (!session.isLoggedIn()) {
      return toLogin(router, state.url);
    }
    if (session.hasPermission(permission)) {
      return true;
    }
    return router.createUrlTree(['/dashboard']);
  };
}

/** The login page for someone who is already signed in goes straight to the app. */
export const guestGuard: CanActivateFn = () => {
  const session = inject(SessionStore);
  const router = inject(Router);
  return session.isLoggedIn() ? router.createUrlTree(['/dashboard']) : true;
};
