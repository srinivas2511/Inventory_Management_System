import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { SessionStore } from '../auth/session.store';
import { TokenStore } from '../auth/token.store';
import { AppConfigService } from '../config/app-config.service';

/** Calls that establish or end a session. A 401 from them is an answer, not an expired token. */
export function isSessionCall(url: string, apiBase: string): boolean {
  return /^\/auth\/(login|change-password|refresh|logout|forgot-password|reset-password)$/.test(
    url.substring(apiBase.length).split('?')[0],
  );
}

/**
 * Adds the bearer token to API calls and recovers from an expired one: on 401 it refreshes once (concurrent
 * failures share a single refresh), retries the request with the new token, and if the session cannot be renewed
 * sends the user to the login page.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokens = inject(TokenStore);
  const session = inject(SessionStore);
  const router = inject(Router);
  const apiBase = inject(AppConfigService).apiBaseUrl;

  if (!req.url.startsWith(apiBase)) {
    return next(req);
  }
  const withToken = (request: HttpRequest<unknown>) => {
    const token = tokens.accessToken();
    return token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
  };

  return next(withToken(req)).pipe(
    catchError((error: unknown) => {
      const expired =
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !isSessionCall(req.url, apiBase) &&
        session.isAuthenticated();
      if (!expired) {
        return throwError(() => error);
      }
      return session.refresh().pipe(
        switchMap((renewed) => {
          if (renewed) {
            return next(withToken(req));
          }
          session.announceExpiry();
          void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
          return throwError(() => error);
        }),
      );
    }),
  );
};
