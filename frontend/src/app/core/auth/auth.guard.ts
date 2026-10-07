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

function toChangePassword(router: Router, url: string): UrlTree {
  const returnUrl = safeReturnUrl(url);
  return returnUrl
    ? router.createUrlTree(['/change-password'], { queryParams: { returnUrl } })
    : router.createUrlTree(['/change-password']);
}

/**
 * Signed in, and not in the middle of a forced first-login password change. The session is restored from the
 * refresh cookie before the router resolves (see the APP_INITIALIZER), so a reload or a deep link arrives here
 * already signed in; otherwise the login page remembers where the user was going.
 *
 * The forced change is enforced here, in the browser, as a convenience for the user: the server does not refuse
 * other calls while a temporary password is in use.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const session = inject(SessionStore);
  const router = inject(Router);
  if (!session.isLoggedIn()) {
    return toLogin(router, state.url);
  }
  if (session.mustChangePassword()) {
    return toChangePassword(router, state.url);
  }
  return true;
};

export function permissionGuard(permission: string): CanActivateFn {
  return (_route, state) => {
    const session = inject(SessionStore);
    const router = inject(Router);
    if (!session.isLoggedIn()) {
      return toLogin(router, state.url);
    }
    if (session.mustChangePassword()) {
      return toChangePassword(router, state.url);
    }
    if (session.hasPermission(permission)) {
      return true;
    }
    return router.createUrlTree(['/dashboard']);
  };
}

/** The change-password page itself: signed in is enough, including while the change is forced. */
export const signedInGuard: CanActivateFn = (_route, state) => {
  const session = inject(SessionStore);
  const router = inject(Router);
  return session.isLoggedIn() ? true : toLogin(router, state.url);
};

/** The login page for someone who is already signed in goes straight to the app. */
export const guestGuard: CanActivateFn = () => {
  const session = inject(SessionStore);
  const router = inject(Router);
  return session.isLoggedIn() ? router.createUrlTree(['/dashboard']) : true;
};
