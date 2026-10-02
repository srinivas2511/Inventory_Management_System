import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionStore } from '../auth/session.store';
import { PermissionMode, PermissionService } from './permission.service';

/** Route data read by {@link permissionGuard}: {@code data: { permission: 'USER_VIEW' }} (or a list, with {@code permissionMode}). */
export interface PermissionRouteData {
  permission?: string | string[];
  permissionMode?: PermissionMode;
}

/** Only signed-in users pass; everyone else goes to the login page and returns here afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  const session = inject(SessionStore);
  return (
    session.isAuthenticated() || inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } })
  );
};

/** For the login pages: someone already signed in is sent on to the application. */
export const guestGuard: CanActivateFn = () => {
  return !inject(SessionStore).isAuthenticated() || inject(Router).createUrlTree(['/']);
};

/** Requires the permission(s) named in the route's data; otherwise shows the "not permitted" page. UX only. */
export const permissionGuard: CanActivateFn = (route) => {
  const data = route.data as PermissionRouteData;
  return (
    inject(PermissionService).check(data.permission, data.permissionMode) ||
    inject(Router).createUrlTree(['/forbidden'])
  );
};
