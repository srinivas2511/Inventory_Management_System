import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SessionStore } from '../auth/session.store';
import { safeReturnUrl } from '../auth/return-url';
import { detailWithFieldErrors, isProblemDetail } from '../models/problem.model';
import { ToastService } from '../notification/toast.service';

/**
 * Maps API problems to user-facing toasts. Validation errors (400) are left to forms to show next to the
 * fields; 401 handling (redirect to login) arrives with authentication in Phase 1.
 *
 * The server refuses everything except the password-change calls while a temporary password is in use and says so
 * with 403 PASSWORD_CHANGE_REQUIRED. That is not a permission problem: the user is taken to the change-password
 * page (staying signed in, so there is no sign-out loop) and no toast is shown.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  const router = inject(Router);
  const session = inject(SessionStore);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        const problem = isProblemDetail(error.error) ? error.error : null;
        if (error.status === 403 && problem?.code === 'PASSWORD_CHANGE_REQUIRED') {
          const user = session.user();
          if (user) {
            session.setUser({ ...user, mustChangePassword: true });
          }
          if (!router.url.startsWith('/change-password')) {
            const returnUrl = safeReturnUrl(router.url);
            void router.navigate(['/change-password'], returnUrl ? { queryParams: { returnUrl } } : {});
          }
        } else if (error.status === 403 && problem?.code === 'STEP_UP_REQUIRED') {
          // the user cancelled the password prompt: the action simply did not happen, so no permission toast
        } else if (error.status === 403 && problem?.code === 'MODULE_INACTIVE') {
          toast.error(problem.detail ?? 'This module is not active.');
        } else if (error.status === 0) {
          toast.error('Cannot reach the server. Check your connection and try again.');
        } else if (error.status === 403) {
          toast.error('You do not have permission to perform this action.');
        } else if (error.status >= 500) {
          toast.error(`Something went wrong on the server.${problem?.traceId ? ` Reference: ${problem.traceId}` : ''}`);
        } else if (error.status !== 400 && error.status !== 401) {
          toast.error(problem?.detail ?? problem?.title ?? 'The request could not be completed.');
        }
        // Forms read `error.detail`; a bare "Validation failed" hides which fields failed (D-31), so name them there.
        if (error.status === 400 && problem?.fieldErrors?.length) {
          return throwError(
            () =>
              new HttpErrorResponse({
                error: { ...problem, detail: detailWithFieldErrors(problem, 'Validation failed') },
                headers: error.headers,
                status: error.status,
                statusText: error.statusText,
                url: error.url ?? undefined,
              }),
          );
        }
      }
      return throwError(() => error);
    }),
  );
};
